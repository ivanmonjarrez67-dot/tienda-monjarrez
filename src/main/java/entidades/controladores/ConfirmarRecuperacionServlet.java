package entidades.controladores;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;

import entidades.DatabaseConnection;
import entidades.RecuperacionPasswordStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/recuperar/confirmar")
public class ConfirmarRecuperacionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/plain; charset=UTF-8");

        String correo = request.getParameter("correo");
        String codigo = request.getParameter("codigo");
        String nuevaContraseña = request.getParameter("nuevaContraseña");

        if (correo == null || correo.trim().isEmpty()
                || codigo == null || codigo.trim().isEmpty()
                || nuevaContraseña == null || nuevaContraseña.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Debe completar todos los campos");
            return;
        }

        if (nuevaContraseña.length() < 6) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("La nueva contraseña debe tener al menos 6 caracteres");
            return;
        }

        String correoClave = correo.trim().toLowerCase();
        String resultado = RecuperacionPasswordStore.validarCodigo(correoClave, codigo.trim());

        switch (resultado) {
            case "EXPIRADO":
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("El código expiró o no es válido. Solicita uno nuevo.");
                return;
            case "BLOQUEADO":
                response.setStatus(429); // Too Many Requests
                response.getWriter().write("Demasiados intentos. Solicita un nuevo código.");
                return;
            case "INCORRECTO":
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Código incorrecto.");
                return;
            default:
                // "OK" → continúa abajo
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String hashNueva = hashSHA256(nuevaContraseña);
            String sql = "UPDATE Usuarios SET contraseña = ? WHERE correo = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, hashNueva);
                pstmt.setString(2, correo.trim());
                int filas = pstmt.executeUpdate();

                if (filas > 0) {
                    RecuperacionPasswordStore.invalidar(correoClave);
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().write("OK");
                    System.out.println("[ConfirmarRecuperacionServlet] ✅ Contraseña actualizada para " + correoClave);
                } else {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("No se pudo actualizar la contraseña.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error en el servidor: " + e.getMessage());
        }
    }

    private String hashSHA256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}