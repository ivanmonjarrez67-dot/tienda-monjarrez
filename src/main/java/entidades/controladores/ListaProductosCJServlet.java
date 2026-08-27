package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

import config.Config;
import entidades.DatabaseConnection;
import entidades.JsonUtils;

// 📋 Devuelve el listado de productos CJ activos para la sección
// "Selección Tienda Monjarrez" del catálogo público. Para cada producto,
// trae el precio de la variante más barata (misma que define
// precio_venta_desde) junto con su tiempo de envío estimado, y calcula
// el precio en colones usando el tipo de cambio configurado.
@WebServlet("/listaProductosCJ")
public class ListaProductosCJServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String sql =
            "SELECT p.id, p.nombre, p.imagen, p.categoria, p.precio_venta_desde, " +
            "       v.envio_dias " +
            "FROM ProductosCJ p " +
            "OUTER APPLY ( " +
            "    SELECT TOP 1 vv.envio_dias " +
            "    FROM VariantesCJ vv " +
            "    WHERE vv.producto_cj_id = p.id AND vv.activo = 1 " +
            "    ORDER BY vv.precio_venta ASC " +
            ") v " +
            "WHERE p.activo = 1 " +
            "ORDER BY p.fecha_importacion DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean primero = true;

            while (rs.next()) {
                if (!primero) json.append(",");
                primero = false;

                double precioUsd = rs.getDouble("precio_venta_desde");
                double precioCrc = precioUsd * Config.TIPO_CAMBIO_USD_CRC;
                String envioDias = rs.getString("envio_dias");

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"nombre\":\"").append(JsonUtils.escapar(rs.getString("nombre"))).append("\",");
                json.append("\"imagen\":\"").append(JsonUtils.escapar(rs.getString("imagen"))).append("\",");
                json.append("\"categoria\":\"").append(JsonUtils.escapar(rs.getString("categoria"))).append("\",");
                json.append("\"precioUsd\":").append(precioUsd).append(",");
                json.append("\"precioCrc\":").append(Math.round(precioCrc)).append(",");
                json.append("\"envioDias\":\"").append(JsonUtils.escapar(envioDias != null ? envioDias : "")).append("\"");
                json.append("}");
            }

            json.append("]");
            out.print(json.toString());

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(500);
            out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar("Error en base de datos: " + e.getMessage()) + "\"}");
        }
    }
}