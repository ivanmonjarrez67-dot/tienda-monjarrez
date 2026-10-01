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
// La identidad se lee de la sesión del servidor, nunca del cliente.
//
// GET  /api/carrito                -> lista el carrito del usuario (crea uno vacío si no existe)
// POST /api/carrito  accion=agregar          (producto_id, cantidad, imagen_elegida opcional)
// POST /api/carrito  accion=comprar_ahora    (producto_id, imagen_elegida)  botón "Comprar ahora" de detalle-nacional:
//                                            deja el producto en el carrito con cantidad 1 SIN sumar
//                                            si ya estaba, y deja guardada la foto elegida
// POST /api/carrito  accion=actualizar       (producto_id, cantidad)  -> si cantidad<=0, elimina la línea
// POST /api/carrito  accion=especificaciones (producto_id, especificaciones)  color, talla, etc.
// POST /api/carrito  accion=eliminar         (producto_id)
// POST /api/carrito  accion=vaciar
//
// 🆕 La foto elegida vive en la tabla ImagenElegidaCarrito (carrito_id, producto_id, imagen).
//    Solo se acepta si es la foto principal del producto o una de sus fotos adicionales.
@WebServlet("/api/carrito")
public class CarritoServlet extends HttpServlet {

    private static final int MAX_ESPECIFICACIONES = 300;

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

    private double precioActualDe(Connection conn, int productoId) throws Exception {
        try (PreparedStatement precioStmt = conn.prepareStatement(
                "SELECT precio FROM Productos WHERE id = ?")) {
            precioStmt.setInt(1, productoId);
            try (ResultSet rs = precioStmt.executeQuery()) {
                if (!rs.next()) return -1;
                return rs.getDouble("precio");
            }
        }
    }

