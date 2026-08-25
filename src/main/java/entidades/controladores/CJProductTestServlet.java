package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import entidades.CJApiService;
import entidades.JsonParser;
import entidades.JsonUtils;

// 🧪 Servlet de PRUEBA: trae UN producto real de CJ (lista → 1 pid →
// detalle completo) y muestra solo los datos que interesa validar
// (PID, nombre, precio, SKU, imagen, variantes, stock). NO guarda nada
// en SQL todavía, NO se publica en la tienda.
@WebServlet("/admin/cjProductTest")
public class CJProductTestServlet extends HttpServlet {

    private static final String LIST_URL =
            "https://developers.cjdropshipping.com/api2.0/v1/product/list?pageNum=1&pageSize=1";

    private static final String QUERY_URL_BASE =
            "https://developers.cjdropshipping.com/api2.0/v1/product/query?pid=";

    @SuppressWarnings("unchecked")
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        try {
            // 1) Traemos 1 producto de la lista general solo para conseguir un pid real.
            Map<String, Object> listaResp = CJApiService.get(LIST_URL);

            if (JsonParser.getInt(listaResp, "code", -1) != 200 || !JsonParser.getBoolean(listaResp, "result", false)) {
                response.setStatus(502);
                out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar(
                        "No se pudo listar productos: " + JsonParser.getString(listaResp, "message")) + "\"}");
                return;
            }

            Map<String, Object> listaData = JsonParser.getMap(listaResp, "data");
            List<Object> lista = JsonParser.getList(listaData, "list");
            if (lista == null || lista.isEmpty()) {
                response.setStatus(404);
                out.print("{\"ok\":false,\"error\":\"CJ no devolvió productos en la lista\"}");
                return;
            }

            Map<String, Object> primerProducto = (Map<String, Object>) lista.get(0);
            String pid = JsonParser.getString(primerProducto, "pid");

            // 2) Con ese pid, pedimos el detalle completo (incluye variantes + stock).
            Map<String, Object> detalleResp = CJApiService.get(QUERY_URL_BASE + pid);

            if (JsonParser.getInt(detalleResp, "code", -1) != 200 || !JsonParser.getBoolean(detalleResp, "result", false)) {
                response.setStatus(502);
                out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar(
                        "No se pudo consultar el detalle: " + JsonParser.getString(detalleResp, "message")) + "\"}");
                return;
            }

            Map<String, Object> p = JsonParser.getMap(detalleResp, "data");

            StringBuilder json = new StringBuilder();
            json.append("{\"ok\":true,");
            json.append("\"pid\":\"").append(JsonUtils.escapar(JsonParser.getString(p, "pid"))).append("\",");
            json.append("\"nombre\":\"").append(JsonUtils.escapar(JsonParser.getString(p, "productNameEn"))).append("\",");
            json.append("\"sku\":\"").append(JsonUtils.escapar(JsonParser.getString(p, "productSku"))).append("\",");
            json.append("\"precio\":").append(JsonParser.getDouble(p, "sellPrice", 0)).append(",");
            json.append("\"imagen\":\"").append(JsonUtils.escapar(JsonParser.getString(p, "bigImage"))).append("\",");
            json.append("\"categoria\":\"").append(JsonUtils.escapar(JsonParser.getString(p, "categoryName"))).append("\",");

            json.append("\"variantes\":[");
            List<Object> variantes = JsonParser.getList(p, "variants");
            if (variantes != null) {
                for (int i = 0; i < variantes.size(); i++) {
                    Map<String, Object> v = (Map<String, Object>) variantes.get(i);
                    if (i > 0) json.append(",");
                    json.append("{");
                    json.append("\"vid\":\"").append(JsonUtils.escapar(JsonParser.getString(v, "vid"))).append("\",");
                    json.append("\"variantSku\":\"").append(JsonUtils.escapar(JsonParser.getString(v, "variantSku"))).append("\",");
                    json.append("\"opciones\":\"").append(JsonUtils.escapar(JsonParser.getString(v, "variantKey"))).append("\",");
                    json.append("\"precio\":").append(JsonParser.getDouble(v, "variantSellPrice", 0)).append(",");

                    int stockTotal = 0;
                    List<Object> inventarios = JsonParser.getList(v, "inventories");
                    if (inventarios != null) {
                        for (Object invObj : inventarios) {
                            Map<String, Object> inv = (Map<String, Object>) invObj;
                            stockTotal += JsonParser.getInt(inv, "totalInventory", 0);
                        }
                    }
                    json.append("\"stock\":").append(stockTotal);
                    json.append("}");
                }
            }
            json.append("]}");

            out.print(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar("Excepción: " + e.getMessage()) + "\"}");
        }
    }
}