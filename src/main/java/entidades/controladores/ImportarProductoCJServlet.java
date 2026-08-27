package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import config.Config;
import entidades.CJApiService;
import entidades.DatabaseConnection;
import entidades.JsonParser;
import entidades.JsonUtils;

// 📦 Importa UN producto real de CJ (con todas sus variantes) hacia las
// tablas propias ProductosCJ / VariantesCJ, completamente separadas de
// Productos/Vendedores. Para cada variante:
//   1) cotiza envío a Costa Rica
//   2) descarta opciones cuyo MÍNIMO de días supere CJ_MAX_DIAS_ENVIO
//   3) entre las válidas, elige la más barata (si ninguna es válida,
//      usa como fallback la más barata de TODAS las opciones)
//   4) costo_total = precio_cj + envio_cj
//   5) precio_venta = costo_total * CJ_MARGEN_MULTIPLICADOR
@WebServlet("/admin/importarProductoCJ")
public class ImportarProductoCJServlet extends HttpServlet {

    private static final String QUERY_URL_BASE =
            "https://developers.cjdropshipping.com/api2.0/v1/product/query?pid=";

    private static final String FREIGHT_URL =
            "https://developers.cjdropshipping.com/api2.0/v1/logistic/freightCalculate";

    // Pequeña clase interna para cargar una opción de envío ya parseada
    private static class OpcionEnvio {
        String transportadora;
        double precioUSD;
        String diasTexto;   // ej "12-50"
        int diasMin;        // primer número del rango
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String pid = request.getParameter("pid");
        if (pid == null || pid.isBlank()) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"error\":\"Falta el parametro pid\"}");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {

            // 1) Traer detalle del producto (incluye lista de variantes)
            Map<String, Object> detalleResp = CJApiService.get(QUERY_URL_BASE + pid);

            if (JsonParser.getInt(detalleResp, "code", -1) != 200
                    || !JsonParser.getBoolean(detalleResp, "result", false)) {
                response.setStatus(502);
                out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar(
                        "No se pudo consultar el producto: " + JsonParser.getString(detalleResp, "message")) + "\"}");
                return;
            }

            Map<String, Object> p = JsonParser.getMap(detalleResp, "data");

            String nombre = JsonParser.getString(p, "productNameEn");
            String sku = JsonParser.getString(p, "productSku");
            String imagen = JsonParser.getString(p, "bigImage");
            String categoria = JsonParser.getString(p, "categoryName");

            List<Object> variantesCJ = JsonParser.getList(p, "variants");
            if (variantesCJ == null || variantesCJ.isEmpty()) {
                response.setStatus(422);
                out.print("{\"ok\":false,\"error\":\"El producto no tiene variantes\"}");
                return;
            }

            conn.setAutoCommit(false);

