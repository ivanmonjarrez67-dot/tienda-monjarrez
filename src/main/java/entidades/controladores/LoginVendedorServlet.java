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

@WebServlet("/LoginVendedorServlet")
public class LoginVendedorServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // Contador de intentos fallidos en memoria, por cédula.
    // NOTA: se reinicia si el servidor se reinicia — solución simple, no persistente.
    private static final ConcurrentHashMap<String, AtomicInteger> intentosFallidos = new ConcurrentHashMap<>();
    private static final int MAX_INTENTOS = 3;

    // 🆕 Bloqueo temporal tras superar MAX_INTENTOS. Guarda, por cédula,
    // el timestamp (epoch ms) hasta el cual el login queda bloqueado.
    private static final ConcurrentHashMap<String, Long> bloqueadosHasta = new ConcurrentHashMap<>();
    private static final long BLOQUEO_MS = 15 * 60 * 1000L; // 15 minutos

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String cedula = request.getParameter("cedula");
        String contraseña = request.getParameter("contraseña");

        if (cedula == null || cedula.trim().isEmpty() || contraseña == null || contraseña.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Debe ingresar todos los datos");
            return;
        }

        // 🆕 Si está bloqueado, ni siquiera se valida la contraseña.
        Long bloqueadoHasta = bloqueadosHasta.get(cedula);
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
                bloqueadosHasta.remove(cedula);
            }
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String hashedPassword = hashContraseña(contraseña);

            // 🔧 Se agrega u.id AS id_usuario — es el id real de la tabla Usuarios,
            // el que PerfilServlet necesita en sesión como "usuarioId". Antes solo
            // se guardaba "vendedorId" (id de la tabla Vendedores), por eso el
            // perfil de un vendedor nunca cargaba aunque el login fuera exitoso.
            String sql = """
                SELECT v.id AS id_vendedor, u.id AS id_usuario, u.nombre AS nombre_vendedor, u.correo
                FROM Vendedores v
                INNER JOIN Usuarios u ON v.usuario_id = u.id
                WHERE v.cedula = ? AND u.contraseña = ?
            """;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, cedula);
                pstmt.setString(2, hashedPassword);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        // Login correcto: se limpia el contador de fallos y cualquier bloqueo
                        intentosFallidos.remove(cedula);
                        bloqueadosHasta.remove(cedula);

                        HttpSession session = request.getSession();
                        session.setAttribute("vendedorId", rs.getInt("id_vendedor"));
                        session.setAttribute("usuarioId", rs.getInt("id_usuario")); // 🔧 nuevo — clave para /api/perfil
                        session.setAttribute("nombreVendedor", rs.getString("nombre_vendedor"));
                        session.setAttribute("correoVendedor", rs.getString("correo"));
                        session.setAttribute("cedulaVendedor", cedula); // 🔑 añadida para "Mi tienda"

                        response.setStatus(HttpServletResponse.SC_OK);
                        response.getWriter().write("OK");
                    } else {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("Credenciales incorrectas");

                        int intentos = intentosFallidos
                                .computeIfAbsent(cedula, k -> new AtomicInteger(0))
                                .incrementAndGet();

                        System.out.println("[LoginVendedorServlet] ⚠️ Intento fallido #" + intentos + " para cédula " + cedula);

                        if (intentos >= MAX_INTENTOS) {
                            // 🆕 Se activa el bloqueo temporal
                            bloqueadosHasta.put(cedula, System.currentTimeMillis() + BLOQUEO_MS);

                            // Buscar al vendedor SOLO por cédula (sin validar contraseña) para poder alertarlo
                            String sqlVendedor = """
                                        SELECT u.nombre AS nombre_vendedor, u.correo
                                        FROM Vendedores v
                                        INNER JOIN Usuarios u ON v.usuario_id = u.id
                                        WHERE v.cedula = ?
                                    """;
                            try (PreparedStatement stmtVendedor = conn.prepareStatement(sqlVendedor)) {
                                stmtVendedor.setString(1, cedula);
                                try (ResultSet rsVendedor = stmtVendedor.executeQuery()) {
                                    if (rsVendedor.next()) {
                                        EmailService.enviarAlertaLoginSospechoso(
                                                rsVendedor.getString("correo"),
                                                rsVendedor.getString("nombre_vendedor"),
                                                cedula
                                        );
                                        System.out.println("[LoginVendedorServlet] 📩 Alerta de login sospechoso enviada para cédula " + cedula);
                                    }
                                }
                            }
                            intentosFallidos.remove(cedula);
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