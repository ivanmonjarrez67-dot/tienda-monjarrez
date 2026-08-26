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

// 🧪 Servlet de PRUEBA: paga con balance (payBalanceV2) usando el
// shipmentOrderId (de addCartConfirm) y el payId (de saveGenerateParentOrder).
// Ultimo paso del flujo: createOrderV2 -> addCart -> addCartConfirm -> saveGenerateParentOrder -> payBalanceV2
@WebServlet("/admin/cjOrderPayBalanceTest")
public class CJOrderPayBalanceTestServlet extends HttpServlet {

    private static final String PAY_BALANCE_URL =
            "https://developers.cjdropshipping.com/api2.0/v1/shopping/pay/payBalanceV2";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String shipmentOrderId = request.getParameter("shipmentOrderId");
        String payId = request.getParameter("payId");

        if (shipmentOrderId == null || shipmentOrderId.isBlank()) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"error\":\"Falta el parametro shipmentOrderId (el que devolvio addCartConfirm)\"}");
            return;
        }
        if (payId == null || payId.isBlank()) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"error\":\"Falta el parametro payId (el que devolvio saveGenerateParentOrder)\"}");
            return;
        }

        try {
            String jsonBody = "{"
                    + "\"shipmentOrderId\":\"" + JsonUtils.escapar(shipmentOrderId) + "\","
                    + "\"payId\":\"" + JsonUtils.escapar(payId) + "\""
                    + "}";

            Map<String, Object> resp = CJApiService.post(PAY_BALANCE_URL, jsonBody);

            int code = JsonParser.getInt(resp, "code", -1);
            String message = JsonParser.getString(resp, "message");
            // code==200 como criterio de exito real (ver nota sobre "result" inconsistente).
            boolean ok = (code == 200);
            Object dataObj = resp.get("data");

            StringBuilder json = new StringBuilder();
            json.append("{\"ok\":").append(ok).append(",");
            json.append("\"code\":").append(code).append(",");
            json.append("\"message\":\"").append(JsonUtils.escapar(message)).append("\",");
            json.append("\"data\":\"").append(JsonUtils.escapar(String.valueOf(dataObj))).append("\",");
            json.append("\"shipmentOrderIdEnviado\":\"").append(JsonUtils.escapar(shipmentOrderId)).append("\",");
            json.append("\"payIdEnviado\":\"").append(JsonUtils.escapar(payId)).append("\"");
            json.append("}");

            out.print(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar("Excepcion: " + e.getMessage()) + "\"}");
        }
    }
}