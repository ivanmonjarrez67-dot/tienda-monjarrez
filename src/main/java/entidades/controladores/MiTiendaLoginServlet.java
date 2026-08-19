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

@WebServlet("/api/mi-tienda-login")
public class MiTiendaLoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // Contador de intentos fallidos en memoria, por cédula.
    // NOTA: se reinicia si el servidor se reinicia — es una solución simple
    // para empezar, no persistente en base de datos.
    private static final ConcurrentHashMap<String, AtomicInteger> intentosFallidos = new ConcurrentHashMap<>();
    private static final int MAX_INTENTOS = 3;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String cedula = request.getParameter("cedula");
        String contraseña = request.getParameter("password");

        if (cedula == null || cedula.trim().isEmpty() || contraseña == null || contraseña.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Debe ingresar todos los datos");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String hashedPassword = hashContraseña(contraseña);

            String sql = """
                        SELECT v.id AS id_vendedor,
                               v.usuario_id,
                               u.nombre AS nombre_vendedor,
                               u.correo,
                               v.suscrito,
                               v.tipo_suscripcion,
                               sv.fecha_vencimiento
                        FROM Vendedores v
                        INNER JOIN Usuarios u ON v.usuario_id = u.id
                        LEFT JOIN SuscripcionVendedor sv ON sv.usuario_id = v.usuario_id
                        WHERE v.cedula = ?
                          AND u.contraseña = ?
                          AND u.tipo = 'Vendedor'
                    """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, cedula);
                stmt.setString(2, hashedPassword);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int suscrito = rs.getInt("suscrito");
                        int usuarioId = rs.getInt("usuario_id");
                        java.sql.Date fechaVencimiento = rs.getDate("fecha_vencimiento");

                        // Login con credenciales correctas: se limpia el contador de fallos
                        intentosFallidos.remove(cedula);

                        // Revisión perezosa: si estaba suscrito pero ya pasó la fecha de
                        // vencimiento, se revierte aquí mismo antes de dejarlo entrar
                        boolean vencida = false;
                        if (suscrito == 1 && fechaVencimiento != null
                                && !java.time.LocalDate.now().isBefore(fechaVencimiento.toLocalDate())) {
                            vencida = true;
                            try (PreparedStatement stmtVencer = conn.prepareStatement(
                                    "UPDATE Vendedores SET suscrito = 0 WHERE usuario_id = ?")) {
                                stmtVencer.setInt(1, usuarioId);
                                stmtVencer.executeUpdate();
                            }
                            try (PreparedStatement stmtEstado = conn.prepareStatement(
                                    "UPDATE SolicitudesDeVendedor SET estado = 'Pendiente' WHERE usuario_id = ?")) {
                                stmtEstado.setInt(1, usuarioId);
                                stmtEstado.executeUpdate();
                            }
                            System.out.println("[MiTiendaLoginServlet] ⏳ Suscripción vencida para usuario " + usuarioId + ", revertida a pendiente");
                        }

                        if (suscrito == 1 && !vencida) {
                            HttpSession session = request.getSession();
                            session.setAttribute("vendedorId", rs.getInt("id_vendedor"));
                            session.setAttribute("usuarioId", usuarioId);
                            session.setAttribute("nombreVendedor", rs.getString("nombre_vendedor"));
                            session.setAttribute("correoVendedor", rs.getString("correo"));
                            session.setAttribute("cedulaVendedor", cedula);

                            String tipoSuscripcion = rs.getString("tipo_suscripcion");

                            response.setStatus(HttpServletResponse.SC_OK);
                            response.getWriter().write("OK:" + usuarioId + ":" + tipoSuscripcion);

                            System.out.println("[MiTiendaLoginServlet] ✅ Login exitoso. Usuario ID: " + usuarioId);

                        } else if (vencida) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write(
                                    "Tu suscripción venció. Debes renovar el pago para volver a acceder a 'Mi Tienda'.\n\n" +
                                            "📩 Para consultas puede escribirnos a: tiendamonjarrez@gmail.com");
                        } else {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write(
                                    "Su solicitud de acceso a 'Mi Tienda' está pendiente. " +
                                            "Debe esperar la aprobación de un administrador de Monjarrez. " +
                                            "Una vez aprobada, podrá gestionar su tienda con normalidad.\n\n" +
                                            "📩 Para consultas puede escribirnos a: tiendamonjarrez@gmail.com");
                        }
                    } else {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("Credenciales incorrectas");

                        // Contar el intento fallido para esta cédula
                        int intentos = intentosFallidos
                                .computeIfAbsent(cedula, k -> new AtomicInteger(0))
                                .incrementAndGet();

                        System.out.println("[MiTiendaLoginServlet] ⚠️ Intento fallido #" + intentos + " para cédula " + cedula);

                        if (intentos >= MAX_INTENTOS) {
                            // Buscar al vendedor SOLO por cédula (sin importar la contraseña)
                            // para poder alertarlo, sin revelar si la cédula existe o no en la respuesta HTTP.
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
                                        System.out.println("[MiTiendaLoginServlet] 📩 Alerta de login sospechoso enviada para cédula " + cedula);
                                    }
                                }
                            }
                            // Reiniciamos el contador tras avisar, para no reenviar en cada intento adicional
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