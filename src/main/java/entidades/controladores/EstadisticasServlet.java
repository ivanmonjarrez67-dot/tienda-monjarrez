package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import entidades.DatabaseConnection;
import entidades.JsonUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Datos para estadisticas.html. Devuelve:
 * { "vendedores":[{id,nombre,icono}], "pedidos":[{id,fecha,estado,cliente,total,items:[...]}] }
 * Solo pedidos desde el #13. La página descarta los cancelados.
 */
@WebServlet("/admin/estadisticas")
public class EstadisticasServlet extends HttpServlet {

    private static final int PEDIDO_DESDE = 13;

    // Nombre del atributo de sesión que guarda tu login de admin.
    // Búscalo en tu servlet de login del panel (AdminLogin...): la línea
    // session.setAttribute("XXXX", ...) -> pon aquí ese "XXXX".
    private static final String ATRIBUTO_SESION_ADMIN = "adminId";

    // Si no hay sesión de admin, NO entrega datos (falla cerrado).
    private boolean esAdmin(HttpServletRequest request) {
        HttpSession ses = request.getSession(false);
        return ses != null && ses.getAttribute(ATRIBUTO_SESION_ADMIN) != null;
    }

    private static String str(String s) {
        return s == null ? "null" : "\"" + JsonUtils.escapar(s) + "\"";
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        if (!esAdmin(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print("{\"error\":\"No autorizado\"}");
            return;
        }

        // Una fila por producto de cada pedido. El estado es la última novedad
        // de PedidoActualizaciones; si no tiene, el de Pedidos.
        String sqlPedidos =
              "SELECT pe.id, pe.fecha, pe.usuario_id AS comprador, "
            + "ISNULL(ult.estado, pe.estado) AS estado, "
            + "dp.nombre_producto, dp.imagen_producto, dp.cantidad, dp.precio_unitario, dp.usuario_id_vendedor "
            + "FROM Pedidos pe "
            + "LEFT JOIN DetallePedido dp ON dp.pedido_id = pe.id "
            + "OUTER APPLY (SELECT TOP 1 estado FROM PedidoActualizaciones a "
            + "             WHERE a.pedido_id = pe.id ORDER BY a.fecha DESC, a.id DESC) ult "
            + "WHERE pe.id >= " + PEDIDO_DESDE + " "
            + "ORDER BY pe.id DESC";

        // Nombre = Nombre_Empresa de sus productos; icono = IconosVendedor.
        String sqlVendedores =
              "SELECT p.usuario_id, MAX(p.Nombre_Empresa) AS nombre, MAX(iv.icono) AS icono "
            + "FROM Productos p "
            + "LEFT JOIN Vendedores v ON v.usuario_id = p.usuario_id "
            + "LEFT JOIN IconosVendedor iv ON iv.vendedor_id = v.id "
            + "GROUP BY p.usuario_id";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement st1 = conn.createStatement();
             Statement st2 = conn.createStatement()) {

            PrintWriter out = response.getWriter();
            out.print("{\"vendedores\":[");

            try (ResultSet rs = st2.executeQuery(sqlVendedores)) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.print(",");
                    first = false;
                    out.print("{\"id\":" + rs.getInt("usuario_id")
                            + ",\"nombre\":" + str(rs.getString("nombre"))
                            + ",\"icono\":" + str(rs.getString("icono")) + "}");
                }
            }

            out.print("],\"pedidos\":[");

            try (ResultSet rs = st1.executeQuery(sqlPedidos)) {
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
                                + ",\"cliente\":\"Cliente #" + rs.getInt("comprador") + "\""
                                + ",\"items\":[");
                    }
                    String nombre = rs.getString("nombre_producto");
                    if (nombre == null) continue; // pedido sin detalle
                    if (!primerItem) out.print(",");
                    primerItem = false;
                    out.print("{\"nombre\":" + str(nombre)
                            + ",\"imagen\":" + str(rs.getString("imagen_producto"))
                            + ",\"cantidad\":" + rs.getInt("cantidad")
                            + ",\"precio\":" + rs.getDouble("precio_unitario")
                            + ",\"vendedor_id\":" + rs.getInt("usuario_id_vendedor") + "}");
                }
                if (actual != -1) out.print("]}");
            }

            out.print("]}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"error\":" + str(e.getMessage()) + "}");
        }
    }
}