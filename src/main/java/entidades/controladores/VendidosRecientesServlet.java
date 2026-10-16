package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import entidades.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Datos públicos para las insignias de la página principal.
 * Reemplaza al servlet anterior de /api/vendidos-recientes (BORRA el viejo
 * o el servidor no arrancará por tener dos servlets en la misma URL).
 *
 * Devuelve:
 * { "ranking":[{"id":12,"u":2}, ...],   // unidades por producto, mismo orden de llegada que EstadisticasServlet
 *   "recientes":[45,12,88, ...] }       // ids distintos, el último vendido primero
 *
 * Usa los MISMOS filtros que estadisticas.html: pedidos desde el #13,
 * sin los cancelados (17, 19, 25) y sin estado que contenga "cancel".
 * Solo expone ids y cantidades (nada de clientes ni montos).
 */
@WebServlet("/api/vendidos-recientes")
public class VendidosRecientesServlet extends HttpServlet {

    private static final int PEDIDO_DESDE = 13;
    private static final Set<Integer> CANCELADOS = Set.of(17, 19, 25);

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        // Mismo estado que usa el panel: última novedad o, si no hay, el del pedido.
        // ⚠ Si tu columna en DetallePedido no se llama producto_id, cámbiala aquí.
        String sql =
              "SELECT pe.id, ISNULL(ult.estado, pe.estado) AS estado, dp.producto_id, dp.cantidad "
            + "FROM Pedidos pe "
            + "JOIN DetallePedido dp ON dp.pedido_id = pe.id "
            + "OUTER APPLY (SELECT TOP 1 estado FROM PedidoActualizaciones a "
            + "             WHERE a.pedido_id = pe.id ORDER BY a.fecha DESC, a.id DESC) ult "
            + "WHERE pe.id >= " + PEDIDO_DESDE + " AND dp.producto_id IS NOT NULL "
            + "ORDER BY pe.id DESC";

        Map<Integer, Integer> unidades = new LinkedHashMap<>(); // orden = primera aparición (pedido más nuevo primero)
        Set<Integer> recientes = new LinkedHashSet<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                int pedidoId = rs.getInt("id");
                String estado = rs.getString("estado");
                if (CANCELADOS.contains(pedidoId)) continue;
                if (estado != null && estado.toLowerCase().contains("cancel")) continue;

                int productoId = rs.getInt("producto_id");
                unidades.merge(productoId, rs.getInt("cantidad"), Integer::sum);
                recientes.add(productoId);
            }

            PrintWriter out = response.getWriter();
            StringBuilder sb = new StringBuilder("{\"ranking\":[");
            boolean first = true;
            for (Map.Entry<Integer, Integer> e : unidades.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"id\":").append(e.getKey()).append(",\"u\":").append(e.getValue()).append("}");
            }
            sb.append("],\"recientes\":[");
            first = true;
            for (Integer id : recientes) {
                if (!first) sb.append(",");
                first = false;
                sb.append(id);
            }
            sb.append("]}");
            out.print(sb);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"error\":\"No disponible\"}");
        }
    }
}