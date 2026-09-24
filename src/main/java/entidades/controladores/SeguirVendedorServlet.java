/*
 * ---- Correr UNA vez en Azure SQL (tabla nueva, no toca ninguna existente) ----
 *
 * CREATE TABLE SeguidoresVendedor (
 *     id            INT IDENTITY(1,1) PRIMARY KEY,
 *     seguidor_id   INT NOT NULL,              -- Usuarios.id de quien sigue
 *     vendedor_id   INT NOT NULL,              -- Usuarios.id del vendedor seguido
 *     fecha         DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
 *     CONSTRAINT UQ_Seguidor_Vendedor UNIQUE (seguidor_id, vendedor_id)
 * );
 * CREATE INDEX IX_SeguidoresVendedor_vendedor ON SeguidoresVendedor (vendedor_id);
 */
package entidades.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import entidades.DatabaseConnection;
import entidades.JsonUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/seguir")
public class SeguirVendedorServlet extends HttpServlet {

    // Mismo atributo de sesión que usa CarritoServlet.
    private static final String ATTR_USUARIO_ID = "usuarioId";
    private static final int MUESTRA_SEGUIDORES = 5;

    /** GET ?vendedor_id=19 -> {"seguidores":N,"siguiendo":true|false}. Público: sin sesión siguiendo=false. */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("mis_seguidores".equals(req.getParameter("accion"))) { misSeguidores(req, resp); return; }
        int vendedorId = leerId(req.getParameter("vendedor_id"));
        if (vendedorId <= 0) { error(resp, 400, "vendedor_id inválido"); return; }
        Integer usuarioId = usuarioActual(req);

        try (Connection con = DatabaseConnection.getConnection()) {
            json(resp, 200, estado(con, vendedorId, usuarioId));
        } catch (Exception e) {
            e.printStackTrace();
            error(resp, 500, "No se pudo consultar los seguidores.");
        }
    }

    /** POST accion=seguir|dejar & vendedor_id=19 (requiere sesión). */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer usuarioId = usuarioActual(req);
        if (usuarioId == null) { error(resp, 401, "Debes iniciar sesión."); return; }

        int vendedorId = leerId(req.getParameter("vendedor_id"));
        String accion = req.getParameter("accion");
        if (vendedorId <= 0 || accion == null) { error(resp, 400, "Datos incompletos."); return; }
        if (vendedorId == usuarioId) { error(resp, 400, "No puedes seguir tu propio emprendimiento."); return; }

        try (Connection con = DatabaseConnection.getConnection()) {
            if ("seguir".equals(accion)) {
                // Solo se puede seguir a un usuario que sea vendedor.
                try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM Vendedores WHERE usuario_id = ?")) {
                    ps.setInt(1, vendedorId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { error(resp, 404, "Vendedor no encontrado."); return; }
                    }
                }
                // Idempotente: si ya lo seguía, no falla ni duplica.
                String sql = "IF NOT EXISTS (SELECT 1 FROM SeguidoresVendedor WHERE seguidor_id = ? AND vendedor_id = ?) "
                           + "INSERT INTO SeguidoresVendedor (seguidor_id, vendedor_id) VALUES (?, ?)";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, usuarioId); ps.setInt(2, vendedorId);
                    ps.setInt(3, usuarioId); ps.setInt(4, vendedorId);
                    ps.executeUpdate();
                }
            } else if ("dejar".equals(accion)) {
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM SeguidoresVendedor WHERE seguidor_id = ? AND vendedor_id = ?")) {
                    ps.setInt(1, usuarioId); ps.setInt(2, vendedorId);
                    ps.executeUpdate();
                }
            } else {
                error(resp, 400, "Acción inválida."); return;
            }
            json(resp, 200, estado(con, vendedorId, usuarioId));
        } catch (Exception e) {
            e.printStackTrace();
            error(resp, 500, "No se pudo completar la acción.");
        }
    }

    /**
     * GET ?accion=mis_seguidores  (para "Mi tienda")
     * Solo el propio vendedor logueado ve su lista: la identidad sale de la sesión, nunca del cliente.
     * Devuelve el total y una muestra pequeña (los más recientes, solo primer nombre) para que la
     * interfaz pueda mostrar "Ana, Luis, Marta y 42 más".
     */
    private void misSeguidores(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer usuarioId = usuarioActual(req);
        if (usuarioId == null) { error(resp, 401, "Debes iniciar sesión."); return; }

        String sqlMuestra = "SELECT TOP (" + MUESTRA_SEGUIDORES + ") u.nombre "
                          + "FROM SeguidoresVendedor sv JOIN Usuarios u ON u.id = sv.seguidor_id "
                          + "WHERE sv.vendedor_id = ? ORDER BY sv.fecha DESC";
        try (Connection con = DatabaseConnection.getConnection()) {
            int total = 0;
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM SeguidoresVendedor WHERE vendedor_id = ?")) {
                ps.setInt(1, usuarioId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) total = rs.getInt(1); }
            }
            StringBuilder sb = new StringBuilder("{\"total\":" + total + ",\"muestra\":[");
            try (PreparedStatement ps = con.prepareStatement(sqlMuestra)) {
                ps.setInt(1, usuarioId);
                try (ResultSet rs = ps.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        String nombre = rs.getString(1) == null ? "" : rs.getString(1).trim();
                        int sp = nombre.indexOf(' ');
                        String primerNombre = sp > 0 ? nombre.substring(0, sp) : nombre;
                        if (!first) sb.append(",");
                        first = false;
                        sb.append("\"").append(JsonUtils.escapar(primerNombre)).append("\"");
                    }
                }
            }
            sb.append("]}");
            json(resp, 200, sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
            error(resp, 500, "No se pudo consultar tus seguidores.");
        }
    }

    // ---------- helpers ----------

    private String estado(Connection con, int vendedorId, Integer usuarioId) throws Exception {
        int total = 0;
        boolean sigue = false;
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM SeguidoresVendedor WHERE vendedor_id = ?")) {
            ps.setInt(1, vendedorId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) total = rs.getInt(1); }
        }
        if (usuarioId != null) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT 1 FROM SeguidoresVendedor WHERE seguidor_id = ? AND vendedor_id = ?")) {
                ps.setInt(1, usuarioId); ps.setInt(2, vendedorId);
                try (ResultSet rs = ps.executeQuery()) { sigue = rs.next(); }
            }
        }
        return "{\"seguidores\":" + total + ",\"siguiendo\":" + sigue + "}";
    }

    private Integer usuarioActual(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return null;
        Object v = session.getAttribute(ATTR_USUARIO_ID);
        if (v == null) return null;
        try { return Integer.valueOf(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    private int leerId(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }

    private void json(HttpServletResponse resp, int status, String body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(body);
    }

    private void error(HttpServletResponse resp, int status, String msg) throws IOException {
        json(resp, status, "{\"error\":\"" + msg.replace("\"", "'") + "\"}");
    }
}