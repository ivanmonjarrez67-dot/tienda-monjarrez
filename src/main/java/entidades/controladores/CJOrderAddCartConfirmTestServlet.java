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

// 🧪 Servlet de PRUEBA: confirma el carrito (addCartConfirm) de una orden
// que fue previamente agregada al carrito con /admin/cjOrderAddCartTest.
// Paso 2 del flujo oficial: createOrderV2 -> addCart -> addCartConfirm -> saveGenerateParentOrder -> payBalanceV2
@WebServlet("/admin/cjOrderAddCartConfirmTest")
public class CJOrderAddCartConfirmTestServlet extends HttpServlet {

    private static final String ADD_CART_CONFIRM_URL =
            "https://developers.cjdropshipping.com/api2.0/v1/shopping/order/addCartConfirm";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String orderId = request.getParameter("orderId");
        if (orderId == null || orderId.isBlank()) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"error\":\"Falta el parametro orderId (el que devolvio /admin/cjOrderTest o similar, SIN pasar por confirmOrder)\"}");
            return;
        }

        try {
            // OJO: aqui el body es un ARRAY, no un string suelto como en confirmOrder/simulatePay
            String jsonBody = "{\"cjOrderIdList\":[\"" + JsonUtils.escapar(orderId) + "\"]}";

            Map<String, Object> resp = CJApiService.post(ADD_CART_CONFIRM_URL, jsonBody);

            int code = JsonParser.getInt(resp, "code", -1);
            boolean result = JsonParser.getBoolean(resp, "result", false);
            String message = JsonParser.getString(resp, "message");
            // No confirmamos aun la forma exacta de "data" para este endpoint,
            // asi que lo mostramos crudo para inspeccionarlo en la primera prueba.
            Object dataObj = resp.get("data");

            StringBuilder json = new StringBuilder();
            json.append("{\"ok\":").append(result).append(",");
            json.append("\"code\":").append(code).append(",");
            json.append("\"message\":\"").append(JsonUtils.escapar(message)).append("\",");
            json.append("\"data\":").append(dataObj == null ? "null" : "\"" + JsonUtils.escapar(String.valueOf(dataObj)) + "\"").append(",");
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