            try {
                // 2) Insertar el producto "cabecera" (precio_venta_desde se
                // actualiza al final, cuando ya sabemos el mínimo real)
                int productoCjId;
                String sqlProducto = "INSERT INTO ProductosCJ "
                        + "(pid, nombre, sku, imagen, categoria, precio_venta_desde) "
                        + "VALUES (?, ?, ?, ?, ?, 0)";

                try (PreparedStatement stmt = conn.prepareStatement(sqlProducto, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, pid);
                    stmt.setString(2, nombre);
                    stmt.setString(3, sku);
                    stmt.setString(4, imagen);
                    stmt.setString(5, categoria);
                    stmt.executeUpdate();

                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            productoCjId = keys.getInt(1);
                        } else {
                            throw new SQLException("No se pudo obtener el ID del producto CJ recién creado.");
                        }
                    }
                }

                double margen = Config.CJ_MARGEN_MULTIPLICADOR;
                int maxDias = Config.CJ_MAX_DIAS_ENVIO;

                double precioVentaMinimo = Double.MAX_VALUE;
                int variantesImportadas = 0;
                List<String> variantesOmitidas = new ArrayList<>();

                String sqlVariante = "INSERT INTO VariantesCJ "
                        + "(producto_cj_id, vid, variant_sku, opciones, imagen, "
                        + "precio_cj, envio_cj, envio_dias, envio_transportadora, "
                        + "costo_total, precio_venta, stock) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmtVariante = conn.prepareStatement(sqlVariante)) {

                    for (Object vObj : variantesCJ) {
                        Map<String, Object> v = (Map<String, Object>) vObj;

                        String vid = JsonParser.getString(v, "vid");
                        String variantSku = JsonParser.getString(v, "variantSku");
                        String opciones = JsonParser.getString(v, "variantKey");
                        String variantImagen = JsonParser.getString(v, "variantImage");
                        double precioCj = JsonParser.getDouble(v, "variantSellPrice", 0);

                        int stockTotal = 0;
                        List<Object> inventarios = JsonParser.getList(v, "inventories");
                        if (inventarios != null) {
                            for (Object invObj : inventarios) {
                                Map<String, Object> inv = (Map<String, Object>) invObj;
                                double factoria = JsonParser.getDouble(inv, "factoryInventory", 0);
                                double bodega = JsonParser.getDouble(inv, "totalInventory", 0);
                                stockTotal += (int) Math.max(factoria, bodega);
                            }
                        }

                        // 3) Cotizar envío para esta variante puntual
                        List<OpcionEnvio> opcionesEnvio = cotizarEnvio(vid, 1);

                        if (opcionesEnvio.isEmpty()) {
                            // CJ no ofrece ninguna opción a Costa Rica -> se omite
                            variantesOmitidas.add(variantSku + " (sin opciones de envío)");
                            continue;
                        }

                        // 4) Filtrar por tope de días (comparando contra el MÍNIMO del rango)
                        List<OpcionEnvio> validas = new ArrayList<>();
                        for (OpcionEnvio o : opcionesEnvio) {
                            if (o.diasMin <= maxDias) {
                                validas.add(o);
                            }
                        }

                        // 5) Elegir la más barata entre las válidas; si ninguna
                        // cumple el tope, usar como fallback la más barata de todas
                        List<OpcionEnvio> candidatas = validas.isEmpty() ? opcionesEnvio : validas;

                        OpcionEnvio elegida = candidatas.get(0);
                        for (OpcionEnvio o : candidatas) {
                            if (o.precioUSD < elegida.precioUSD) {
                                elegida = o;
                            }
                        }

                        double costoTotal = precioCj + elegida.precioUSD;
                        double precioVenta = costoTotal * margen;

                        if (precioVenta < precioVentaMinimo) {
                            precioVentaMinimo = precioVenta;
                        }

                        stmtVariante.setInt(1, productoCjId);
                        stmtVariante.setString(2, vid);
                        stmtVariante.setString(3, variantSku);
                        stmtVariante.setString(4, opciones);
                        stmtVariante.setString(5, variantImagen != null && !variantImagen.isBlank() ? variantImagen : imagen);
                        stmtVariante.setDouble(6, precioCj);
                        stmtVariante.setDouble(7, elegida.precioUSD);
                        stmtVariante.setString(8, elegida.diasTexto);
                        stmtVariante.setString(9, elegida.transportadora);
                        stmtVariante.setDouble(10, costoTotal);
                        stmtVariante.setDouble(11, precioVenta);
                        stmtVariante.setInt(12, stockTotal);
                        stmtVariante.addBatch();

                        variantesImportadas++;
                    }

                    if (variantesImportadas == 0) {
                        throw new SQLException("Ninguna variante pudo importarse (sin opciones de envío para todas).");
                    }

                    stmtVariante.executeBatch();
                }

                // Actualizar el precio "desde" del producto con el mínimo real encontrado
                try (PreparedStatement stmtUpdate = conn.prepareStatement(
                        "UPDATE ProductosCJ SET precio_venta_desde = ? WHERE id = ?")) {
                    stmtUpdate.setDouble(1, precioVentaMinimo);
                    stmtUpdate.setInt(2, productoCjId);
                    stmtUpdate.executeUpdate();
                }

                conn.commit();

                StringBuilder json = new StringBuilder();
                json.append("{\"ok\":true,");
                json.append("\"productoCjId\":").append(productoCjId).append(",");
                json.append("\"variantesImportadas\":").append(variantesImportadas).append(",");
                json.append("\"variantesOmitidas\":").append(variantesOmitidas.size()).append(",");
                json.append("\"precioVentaDesde\":").append(precioVentaMinimo);
                json.append("}");

                response.setStatus(HttpServletResponse.SC_OK);
                out.print(json.toString());

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

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

    // Cotiza envío CN -> CR para una variante puntual y devuelve la lista
    // de opciones ya parseadas (precio + días mínimos del rango)
    @SuppressWarnings("unchecked")
    private List<OpcionEnvio> cotizarEnvio(String vid, int cantidad) throws Exception {
        List<OpcionEnvio> resultado = new ArrayList<>();

        String requestBody = "{"
                + "\"startCountryCode\":\"CN\","
                + "\"endCountryCode\":\"CR\","
                + "\"products\":[{\"quantity\":" + cantidad + ",\"vid\":\"" + JsonUtils.escapar(vid) + "\"}]"
                + "}";

        Map<String, Object> resp = CJApiService.post(FREIGHT_URL, requestBody);

        if (JsonParser.getInt(resp, "code", -1) != 200 || !JsonParser.getBoolean(resp, "result", false)) {
            return resultado; // sin opciones -> lista vacía
        }

        List<Object> opciones = JsonParser.getList(resp, "data");
        if (opciones == null) {
            return resultado;
        }

        for (Object oObj : opciones) {
            Map<String, Object> o = (Map<String, Object>) oObj;

            OpcionEnvio opcion = new OpcionEnvio();
            opcion.transportadora = JsonParser.getString(o, "logisticName");
            opcion.precioUSD = JsonParser.getDouble(o, "logisticPrice", 0);
            opcion.diasTexto = JsonParser.getString(o, "logisticAging");
            opcion.diasMin = extraerDiasMin(opcion.diasTexto);

            resultado.add(opcion);
        }

        return resultado;
    }

    // Extrae el primer número de un rango tipo "12-50" -> 12.
    // Si no hay guion o no se puede parsear, intenta usar el texto
    // completo como número; si tampoco funciona, devuelve un valor alto
    // para que esa opción quede descartada por seguridad (no aceptar
    // "por defecto" un tiempo de entrega desconocido).
    private int extraerDiasMin(String diasTexto) {
        if (diasTexto == null || diasTexto.isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            String primeraParte = diasTexto.split("-")[0].trim();
            return Integer.parseInt(primeraParte.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/plain");
        response.getWriter().write("Este servlet solo acepta POST para importar un producto de CJ (parametro pid).");
    }
}