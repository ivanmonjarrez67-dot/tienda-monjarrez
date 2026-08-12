package entidades.controladores;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import entidades.DatabaseConnection;
import entidades.EmailService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/LoginCompradorServlet")
public class LoginCompradorServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // Contador de intentos fallidos en memoria, por correo (en minúsculas).
    // NOTA: se reinicia si el servidor se reinicia — solución simple, no persistente.
    private static final ConcurrentHashMap<String, AtomicInteger> intentosFallidos = new ConcurrentHashMap<>();
    private static final int MAX_INTENTOS = 3;

    // 🆕 Bloqueo temporal tras superar MAX_INTENTOS. Guarda, por correo,
    // el timestamp (epoch ms) hasta el cual el login queda bloqueado.
    private static final ConcurrentHashMap<String, Long> bloqueadosHasta = new ConcurrentHashMap<>();
    private static final long BLOQUEO_MS = 15 * 60 * 1000L; // 15 minutos

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String correo = request.getParameter("correo");
        String contraseña = request.getParameter("contraseña");

        if (correo == null || correo.trim().isEmpty() || contraseña == null || contraseña.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Debe ingresar todos los datos");
            return;
        }

        String correoClave = correo.trim().toLowerCase();

        // 🆕 Si está bloqueado, ni siquiera se valida la contraseña.
        Long bloqueadoHasta = bloqueadosHasta.get(correoClave);
        if (bloqueadoHasta != null) {
            long restanteMs = bloqueadoHasta - System.currentTimeMillis();
            if (restanteMs > 0) {
                long minutosRestantes = (long) Math.ceil(restanteMs / 60000.0);
                response.setStatus(429); // Too Many Requests
                response.getWriter().write(
                    "Demasiados intentos fallidos. Por seguridad, tu acceso está bloqueado temporalmente. " +
                    "Intenta de nuevo en " + minutosRestantes + " minuto(s), o usa \"¿Olvidaste tu contraseña?\"."
                );
                return;
            } else {
                // Ya expiró el bloqueo
                bloqueadosHasta.remove(correoClave);
            }
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String hashedPassword = hashContraseña(contraseña);

            String sql = """
                SELECT id AS id_usuario, nombre, correo
                FROM Usuarios
                WHERE correo = ? AND contraseña = ?
            """;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, correo);
                pstmt.setString(2, hashedPassword);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        // Login correcto: se limpia el contador de fallos y cualquier bloqueo
                        intentosFallidos.remove(correoClave);
                        bloqueadosHasta.remove(correoClave);

                        HttpSession session = request.getSession();
                        session.setAttribute("usuarioId", rs.getInt("id_usuario"));
                        session.setAttribute("nombreUsuario", rs.getString("nombre"));
                        session.setAttribute("correoUsuario", rs.getString("correo"));

                        response.setStatus(HttpServletResponse.SC_OK);
                        response.getWriter().write("OK");
                    } else {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("Credenciales incorrectas");

                        int intentos = intentosFallidos
                                .computeIfAbsent(correoClave, k -> new AtomicInteger(0))
                                .incrementAndGet();

                        System.out.println("[LoginCompradorServlet] ⚠️ Intento fallido #" + intentos + " para " + correoClave);

                        if (intentos >= MAX_INTENTOS) {
                            // 🆕 Se activa el bloqueo temporal
                            bloqueadosHasta.put(correoClave, System.currentTimeMillis() + BLOQUEO_MS);

                            // Buscar el nombre SOLO por correo (sin validar contraseña) para poder alertar
                            String sqlUsuario = "SELECT nombre, correo FROM Usuarios WHERE correo = ?";
                            try (PreparedStatement stmtUsuario = conn.prepareStatement(sqlUsuario)) {
                                stmtUsuario.setString(1, correo);
                                try (ResultSet rsUsuario = stmtUsuario.executeQuery()) {
                                    if (rsUsuario.next()) {
                                        EmailService.enviarAlertaLoginSospechoso(
                                                rsUsuario.getString("correo"),
                                                rsUsuario.getString("nombre")
                                        );
                                        System.out.println("[LoginCompradorServlet] 📩 Alerta de login sospechoso enviada a " + correoClave);
                                    }
                                }
                            }
                            intentosFallidos.remove(correoClave);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error en el servidor: " + e.getMessage());
        }
    }

    private String hashContraseña(String contraseña) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contraseña.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}