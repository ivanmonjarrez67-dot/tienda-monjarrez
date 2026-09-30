package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import entidades.DatabaseConnection;
import entidades.JsonUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

// GET  /api/mis-pedidos            -> pedidos del usuario en sesión + seguimiento + novedades
// POST /api/mis-pedidos accion=recibido   pedido_id
// POST /api/mis-pedidos accion=entrega    pedido_id, telefono_contacto, direccion_entrega
@WebServlet("/api/mis-pedidos")
public class MisPedidosServlet extends HttpServlet {

    private Integer uid(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        Object id = (s == null) ? null : s.getAttribute("usuarioId");
        return (id instanceof Integer) ? (Integer) id : null;
    }
    private static String js(Object o) { return o == null ? "null" : "\"" + JsonUtils.escapar(o.toString()) + "\""; }

    static void asegurar(Connection c, int pedidoId) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(
            "IF NOT EXISTS (SELECT 1 FROM PedidoSeguimiento WHERE pedido_id=?) INSERT INTO PedidoSeguimiento(pedido_id) VALUES(?)")) {
            st.setInt(1, pedidoId); st.setInt(2, pedidoId); st.executeUpdate();
        }
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        Integer u = uid(req);
        if (u == null) { res.setStatus(401); res.getWriter().print("{\"error\":\"Inicia sesión.\"}"); return; }
        try (Connection c = DatabaseConnection.getConnection()) {
            PrintWriter out = res.getWriter();
            out.print("[");
            try (PreparedStatement st = c.prepareStatement(
                "SELECT p.id, p.fecha, p.total, p.metodo_pago, p.telefono_contacto, p.direccion_entrega, "
              + "ISNULL(s.estado,'pago_pendiente') estado, ISNULL(s.origen,'nacional') origen, ISNULL(s.monto_pagado,0) pagado, "
              + "s.sinpe_numero, s.fecha_est_desde, s.fecha_est_hasta, s.numero_guia, s.url_rastreo, "
              + "ISNULL(s.fecha_ultima_novedad,p.fecha) ult, s.recibido_fecha "
              + "FROM Pedidos p LEFT JOIN PedidoSeguimiento s ON s.pedido_id=p.id WHERE p.usuario_id=? ORDER BY p.id DESC")) {
                st.setInt(1, u);
                try (ResultSet rs = st.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        int pid = rs.getInt("id");
                        if (!first) out.print(","); first = false;
                        out.print("{\"id\":" + pid + ",\"fecha\":" + js(rs.getTimestamp("fecha")) + ",\"total\":" + rs.getDouble("total")
                            + ",\"metodo_pago\":" + js(rs.getString("metodo_pago")) + ",\"telefono\":" + js(rs.getString("telefono_contacto"))
                            + ",\"direccion\":" + js(rs.getString("direccion_entrega")) + ",\"estado\":" + js(rs.getString("estado"))
                            + ",\"origen\":" + js(rs.getString("origen")) + ",\"pagado\":" + rs.getDouble("pagado")
                            + ",\"sinpe\":" + js(rs.getString("sinpe_numero")) + ",\"desde\":" + js(rs.getDate("fecha_est_desde"))
                            + ",\"hasta\":" + js(rs.getDate("fecha_est_hasta")) + ",\"guia\":" + js(rs.getString("numero_guia"))
                            + ",\"rastreo\":" + js(rs.getString("url_rastreo")) + ",\"ultima\":" + js(rs.getTimestamp("ult"))
                            + ",\"recibido\":" + js(rs.getTimestamp("recibido_fecha")) + ",\"items\":[");
                        try (PreparedStatement it = c.prepareStatement(
                            "SELECT nombre_producto, imagen_producto, cantidad, especificaciones FROM DetallePedido WHERE pedido_id=?")) {
                            it.setInt(1, pid);
                            try (ResultSet r2 = it.executeQuery()) {
                                boolean f2 = true;
                                while (r2.next()) {
                                    if (!f2) out.print(","); f2 = false;
                                    out.print("{\"nombre\":" + js(r2.getString(1)) + ",\"imagen\":" + js(r2.getString(2))
                                        + ",\"cantidad\":" + r2.getInt(3) + ",\"especificaciones\":" + js(r2.getString(4)) + "}");
                                }
                            }
                        }
                        out.print("],\"novedades\":[");
                        try (PreparedStatement nv = c.prepareStatement(
                            "SELECT TOP 30 fecha, estado, mensaje FROM PedidoActualizaciones WHERE pedido_id=? ORDER BY fecha DESC, id DESC")) {
                            nv.setInt(1, pid);
                            try (ResultSet r3 = nv.executeQuery()) {
                                boolean f3 = true;
                                while (r3.next()) {
                                    if (!f3) out.print(","); f3 = false;
                                    out.print("{\"fecha\":" + js(r3.getTimestamp(1)) + ",\"estado\":" + js(r3.getString(2)) + ",\"mensaje\":" + js(r3.getString(3)) + "}");
                                }
                            }
                        }
                        out.print("]}");
                    }
                }
            }
            out.print("]");
        } catch (Exception e) { e.printStackTrace(); res.setStatus(500); res.getWriter().print("{\"error\":\"Error del servidor.\"}"); }
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        req.setCharacterEncoding("UTF-8");
        res.setContentType("application/json;charset=UTF-8");
        Integer u = uid(req);
        if (u == null) { res.setStatus(401); res.getWriter().print("{\"error\":\"Inicia sesión.\"}"); return; }
        try (Connection c = DatabaseConnection.getConnection()) {
            int pid = Integer.parseInt(req.getParameter("pedido_id"));
            try (PreparedStatement chk = c.prepareStatement("SELECT 1 FROM Pedidos WHERE id=? AND usuario_id=?")) {
                chk.setInt(1, pid); chk.setInt(2, u);
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next()) { res.setStatus(403); res.getWriter().print("{\"error\":\"No es tu pedido.\"}"); return; }
                }
            }
            asegurar(c, pid);
            String accion = req.getParameter("accion");
            if ("recibido".equals(accion)) {
                try (PreparedStatement st = c.prepareStatement(
                    "UPDATE PedidoSeguimiento SET estado='entregado', recibido_fecha=SYSDATETIME(), fecha_ultima_novedad=SYSDATETIME() WHERE pedido_id=?")) {
                    st.setInt(1, pid); st.executeUpdate();
                }
                try (PreparedStatement st = c.prepareStatement(
                    "INSERT INTO PedidoActualizaciones(pedido_id,estado,mensaje) VALUES(?, 'entregado', N'El cliente confirmó que recibió su pedido. ¡Gracias por tu compra!')")) {
                    st.setInt(1, pid); st.executeUpdate();
                }
            } else if ("entrega".equals(accion)) {
                String tel = req.getParameter("telefono_contacto") == null ? "" : req.getParameter("telefono_contacto").replaceAll("\\D", "");
                String dir = req.getParameter("direccion_entrega") == null ? "" : req.getParameter("direccion_entrega").trim();
                if (tel.length() < 8 || tel.length() > 15 || dir.length() < 10 || dir.length() > 400) {
                    res.setStatus(400); res.getWriter().print("{\"error\":\"Revisa el teléfono y la dirección.\"}"); return;
                }
                try (PreparedStatement st = c.prepareStatement(
                    "UPDATE Pedidos SET telefono_contacto=?, direccion_entrega=? WHERE id=? AND usuario_id=? "
                  + "AND EXISTS (SELECT 1 FROM PedidoSeguimiento WHERE pedido_id=? AND estado IN ('pago_pendiente','pago_verificado','comprado'))")) {
                    st.setString(1, tel); st.setString(2, dir); st.setInt(3, pid); st.setInt(4, u); st.setInt(5, pid);
                    if (st.executeUpdate() == 0) { res.setStatus(400); res.getWriter().print("{\"error\":\"Ya no se pueden cambiar los datos: el pedido va en camino.\"}"); return; }
                }
            } else { res.setStatus(400); res.getWriter().print("{\"error\":\"Acción no reconocida.\"}"); return; }
            res.getWriter().print("{\"ok\":true}");
        } catch (Exception e) { e.printStackTrace(); res.setStatus(500); res.getWriter().print("{\"error\":\"Error del servidor.\"}"); }
    }
}