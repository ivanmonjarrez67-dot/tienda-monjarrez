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
// POST /api/pedido accion=confirmar
//        (metodo_pago, referencia_pago opcional,
//         🆕 telefono_contacto y direccion_entrega OBLIGATORIOS)
//      -> copia el carrito actual (con las especificaciones de cada producto)
//         a Pedidos/DetallePedido, guarda teléfono/dirección en el perfil para
//         el próximo pedido, vacía el carrito, envía los correos con el PDF
//         y devuelve el id del pedido creado.
//
// 🆕 Seguimiento: al confirmar, también se crea la fila inicial en
//    PedidoSeguimiento (origen nacional/importado + fechas estimadas) y la
//    primera novedad en PedidoActualizaciones. Ver MisPedidosServlet y
//    PedidosAdminServlet.
@WebServlet("/api/pedido")
public class PedidoServlet extends HttpServlet {

    private Integer usuarioIdDeSesion(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object id = session.getAttribute("usuarioId");
        return (id instanceof Integer) ? (Integer) id : null;
    }

    private static String jsonStr(String s) {
        return s == null ? "null" : "\"" + JsonUtils.escapar(s) + "\"";
    }

    /** Deja solo dígitos. Devuelve null si no es un teléfono razonable (8 a 15 dígitos). */
    private static String limpiarTelefono(String crudo) {
        if (crudo == null) return null;
        String soloDigitos = crudo.replaceAll("\\D", "");
        if (soloDigitos.length() < 8 || soloDigitos.length() > 15) return null;
        return soloDigitos;
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
            // 🆕 Se une con Usuarios para mostrar nombre/correo del cliente,
            // y se traen telefono_contacto y direccion_entrega.
            String sqlPedido = "SELECT p.id, p.usuario_id, p.fecha, p.metodo_pago, p.referencia_pago, p.estado, p.total, "
                              + "p.telefono_contacto, p.direccion_entrega, u.nombre AS nombre_cliente, u.correo AS correo_cliente "
                              + "FROM Pedidos p JOIN Usuarios u ON u.id = p.usuario_id WHERE p.id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlPedido)) {
                stmt.setInt(1, pedidoId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        response.setStatus(404);
                        response.getWriter().print("{\"error\":\"Pedido no encontrado.\"}");
                        return;
                    }
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
                    out.print("\"referencia_pago\":" + jsonStr(rs.getString("referencia_pago")) + ",");
                    out.print("\"estado\":\"" + JsonUtils.escapar(rs.getString("estado")) + "\",");
                    out.print("\"total\":" + rs.getDouble("total") + ",");
                    out.print("\"nombre_cliente\":" + jsonStr(rs.getString("nombre_cliente")) + ",");
                    out.print("\"correo_cliente\":" + jsonStr(rs.getString("correo_cliente")) + ",");
                    out.print("\"telefono_contacto\":" + jsonStr(rs.getString("telefono_contacto")) + ",");
                    out.print("\"direccion_entrega\":" + jsonStr(rs.getString("direccion_entrega")) + ",");

                    out.print("\"items\":[");
                    try (PreparedStatement itemsStmt = conn.prepareStatement(
                            "SELECT producto_id, nombre_producto, imagen_producto, cantidad, precio_unitario, especificaciones "
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
                                out.print("\"imagen\":" + jsonStr(itemsRs.getString("imagen_producto")) + ",");
                                out.print("\"cantidad\":" + itemsRs.getInt("cantidad") + ",");
                                out.print("\"precio_unitario\":" + itemsRs.getDouble("precio_unitario") + ",");
                                out.print("\"especificaciones\":" + jsonStr(itemsRs.getString("especificaciones")));
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
        request.setCharacterEncoding("UTF-8");
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

        // 🆕 Teléfono y dirección de entrega: obligatorios.
        String telefono = limpiarTelefono(request.getParameter("telefono_contacto"));
        if (telefono == null) {
            response.setStatus(400);
            response.getWriter().print("{\"error\":\"Ingresa un teléfono de contacto válido (mínimo 8 dígitos).\"}");
            return;
        }
        String direccion = request.getParameter("direccion_entrega");
        direccion = (direccion == null) ? "" : direccion.trim();
        if (direccion.length() < 10) {
            response.setStatus(400);
            response.getWriter().print("{\"error\":\"Ingresa tu dirección de entrega completa (provincia, cantón, distrito y señas).\"}");
            return;
        }
        if (direccion.length() > 400) {
            response.setStatus(400);
            response.getWriter().print("{\"error\":\"La dirección es demasiado larga (máx. 400 caracteres).\"}");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
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
                        "INSERT INTO Pedidos (usuario_id, metodo_pago, referencia_pago, total, telefono_contacto, direccion_entrega) "
                      + "OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?)")) {
                    crearPedido.setInt(1, usuarioId);
                    crearPedido.setString(2, metodoPago);
                    if (referenciaPago == null || referenciaPago.trim().isEmpty()) {
                        crearPedido.setNull(3, java.sql.Types.VARCHAR);
                    } else {
                        crearPedido.setString(3, referenciaPago.trim());
                    }
                    crearPedido.setDouble(4, total);
                    crearPedido.setString(5, telefono);
                    crearPedido.setString(6, direccion);
                    try (ResultSet rs = crearPedido.executeQuery()) {
                        rs.next();
                        pedidoId = rs.getInt(1);
                    }
                }

                // Copiar cada línea del carrito a DetallePedido, "congelando"
                // nombre/imagen/precio y 🆕 las especificaciones del cliente.
                try (PreparedStatement copiar = conn.prepareStatement(
                        "INSERT INTO DetallePedido (pedido_id, producto_id, nombre_producto, imagen_producto, cantidad, precio_unitario, usuario_id_vendedor, especificaciones) "
                      + "SELECT ?, p.id, p.nombre, p.imagen, dc.cantidad, p.precio, p.usuario_id, dc.especificaciones "
                      + "FROM DetalleCarrito dc JOIN Productos p ON p.id = dc.producto_id "
                      + "WHERE dc.carrito_id = ?")) {
                    copiar.setInt(1, pedidoId);
                    copiar.setInt(2, carritoId);
                    copiar.executeUpdate();
                }

                // 🆕 Seguimiento inicial del pedido: origen "importado" si algún
                // producto está en ProductosExtranjeros (reventa Temu/CJ), si no
                // "nacional". Las fechas estimadas son un rango por defecto
                // (importado 25-35 días, nacional 2-5 días) que la tienda puede
                // ajustar después desde panelAdmin.
                try (PreparedStatement seg = conn.prepareStatement(
                        "DECLARE @imp bit = CASE WHEN EXISTS (SELECT 1 FROM DetallePedido dp JOIN ProductosExtranjeros pe "
                      + "ON pe.producto_id = dp.producto_id WHERE dp.pedido_id = ?) THEN 1 ELSE 0 END; "
                      + "INSERT INTO PedidoSeguimiento (pedido_id, origen, fecha_est_desde, fecha_est_hasta) VALUES (?, "
                      + "IIF(@imp=1,'importado','nacional'), DATEADD(day, IIF(@imp=1,25,2), CAST(SYSDATETIME() AS date)), "
                      + "DATEADD(day, IIF(@imp=1,35,5), CAST(SYSDATETIME() AS date)));")) {
                    seg.setInt(1, pedidoId);
                    seg.setInt(2, pedidoId);
                    seg.executeUpdate();
                }
                try (PreparedStatement nov = conn.prepareStatement(
                        "INSERT INTO PedidoActualizaciones (pedido_id, estado, mensaje) VALUES (?, 'pago_pendiente', "
                      + "N'Recibimos tu pedido. Estamos verificando tu pago; te avisaremos apenas quede confirmado.')")) {
                    nov.setInt(1, pedidoId);
                    nov.executeUpdate();
                }

                // 🆕 Recordar teléfono y dirección para el próximo pedido
                // (y para "Mi Perfil"). Se pisa lo anterior con lo más reciente.
                try (PreparedStatement guardarEnvio = conn.prepareStatement(
                        "MERGE DatosEnvioUsuario AS d "
                      + "USING (SELECT ? AS usuario_id) AS o ON d.usuario_id = o.usuario_id "
                      + "WHEN MATCHED THEN UPDATE SET telefono = ?, direccion = ?, fecha_actualizacion = SYSDATETIME() "
                      + "WHEN NOT MATCHED THEN INSERT (usuario_id, telefono, direccion) VALUES (?, ?, ?);")) {
                    guardarEnvio.setInt(1, usuarioId);
                    guardarEnvio.setString(2, telefono);
                    guardarEnvio.setString(3, direccion);
                    guardarEnvio.setInt(4, usuarioId);
                    guardarEnvio.setString(5, telefono);
                    guardarEnvio.setString(6, direccion);
                    guardarEnvio.executeUpdate();
                }

                try (PreparedStatement vaciar = conn.prepareStatement(
                        "DELETE FROM DetalleCarrito WHERE carrito_id = ?")) {
                    vaciar.setInt(1, carritoId);
                    vaciar.executeUpdate();
                }

                conn.commit();

                // Correos DESPUÉS del commit y en su propio try/catch: si
                // Brevo o el PDF fallan, el pedido ya quedó guardado.
                try {
                    enviarCorreosDePedido(conn, pedidoId, usuarioId, metodoPago, referenciaPago, total, telefono, direccion);
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

    // Arma el PDF de la factura y dispara los tres correos:
    // comprador, cada vendedor distinto con productos en el pedido, y admin.
    private void enviarCorreosDePedido(Connection conn, int pedidoId, int compradorId,
                                        String metodoPago, String referenciaPago, double total,
                                        String telefono, String direccion) throws Exception {
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

        List<ItemFactura> itemsFactura = new ArrayList<>();
        Map<Integer, String[]> vendedoresMap = new LinkedHashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT dp.nombre_producto, dp.cantidad, dp.precio_unitario, dp.usuario_id_vendedor, dp.especificaciones, "
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
                            rs.getDouble("precio_unitario"),
                            rs.getString("especificaciones")
                    ));
                    int vendedorId = rs.getInt("usuario_id_vendedor");
                    vendedoresMap.putIfAbsent(vendedorId, new String[]{
                            rs.getString("nombre_vendedor"), rs.getString("correo_vendedor")
                    });
                }
            }
        }

        byte[] pdfBytes = FacturaPdfGenerator.generar(
                pedidoId, new java.util.Date(), metodoPago, referenciaPago, total, itemsFactura,
                nombreComprador, correoComprador, telefono, direccion);

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