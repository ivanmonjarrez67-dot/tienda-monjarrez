package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import entidades.JsonUtils;
import config.Config;

// 🔐 Servlet de PRUEBA para verificar que el servidor puede
// autenticarse contra la API de CJdropshipping usando CJ_API_KEY.
// Solo confirma éxito/fracaso — el accessToken NUNCA se envía
// al navegador, se queda en el backend.
@WebServlet("/admin/cjAuth")
public class CJAuthServlet extends HttpServlet {

    private static final String CJ_AUTH_URL =
            "https://developers.cjdropshipping.com/api2.0/v1/authentication/getAccessToken";

    // TODO: cuando esto funcione, mover accessToken/refreshToken a un
    // almacenamiento real (tabla en SQL o similar) en vez de estático en memoria.
    private static String accessTokenGuardado = null;

    // --- Extracción manual de campos JSON (sin librería externa) ---
    // Sirve porque la respuesta de CJ tiene una forma conocida y simple.
    // No es un parser JSON genérico, ojo si CJ cambia el formato.

    private static String extraerString(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static Integer extraerInt(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private static Boolean extraerBoolean(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*(true|false)").matcher(json);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : null;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String apiKey = Config.CJ_API_KEY;

        if (apiKey == null || apiKey.isBlank()) {
            response.setStatus(500);
            out.println("{\"ok\":false,\"error\":\"CJ_API_KEY no está configurada\"}");
            return;
        }

        try {
            String requestBody = "{\"apiKey\":\"" + JsonUtils.escapar(apiKey) + "\"}";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest cjRequest = HttpRequest.newBuilder()
                    .uri(URI.create(CJ_AUTH_URL))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> cjResponse = client.send(cjRequest, HttpResponse.BodyHandlers.ofString());
            String cuerpo = cjResponse.body();

            Integer code = extraerInt(cuerpo, "code");
            Boolean result = extraerBoolean(cuerpo, "result");

            if (code != null && code == 200 && Boolean.TRUE.equals(result)) {
                // accessToken está dentro de "data": { ... } — como el bloque
                // es plano (sin llaves anidadas dentro de accessToken/refreshToken)
                // el mismo regex de extraerString lo encuentra igual dentro de todo el cuerpo.
                accessTokenGuardado = extraerString(cuerpo, "accessToken");

                String preview = (accessTokenGuardado != null && accessTokenGuardado.length() > 8)
                        ? accessTokenGuardado.substring(0, 8) + "..."
                        : "N/A";

                out.print("{\"ok\":true,");
                out.print("\"message\":\"Autenticacion con CJ exitosa\",");
                out.print("\"tokenPreview\":\"" + JsonUtils.escapar(preview) + "\"}");
            } else {
                response.setStatus(502);
                String mensaje = extraerString(cuerpo, "message");
                out.print("{\"ok\":false,");
                out.print("\"cjCode\":" + (code == null ? "null" : code) + ",");
                out.print("\"error\":\"" + JsonUtils.escapar(mensaje != null ? mensaje : "Respuesta inesperada de CJ") + "\"}");
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            out.print("{\"ok\":false,\"error\":\"Excepcion al conectar con CJ: "
                    + JsonUtils.escapar(e.getMessage()) + "\"}");
        }
    }
}