package entidades.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import entidades.DatabaseConnection;
import entidades.EmailService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/guardarSuscripcion")
public class SuscripcionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // Recibir todos los parámetros desde el JS
        String usuarioIdStr = request.getParameter("usuario_id"); // ✅ ID desde JS
        String tipoSuscripcion = request.getParameter("suscripcion");
        String metodoPago = request.getParameter("metodoPago");
        String cedula = request.getParameter("cedula");
        // Validar usuario_id
        if (usuarioIdStr == null || usuarioIdStr.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Falta usuario_id.");
            return;
        }

        int usuarioId;
        try {
            usuarioId = Integer.parseInt(usuarioIdStr);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "usuario_id inválido.");
            return;
        }

        // Validar campos obligatorios
        if (tipoSuscripcion == null || metodoPago == null || cedula == null ||
            tipoSuscripcion.isEmpty() || metodoPago.isEmpty() || cedula.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Faltan datos obligatorios.");
            return;
        }

        // Inserción en la tabla Vendedores (asegúrate de haber agregado la columna 'cedula')
        String sqlInsert = "INSERT INTO Vendedores (usuario_id, suscrito, tipo_suscripcion, metodo_de_pago, cedula) "
                         + "VALUES (?, 0, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {

            pstmt.setInt(1, usuarioId);
            pstmt.setString(2, tipoSuscripcion);
            pstmt.setString(3, metodoPago);
            pstmt.setString(4, cedula);

            pstmt.executeUpdate();

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"mensaje\":\"Suscripción guardada\"}");

            // 📩 Avisar al vendedor que su suscripción quedó pendiente de revisión
            try {
                String sqlUsuario = "SELECT nombre, correo FROM Usuarios WHERE id = ?";
                try (PreparedStatement stmtUsuario = conn.prepareStatement(sqlUsuario)) {
                    stmtUsuario.setInt(1, usuarioId);
                    try (ResultSet rsUsuario = stmtUsuario.executeQuery()) {
                        if (rsUsuario.next()) {
                            EmailService.enviarSuscripcionEnRevision(
                                rsUsuario.getString("correo"),
                                rsUsuario.getString("nombre"),
                                tipoSuscripcion
                            );
                        }
                    }
                }
            } catch (SQLException e) {
                // No dejamos que un fallo al enviar el correo rompa el guardado de la suscripción
                System.out.println("[SuscripcionServlet] ⚠️ No se pudo enviar el correo de suscripción en revisión: " + e.getMessage());
            }

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error guardando suscripción: " + e.getMessage());
        }
    }
}