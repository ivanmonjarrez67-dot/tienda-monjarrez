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

// 🚚 Servlet de PRUEBA: calcula el costo de envío de una variante de CJ
// hacia Costa Rica (CR). Recibe el vid por parámetro (lo sacás del
// resultado de /admin/cjProductTest). NO guarda nada, solo consulta.
@WebServlet("/admin/cjFreightTest")
public class CJFreightTestServlet extends HttpServlet {

    private static final String FREIGHT_URL =
            "https://developers.cjdropshipping.com/api2.0/v1/logistic/freightCalculate";

    @SuppressWarnings("unchecked")
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String vid = request.getParameter("vid");
        String cantidadParam = request.getParameter("qty");
        int cantidad = 1;
        try {
            if (cantidadParam != null) cantidad = Integer.parseInt(cantidadParam);
        } catch (NumberFormatException ignored) { }

        if (vid == null || vid.isBlank()) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"error\":\"Falta el parametro vid (usa uno de /admin/cjProductTest)\"}");
            return;
        }

        try {
            String requestBody = "{"
                    + "\"startCountryCode\":\"CN\","
                    + "\"endCountryCode\":\"CR\","
                    + "\"products\":[{\"quantity\":" + cantidad + ",\"vid\":\"" + JsonUtils.escapar(vid) + "\"}]"
                    + "}";

            Map<String, Object> resp = CJApiService.post(FREIGHT_URL, requestBody);

            if (JsonParser.getInt(resp, "code", -1) != 200 || !JsonParser.getBoolean(resp, "result", false)) {
                response.setStatus(502);
                out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar(
                        "CJ rechazo la consulta: " + JsonParser.getString(resp, "message")) + "\"}");
                return;
            }

            List<Object> opciones = JsonParser.getList(resp, "data");

            if (opciones == null || opciones.isEmpty()) {
                out.print("{\"ok\":true,\"vid\":\"" + JsonUtils.escapar(vid)
                        + "\",\"opciones\":[],\"mensaje\":\"CJ no ofrece ninguna opcion de envio a Costa Rica para esta variante\"}");
                return;
            }

            StringBuilder json = new StringBuilder();
            json.append("{\"ok\":true,\"vid\":\"").append(JsonUtils.escapar(vid)).append("\",\"opciones\":[");
            for (int i = 0; i < opciones.size(); i++) {
                Map<String, Object> o = (Map<String, Object>) opciones.get(i);
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"transportadora\":\"").append(JsonUtils.escapar(JsonParser.getString(o, "logisticName"))).append("\",");
                json.append("\"precioEnvioUSD\":").append(JsonParser.getDouble(o, "logisticPrice", 0)).append(",");
                json.append("\"diasEstimados\":\"").append(JsonUtils.escapar(JsonParser.getString(o, "logisticAging"))).append("\"");
                json.append("}");
            }
            json.append("]}");

            out.print(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar("Excepcion: " + e.getMessage()) + "\"}");
        }
    }
}