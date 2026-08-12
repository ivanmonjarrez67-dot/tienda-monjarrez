package entidades.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;

import entidades.DatabaseConnection;
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
 * Orden de borrado (importante por las llaves foráneas hacia Usuarios.id):
 *   1. Intereses
 *   2. Productos
 *   3. SolicitudesDeVendedor
 *   4. Vendedores
 *   5. Usuarios   (al final, porque las demás tablas dependen de este id)
 *
 * Todo se hace dentro de una sola transacción: si algo falla, se revierte
 * todo (rollback) y la cuenta no queda a medio borrar.
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

            boolean autoCommitOriginal = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                ejecutarDelete(conn, "DELETE FROM Intereses WHERE usuario_id = ?", usuarioId);
                ejecutarDelete(conn, "DELETE FROM Productos WHERE usuario_id = ?", usuarioId);
                ejecutarDelete(conn, "DELETE FROM SolicitudesDeVendedor WHERE usuario_id = ?", usuarioId);
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

            System.out.println("[EliminarPerfilServlet] 🗑️ Usuario " + usuarioId + " eliminó su cuenta.");

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