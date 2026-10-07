package entidades.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import entidades.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Permite entrar a "Mi tienda" sin cédula ni contraseña cuando este dispositivo
 * tiene una sesión larga de vendedor (SesionPersistenteFilter ya la restauró).
 *
 * Aplica las MISMAS reglas de suscripción que /api/mi-tienda-login (suscrito = 1 y
 * no vencida), así una sesión recordada nunca salta el control de pago.
 *
 * Responde:
 *   200 "OK:usuarioId:tipoSuscripcion"  → entrar directo
 *   401                                 → no hay sesión completa de vendedor (el front abre el login)
 *   403 "mensaje"                       → suscripción pendiente o vencida
 */
@WebServlet("/api/mi-tienda-sesion")
public class MiTiendaSesionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        HttpSession s = request.getSession(false);
        if (s == null || s.getAttribute("usuarioId") == null
                || s.getAttribute("vendedorId") == null
                || s.getAttribute("sesionLimitada") != null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        int usuarioId = (Integer) s.getAttribute("usuarioId");

        String sql = """
                SELECT v.suscrito, v.tipo_suscripcion, sv.fecha_vencimiento
                FROM Vendedores v
                LEFT JOIN SuscripcionVendedor sv ON sv.usuario_id = v.usuario_id
                WHERE v.usuario_id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                int suscrito = rs.getInt("suscrito");
                String tipoSuscripcion = rs.getString("tipo_suscripcion");
                java.sql.Date fechaVencimiento = rs.getDate("fecha_vencimiento");

                boolean vencida = suscrito == 1 && fechaVencimiento != null
                        && !java.time.LocalDate.now().isBefore(fechaVencimiento.toLocalDate());

                if (vencida) {
                    // Misma revisión perezosa que /api/mi-tienda-login
                    try (PreparedStatement up1 = conn.prepareStatement(
                            "UPDATE Vendedores SET suscrito = 0 WHERE usuario_id = ?")) {
                        up1.setInt(1, usuarioId);
                        up1.executeUpdate();
                    }
                    try (PreparedStatement up2 = conn.prepareStatement(
                            "UPDATE SolicitudesDeVendedor SET estado = 'Pendiente' WHERE usuario_id = ?")) {
                        up2.setInt(1, usuarioId);
                        up2.executeUpdate();
                    }
                    System.out.println("[MiTiendaSesionServlet] ⏳ Suscripción vencida para usuario " + usuarioId + ", revertida a pendiente");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write(
                            "Tu suscripción venció. Debes renovar el pago para volver a acceder a 'Mi Tienda'.\n\n" +
                                    "📩 Para consultas puede escribirnos a: tiendamonjarrez@gmail.com");
                    return;
                }
                if (suscrito != 1) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write(
                            "Su solicitud de acceso a 'Mi Tienda' está pendiente. " +
                                    "Debe esperar la aprobación de un administrador de Monjarrez. " +
                                    "Una vez aprobada, podrá gestionar su tienda con normalidad.\n\n" +
                                    "📩 Para consultas puede escribirnos a: tiendamonjarrez@gmail.com");
                    return;
                }
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write("OK:" + usuarioId + ":" + tipoSuscripcion);
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error en el servidor: " + e.getMessage());
        }
    }
}