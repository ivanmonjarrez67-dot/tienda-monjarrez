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

// 🧪 Servlet de PRUEBA: simula el pago (POST) de una orden Sandbox
// creada con isSandbox=1 en /admin/cjOrderTest.
// Endpoint oficial de Sandbox: /shopping/sandbox/simulatePay
@WebServlet("/admin/cjOrderSandboxPayTest")
public class CJOrderSandboxPayTestServlet extends HttpServlet {

    private static final String SIMULATE_PAY_URL =
            "https://developers.cjdropshipping.com/api2.0/v1/shopping/sandbox/simulatePay";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String orderId = request.getParameter("orderId");
        if (orderId == null || orderId.isBlank()) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"error\":\"Falta el parametro orderId (el que devolvio /admin/cjOrderTest)\"}");
            return;
        }

        try {
            String jsonBody = "{\"orderId\":\"" + JsonUtils.escapar(orderId) + "\"}";

            // OJO: aqui usamos post, no patch
            Map<String, Object> resp = CJApiService.post(SIMULATE_PAY_URL, jsonBody);

            int code = JsonParser.getInt(resp, "code", -1);
            boolean result = JsonParser.getBoolean(resp, "result", false);
            String message = JsonParser.getString(resp, "message");
            // "data" en simulatePay es boolean (true/false), no un String con orderId
            Object dataObj = resp.get("data");

            StringBuilder json = new StringBuilder();
            json.append("{\"ok\":").append(result).append(",");
            json.append("\"code\":").append(code).append(",");
            json.append("\"message\":\"").append(JsonUtils.escapar(message)).append("\",");
            json.append("\"data\":").append(dataObj).append(",");
            json.append("\"orderIdEnviado\":\"").append(JsonUtils.escapar(orderId)).append("\"");
            json.append("}");

            out.print(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar("Excepcion: " + e.getMessage()) + "\"}");
        }
    }
}