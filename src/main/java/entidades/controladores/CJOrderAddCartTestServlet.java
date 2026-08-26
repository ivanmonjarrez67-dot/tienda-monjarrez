package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import entidades.CJApiService;
import entidades.JsonParser;
import entidades.JsonUtils;

// 🧪 Servlet de PRUEBA: agrega una orden CJ (creada con createOrderV2)
// al carrito de CJ, via addCart. Paso 1 del flujo CREATED -> IN_CART ->
// (addCartConfirm) -> UNPAID -> simulatePay. Solo hace addCart, nada mas.
@WebServlet("/admin/cjOrderAddCartTest")
public class CJOrderAddCartTestServlet extends HttpServlet {

    private static final String ADD_CART_URL =
            "https://developers.cjdropshipping.com/api2.0/v1/shopping/order/addCart";

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
            String jsonBody = "{\"cjOrderIdList\":[\"" + JsonUtils.escapar(orderId) + "\"]}";

            Map<String, Object> resp = CJApiService.post(ADD_CART_URL, jsonBody);

            int code = JsonParser.getInt(resp, "code", -1);
            boolean result = JsonParser.getBoolean(resp, "result", false);
            String message = JsonParser.getString(resp, "message");
            Map<String, Object> data = JsonParser.getMap(resp, "data");

            StringBuilder json = new StringBuilder();
            json.append("{\"ok\":").append(result).append(",");
            json.append("\"code\":").append(code).append(",");
            json.append("\"message\":\"").append(JsonUtils.escapar(message)).append("\",");

            if (data != null) {
                json.append("\"successCount\":").append(JsonParser.getInt(data, "successCount", 0)).append(",");
                json.append("\"unInterceptAddressCount\":").append(JsonParser.getInt(data, "unInterceptAddressCount", 0));
            } else {
                json.append("\"detalle\":\"sin data en la respuesta\"");
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