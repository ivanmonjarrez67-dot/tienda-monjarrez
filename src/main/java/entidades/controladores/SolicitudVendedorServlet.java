package entidades.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import entidades.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/guardarSolicitud")
public class SolicitudVendedorServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // Obtener datos del formulario
        String provincia = request.getParameter("provincia");
        String canton = request.getParameter("canton");
        String descripcion = request.getParameter("descripcion");
        String precioPromedioStr = request.getParameter("precio_promedio");
        String telefono = request.getParameter("telefono");
        String usuarioIdStr = request.getParameter("usuario_id"); // Recibimos el ID desde JS

        if (usuarioIdStr == null || usuarioIdStr.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Falta usuario_id");
            return;
        }

        int usuarioId;
        try {
            usuarioId = Integer.parseInt(usuarioIdStr);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "usuario_id inválido.");
            return;
        }

        // 🔧 Validación agregada: un usuario_id <= 0 (por ejemplo -1, que
        // /registroVendedor devuelve cuando el correo ya existía) nunca
        // corresponde a un usuario real. Antes esto llegaba hasta el
        // INSERT y rompía con un 500 por violar la llave foránea.
        if (usuarioId <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "usuario_id inválido: el registro de usuario no se completó correctamente.");
            return;
        }

        // Validar y convertir precio
        double precioPromedio = 0;
        if (precioPromedioStr != null && !precioPromedioStr.trim().isEmpty()) {
            try {
                precioPromedio = Double.parseDouble(precioPromedioStr.trim());
            } catch (NumberFormatException e) {
                // Si no es válido, dejamos precio en 0
            }
        }

        String sqlCheckUsuario = "SELECT id FROM Usuarios WHERE id = ?";
        String sqlInsert = "INSERT INTO SolicitudesDeVendedor "
                   + "(usuario_id, provincia, canton, descripcion, precio_promedio, telefono, estado) "
                   + "VALUES (?, ?, ?, ?, ?, ?, 'Pendiente')";

        try (Connection conn = DatabaseConnection.getConnection()) {

            // 🔧 Verificamos que el usuario exista antes de intentar el
            // INSERT, para devolver un mensaje claro en vez de un 500
            // genérico si el usuario_id no es válido (registro incompleto,
            // usuario borrado, etc.).
            boolean usuarioExiste;
            try (PreparedStatement chk = conn.prepareStatement(sqlCheckUsuario)) {
                chk.setInt(1, usuarioId);
                try (ResultSet rs = chk.executeQuery()) {
                    usuarioExiste = rs.next();
                }
            }

            if (!usuarioExiste) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "No existe un usuario con ese id. Vuelva a iniciar el registro.");
                return;
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
                pstmt.setInt(1, usuarioId);
                pstmt.setString(2, provincia);
                pstmt.setString(3, canton);
                pstmt.setString(4, descripcion);
                pstmt.setDouble(5, precioPromedio);
                pstmt.setString(6, telefono);

                pstmt.executeUpdate();
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"mensaje\":\"Solicitud guardada\"}");

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error al guardar la solicitud.");
        }
    }
}