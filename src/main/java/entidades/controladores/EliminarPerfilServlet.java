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
 * Orden de borrado (importante por las llaves foráneas). Los pasos se
 * agrupan en: datos que el usuario generó como cliente, datos ligados a
 * SUS productos (si es vendedor), y por último las tablas "padre".
 *
 *   -- Como usuario/cliente (compradores Y vendedores) --
 *   1.  Intereses                    (usuario_id)
 *   2.  Compradores                  (usuario_id)
 *   3.  Resenas                      (usuario_id — reseñas que ESCRIBIÓ)
 *   4.  ToquesContacto               (usuario_id — sus clics de contacto)
 *   5.  DetalleCarrito               (carrito_id, de SU carrito)
 *   6.  Carrito                      (usuario_id)
 *   7.  DetallePedido                (pedido_id, de SUS pedidos como comprador)
 *   8.  Pedidos                      (usuario_id)
 *
 *   -- Ligado a SUS productos (vendedores) --
 *   9.  Resenas                      (producto_id de sus productos)
 *  10.  ToquesContacto               (producto_id de sus productos)
 *  11.  DetalleCarrito               (producto_id de sus productos, en carritos ajenos)
 *  12.  DetallePedido                (usuario_id_vendedor / producto_id de sus productos)
 *  13.  ImagenesAdicionalesProducto  (producto_id)
 *  14.  Descuentos                   (producto_id)
 *  15.  ProductosExtranjeros         (producto_id)
 *  16.  Productos                    (usuario_id) — ya sin hijos pendientes
 *
 *   -- Datos del registro de vendedor y tabla padre --
 *  17.  SuscripcionVendedor          (usuario_id)
 *  18.  Notas                        (solicitud_id, de sus solicitudes)
 *  19.  SolicitudesDeVendedor        (usuario_id) — ya sin Notas pendientes
 *  20.  IconosVendedor               (vendedor_id, de la fila del usuario en Vendedores)
 *  21.  Vendedores                   (usuario_id) — ya sin hijos pendientes
 *  22.  Usuarios                     (al final, porque las demás tablas dependen de este id)
 *
 * Por qué este orden: cada tabla hija tiene que vaciarse ANTES que la
 * tabla a la que apunta, o SQL Server rechaza el DELETE por la llave
 * foránea (errores tipo "FK_Resenas_Usuario", "FK_ProductosExtranjeros_Productos",
 * "FK_IconosVendedor_Vendedores"). Cuando la tabla hija no guarda el
 * usuario_id directo, se usa una subconsulta (IN (SELECT id FROM ...)).
 *
 * ⚠️ Los pedidos: DetallePedido/Pedidos son historial de compras. Al
 * borrar una cuenta se eliminan los pedidos de ese comprador y las líneas
 * de pedido que involucran productos del vendedor que se va. Si prefieres
 * conservar ese historial (p. ej. por contabilidad), en vez de borrar
 * habría que anonimizar (poner usuario_id/producto_id en NULL, lo que
 * exige que esas columnas permitan NULL).
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

    // Subconsultas que se repiten en varios DELETE
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

                // ---------- Como usuario/cliente ----------
                ejecutarDelete(conn, "DELETE FROM Intereses WHERE usuario_id = ?", usuarioId);
                // 🆕 Compradores.usuario_id → Usuarios (FK__Comprador__usuar__7C4F7684)
                ejecutarDelete(conn, "DELETE FROM Compradores WHERE usuario_id = ?", usuarioId);
                ejecutarDelete(conn, "DELETE FROM Resenas WHERE usuario_id = ?", usuarioId);
                ejecutarDelete(conn, "DELETE FROM ToquesContacto WHERE usuario_id = ?", usuarioId);

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

                // Tablas hijas de Productos: antes de borrar Productos
                ejecutarDelete(conn, "DELETE FROM ImagenesAdicionalesProducto WHERE producto_id IN " + PRODUCTOS_DEL_USUARIO, usuarioId);
                ejecutarDelete(conn, "DELETE FROM Descuentos WHERE producto_id IN " + PRODUCTOS_DEL_USUARIO, usuarioId);
                ejecutarDelete(conn, "DELETE FROM ProductosExtranjeros WHERE producto_id IN " + PRODUCTOS_DEL_USUARIO, usuarioId);

                ejecutarDelete(conn, "DELETE FROM Productos WHERE usuario_id = ?", usuarioId);

                // ---------- Registro de vendedor y tabla padre ----------
                ejecutarDelete(conn, "DELETE FROM SuscripcionVendedor WHERE usuario_id = ?", usuarioId);

                // Notas.solicitud_id apunta a SolicitudesDeVendedor.id: va antes.
                ejecutarDelete(conn, "DELETE FROM Notas WHERE solicitud_id IN " + SOLICITUDES_DEL_USUARIO, usuarioId);
                ejecutarDelete(conn, "DELETE FROM SolicitudesDeVendedor WHERE usuario_id = ?", usuarioId);

                // IconosVendedor.vendedor_id apunta a Vendedores.id (no a usuario_id).
                ejecutarDelete(conn, "DELETE FROM IconosVendedor WHERE vendedor_id IN " + VENDEDORES_DEL_USUARIO, usuarioId);
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