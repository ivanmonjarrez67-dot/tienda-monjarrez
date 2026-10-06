package entidades.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import entidades.DatabaseConnection;
import entidades.JsonUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Estrategia de urgencia selectiva.
 *  - GET  /api/urgencia-config   (público)  -> {"ids":[ids de producto que SÍ llevan urgencia]}
 *  - GET  /admin/urgenciaConfig  (admin)    -> vendedores + categorías con su estado
 *  - POST /admin/urgenciaConfig  (admin)    -> accion=guardar&vendedores=1,2,3&categorias=Damas|Hogar y Decoración
 *
 * Un producto lleva urgencia solo si su vendedor está activo Y su categoría está activa.
 */
@WebServlet(urlPatterns = {"/api/urgencia-config", "/admin/urgenciaConfig"})
public class UrgenciaConfigServlet extends HttpServlet {

    private Connection conectar() throws SQLException {
        return DatabaseConnection.getConnection();
    }

    // ⚠️ AJUSTAR (único pendiente): pon aquí la misma validación de sesión de admin que usa tu
    // PromoCuponServlet (el atributo de sesión que guarda el login de admin). "adminId" es un supuesto.
    private boolean esAdmin(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && s.getAttribute("adminId") != null;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setHeader("Cache-Control", "no-store");
        if (req.getServletPath().equals("/api/urgencia-config")) {
            publico(resp);
        } else if (!esAdmin(req)) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"no autorizado\"}");
        } else {
            adminListar(resp);
        }
    }

    private void publico(HttpServletResponse resp) throws IOException {
        String sql = "SELECT p.id FROM Productos p "
                + "JOIN UrgenciaVendedores uv ON uv.usuario_id = p.usuario_id AND uv.activa = 1 "
                + "JOIN UrgenciaCategorias uc ON uc.categoria = p.categoria AND uc.activa = 1";
        StringBuilder sb = new StringBuilder("{\"ids\":[");
        try (Connection c = conectar(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            boolean primero = true;
            while (rs.next()) {
                if (!primero) sb.append(',');
                sb.append(rs.getLong(1));
                primero = false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write("{\"ids\":[]}");
            return;
        }
        sb.append("]}");
        resp.getWriter().write(sb.toString());
    }

    private void adminListar(HttpServletResponse resp) throws IOException {
        // Vendedores = usuarios de la tabla Usuarios que tienen al menos un producto.
        String sqlV = "SELECT u.id AS usuario_id, u.nombre, COUNT(p.id) AS productos, "
                + "CAST(MAX(CASE WHEN uv.activa = 1 THEN 1 ELSE 0 END) AS INT) AS activo "
                + "FROM Usuarios u JOIN Productos p ON p.usuario_id = u.id "
                + "LEFT JOIN UrgenciaVendedores uv ON uv.usuario_id = u.id "
                + "GROUP BY u.id, u.nombre ORDER BY u.nombre";
        String sqlC = "SELECT categoria, activa FROM UrgenciaCategorias ORDER BY categoria";
        StringBuilder sb = new StringBuilder("{\"vendedores\":[");
        try (Connection c = conectar()) {
            try (PreparedStatement ps = c.prepareStatement(sqlV); ResultSet rs = ps.executeQuery()) {
                boolean primero = true;
                while (rs.next()) {
                    if (!primero) sb.append(',');
                    sb.append("{\"usuario_id\":").append(rs.getInt("usuario_id"))
                      .append(",\"nombre\":\"").append(JsonUtils.escapar(rs.getString("nombre"))).append("\"")
                      .append(",\"productos\":").append(rs.getInt("productos"))
                      .append(",\"activo\":").append(rs.getInt("activo") == 1).append('}');
                    primero = false;
                }
            }
            sb.append("],\"categorias\":[");
            try (PreparedStatement ps = c.prepareStatement(sqlC); ResultSet rs = ps.executeQuery()) {
                boolean primero = true;
                while (rs.next()) {
                    if (!primero) sb.append(',');
                    sb.append("{\"nombre\":\"").append(JsonUtils.escapar(rs.getString(1))).append("\",\"activa\":")
                      .append(rs.getBoolean(2)).append('}');
                    primero = false;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"error de base de datos\"}");
            return;
        }
        sb.append("]}");
        resp.getWriter().write(sb.toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        if (!esAdmin(req)) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"no autorizado\"}");
            return;
        }
        if (!"guardar".equals(req.getParameter("accion"))) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"acción inválida\"}");
            return;
        }
        List<Integer> vendedores = new ArrayList<>();
        String v = req.getParameter("vendedores");
        if (v != null && !v.trim().isEmpty()) {
            for (String t : v.split(",")) {
                try { vendedores.add(Integer.parseInt(t.trim())); } catch (NumberFormatException ignore) { }
            }
        }
        Set<String> activas = new HashSet<>();
        String cats = req.getParameter("categorias");
        if (cats != null && !cats.trim().isEmpty()) {
            for (String t : cats.split("\\|")) activas.add(t.trim().toLowerCase());
        }

        try (Connection c = conectar()) {
            c.setAutoCommit(false);
            try {
                // Vendedores: se reemplaza la lista completa por lo que marcó el admin.
                try (Statement st = c.createStatement()) { st.executeUpdate("DELETE FROM UrgenciaVendedores"); }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO UrgenciaVendedores (usuario_id, activa) VALUES (?, 1)")) {
                    for (Integer id : vendedores) { ps.setInt(1, id); ps.addBatch(); }
                    ps.executeBatch();
                }
                // Categorías: se actualiza el estado de las que ya existen.
                List<String> nombres = new ArrayList<>();
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT categoria FROM UrgenciaCategorias")) {
                    while (rs.next()) nombres.add(rs.getString(1));
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE UrgenciaCategorias SET activa = ? WHERE categoria = ?")) {
                    for (String n : nombres) {
                        ps.setBoolean(1, activas.contains(n.toLowerCase()));
                        ps.setString(2, n);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"no se pudo guardar\"}");
            return;
        }
        resp.getWriter().write("{\"ok\":true}");
    }
}