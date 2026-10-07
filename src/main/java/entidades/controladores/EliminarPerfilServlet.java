package entidades.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import entidades.CloudinaryService;
import entidades.DatabaseConnection;
import entidades.EmailService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Elimina de forma permanente la cuenta del usuario logueado y todos sus
 * datos asociados. El usuario_id sale de la SESIÓN del servidor, nunca de un
 * parámetro del cliente.
 *
 * Orden de borrado: cada tabla hija se vacía ANTES que la tabla a la que
 * apunta (llaves foráneas). Todo va en una sola transacción: si algo falla
 * se hace rollback y la cuenta no queda a medio borrar.
 *
 * 🆕 Cambios de esta versión:
 *  - ImagenElegidaCarrito: se borra antes de DetalleCarrito/Carrito/Productos
 *    (antes faltaba y podía hacer fallar el borrado por llave foránea).
 *  - Cloudinary: antes de borrar se juntan las URLs de las fotos del usuario
 *    (productos, adicionales, logo de vendedor) y, SOLO si el commit sale
 *    bien, se borran en Cloudinary.
 *  - Correo de confirmación: igual que antes, solo tras el commit.
 */
@WebServlet("/api/perfil/eliminar")
public class EliminarPerfilServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final String PRODUCTOS_DEL_USUARIO = "(SELECT id FROM Productos WHERE usuario_id = ?)";
    private static final String CARRITOS_DEL_USUARIO = "(SELECT id FROM Carrito WHERE usuario_id = ?)";
    private static final String PEDIDOS_DEL_USUARIO = "(SELECT id FROM Pedidos WHERE usuario_id = ?)";
    private static final String SOLICITUDES_DEL_USUARIO = "(SELECT id FROM SolicitudesDeVendedor WHERE usuario_id = ?)";
    private static final String VENDEDORES_DEL_USUARIO = "(SELECT id FROM Vendedores WHERE usuario_id = ?)";

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

            // Datos que ya no se podrán leer después del DELETE
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

            // 🆕 URLs de fotos del usuario (se leen ANTES de borrar las filas)
            List<String> urlsImagenes = new ArrayList<>();
            recolectarUrls(conn, "SELECT imagen FROM Productos WHERE usuario_id = ?", usuarioId, urlsImagenes);
            recolectarUrls(conn, "SELECT * FROM ImagenesAdicionalesProducto WHERE producto_id IN " + PRODUCTOS_DEL_USUARIO, usuarioId, urlsImagenes);
            recolectarUrls(conn, "SELECT * FROM ImagenesProducto WHERE producto_id IN " + PRODUCTOS_DEL_USUARIO, usuarioId, urlsImagenes);
            recolectarUrls(conn, "SELECT * FROM IconosVendedor WHERE vendedor_id IN " + VENDEDORES_DEL_USUARIO, usuarioId, urlsImagenes);

            boolean autoCommitOriginal = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                // ---------- Como usuario/cliente ----------
                ejecutarDelete(conn, "DELETE FROM Intereses WHERE usuario_id = ?", usuarioId);
                ejecutarDelete(conn, "DELETE FROM Compradores WHERE usuario_id = ?", usuarioId);
                ejecutarDelete(conn, "DELETE FROM DatosEnvioUsuario WHERE usuario_id = ?", usuarioId);
                ejecutarDelete(conn, "DELETE FROM Resenas WHERE usuario_id = ?", usuarioId);
                ejecutarDelete(conn, "DELETE FROM ToquesContacto WHERE usuario_id = ?", usuarioId);

                // 🆕 ImagenElegidaCarrito: no sé con certeza cuáles de sus columnas
                // apuntan a otras tablas, así que se borra por cada columna conocida
                // que EXISTA (usuario_id / carrito_id / producto_id).
                borrarImagenElegidaCarrito(conn, usuarioId);

                ejecutarDelete(conn, "DELETE FROM DetalleCarrito WHERE carrito_id IN " + CARRITOS_DEL_USUARIO, usuarioId);
                ejecutarDelete(conn, "DELETE FROM Carrito WHERE usuario_id = ?", usuarioId);

                ejecutarDelete(conn, "DELETE FROM DetallePedido WHERE pedido_id IN " + PEDIDOS_DEL_USUARIO, usuarioId);
                ejecutarDelete(conn, "DELETE FROM Pedidos WHERE usuario_id = ?", usuarioId);

                // ---------- Ligado a SUS productos (si es vendedor) ----------
                ejecutarDelete(conn, "DELETE FROM Resenas WHERE producto_id IN " + PRODUCTOS_DEL_USUARIO, usuarioId);
                ejecutarDelete(conn, "DELETE FROM ToquesContacto WHERE producto_id IN " + PRODUCTOS_DEL_USUARIO, usuarioId);
                ejecutarDelete(conn, "DELETE FROM DetalleCarrito WHERE producto_id IN " + PRODUCTOS_DEL_USUARIO, usuarioId);

                ejecutarDelete(conn, "DELETE FROM DetallePedido WHERE usuario_id_vendedor = ?", usuarioId);
                ejecutarDelete(conn, "DELETE FROM DetallePedido WHERE producto_id IN " + PRODUCTOS_DEL_USUARIO, usuarioId);

                ejecutarDelete(conn, "DELETE FROM ImagenesAdicionalesProducto WHERE producto_id IN " + PRODUCTOS_DEL_USUARIO, usuarioId);
                ejecutarDelete(conn, "DELETE FROM Descuentos WHERE producto_id IN " + PRODUCTOS_DEL_USUARIO, usuarioId);
                ejecutarDelete(conn, "DELETE FROM ProductosExtranjeros WHERE producto_id IN " + PRODUCTOS_DEL_USUARIO, usuarioId);

                ejecutarDelete(conn, "DELETE FROM Productos WHERE usuario_id = ?", usuarioId);

                // ---------- Registro de vendedor y tabla padre ----------
                ejecutarDelete(conn, "DELETE FROM SuscripcionVendedor WHERE usuario_id = ?", usuarioId);
                ejecutarDelete(conn, "DELETE FROM Notas WHERE solicitud_id IN " + SOLICITUDES_DEL_USUARIO, usuarioId);
                ejecutarDelete(conn, "DELETE FROM SolicitudesDeVendedor WHERE usuario_id = ?", usuarioId);
                ejecutarDelete(conn, "DELETE FROM IconosVendedor WHERE vendedor_id IN " + VENDEDORES_DEL_USUARIO, usuarioId);
                ejecutarDelete(conn, "DELETE FROM Vendedores WHERE usuario_id = ?", usuarioId);

                int filasBorradas = ejecutarDelete(conn, "DELETE FROM Usuarios WHERE id = ?", usuarioId);

                if (filasBorradas == 0) {
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

            session.invalidate();

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("Cuenta eliminada correctamente");

            System.out.println("[EliminarPerfilServlet] Usuario " + usuarioId + " eliminó su cuenta.");

            // 🆕 Commit exitoso → ahora sí se borran las fotos en Cloudinary
            // (mejor esfuerzo: si falla, solo se registra).
            try {
                CloudinaryService.borrarPorUrls(urlsImagenes);
            } catch (Exception e) {
                System.out.println("[EliminarPerfilServlet] No se pudieron borrar imágenes de Cloudinary: " + e.getMessage());
            }

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
    private int ejecutarDelete(Connection conn, String sql, int usuarioId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, usuarioId);
            return stmt.executeUpdate();
        }
    }

    /**
     * 🆕 Borra de ImagenElegidaCarrito lo que dependa del usuario, según las
     * columnas que realmente tenga la tabla. Si la tabla no existe, no hace nada.
     */
    private void borrarImagenElegidaCarrito(Connection conn, int usuarioId) throws SQLException {
        boolean tieneUsuario = false, tieneCarrito = false, tieneProducto = false;
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "ImagenElegidaCarrito", null)) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if ("usuario_id".equalsIgnoreCase(col)) tieneUsuario = true;
                if ("carrito_id".equalsIgnoreCase(col)) tieneCarrito = true;
                if ("producto_id".equalsIgnoreCase(col)) tieneProducto = true;
            }
        }
        if (tieneUsuario) {
            ejecutarDelete(conn, "DELETE FROM ImagenElegidaCarrito WHERE usuario_id = ?", usuarioId);
        }
        if (tieneCarrito) {
            ejecutarDelete(conn, "DELETE FROM ImagenElegidaCarrito WHERE carrito_id IN " + CARRITOS_DEL_USUARIO, usuarioId);
        }
        if (tieneProducto) {
            // Filas de OTROS usuarios que eligieron imagen de un producto de este vendedor
            ejecutarDelete(conn, "DELETE FROM ImagenElegidaCarrito WHERE producto_id IN " + PRODUCTOS_DEL_USUARIO, usuarioId);
        }
    }

    /**
     * 🆕 Agrega a "destino" todo texto que empiece con http en las filas que
     * devuelva la consulta (un parámetro: usuarioId). Genérico a propósito: no
     * depende del nombre de las columnas. Si la tabla no existe, no agrega nada.
     */
    private void recolectarUrls(Connection conn, String sql, int usuarioId, List<String> destino) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    for (int c = 1; c <= md.getColumnCount(); c++) {
                        int t = md.getColumnType(c);
                        if (t == Types.VARCHAR || t == Types.NVARCHAR || t == Types.LONGVARCHAR || t == Types.LONGNVARCHAR) {
                            String v = rs.getString(c);
                            if (v != null && v.startsWith("http")) destino.add(v);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("[EliminarPerfilServlet] No se leyeron URLs (" + e.getMessage() + ")");
        }
    }
}