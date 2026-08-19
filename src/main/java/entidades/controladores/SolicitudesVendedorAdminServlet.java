package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
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
                   s.id AS solicitud_id, s.provincia, s.canton, s.descripcion, s.telefono, s.precio_promedio,
                   v.id AS vendedor_id, v.tipo_suscripcion, v.cedula, v.suscrito, v.metodo_de_pago,
                   sv.fecha_inicio, sv.fecha_vencimiento, n.nota AS notas,
                   CASE WHEN v.suscrito = 1 AND sv.fecha_vencimiento IS NOT NULL
                             AND sv.fecha_vencimiento <= CAST(GETDATE() AS DATE)
                        THEN 1 ELSE 0 END AS vencida
            FROM Usuarios u
            JOIN SolicitudesDeVendedor s ON s.usuario_id = u.id
            JOIN Vendedores v ON v.usuario_id = u.id
            LEFT JOIN SuscripcionVendedor sv ON sv.usuario_id = u.id
            LEFT JOIN Notas n ON n.solicitud_id = s.id
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
                BigDecimal precioProm = rs.getBigDecimal("precio_promedio");
                json.append("{")
                    .append("\"usuario_id\":").append(rs.getInt("usuario_id")).append(",")
                    .append("\"solicitud_id\":").append(rs.getInt("solicitud_id")).append(",")
                    .append("\"nombre\":\"").append(esc(rs.getString("nombre"))).append("\",")
                    .append("\"correo\":\"").append(esc(rs.getString("correo"))).append("\",")
                    .append("\"provincia\":\"").append(esc(rs.getString("provincia"))).append("\",")
                    .append("\"canton\":\"").append(esc(rs.getString("canton"))).append("\",")
                    .append("\"descripcion\":\"").append(esc(rs.getString("descripcion"))).append("\",")
                    .append("\"telefono\":\"").append(esc(rs.getString("telefono"))).append("\",")
                    .append("\"precio_promedio\":").append(precioProm == null ? "null" : precioProm.toPlainString()).append(",")
                    .append("\"vendedor_id\":").append(rs.getInt("vendedor_id")).append(",")
                    .append("\"tipo_suscripcion\":\"").append(esc(rs.getString("tipo_suscripcion"))).append("\",")
                    .append("\"cedula\":\"").append(esc(rs.getString("cedula"))).append("\",")
                    .append("\"suscrito\":").append(rs.getInt("suscrito")).append(",")
                    .append("\"metodo_pago\":\"").append(esc(rs.getString("metodo_de_pago"))).append("\",")
                    .append("\"notas\":\"").append(esc(rs.getString("notas"))).append("\",")
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

        String accion = req.getParameter("accion");

        if (!"aprobar".equals(accion) && !"revertir".equals(accion) && !"nota".equals(accion)) {
            resp.setStatus(400);
            out.print("{\"error\":\"acción inválida\"}");
            return;
        }

        if ("nota".equals(accion)) {
            int solicitudId;
            try {
                solicitudId = Integer.parseInt(req.getParameter("solicitud_id"));
            } catch (Exception e) {
                resp.setStatus(400);
                out.print("{\"error\":\"solicitud_id inválido\"}");
                return;
            }
            String nota = req.getParameter("nota");
            if (nota != null && nota.length() > 500) {
                nota = nota.substring(0, 500);
            }
            String sqlUpsertNota = """
                MERGE Notas AS target
                USING (SELECT ? AS solicitud_id) AS source
                ON target.solicitud_id = source.solicitud_id
                WHEN MATCHED THEN
                    UPDATE SET nota = ?, fecha_actualizacion = GETDATE()
                WHEN NOT MATCHED THEN
                    INSERT (solicitud_id, nota, fecha_actualizacion)
                    VALUES (source.solicitud_id, ?, GETDATE());
                """;
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sqlUpsertNota)) {
                ps.setInt(1, solicitudId);
                ps.setString(2, nota);
                ps.setString(3, nota);
                ps.executeUpdate();
            } catch (SQLException e) {
                resp.setStatus(500);
                out.print("{\"error\":\"" + esc(e.getMessage()) + "\"}");
                return;
            }
            out.print("{\"ok\":true}");
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