    // 🆕 Devuelve la URL si pertenece a este producto (principal o adicional); si no, null.
    // Así nadie puede colar una URL cualquiera que luego salga en pedidos y correos.
    private String validarImagenElegida(Connection conn, int productoId, String solicitada) throws Exception {
        if (solicitada == null) return null;
        solicitada = solicitada.trim();
        if (solicitada.isEmpty() || solicitada.length() > 500) return null;
        // Fotos válidas del producto: la principal (Productos.imagen), imagen2/imagen3
        // (ImagenesAdicionalesProducto) y las de la galería (ImagenesProducto.url).
        String sql = "SELECT TOP 1 1 FROM ("
                   + "SELECT imagen AS url FROM Productos WHERE id = ? "
                   + "UNION ALL SELECT imagen2 FROM ImagenesAdicionalesProducto WHERE producto_id = ? "
                   + "UNION ALL SELECT imagen3 FROM ImagenesAdicionalesProducto WHERE producto_id = ? "
                   + "UNION ALL SELECT url FROM ImagenesProducto WHERE producto_id = ?"
                   + ") t WHERE t.url = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productoId);
            ps.setInt(2, productoId);
            ps.setInt(3, productoId);
            ps.setInt(4, productoId);
            ps.setString(5, solicitada);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? solicitada : null;
            }
        }
    }

    // 🆕 Guarda (o reemplaza) la foto elegida de esta línea del carrito.
    private void guardarImagenElegida(Connection conn, int carritoId, int productoId, String imagen) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "MERGE ImagenElegidaCarrito AS d "
              + "USING (SELECT ? AS carrito_id, ? AS producto_id) AS o "
              + "ON d.carrito_id = o.carrito_id AND d.producto_id = o.producto_id "
              + "WHEN MATCHED THEN UPDATE SET imagen = ?, fecha_actualizacion = SYSDATETIME() "
              + "WHEN NOT MATCHED THEN INSERT (carrito_id, producto_id, imagen) VALUES (?, ?, ?);")) {
            ps.setInt(1, carritoId);
            ps.setInt(2, productoId);
            ps.setString(3, imagen);
            ps.setInt(4, carritoId);
            ps.setInt(5, productoId);
            ps.setString(6, imagen);
            ps.executeUpdate();
        }
    }

    // 🆕 productoId <= 0 borra la de todo el carrito.
    private void borrarImagenElegida(Connection conn, int carritoId, int productoId) throws Exception {
        String sql = productoId > 0
                ? "DELETE FROM ImagenElegidaCarrito WHERE carrito_id = ? AND producto_id = ?"
                : "DELETE FROM ImagenElegidaCarrito WHERE carrito_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, carritoId);
            if (productoId > 0) ps.setInt(2, productoId);
            ps.executeUpdate();
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        Integer usuarioId = usuarioIdDeSesion(request);
        if (usuarioId == null) { responderSinSesion(response); return; }

        // 🆕 LEFT JOIN con ImagenElegidaCarrito: ie.imagen es la foto elegida (o NULL = principal).
        String sql = "SELECT dc.producto_id, dc.cantidad, dc.precio_unitario AS precio_guardado, dc.especificaciones, "
                   + "p.nombre, p.imagen, p.precio AS precio_actual, p.categoria, p.usuario_id AS vendedor_id, "
                   + "p.telefono, p.correo, p.Nombre_Empresa AS empresa, "
                   + "d.precio_anterior, ie.imagen AS imagen_elegida "
                   + "FROM Carrito c "
                   + "JOIN DetalleCarrito dc ON dc.carrito_id = c.id "
                   + "JOIN Productos p ON p.id = dc.producto_id "
                   + "LEFT JOIN Descuentos d ON d.producto_id = p.id "
                   + "LEFT JOIN ImagenElegidaCarrito ie ON ie.carrito_id = c.id AND ie.producto_id = dc.producto_id "
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
                    String imagenElegida = rs.getString("imagen_elegida");
                    out.print("\"imagen_elegida\":" + (imagenElegida == null ? "null" : "\"" + JsonUtils.escapar(imagenElegida) + "\"") + ",");
                    out.print("\"categoria\":\"" + JsonUtils.escapar(rs.getString("categoria")) + "\",");
                    out.print("\"cantidad\":" + rs.getInt("cantidad") + ",");
                    out.print("\"precio_unitario\":" + precioActual + ",");
                    out.print("\"precio_cambio\":" + (precioActual != precioGuardado) + ",");
                    double precioAnterior = rs.getDouble("precio_anterior");
                    out.print("\"precio_anterior\":" + (rs.wasNull() ? "null" : precioAnterior) + ",");

                    String telefono = rs.getString("telefono");
                    out.print("\"telefono\":" + (telefono == null ? "null" : "\"" + JsonUtils.escapar(telefono) + "\"") + ",");
                    String correo = rs.getString("correo");
                    out.print("\"correo\":" + (correo == null ? "null" : "\"" + JsonUtils.escapar(correo) + "\"") + ",");
                    String empresa = rs.getString("empresa");
                    out.print("\"empresa\":" + (empresa == null ? "null" : "\"" + JsonUtils.escapar(empresa) + "\"") + ",");

                    // Lo que el cliente pidió para ESTE producto (color, talla, etc.)
                    String especificaciones = rs.getString("especificaciones");
                    out.print("\"especificaciones\":" + (especificaciones == null ? "null" : "\"" + JsonUtils.escapar(especificaciones) + "\""));

                    out.print("}");
                }
                out.print("]");
            }
        } catch (Exception e) {
            e.printStackTrace(response.getWriter());
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
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

                double precioActual = precioActualDe(conn, productoId);
                if (precioActual < 0) {
                    response.setStatus(404);
                    out.print("{\"error\":\"El producto ya no está disponible.\"}");
                    return;
                }

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
                // 🆕 Desde el catálogo no viene foto: se respeta la que ya hubiera elegido.
                String imagenValida = validarImagenElegida(conn, productoId, request.getParameter("imagen_elegida"));
                if (imagenValida != null) guardarImagenElegida(conn, carritoId, productoId, imagenValida);
                out.print("{\"ok\":true}");

            } else if ("comprar_ahora".equals(accion)) {
                // Igual que "agregar", pero si el producto ya estaba en el
                // carrito NO suma cantidad (solo refresca el precio).
                int productoId = Integer.parseInt(request.getParameter("producto_id"));
                double precioActual = precioActualDe(conn, productoId);
                if (precioActual < 0) {
                    response.setStatus(404);
                    out.print("{\"error\":\"El producto ya no está disponible.\"}");
                    return;
                }
                try (PreparedStatement upsert = conn.prepareStatement(
                        "MERGE DetalleCarrito AS destino " +
                        "USING (SELECT ? AS carrito_id, ? AS producto_id) AS origen " +
                        "ON destino.carrito_id = origen.carrito_id AND destino.producto_id = origen.producto_id " +
                        "WHEN MATCHED THEN UPDATE SET precio_unitario = ? " +
                        "WHEN NOT MATCHED THEN INSERT (carrito_id, producto_id, cantidad, precio_unitario) " +
                        "VALUES (?, ?, 1, ?);")) {
                    upsert.setInt(1, carritoId);
                    upsert.setInt(2, productoId);
                    upsert.setDouble(3, precioActual);
                    upsert.setInt(4, carritoId);
                    upsert.setInt(5, productoId);
                    upsert.setDouble(6, precioActual);
                    upsert.executeUpdate();
                }
                // 🆕 La última foto seleccionada manda: reemplaza la anterior (aunque el
                // producto ya estuviera en el carrito). Sin foto válida = foto principal.
                String imagenValida = validarImagenElegida(conn, productoId, request.getParameter("imagen_elegida"));
                if (imagenValida != null) guardarImagenElegida(conn, carritoId, productoId, imagenValida);
                else borrarImagenElegida(conn, carritoId, productoId);
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
                    borrarImagenElegida(conn, carritoId, productoId);
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

            } else if ("especificaciones".equals(accion)) {
                // Guarda lo que el cliente quiere de ESTE producto.
                int productoId = Integer.parseInt(request.getParameter("producto_id"));
                String texto = request.getParameter("especificaciones");
                texto = (texto == null) ? "" : texto.trim();
                if (texto.length() > MAX_ESPECIFICACIONES) {
                    response.setStatus(400);
                    out.print("{\"error\":\"Las especificaciones son muy largas (máx. " + MAX_ESPECIFICACIONES + " caracteres).\"}");
                    return;
                }
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE DetalleCarrito SET especificaciones = ? WHERE carrito_id = ? AND producto_id = ?")) {
                    if (texto.isEmpty()) upd.setNull(1, java.sql.Types.NVARCHAR);
                    else upd.setString(1, texto);
                    upd.setInt(2, carritoId);
                    upd.setInt(3, productoId);
                    upd.executeUpdate();
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
                borrarImagenElegida(conn, carritoId, productoId);
                out.print("{\"ok\":true}");

            } else if ("vaciar".equals(accion)) {
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM DetalleCarrito WHERE carrito_id = ?")) {
                    del.setInt(1, carritoId);
                    del.executeUpdate();
                }
                borrarImagenElegida(conn, carritoId, 0);
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