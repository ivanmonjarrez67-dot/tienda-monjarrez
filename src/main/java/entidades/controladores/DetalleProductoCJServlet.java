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

// 🔎 Trae el detalle COMPLETO de un producto CJ para la página de
// "Ver más detalles" (pestaña nueva, estilo Temu): datos del producto
// + TODAS sus variantes (imagen, precio, envío, opciones) — a
// diferencia de /listaProductosCJ, que solo trae lo mínimo para la
// tarjeta chica del catálogo (la variante más barata).
// Público (sin /admin), de solo lectura: no modifica nada.
@WebServlet("/detalleProductoCJ")
public class DetalleProductoCJServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String idParam = request.getParameter("id");
        int productoCjId;
        try {
            productoCjId = Integer.parseInt(idParam);
        } catch (Exception e) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"error\":\"Falta o es invalido el parametro id\"}");
            return;
        }

        String sqlProducto = "SELECT id, pid, nombre, sku, imagen, categoria, precio_venta_desde "
                + "FROM ProductosCJ WHERE id = ? AND activo = 1";

        String sqlVariantes = "SELECT id, vid, variant_sku, opciones, imagen, envio_dias, "
                + "envio_transportadora, precio_venta, stock "
                + "FROM VariantesCJ WHERE producto_cj_id = ? AND activo = 1 "
                + "ORDER BY precio_venta ASC";

        try (Connection conn = DatabaseConnection.getConnection()) {

            StringBuilder json = new StringBuilder();

            try (PreparedStatement stmtProducto = conn.prepareStatement(sqlProducto)) {
                stmtProducto.setInt(1, productoCjId);

                try (ResultSet rsProducto = stmtProducto.executeQuery()) {
                    if (!rsProducto.next()) {
                        response.setStatus(404);
                        out.print("{\"ok\":false,\"error\":\"Producto no encontrado\"}");
                        return;
                    }

                    json.append("{\"ok\":true,\"producto\":{");
                    json.append("\"id\":").append(rsProducto.getInt("id")).append(",");
                    json.append("\"pid\":\"").append(JsonUtils.escapar(rsProducto.getString("pid"))).append("\",");
                    json.append("\"nombre\":\"").append(JsonUtils.escapar(rsProducto.getString("nombre"))).append("\",");
                    json.append("\"sku\":\"").append(JsonUtils.escapar(rsProducto.getString("sku"))).append("\",");
                    json.append("\"imagen\":\"").append(JsonUtils.escapar(rsProducto.getString("imagen"))).append("\",");
                    json.append("\"categoria\":\"").append(JsonUtils.escapar(rsProducto.getString("categoria"))).append("\",");
                    double precioVentaDesde = rsProducto.getDouble("precio_venta_desde");
                    json.append("\"precioVentaDesde\":").append(precioVentaDesde).append(",");
                    json.append("\"precioVentaDesdeCrc\":").append(precioVentaDesde * Config.TIPO_CAMBIO_USD_CRC);
                    json.append("},\"variantes\":[");
                }
            }

            boolean primera = true;
            try (PreparedStatement stmtVariantes = conn.prepareStatement(sqlVariantes)) {
                stmtVariantes.setInt(1, productoCjId);

                try (ResultSet rsVariante = stmtVariantes.executeQuery()) {
                    while (rsVariante.next()) {
                        if (!primera) json.append(",");
                        primera = false;

                        json.append("{");
                        json.append("\"id\":").append(rsVariante.getInt("id")).append(",");
                        json.append("\"vid\":\"").append(JsonUtils.escapar(rsVariante.getString("vid"))).append("\",");
                        json.append("\"variantSku\":\"").append(JsonUtils.escapar(rsVariante.getString("variant_sku"))).append("\",");
                        json.append("\"opciones\":\"").append(JsonUtils.escapar(rsVariante.getString("opciones"))).append("\",");
                        json.append("\"imagen\":\"").append(JsonUtils.escapar(rsVariante.getString("imagen"))).append("\",");
                        json.append("\"envioDias\":\"").append(JsonUtils.escapar(rsVariante.getString("envio_dias"))).append("\",");
                        json.append("\"envioTransportadora\":\"").append(JsonUtils.escapar(rsVariante.getString("envio_transportadora"))).append("\",");
                        double precioVenta = rsVariante.getDouble("precio_venta");
                        json.append("\"precioVenta\":").append(precioVenta).append(",");
                        json.append("\"precioVentaCrc\":").append(precioVenta * Config.TIPO_CAMBIO_USD_CRC).append(",");
                        json.append("\"stock\":").append(rsVariante.getInt("stock"));
                        json.append("}");
                    }
                }
            }

            json.append("]}");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json.toString());

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(500);
            out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar("Error en base de datos: " + e.getMessage()) + "\"}");
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar("Error inesperado: " + e.getMessage()) + "\"}");
        }
    }
}