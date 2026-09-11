package entidades.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import entidades.DatabaseConnection;
import entidades.EmailService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Elimina de forma permanente la cuenta del usuario actualmente logueado,
 * junto con todos sus datos asociados en las demás tablas.
 *
 * Igual que PerfilServlet y ActualizarPerfilServlet: el usuario_id sale de
 * la SESIÓN del servidor, nunca de un parámetro del cliente — así nadie
 * puede borrar la cuenta de otra persona.
 *
 * Orden de borrado (importante por las llaves foráneas):
 *   1. Intereses                       (usuario_id)
 *   2. ImagenesAdicionalesProducto     (producto_id, de los productos del usuario)
 *   3. Descuentos                      (producto_id, de los productos del usuario)
 *   4. ProductosExtranjeros            (producto_id, de los productos del usuario)
 *   5. Productos                       (usuario_id) — ya sin hijos pendientes
 *   6. SuscripcionVendedor             (usuario_id)
 *   7. SolicitudesDeVendedor           (usuario_id)
 *   8. IconosVendedor                  (vendedor_id, de la fila del usuario en Vendedores)
 *   9. Vendedores                      (usuario_id) — ya sin hijos pendientes
 *  10. Usuarios                        (al final, porque las demás tablas dependen de este id)
 *
 * 🆕 Productos tiene sus propias tablas hijas (imágenes adicionales,
 * descuentos, productos extranjeros) que apuntan a Productos.id. Hay que
 * vaciarlas ANTES de borrar Productos, o SQL Server rechaza el DELETE por
 * la llave foránea (ese era el error "FK_ProductosExtranjeros_Productos").
 *
 * 🆕 Mismo caso con IconosVendedor: su FK (FK_IconosVendedor_Vendedores)
 * apunta a Vendedores.id, no a usuario_id directo. Por eso el DELETE usa
 * una subconsulta a Vendedores en vez de un usuario_id = ? plano, y tiene
 * que ejecutarse ANTES de borrar Vendedores.
 *
 * Todo se hace dentro de una sola transacción: si algo falla, se revierte
 * todo (rollback) y la cuenta no queda a medio borrar.
 *
 * 🆕 Antes de borrar nada, se capturan nombre, correo y tipo (rol) del
 * usuario desde la tabla Usuarios. Esos datos son los ÚLTIMOS que se van
 * a poder leer de esta cuenta, así que se guardan en variables antes del
 * DELETE. Solo si el commit() sale bien se envía el correo de
 * confirmación de eliminación, usando esos datos ya capturados — nunca
 * se manda el correo si el borrado falla.
 */
@WebServlet("/api/perfil/eliminar")
public class EliminarPerfilServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        Object usuarioIdObj = (session != null) ? session.getAttribute("usuarioId") : null;

        if (usuarioIdObj == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("No hay sesión activa");
            return;
        }

        int usuarioId = (Integer) usuarioIdObj;

        try (Connection conn = DatabaseConnection.getConnection()) {

            // 🆕 Capturar los datos del perfil ANTES de borrar nada — son
            // los últimos que vamos a poder leer de esta cuenta.
            // La tabla Usuarios ya trae el rol en la columna "tipo", así
            // que no hace falta consultar Vendedores para inferirlo.
            String nombre = null;
            String correo = null;
            String tipo = null;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT nombre, correo, tipo FROM Usuarios WHERE id = ?")) {
                stmt.setInt(1, usuarioId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        nombre = rs.getString("nombre");
                        correo = rs.getString("correo");
                        tipo = rs.getString("tipo");
                    }
                }
            }

            boolean autoCommitOriginal = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                ejecutarDelete(conn, "DELETE FROM Intereses WHERE usuario_id = ?", usuarioId);

                // 🆕 Las tablas hijas de Productos (imágenes, descuentos,
                // productos extranjeros) hay que vaciarlas ANTES de borrar
                // Productos, usando una subconsulta por producto_id — así
                // no hace falta traer los ids a Java ni armar un IN (...)
                // a mano.
                ejecutarDelete(conn,
                    "DELETE FROM ImagenesAdicionalesProducto WHERE producto_id IN " +
                    "(SELECT id FROM Productos WHERE usuario_id = ?)", usuarioId);
                ejecutarDelete(conn,
                    "DELETE FROM Descuentos WHERE producto_id IN " +
                    "(SELECT id FROM Productos WHERE usuario_id = ?)", usuarioId);
                ejecutarDelete(conn,
                    "DELETE FROM ProductosExtranjeros WHERE producto_id IN " +
                    "(SELECT id FROM Productos WHERE usuario_id = ?)", usuarioId);

                ejecutarDelete(conn, "DELETE FROM Productos WHERE usuario_id = ?", usuarioId);
                ejecutarDelete(conn, "DELETE FROM SuscripcionVendedor WHERE usuario_id = ?", usuarioId);
                ejecutarDelete(conn, "DELETE FROM SolicitudesDeVendedor WHERE usuario_id = ?", usuarioId);

                // 🆕 IconosVendedor.vendedor_id apunta a Vendedores.id (no a
                // usuario_id), así que hay que resolverlo con una
                // subconsulta y borrarlo ANTES de Vendedores, o SQL Server
                // rechaza el DELETE de Vendedores por FK_IconosVendedor_Vendedores.
                ejecutarDelete(conn,
                    "DELETE FROM IconosVendedor WHERE vendedor_id IN " +
                    "(SELECT id FROM Vendedores WHERE usuario_id = ?)", usuarioId);

                ejecutarDelete(conn, "DELETE FROM Vendedores WHERE usuario_id = ?", usuarioId);

                int filasBorradas = ejecutarDelete(conn, "DELETE FROM Usuarios WHERE id = ?", usuarioId);

                if (filasBorradas == 0) {
                    // No existía el usuario (cuenta ya borrada, sesión vieja, etc.)
                    conn.rollback();
                    conn.setAutoCommit(autoCommitOriginal);
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.getWriter().write("Usuario no encontrado");
                    return;
                }

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(autoCommitOriginal);
            }

            // Cerrar la sesión del servidor ya que la cuenta ya no existe
            session.invalidate();

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("Cuenta eliminada correctamente");

            System.out.println("[EliminarPerfilServlet] Usuario " + usuarioId + " eliminó su cuenta.");

            // 🆕 El commit ya fue exitoso — recién ahora se envía el correo,
            // con los datos capturados antes del borrado. Si por algún
            // motivo no se pudo leer el email (fila no encontrada arriba,
            // dato nulo, etc.) simplemente no se envía, sin romper la
            // respuesta al cliente (la cuenta ya se borró de todas formas).
            if (correo != null && !correo.isEmpty()) {
                String rol = "vendedor".equalsIgnoreCase(tipo) ? "vendedor" : "comprador";
                String nombreParaCorreo = (nombre != null && !nombre.isEmpty()) ? nombre : "usuario";
                EmailService.enviarCuentaEliminada(correo, nombreParaCorreo, rol);
            } else {
                System.out.println("[EliminarPerfilServlet] No se pudo obtener el correo del usuario "
                        + usuarioId + "; no se envió el correo de confirmación.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error en el servidor: " + e.getMessage());
        }
    }

    /** Ejecuta un DELETE parametrizado por usuarioId y devuelve las filas afectadas. */
    private int ejecutarDelete(Connection conn, String sql, int usuarioId) throws java.sql.SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, usuarioId);
            return stmt.executeUpdate();
        }
    }
}