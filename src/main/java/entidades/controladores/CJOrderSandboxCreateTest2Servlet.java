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

// 🧪 Servlet de PRUEBA: repite createOrderV2 en modo SANDBOX con
// payType=2 ("pago con balance, avanza automatico por carrito ->
// confirmacion -> descuento"). Si CJ rechaza la orden, ahora mostramos
// el "data" crudo del error para saber exactamente que esta fallando.
@WebServlet("/admin/cjOrderSandboxCreateTest2")
public class CJOrderSandboxCreateTest2Servlet extends HttpServlet {

    private static final String ORDER_URL =
            "https://developers.cjdropshipping.com/api2.0/v1/shopping/order/createOrderV2";

    private static final String VID_PRUEBA = "2608251230391600200";
    private static final String LOGISTICA_PRUEBA = "CJPacket Eub";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        try {
            String orderNumber = "TEST-SANDBOX-PT2-" + System.currentTimeMillis();

            String jsonBody = "{"
                    + "\"orderNumber\":\"" + JsonUtils.escapar(orderNumber) + "\","
                    + "\"shippingCountryCode\":\"CR\","
                    + "\"shippingCountry\":\"Costa Rica\","
                    + "\"shippingProvince\":\"Alajuela\","
                    + "\"shippingCity\":\"Alajuela\","
                    + "\"shippingZip\":\"20101\","
                    + "\"shippingPhone\":\"88888888\","
                    + "\"shippingCustomerName\":\"Test Tienda Monjarrez\","
                    + "\"shippingAddress\":\"Direccion de prueba 123\","
                    + "\"email\":\"test@tiendamonjarrez.com\","
                    + "\"logisticName\":\"" + JsonUtils.escapar(LOGISTICA_PRUEBA) + "\","
                    + "\"fromCountryCode\":\"CN\","
                    + "\"platform\":\"Api\","
                    + "\"orderFlow\":1,"
                    + "\"isSandbox\":1,"
                    + "\"payType\":2,"
                    + "\"products\":[{"
                        + "\"vid\":\"" + JsonUtils.escapar(VID_PRUEBA) + "\","
                        + "\"quantity\":1,"
                        + "\"storeLineItemId\":\"test-lineitem-pt2-1\""
                    + "}]"
                    + "}";

            Map<String, Object> resp = CJApiService.post(ORDER_URL, jsonBody);

            int code = JsonParser.getInt(resp, "code", -1);
            boolean result = JsonParser.getBoolean(resp, "result", false);
            String message = JsonParser.getString(resp, "message");

            StringBuilder json = new StringBuilder();
            json.append("{\"ok\":").append(result).append(",");
            json.append("\"code\":").append(code).append(",");
            json.append("\"message\":\"").append(JsonUtils.escapar(message)).append("\",");

            if (result) {
                Map<String, Object> data = JsonParser.getMap(resp, "data");
                json.append("\"orderId\":\"").append(JsonUtils.escapar(JsonParser.getString(data, "orderId"))).append("\",");
                json.append("\"orderNumber\":\"").append(JsonUtils.escapar(JsonParser.getString(data, "orderNumber"))).append("\",");
                json.append("\"orderStatus\":\"").append(JsonUtils.escapar(JsonParser.getString(data, "orderStatus"))).append("\",");
                json.append("\"orderAmount\":").append(JsonParser.getDouble(data, "orderAmount", 0)).append(",");
                json.append("\"productAmount\":").append(JsonParser.getDouble(data, "productAmount", 0)).append(",");
                json.append("\"postageAmount\":").append(JsonParser.getDouble(data, "postageAmount", 0)).append(",");
                json.append("\"actualPayment\":").append(JsonParser.getDouble(data, "actualPayment", 0));
            } else {
                // 🆕 En vez de un mensaje generico, mostramos el "data" crudo
                // que vino en la respuesta de CJ (puede traer detalles del
                // error, como interceptOrderReasons u otros campos).
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