package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Set;
import entidades.DatabaseConnection;
import entidades.EmailService;
import entidades.JsonUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

// GET  /admin/pedidos                 -> pedidos activos (o ?todos=1) con días sin novedad
// POST /admin/pedidos accion=novedad  -> cambia estado/fechas/pago, publica novedad y (opcional) avisa por correo
@WebServlet("/admin/pedidos")
public class PedidosAdminServlet extends HttpServlet {

    private static final Set<String> ESTADOS = Set.of("pago_pendiente","pago_verificado","comprado","en_camino","en_cr","entregado");
    private static final String[][] ETQ = {{"pago_pendiente","Pago pendiente de verificar"},{"pago_verificado","Pago verificado"},
        {"comprado","Comprado al proveedor"},{"en_camino","En camino"},{"en_cr","Llegó a Costa Rica"},{"entregado","Entregado"}};

    // ⚠️ AJUSTA esto a lo mismo que valida SolicitudesVendedorAdminServlet (atributo de sesión del admin).
    private boolean esAdmin(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        return s != null && s.getAttribute("adminId") != null;
    }
    private static String js(Object o) { return o == null ? "null" : "\"" + JsonUtils.escapar(o.toString()) + "\""; }
    private static String vacioNull(String s) { return (s == null || s.trim().isEmpty()) ? null : s.trim(); }

    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        if (!esAdmin(req)) { res.setStatus(401); res.getWriter().print("{\"error\":\"No autorizado\"}"); return; }
        boolean todos = "1".equals(req.getParameter("todos"));
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement st = c.prepareStatement(
                "SELECT TOP 100 p.id, p.fecha, p.total, u.nombre, u.correo, p.telefono_contacto, p.metodo_pago, p.referencia_pago, "
              + "ISNULL(s.estado,'pago_pendiente') estado, ISNULL(s.origen,'nacional') origen, ISNULL(s.monto_pagado,0) pagado, s.sinpe_numero, "
              + "s.fecha_est_desde, s.fecha_est_hasta, s.numero_guia, s.url_rastreo, "
              + "DATEDIFF(day, ISNULL(s.fecha_ultima_novedad,p.fecha), SYSDATETIME()) dias "
              + "FROM Pedidos p JOIN Usuarios u ON u.id=p.usuario_id LEFT JOIN PedidoSeguimiento s ON s.pedido_id=p.id "
              + (todos ? "" : "WHERE ISNULL(s.estado,'pago_pendiente') <> 'entregado' ") + "ORDER BY p.id DESC");
             ResultSet rs = st.executeQuery()) {
            PrintWriter out = res.getWriter();
            out.print("[");
            boolean f = true;
            while (rs.next()) {
                if (!f) out.print(","); f = false;
                out.print("{\"id\":" + rs.getInt("id") + ",\"fecha\":" + js(rs.getTimestamp("fecha")) + ",\"total\":" + rs.getDouble("total")
                    + ",\"nombre\":" + js(rs.getString("nombre")) + ",\"correo\":" + js(rs.getString("correo")) + ",\"telefono\":" + js(rs.getString("telefono_contacto"))
                    + ",\"metodo_pago\":" + js(rs.getString("metodo_pago")) + ",\"referencia\":" + js(rs.getString("referencia_pago"))
                    + ",\"estado\":" + js(rs.getString("estado")) + ",\"origen\":" + js(rs.getString("origen")) + ",\"pagado\":" + rs.getDouble("pagado")
                    + ",\"sinpe\":" + js(rs.getString("sinpe_numero")) + ",\"desde\":" + js(rs.getDate("fecha_est_desde")) + ",\"hasta\":" + js(rs.getDate("fecha_est_hasta"))
                    + ",\"guia\":" + js(rs.getString("numero_guia")) + ",\"rastreo\":" + js(rs.getString("url_rastreo")) + ",\"dias\":" + rs.getInt("dias") + "}");
            }
            out.print("]");
        } catch (Exception e) { e.printStackTrace(); res.setStatus(500); res.getWriter().print("{\"error\":\"Error del servidor\"}"); }
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        req.setCharacterEncoding("UTF-8");
        res.setContentType("application/json;charset=UTF-8");
        if (!esAdmin(req)) { res.setStatus(401); res.getWriter().print("{\"error\":\"No autorizado\"}"); return; }
        try (Connection c = DatabaseConnection.getConnection()) {
            int pid = Integer.parseInt(req.getParameter("pedido_id"));
            String estado = req.getParameter("estado");
            if (!"novedad".equals(req.getParameter("accion")) || !ESTADOS.contains(estado)) {
                res.setStatus(400); res.getWriter().print("{\"error\":\"Datos inválidos\"}"); return;
            }
            String mensaje = vacioNull(req.getParameter("mensaje"));
            String monto = vacioNull(req.getParameter("monto_pagado"));
            MisPedidosServlet.asegurar(c, pid);
            try (PreparedStatement st = c.prepareStatement(
                "UPDATE PedidoSeguimiento SET estado=?, monto_pagado=COALESCE(TRY_CONVERT(decimal(12,2),?),monto_pagado), "
              + "sinpe_numero=COALESCE(?,sinpe_numero), fecha_est_desde=COALESCE(TRY_CONVERT(date,?),fecha_est_desde), "
              + "fecha_est_hasta=COALESCE(TRY_CONVERT(date,?),fecha_est_hasta), numero_guia=COALESCE(?,numero_guia), "
              + "url_rastreo=COALESCE(?,url_rastreo), fecha_ultima_novedad=SYSDATETIME() WHERE pedido_id=?")) {
                st.setString(1, estado); st.setString(2, monto); st.setString(3, vacioNull(req.getParameter("sinpe_numero")));
                st.setString(4, vacioNull(req.getParameter("fecha_est_desde"))); st.setString(5, vacioNull(req.getParameter("fecha_est_hasta")));
                st.setString(6, vacioNull(req.getParameter("numero_guia"))); st.setString(7, vacioNull(req.getParameter("url_rastreo")));
                st.setInt(8, pid); st.executeUpdate();
            }
            if (mensaje != null) {
                try (PreparedStatement st = c.prepareStatement("INSERT INTO PedidoActualizaciones(pedido_id,estado,mensaje) VALUES(?,?,?)")) {
                    st.setInt(1, pid); st.setString(2, estado); st.setString(3, mensaje.length() > 600 ? mensaje.substring(0, 600) : mensaje); st.executeUpdate();
                }
            }
            if (mensaje != null && "1".equals(req.getParameter("notificar"))) {
                try (PreparedStatement st = c.prepareStatement("SELECT u.nombre, u.correo FROM Pedidos p JOIN Usuarios u ON u.id=p.usuario_id WHERE p.id=?")) {
                    st.setInt(1, pid);
                    try (ResultSet rs = st.executeQuery()) {
                        if (rs.next() && rs.getString(2) != null) {
                            String etq = estado;
                            for (String[] e : ETQ) if (e[0].equals(estado)) etq = e[1];
                            EmailService.enviarNovedadPedido(rs.getString(2), rs.getString(1), String.valueOf(pid), etq, mensaje);
                        }
                    }
                }
            }
            res.getWriter().print("{\"ok\":true}");
        } catch (Exception e) { e.printStackTrace(); res.setStatus(500); res.getWriter().print("{\"error\":\"Error del servidor\"}"); }
    }
}