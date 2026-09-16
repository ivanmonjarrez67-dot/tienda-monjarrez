package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import entidades.DatabaseConnection;
import entidades.EmailService;
import entidades.FacturaPdfGenerator;
import entidades.FacturaPdfGenerator.ItemFactura;
import entidades.JsonUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

// GET  /api/pedido?id=X     -> detalle de un pedido (factura). Solo lo puede ver
//                              quien lo hizo (se compara contra la sesión).
// POST /api/pedido accion=confirmar  (metodo_pago, referencia_pago opcional)
//      -> copia el carrito actual del usuario a Pedidos/DetallePedido,
//         vacía el carrito, envía los correos de confirmación (comprador,
//         cada vendedor involucrado y el admin, todos con el PDF de la
//         factura adjunto) y devuelve el id del pedido creado.
@WebServlet("/api/pedido")
public class PedidoServlet extends HttpServlet {

    private Integer usuarioIdDeSesion(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object id = session.getAttribute("usuarioId");
        return (id instanceof Integer) ? (Integer) id : null;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        Integer usuarioId = usuarioIdDeSesion(request);
        if (usuarioId == null) {
            response.setStatus(401);
            response.getWriter().print("{\"error\":\"Debes iniciar sesión para ver este pedido.\"}");
            return;
        }

        int pedidoId;
        try {
            pedidoId = Integer.parseInt(request.getParameter("id"));
        } catch (Exception e) {
            response.setStatus(400);
            response.getWriter().print("{\"error\":\"Pedido inválido.\"}");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sqlPedido = "SELECT id, usuario_id, fecha, metodo_pago, referencia_pago, estado, total "
                              + "FROM Pedidos WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlPedido)) {
                stmt.setInt(1, pedidoId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        response.setStatus(404);
                        response.getWriter().print("{\"error\":\"Pedido no encontrado.\"}");
                        return;
                    }
                    // Solo el dueño del pedido puede verlo (nada de facturas ajenas por id adivinado).
                    if (rs.getInt("usuario_id") != usuarioId) {
                        response.setStatus(403);
                        response.getWriter().print("{\"error\":\"No tienes acceso a este pedido.\"}");
                        return;
                    }

                    PrintWriter out = response.getWriter();
                    out.print("{");
                    out.print("\"id\":" + rs.getInt("id") + ",");
                    out.print("\"fecha\":\"" + rs.getTimestamp("fecha") + "\",");
                    out.print("\"metodo_pago\":\"" + JsonUtils.escapar(rs.getString("metodo_pago")) + "\",");
                    String referencia = rs.getString("referencia_pago");
                    out.print("\"referencia_pago\":" + (referencia == null ? "null" : "\"" + JsonUtils.escapar(referencia) + "\"") + ",");
                    out.print("\"estado\":\"" + JsonUtils.escapar(rs.getString("estado")) + "\",");
                    out.print("\"total\":" + rs.getDouble("total") + ",");

                    out.print("\"items\":[");
                    try (PreparedStatement itemsStmt = conn.prepareStatement(
                            "SELECT producto_id, nombre_producto, imagen_producto, cantidad, precio_unitario "
                          + "FROM DetallePedido WHERE pedido_id = ?")) {
                        itemsStmt.setInt(1, pedidoId);
                        try (ResultSet itemsRs = itemsStmt.executeQuery()) {
                            boolean first = true;
                            while (itemsRs.next()) {
                                if (!first) out.print(",");
                                first = false;
                                out.print("{");
                                out.print("\"producto_id\":" + itemsRs.getInt("producto_id") + ",");
                                out.print("\"nombre\":\"" + JsonUtils.escapar(itemsRs.getString("nombre_producto")) + "\",");
                                String imagen = itemsRs.getString("imagen_producto");
                                out.print("\"imagen\":" + (imagen == null ? "null" : "\"" + JsonUtils.escapar(imagen) + "\"") + ",");
                                out.print("\"cantidad\":" + itemsRs.getInt("cantidad") + ",");
                                out.print("\"precio_unitario\":" + itemsRs.getDouble("precio_unitario"));
                                out.print("}");
                            }
                        }
                    }
                    out.print("]}");
                }
            }
        } catch (Exception e) {
            e.printStackTrace(response.getWriter());
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        Integer usuarioId = usuarioIdDeSesion(request);
        if (usuarioId == null) {
            response.setStatus(401);
            response.getWriter().print("{\"error\":\"Debes iniciar sesión para confirmar el pedido.\"}");
            return;
        }

        String accion = request.getParameter("accion");
        if (!"confirmar".equals(accion)) {
            response.setStatus(400);
            response.getWriter().print("{\"error\":\"Acción no reconocida.\"}");
            return;
        }

        String metodoPago = request.getParameter("metodo_pago"); // "sinpe" | "efectivo"
        String referenciaPago = request.getParameter("referencia_pago"); // opcional, comprobante SINPE
        if (!"sinpe".equals(metodoPago) && !"efectivo".equals(metodoPago)) {
            response.setStatus(400);
            response.getWriter().print("{\"error\":\"Método de pago inválido.\"}");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1) Traer las líneas del carrito con el precio y datos ACTUALES
                //    del producto (lo que ve la persona en carrito.html justo
                //    antes de confirmar), para que la factura quede "congelada"
                //    con esos valores.
                int carritoId = -1;
                try (PreparedStatement buscarCarrito = conn.prepareStatement(
                        "SELECT id FROM Carrito WHERE usuario_id = ?")) {
                    buscarCarrito.setInt(1, usuarioId);
                    try (ResultSet rs = buscarCarrito.executeQuery()) {
                        if (rs.next()) carritoId = rs.getInt("id");
                    }
                }

                if (carritoId == -1) {
                    response.setStatus(400);
                    response.getWriter().print("{\"error\":\"Tu carrito está vacío.\"}");
                    conn.rollback();
                    return;
                }

                double total = 0;
                try (PreparedStatement totalStmt = conn.prepareStatement(
                        "SELECT SUM(p.precio * dc.cantidad) AS total "
                      + "FROM DetalleCarrito dc JOIN Productos p ON p.id = dc.producto_id "
                      + "WHERE dc.carrito_id = ?")) {
                    totalStmt.setInt(1, carritoId);
                    try (ResultSet rs = totalStmt.executeQuery()) {
                        if (rs.next()) total = rs.getDouble("total");
                    }
                }

                if (total <= 0) {
                    response.setStatus(400);
                    response.getWriter().print("{\"error\":\"Tu carrito está vacío.\"}");
                    conn.rollback();
                    return;
                }

                int pedidoId;
                try (PreparedStatement crearPedido = conn.prepareStatement(
                        "INSERT INTO Pedidos (usuario_id, metodo_pago, referencia_pago, total) "
                      + "OUTPUT INSERTED.id VALUES (?, ?, ?, ?)")) {
                    crearPedido.setInt(1, usuarioId);
                    crearPedido.setString(2, metodoPago);
                    if (referenciaPago == null || referenciaPago.trim().isEmpty()) {
                        crearPedido.setNull(3, java.sql.Types.VARCHAR);
                    } else {
                        crearPedido.setString(3, referenciaPago.trim());
                    }
                    crearPedido.setDouble(4, total);
                    try (ResultSet rs = crearPedido.executeQuery()) {
                        rs.next();
                        pedidoId = rs.getInt(1);
                    }
                }

                // 2) Copiar cada línea del carrito a DetallePedido, "congelando"
                //    nombre/imagen/precio y guardando también el vendedor dueño
                //    de cada producto (para que después pueda ver sus pedidos).
                try (PreparedStatement copiar = conn.prepareStatement(
                        "INSERT INTO DetallePedido (pedido_id, producto_id, nombre_producto, imagen_producto, cantidad, precio_unitario, usuario_id_vendedor) "
                      + "SELECT ?, p.id, p.nombre, p.imagen, dc.cantidad, p.precio, p.usuario_id "
                      + "FROM DetalleCarrito dc JOIN Productos p ON p.id = dc.producto_id "
                      + "WHERE dc.carrito_id = ?")) {
                    copiar.setInt(1, pedidoId);
                    copiar.setInt(2, carritoId);
                    copiar.executeUpdate();
                }

                // 3) Vaciar el carrito: el pedido ya quedó "congelado" aparte.
                try (PreparedStatement vaciar = conn.prepareStatement(
                        "DELETE FROM DetalleCarrito WHERE carrito_id = ?")) {
                    vaciar.setInt(1, carritoId);
                    vaciar.executeUpdate();
                }

                conn.commit();

                // 4) 🆕 Generar el PDF de la factura y avisar por correo a
                //    comprador, cada vendedor involucrado y al admin.
                //    Se hace DESPUÉS del commit y en su propio try/catch: si
                //    algo falla acá (Brevo caído, PDF, etc.) el pedido ya
                //    quedó guardado y la respuesta al front no se ve afectada.
                //
                // 🔧 FIX: antes solo se imprimía correoErr.getMessage(), que
                // para errores como NoClassDefFoundError (ej. si falta la
                // librería OpenPDF en el classpath de despliegue) suele venir
                // vacío o poco útil, dejando el fallo prácticamente invisible
                // en el log. Ahora se imprime el stack trace completo con
                // printStackTrace(), para poder ver EXACTAMENTE en qué clase/
                // línea explota (generación del PDF, alguna de las consultas
                // SQL de vendedor/comprador, etc.) la próxima vez que un
                // pedido no mande los correos.
                try {
                    enviarCorreosDePedido(conn, pedidoId, usuarioId, metodoPago, referenciaPago, total);
                } catch (Exception correoErr) {
                    System.out.println("[PedidoServlet] No se pudieron enviar los correos del pedido #" + pedidoId + ":");
                    correoErr.printStackTrace();
                }

                response.getWriter().print("{\"ok\":true,\"pedido_id\":" + pedidoId + "}");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace(response.getWriter());
        }
    }

    // ---------------------------------------------------------
    // 🆕 Arma el PDF de la factura y dispara los tres correos:
    // comprador, cada vendedor distinto con productos en el pedido, y admin.
    //
    // ⚠️ AJUSTA ESTO A TU ESQUEMA REAL: se asume una tabla "Usuarios" con
    // columnas "nombre" y "correo" tanto para compradores como vendedores,
    // y opcionalmente "empresa" para el nombre comercial del vendedor. Si
    // tus nombres de tabla/columna son distintos, cambia solo las dos
    // consultas SQL de acá abajo — el resto no necesita tocarse.
    // ---------------------------------------------------------
    private void enviarCorreosDePedido(Connection conn, int pedidoId, int compradorId,
                                        String metodoPago, String referenciaPago, double total) throws Exception {
        // Datos del comprador
        String nombreComprador = "Cliente";
        String correoComprador = null;
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT nombre, correo FROM Usuarios WHERE id = ?")) {
            stmt.setInt(1, compradorId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    nombreComprador = rs.getString("nombre");
                    correoComprador = rs.getString("correo");
                }
            }
        }

        // Items del pedido + datos del vendedor de cada uno
        List<ItemFactura> itemsFactura = new ArrayList<>();
        // vendedorId -> {nombre, correo}
        Map<Integer, String[]> vendedoresMap = new LinkedHashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT dp.nombre_producto, dp.cantidad, dp.precio_unitario, dp.usuario_id_vendedor, "
              + "       v.nombre AS nombre_vendedor, v.correo AS correo_vendedor "
              + "FROM DetallePedido dp "
              + "JOIN Usuarios v ON v.id = dp.usuario_id_vendedor "
              + "WHERE dp.pedido_id = ?")) {
            stmt.setInt(1, pedidoId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    itemsFactura.add(new ItemFactura(
                            rs.getString("nombre_producto"),
                            rs.getInt("cantidad"),
                            rs.getDouble("precio_unitario")
                    ));
                    int vendedorId = rs.getInt("usuario_id_vendedor");
                    vendedoresMap.putIfAbsent(vendedorId, new String[]{
                            rs.getString("nombre_vendedor"), rs.getString("correo_vendedor")
                    });
                }
            }
        }

        byte[] pdfBytes = FacturaPdfGenerator.generar(
                pedidoId, new java.util.Date(), metodoPago, referenciaPago, total, itemsFactura);

        String numeroPedido = String.valueOf(pedidoId);

        if (correoComprador != null && !correoComprador.isEmpty()) {
            EmailService.enviarFacturaComprador(correoComprador, nombreComprador, numeroPedido, pdfBytes);
        }

        for (String[] vendedor : vendedoresMap.values()) {
            String nombreVendedor = vendedor[0];
            String correoVendedor = vendedor[1];
            if (correoVendedor != null && !correoVendedor.isEmpty()) {
                EmailService.enviarNotificacionPedidoVendedor(correoVendedor, nombreVendedor, numeroPedido, pdfBytes);
            }
        }

        EmailService.enviarAlertaNuevoPedidoAdmin(numeroPedido, nombreComprador, pdfBytes);
    }
}