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
        String usuarioIdStr = request.getParameter("usuario_id");
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

        // 🔧 Validación agregada: un usuario_id <= 0 (por ejemplo -1, que
        // /registroVendedor devuelve cuando el correo ya existía) nunca
        // corresponde a un usuario real. Antes esto llegaba hasta el
        // INSERT y rompía con un 500 por violar la llave foránea.
        if (usuarioId <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "usuario_id inválido: el registro de usuario no se completó correctamente.");
            return;
        }

        // Validar campos obligatorios
        if (tipoSuscripcion == null || metodoPago == null || cedula == null ||
            tipoSuscripcion.isEmpty() || metodoPago.isEmpty() || cedula.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Faltan datos obligatorios.");
            return;
        }

        String sqlCheckUsuario = "SELECT nombre, correo FROM Usuarios WHERE id = ?";
        String sqlCheckVendedor = "SELECT id FROM Vendedores WHERE usuario_id = ?";
        String sqlInsert = "INSERT INTO Vendedores (usuario_id, suscrito, tipo_suscripcion, metodo_de_pago, cedula) "
                         + "VALUES (?, 0, ?, ?, ?)";
        String sqlUpdate = "UPDATE Vendedores SET tipo_suscripcion = ?, metodo_de_pago = ?, cedula = ?, suscrito = 0 "
                         + "WHERE usuario_id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {

            // 🔧 Verificamos que el usuario exista antes de tocar Vendedores,
            // igual que en SolicitudVendedorServlet.
            String nombreUsuario = null;
            String correoUsuario = null;
            try (PreparedStatement chkUsuario = conn.prepareStatement(sqlCheckUsuario)) {
                chkUsuario.setInt(1, usuarioId);
                try (ResultSet rs = chkUsuario.executeQuery()) {
                    if (rs.next()) {
                        nombreUsuario = rs.getString("nombre");
                        correoUsuario = rs.getString("correo");
                    }
                }
            }

            if (correoUsuario == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "No existe un usuario con ese id. Vuelva a iniciar el registro.");
                return;
            }

            // 🔧 Si el usuario ya tiene una fila en Vendedores (por ejemplo,
            // volvió a intentar el registro), actualizamos en vez de
            // insertar de nuevo, para no chocar con la llave única de
            // usuario_id y devolver un 500.
            boolean yaExiste;
            try (PreparedStatement chkVendedor = conn.prepareStatement(sqlCheckVendedor)) {
                chkVendedor.setInt(1, usuarioId);
                try (ResultSet rs = chkVendedor.executeQuery()) {
                    yaExiste = rs.next();
                }
            }

            if (yaExiste) {
                try (PreparedStatement upd = conn.prepareStatement(sqlUpdate)) {
                    upd.setString(1, tipoSuscripcion);
                    upd.setString(2, metodoPago);
                    upd.setString(3, cedula);
                    upd.setInt(4, usuarioId);
                    upd.executeUpdate();
                }
            } else {
                try (PreparedStatement ins = conn.prepareStatement(sqlInsert)) {
                    ins.setInt(1, usuarioId);
                    ins.setString(2, tipoSuscripcion);
                    ins.setString(3, metodoPago);
                    ins.setString(4, cedula);
                    ins.executeUpdate();
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"mensaje\":\"Suscripción guardada\"}");

            // 📩 Avisar al vendedor que su suscripción quedó pendiente de revisión
            try {
                EmailService.enviarSuscripcionEnRevision(correoUsuario, nombreUsuario, tipoSuscripcion);
            } catch (Exception e) {
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