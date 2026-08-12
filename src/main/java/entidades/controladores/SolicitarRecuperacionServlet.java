package entidades.controladores;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import entidades.DatabaseConnection;
import entidades.EmailService;
import entidades.RecuperacionPasswordStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/recuperar/solicitar")
public class SolicitarRecuperacionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long COOLDOWN_MS = 60_000; // 1 minuto entre solicitudes por correo

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/plain; charset=UTF-8");
        String correo = request.getParameter("correo");

        if (correo == null || correo.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Debe ingresar un correo");
            return;
        }

        String correoClave = correo.trim().toLowerCase();
        // Mensaje SIEMPRE igual, exista o no el correo — no delatamos si está registrado.
        String mensajeGenerico = "Si el correo está registrado, te enviamos un código de verificación.";

        if (!RecuperacionPasswordStore.puedeSolicitar(correoClave, COOLDOWN_MS)) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(mensajeGenerico);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT nombre, correo FROM Usuarios WHERE correo = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, correo.trim());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String codigo = generarCodigo();
                        RecuperacionPasswordStore.guardarCodigo(correoClave, codigo);
                        EmailService.enviarCodigoRecuperacion(rs.getString("correo"), rs.getString("nombre"), codigo);
                        System.out.println("[SolicitarRecuperacionServlet] 📩 Código enviado a " + correoClave);
                    } else {
                        System.out.println("[SolicitarRecuperacionServlet] Correo no registrado: " + correoClave);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // No delatamos el error interno — igual respondemos el mensaje genérico.
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(mensajeGenerico);
    }

    private String generarCodigo() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}