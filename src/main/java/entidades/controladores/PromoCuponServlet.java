package entidades.controladores;

import java.io.IOException;
import java.sql.*;
import entidades.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

/**
 * GET  /promoCupon            -> pública: datos del cupón + cupos restantes (?pedido=ID agrega "tuPedidoGana")
 * GET  /admin/promoCupon      -> admin: lo mismo (para llenar el formulario)
 * POST /admin/promoCupon      -> admin: guarda cambios (accion=guardar | accion=reiniciar)
 * Las rutas /admin/* se validan aquí mismo con la sesión del admin (igual que PedidosAdminServlet).
 */
@WebServlet({"/promoCupon", "/admin/promoCupon"})
public class PromoCuponServlet extends HttpServlet {

    static Connection conexion() throws SQLException {
        return DatabaseConnection.getConnection();
    }

    // ⚠️ Igual que en PedidosAdminServlet: atributo de sesión del admin.
    private boolean esAdmin(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        return s != null && s.getAttribute("adminId") != null;
    }
    private boolean esRutaAdmin(HttpServletRequest r) { return r.getRequestURI().contains("/admin/"); }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Cache-Control", "no-store");
        if (esRutaAdmin(req) && !esAdmin(req)) { res.setStatus(401); res.getWriter().write("{\"error\":\"No autorizado\"}"); return; }
        try (Connection c = conexion()) {
            res.getWriter().write(json(c, req.getParameter("pedido")));
        } catch (Exception e) {
            res.setStatus(500);
            res.getWriter().write("{\"error\":\"No se pudo cargar la promoción\"}");
        }
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        req.setCharacterEncoding("UTF-8");
        res.setContentType("application/json;charset=UTF-8");
        if (!esRutaAdmin(req)) { res.setStatus(405); return; }
        if (!esAdmin(req)) { res.setStatus(401); res.getWriter().write("{\"error\":\"No autorizado\"}"); return; }
        try (Connection c = conexion()) {
            if ("reiniciar".equals(req.getParameter("accion"))) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE PromoCupon SET campania = campania + 1 WHERE id = 1")) { ps.executeUpdate(); }
            } else {
                int cupos = Math.max(1, Math.min(1000, Integer.parseInt(req.getParameter("cupos"))));
                String entrega = "comprador".equals(req.getParameter("entrega")) ? "comprador" : "tienda";
                String sql = "UPDATE PromoCupon SET activa=?, etiqueta=?, titulo=?, condiciones=?, categoria=?, entrega=?, cupos=?, fecha_fin=? WHERE id=1";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setBoolean(1, "1".equals(req.getParameter("activa")));
                    ps.setString(2, recorte(req.getParameter("etiqueta"), 120));
                    ps.setString(3, recorte(req.getParameter("titulo"), 300));
                    ps.setString(4, recorte(req.getParameter("condiciones"), 300));
                    ps.setString(5, recorte(req.getParameter("categoria"), 60));
                    ps.setString(6, entrega);
                    ps.setInt(7, cupos);
                    String f = req.getParameter("fechaFin").replace('T', ' ');
                    if (f.length() == 16) f += ":00";
                    ps.setTimestamp(8, Timestamp.valueOf(f));
                    ps.executeUpdate();
                }
            }
            res.getWriter().write(json(c, null));
        } catch (Exception e) {
            res.setStatus(400);
            res.getWriter().write("{\"error\":\"Datos inválidos\"}");
        }
    }

    /**
     * 👉 Llámalo UNA vez al confirmar un pedido (en el servlet que ya envía la factura).
     * Devuelve true si este pedido se lleva el producto gratis (queda registrado, sin pasarse de los cupos).
     * Úsalo para añadir la línea del regalo en la factura/PDF y en los correos.
     */
    public static boolean registrarPedido(Connection c, int pedidoId) throws SQLException {
        String sql =
            "SET NOCOUNT ON; DECLARE @camp INT, @cupos INT, @activa BIT, @fin DATETIME2; " +
            "SELECT @camp=campania, @cupos=cupos, @activa=activa, @fin=fecha_fin FROM PromoCupon WITH (UPDLOCK, HOLDLOCK) WHERE id=1; " +
            "IF @activa=1 AND @fin > SYSDATETIME() AND (SELECT COUNT(*) FROM PromoCuponGanadores WHERE campania=@camp) < @cupos " +
            "AND NOT EXISTS (SELECT 1 FROM PromoCuponGanadores WHERE campania=@camp AND pedido_id=?) " +
            "BEGIN INSERT INTO PromoCuponGanadores(campania, pedido_id) VALUES (@camp, ?); END " +
            "SELECT CASE WHEN EXISTS (SELECT 1 FROM PromoCuponGanadores WHERE campania=@camp AND pedido_id=?) THEN 1 ELSE 0 END;";
        boolean auto = c.getAutoCommit();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, pedidoId); ps.setInt(2, pedidoId); ps.setInt(3, pedidoId);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); boolean gana = rs.getInt(1) == 1; c.commit(); return gana; }
            }
        } catch (SQLException e) { c.rollback(); throw e; }
        finally { c.setAutoCommit(auto); }
    }

    private static String json(Connection c, String pedido) throws SQLException {
        String sql = "SELECT p.activa,p.etiqueta,p.titulo,p.condiciones,p.categoria,p.entrega,p.cupos,p.campania," +
                     "CONVERT(VARCHAR(16), p.fecha_fin, 126) AS fin, " +
                     "(SELECT COUNT(*) FROM PromoCuponGanadores g WHERE g.campania=p.campania) AS usados " +
                     "FROM PromoCupon p WHERE p.id=1";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) return "{\"activa\":false}";
            int cupos = rs.getInt("cupos"), usados = rs.getInt("usados"), camp = rs.getInt("campania");
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"activa\":").append(rs.getBoolean("activa"));
            sb.append(",\"etiqueta\":").append(q(rs.getString("etiqueta")));
            sb.append(",\"titulo\":").append(q(rs.getString("titulo")));
            sb.append(",\"condiciones\":").append(q(rs.getString("condiciones")));
            sb.append(",\"categoria\":").append(q(rs.getString("categoria")));
            sb.append(",\"entrega\":").append(q(rs.getString("entrega")));
            sb.append(",\"cupos\":").append(cupos).append(",\"usados\":").append(usados);
            sb.append(",\"restantes\":").append(Math.max(0, cupos - usados));
            sb.append(",\"fechaFin\":").append(q(rs.getString("fin") + ":00-06:00")); // Costa Rica UTC-6
            if (pedido != null && pedido.matches("\\d{1,9}")) {
                try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM PromoCuponGanadores WHERE campania=? AND pedido_id=?")) {
                    ps.setInt(1, camp); ps.setInt(2, Integer.parseInt(pedido));
                    try (ResultSet r2 = ps.executeQuery()) { sb.append(",\"tuPedidoGana\":").append(r2.next()); }
                }
            }
            return sb.append("}").toString();
        }
    }

    private static String recorte(String s, int max) { s = s == null ? "" : s.trim(); return s.length() > max ? s.substring(0, max) : s; }
    private static String q(String s) {
        if (s == null) return "\"\"";
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 0x20) b.append(' ');
            else b.append(ch);
        }
        return b.append('"').toString();
    }
}