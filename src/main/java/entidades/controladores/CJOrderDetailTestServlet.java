package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import entidades.CJApiService;
import entidades.JsonParser;
import entidades.JsonUtils;

// 🔍 Servlet de INSPECCIÓN: consulta el estado real de una orden CJ
// (sandbox o normal) via getOrderDetail. Solo lectura, no modifica nada.
@WebServlet("/admin/cjOrderDetailTest")
public class CJOrderDetailTestServlet extends HttpServlet {

    private static final String DETAIL_URL_BASE =
            "https://developers.cjdropshipping.com/api2.0/v1/shopping/order/getOrderDetail?orderId=";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String orderId = request.getParameter("orderId");
        if (orderId == null || orderId.isBlank()) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"error\":\"Falta el parametro orderId\"}");
            return;
        }

        try {
            String orderIdCodificado = URLEncoder.encode(orderId, StandardCharsets.UTF_8);
            String url = DETAIL_URL_BASE + orderIdCodificado;

            Map<String, Object> resp = CJApiService.get(url);

            int code = JsonParser.getInt(resp, "code", -1);
            boolean result = JsonParser.getBoolean(resp, "result", false);
            String message = JsonParser.getString(resp, "message");

            StringBuilder json = new StringBuilder();
            json.append("{\"ok\":").append(result).append(",");
            json.append("\"code\":").append(code).append(",");
            json.append("\"message\":\"").append(JsonUtils.escapar(message)).append("\"");

            if (result) {
                Map<String, Object> data = JsonParser.getMap(resp, "data");
                json.append(",");
                json.append("\"orderId\":\"").append(JsonUtils.escapar(JsonParser.getString(data, "orderId"))).append("\",");
                json.append("\"orderNum\":\"").append(JsonUtils.escapar(JsonParser.getString(data, "orderNum"))).append("\",");
                json.append("\"cjOrderId\":\"").append(JsonUtils.escapar(JsonParser.getString(data, "cjOrderId"))).append("\",");
                json.append("\"orderStatus\":\"").append(JsonUtils.escapar(JsonParser.getString(data, "orderStatus"))).append("\",");
                json.append("\"subStatus\":\"").append(JsonUtils.escapar(JsonParser.getString(data, "subStatus"))).append("\",");
                json.append("\"isSandbox\":").append(JsonParser.getInt(data, "isSandbox", -1)).append(",");
                json.append("\"orderAmount\":").append(JsonParser.getDouble(data, "orderAmount", 0)).append(",");
                json.append("\"productAmount\":").append(JsonParser.getDouble(data, "productAmount", 0)).append(",");
                json.append("\"postageAmount\":").append(JsonParser.getDouble(data, "postageAmount", 0)).append(",");
                json.append("\"logisticName\":\"").append(JsonUtils.escapar(JsonParser.getString(data, "logisticName"))).append("\",");
                json.append("\"createDate\":\"").append(JsonUtils.escapar(JsonParser.getString(data, "createDate"))).append("\",");
                json.append("\"paymentDate\":\"").append(JsonUtils.escapar(JsonParser.getString(data, "paymentDate"))).append("\",");
                json.append("\"isComplete\":").append(JsonParser.getInt(data, "isComplete", -1));
            }
            json.append("}");

            out.print(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar("Excepcion: " + e.getMessage()) + "\"}");
        }
    }
}