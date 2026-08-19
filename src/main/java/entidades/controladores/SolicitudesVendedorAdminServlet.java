package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import entidades.DatabaseConnection;

@WebServlet("/admin/solicitudesVendedor")
public class SolicitudesVendedorAdminServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        if (req.getSession().getAttribute("adminId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"error\":\"No autorizado\"}");
            return;
        }

        String sql = """
            SELECT u.id AS usuario_id, u.nombre, u.correo,
                   s.provincia, s.canton, s.descripcion, s.telefono,
                   v.id AS vendedor_id, v.tipo_suscripcion, v.cedula, v.suscrito,
                   sv.fecha_inicio, sv.fecha_vencimiento,
                   CASE WHEN v.suscrito = 1 AND sv.fecha_vencimiento IS NOT NULL
                             AND sv.fecha_vencimiento <= CAST(GETDATE() AS DATE)
                        THEN 1 ELSE 0 END AS vencida
            FROM Usuarios u
            JOIN SolicitudesDeVendedor s ON s.usuario_id = u.id
            JOIN Vendedores v ON v.usuario_id = u.id
            LEFT JOIN SuscripcionVendedor sv ON sv.usuario_id = u.id
            WHERE v.suscrito = 0
               OR (v.suscrito = 1 AND sv.fecha_vencimiento IS NOT NULL
                   AND sv.fecha_vencimiento <= CAST(GETDATE() AS DATE))
            ORDER BY s.id DESC
            """;

        StringBuilder json = new StringBuilder("[");
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                Date vencDate = rs.getDate("fecha_vencimiento");
                json.append("{")
                    .append("\"usuario_id\":").append(rs.getInt("usuario_id")).append(",")
                    .append("\"nombre\":\"").append(esc(rs.getString("nombre"))).append("\",")
                    .append("\"correo\":\"").append(esc(rs.getString("correo"))).append("\",")
                    .append("\"provincia\":\"").append(esc(rs.getString("provincia"))).append("\",")
                    .append("\"canton\":\"").append(esc(rs.getString("canton"))).append("\",")
                    .append("\"descripcion\":\"").append(esc(rs.getString("descripcion"))).append("\",")
                    .append("\"telefono\":\"").append(esc(rs.getString("telefono"))).append("\",")
                    .append("\"vendedor_id\":").append(rs.getInt("vendedor_id")).append(",")
                    .append("\"tipo_suscripcion\":\"").append(esc(rs.getString("tipo_suscripcion"))).append("\",")
                    .append("\"cedula\":\"").append(esc(rs.getString("cedula"))).append("\",")
                    .append("\"suscrito\":").append(rs.getInt("suscrito")).append(",")
                    .append("\"fecha_vencimiento\":\"").append(vencDate == null ? "" : vencDate.toString()).append("\",")
                    .append("\"vencida\":").append(rs.getInt("vencida"))
                    .append("}");
            }
        } catch (SQLException e) {
            resp.setStatus(500);
            out.print("{\"error\":\"" + esc(e.getMessage()) + "\"}");
            return;
        }
        json.append("]");
        out.print(json);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        if (req.getSession().getAttribute("adminId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"error\":\"No autorizado\"}");
            return;
        }

        int usuarioId;
        try {
            usuarioId = Integer.parseInt(req.getParameter("usuario_id"));
        } catch (Exception e) {
            resp.setStatus(400);
            out.print("{\"error\":\"usuario_id inválido\"}");
            return;
        }
        String accion = req.getParameter("accion");

        if (!"aprobar".equals(accion) && !"revertir".equals(accion)) {
            resp.setStatus(400);
            out.print("{\"error\":\"acción inválida\"}");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if ("aprobar".equals(accion)) {
                    String sqlUpsert = """
                        MERGE SuscripcionVendedor AS target
                        USING (SELECT ? AS usuario_id) AS source
                        ON target.usuario_id = source.usuario_id
                        WHEN MATCHED THEN
                            UPDATE SET fecha_inicio = GETDATE(), fecha_vencimiento = DATEADD(MONTH, 1, GETDATE())
                        WHEN NOT MATCHED THEN
                            INSERT (usuario_id, fecha_inicio, fecha_vencimiento)
                            VALUES (source.usuario_id, GETDATE(), DATEADD(MONTH, 1, GETDATE()));
                        """;
                    try (PreparedStatement ps = conn.prepareStatement(sqlUpsert)) {
                        ps.setInt(1, usuarioId);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE Vendedores SET suscrito = 1 WHERE usuario_id = ?")) {
                        ps.setInt(1, usuarioId);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE SolicitudesDeVendedor SET estado = 'Activo' WHERE usuario_id = ?")) {
                        ps.setInt(1, usuarioId);
                        ps.executeUpdate();
                    }
                } else { // revertir
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE Vendedores SET suscrito = 0 WHERE usuario_id = ?")) {
                        ps.setInt(1, usuarioId);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE SolicitudesDeVendedor SET estado = 'Pendiente' WHERE usuario_id = ?")) {
                        ps.setInt(1, usuarioId);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            resp.setStatus(500);
            out.print("{\"error\":\"" + esc(e.getMessage()) + "\"}");
            return;
        }
        out.print("{\"ok\":true}");
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}