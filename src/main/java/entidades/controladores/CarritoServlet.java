package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import entidades.DatabaseConnection;
import entidades.JsonUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

// Carrito de compras del usuario logueado (Comprador o Vendedor comprando).
// Igual que el resto de endpoints "personales" (/api/perfil), la
// identidad NO se manda desde el cliente: se lee de la sesión real del
// servidor (session.getAttribute("usuarioId")), que ya se crea en
// LoginVendedorServlet / LoginCompradorServlet al autenticar. Así un
// Invitado (sin sesión) nunca puede leer ni tocar el carrito de otra
// persona.
//
// GET  /api/carrito                -> lista el carrito del usuario (crea uno vacío si no existe)
// POST /api/carrito  accion=agregar        (producto_id, cantidad)
// POST /api/carrito  accion=actualizar     (producto_id, cantidad)  -> si cantidad<=0, elimina la línea
// POST /api/carrito  accion=eliminar       (producto_id)
// POST /api/carrito  accion=vaciar
@WebServlet("/api/carrito")
public class CarritoServlet extends HttpServlet {

    private Integer usuarioIdDeSesion(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object id = session.getAttribute("usuarioId");
        return (id instanceof Integer) ? (Integer) id : null;
    }

    private void responderSinSesion(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print("{\"error\":\"Debes iniciar sesión para usar el carrito.\"}");
    }

