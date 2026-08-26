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

// 🧪 Servlet de PRUEBA: intenta generar la orden padre / obtener el
// Payment ID para una orden UNPAID via saveGenerateParentOrder.
// OJO: la doc de CJ pide "shipmentOrderId", no "orderId" — en nuestra
// orden ese campo vino vacio al crearla, asi que esto es exploratorio:
// puede que este paso solo aplique despues de addCart/addCartConfirm.
@WebServlet("/admin/cjOrderGeneratePaymentTest")
public class CJOrderGeneratePaymentTestServlet extends HttpServlet {

    private static final String GENERATE_URL =
            "https://developers.cjdropshipping.com/api2.0/v1/shopping/order/saveGenerateParentOrder";

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
            // Mandamos el orderId en el campo que pide la doc (shipmentOrderId),
            // aunque en nuestro caso ese campo vino vacio al crear la orden.
            String jsonBody = "{\"shipmentOrderId\":\"" + JsonUtils.escapar(orderId) + "\"}";

            Map<String, Object> resp = CJApiService.post(GENERATE_URL, jsonBody);

            int code = JsonParser.getInt(resp, "code", -1);
            boolean result = JsonParser.getBoolean(resp, "result", false);
            String message = JsonParser.getString(resp, "message");

            StringBuilder json = new StringBuilder();
            json.append("{\"ok\":").append(result).append(",");
            json.append("\"code\":").append(code).append(",");
            json.append("\"message\":\"").append(JsonUtils.escapar(message)).append("\",");

            Map<String, Object> data = JsonParser.getMap(resp, "data");

            if (result && data != null) {
                json.append("\"payId\":\"").append(JsonUtils.escapar(JsonParser.getString(data, "payId"))).append("\",");
                json.append("\"orderMoney\":").append(JsonParser.getDouble(data, "orderMoney", 0)).append(",");
                json.append("\"payExpireTime\":\"").append(JsonUtils.escapar(JsonParser.getString(data, "payExpireTime"))).append("\",");
                json.append("\"submitSuccess\":").append(JsonParser.getBoolean(data, "submitSuccess", false));
            } else {
                // Mostramos el data crudo igual, este endpoint puede fallar
                // por el tema del shipmentOrderId vacio -- queremos ver el detalle.
                String rawData = String.valueOf(resp.get("data"));
                json.append("\"detalle\":\"").append(JsonUtils.escapar(rawData)).append("\"");
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