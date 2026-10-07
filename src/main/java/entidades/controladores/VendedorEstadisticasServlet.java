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

/**
 * Estadísticas del vendedor que tiene la sesión abierta (botón "Estadísticas" en Mi tienda).
 * Solo devuelve SUS ventas: el id sale de la sesión, nunca de un parámetro.
 */
@WebServlet("/api/vendedor/estadisticas")
public class VendedorEstadisticasServlet extends HttpServlet {

    private static final int PEDIDO_DESDE = 13;
    // ⚠ Nombre(s) del atributo de sesión donde tu login de vendedor guarda su usuario_id.
    // Míralo en tu servlet de /api/mi-tienda-login (session.setAttribute("...", ...)).
    private static final String[] ATRIBUTOS_SESION = {"usuario_id", "usuarioId", "vendedorId"};

    private Integer usuarioDeSesion(HttpServletRequest request) {
        HttpSession ses = request.getSession(false);
        if (ses == null) return null;
        for (String k : ATRIBUTOS_SESION) {
            Object v = ses.getAttribute(k);
            if (v != null) {
                try { return Integer.parseInt(v.toString().trim()); } catch (NumberFormatException ignorar) { }
            }
        }
        return null;
    }

    private static String str(String s) {
        return s == null ? "null" : "\"" + JsonUtils.escapar(s) + "\"";
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        Integer uid = usuarioDeSesion(request);
        if (uid == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"error\":\"No autorizado\"}");
            return;
        }

        String sqlEsVendedor = "SELECT 1 FROM Vendedores WHERE usuario_id = ?";
        String sql =
              "SELECT pe.id, pe.fecha, ISNULL(ult.estado, pe.estado) AS estado, "
            + "dp.nombre_producto, dp.imagen_producto, dp.cantidad, dp.precio_unitario "
            + "FROM Pedidos pe "
            + "JOIN DetallePedido dp ON dp.pedido_id = pe.id "
            + "OUTER APPLY (SELECT TOP 1 estado FROM PedidoActualizaciones a "
            + "             WHERE a.pedido_id = pe.id ORDER BY a.fecha DESC, a.id DESC) ult "
            + "WHERE pe.id >= " + PEDIDO_DESDE + " AND pe.id NOT IN (17, 19, 25) "
            + "AND dp.usuario_id_vendedor = ? "
            + "AND ISNULL(ult.estado, pe.estado) NOT LIKE '%cancel%' "
            + "ORDER BY pe.id DESC";

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sqlEsVendedor)) {
                ps.setInt(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().print("{\"error\":\"No es vendedor\"}");
                        return;
                    }
                }
            }

            PrintWriter out = response.getWriter();
            out.print("{\"usuario_id\":" + uid + ",\"pedidos\":[");

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    int actual = -1;
                    boolean primerPedido = true, primerItem = true;
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        if (id != actual) {
                            if (actual != -1) out.print("]}");
                            if (!primerPedido) out.print(",");
                            primerPedido = false;
                            primerItem = true;
                            actual = id;
                            out.print("{\"id\":" + id
                                    + ",\"fecha\":" + str(String.valueOf(rs.getTimestamp("fecha")))
                                    + ",\"estado\":" + str(rs.getString("estado"))
                                    + ",\"items\":[");
                        }
                        if (!primerItem) out.print(",");
                        primerItem = false;
                        out.print("{\"nombre\":" + str(rs.getString("nombre_producto"))
                                + ",\"imagen\":" + str(rs.getString("imagen_producto"))
                                + ",\"cantidad\":" + rs.getInt("cantidad")
                                + ",\"precio\":" + rs.getDouble("precio_unitario") + "}");
                    }
                    if (actual != -1) out.print("]}");
                }
            }
            out.print("]}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"error\":" + str(e.getMessage()) + "}");
        }
    }
}