    // Devuelve el id del carrito del usuario, creándolo si todavía no existe.
    private int obtenerOCrearCarrito(Connection conn, int usuarioId) throws Exception {
        try (PreparedStatement buscar = conn.prepareStatement(
                "SELECT id FROM Carrito WHERE usuario_id = ?")) {
            buscar.setInt(1, usuarioId);
            try (ResultSet rs = buscar.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        }
        try (PreparedStatement crear = conn.prepareStatement(
                "INSERT INTO Carrito (usuario_id) OUTPUT INSERTED.id VALUES (?)")) {
            crear.setInt(1, usuarioId);
            try (ResultSet rs = crear.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        Integer usuarioId = usuarioIdDeSesion(request);
        if (usuarioId == null) { responderSinSesion(response); return; }

        // Se trae el precio y el stock/estado ACTUALES del producto (no el
        // guardado al agregarlo) para poder avisar en el carrito si el
        // precio cambió o si el producto ya no existe / fue desactivado.
        String sql = "SELECT dc.producto_id, dc.cantidad, dc.precio_unitario AS precio_guardado, "
                   + "p.nombre, p.imagen, p.precio AS precio_actual, p.categoria, p.usuario_id AS vendedor_id, "
                   + "d.precio_anterior "
                   + "FROM Carrito c "
                   + "JOIN DetalleCarrito dc ON dc.carrito_id = c.id "
                   + "JOIN Productos p ON p.id = dc.producto_id "
                   + "LEFT JOIN Descuentos d ON d.producto_id = p.id "
                   + "WHERE c.usuario_id = ? "
                   + "ORDER BY dc.fecha_agregado DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, usuarioId);
            try (ResultSet rs = stmt.executeQuery()) {
                PrintWriter out = response.getWriter();
                out.print("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.print(",");
                    first = false;
                    double precioActual = rs.getDouble("precio_actual");
                    double precioGuardado = rs.getDouble("precio_guardado");
                    out.print("{");
                    out.print("\"producto_id\":" + rs.getInt("producto_id") + ",");
                    out.print("\"nombre\":\"" + JsonUtils.escapar(rs.getString("nombre")) + "\",");
                    out.print("\"imagen\":\"" + JsonUtils.escapar(rs.getString("imagen")) + "\",");
                    out.print("\"categoria\":\"" + JsonUtils.escapar(rs.getString("categoria")) + "\",");
                    out.print("\"cantidad\":" + rs.getInt("cantidad") + ",");
                    out.print("\"precio_unitario\":" + precioActual + ",");
                    out.print("\"precio_cambio\":" + (precioActual != precioGuardado) + ",");
                    double precioAnterior = rs.getDouble("precio_anterior");
                    out.print("\"precio_anterior\":" + (rs.wasNull() ? "null" : precioAnterior));
                    out.print("}");
                }
                out.print("]");
            }
        } catch (Exception e) {
            e.printStackTrace(response.getWriter());
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        Integer usuarioId = usuarioIdDeSesion(request);
        if (usuarioId == null) { responderSinSesion(response); return; }

        String accion = request.getParameter("accion");
        PrintWriter out = response.getWriter();

        try (Connection conn = DatabaseConnection.getConnection()) {
            int carritoId = obtenerOCrearCarrito(conn, usuarioId);

            if ("agregar".equals(accion)) {
                int productoId = Integer.parseInt(request.getParameter("producto_id"));
                int cantidad = Math.max(1, Integer.parseInt(request.getParameter("cantidad")));

                double precioActual;
                try (PreparedStatement precioStmt = conn.prepareStatement(
                        "SELECT precio FROM Productos WHERE id = ?")) {
                    precioStmt.setInt(1, productoId);
                    try (ResultSet rs = precioStmt.executeQuery()) {
                        if (!rs.next()) {
                            response.setStatus(404);
                            out.print("{\"error\":\"El producto ya no está disponible.\"}");
                            return;
                        }
                        precioActual = rs.getDouble("precio");
                    }
                }

                // Si ya estaba en el carrito, se suma la cantidad y se
                // refresca el precio guardado; si no, se inserta la línea.
                try (PreparedStatement upsert = conn.prepareStatement(
                        "MERGE DetalleCarrito AS destino " +
                        "USING (SELECT ? AS carrito_id, ? AS producto_id) AS origen " +
                        "ON destino.carrito_id = origen.carrito_id AND destino.producto_id = origen.producto_id " +
                        "WHEN MATCHED THEN UPDATE SET cantidad = destino.cantidad + ?, precio_unitario = ? " +
                        "WHEN NOT MATCHED THEN INSERT (carrito_id, producto_id, cantidad, precio_unitario) " +
                        "VALUES (?, ?, ?, ?);")) {
                    upsert.setInt(1, carritoId);
                    upsert.setInt(2, productoId);
                    upsert.setInt(3, cantidad);
                    upsert.setDouble(4, precioActual);
                    upsert.setInt(5, carritoId);
                    upsert.setInt(6, productoId);
                    upsert.setInt(7, cantidad);
                    upsert.setDouble(8, precioActual);
                    upsert.executeUpdate();
                }
                out.print("{\"ok\":true}");

            } else if ("actualizar".equals(accion)) {
                int productoId = Integer.parseInt(request.getParameter("producto_id"));
                int cantidad = Integer.parseInt(request.getParameter("cantidad"));
                if (cantidad <= 0) {
                    try (PreparedStatement del = conn.prepareStatement(
                            "DELETE FROM DetalleCarrito WHERE carrito_id = ? AND producto_id = ?")) {
                        del.setInt(1, carritoId);
                        del.setInt(2, productoId);
                        del.executeUpdate();
                    }
                } else {
                    try (PreparedStatement upd = conn.prepareStatement(
                            "UPDATE DetalleCarrito SET cantidad = ? WHERE carrito_id = ? AND producto_id = ?")) {
                        upd.setInt(1, cantidad);
                        upd.setInt(2, carritoId);
                        upd.setInt(3, productoId);
                        upd.executeUpdate();
                    }
                }
                out.print("{\"ok\":true}");

            } else if ("eliminar".equals(accion)) {
                int productoId = Integer.parseInt(request.getParameter("producto_id"));
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM DetalleCarrito WHERE carrito_id = ? AND producto_id = ?")) {
                    del.setInt(1, carritoId);
                    del.setInt(2, productoId);
                    del.executeUpdate();
                }
                out.print("{\"ok\":true}");

            } else if ("vaciar".equals(accion)) {
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM DetalleCarrito WHERE carrito_id = ?")) {
                    del.setInt(1, carritoId);
                    del.executeUpdate();
                }
                out.print("{\"ok\":true}");

            } else {
                response.setStatus(400);
                out.print("{\"error\":\"Acción no reconocida.\"}");
            }
        } catch (Exception e) {
            e.printStackTrace(response.getWriter());
        }
    }
}