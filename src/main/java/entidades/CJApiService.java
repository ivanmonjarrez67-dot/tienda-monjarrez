package entidades;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import config.Config;

// 🔧 Centraliza autenticación y llamadas GET/POST a la API de CJdropshipping.
public class CJApiService {

    private static final String CJ_AUTH_URL =
            "https://developers.cjdropshipping.com/api2.0/v1/authentication/getAccessToken";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // TODO: mover a almacenamiento persistente (SQL) para producción —
    // en memoria se pierde si Render reinicia, y no maneja refreshToken todavía.
    private static String accessTokenCacheado = null;

    @SuppressWarnings("unchecked")
    public static synchronized String obtenerAccessToken() throws Exception {
        if (accessTokenCacheado != null) {
            return accessTokenCacheado;
        }

        String apiKey = Config.CJ_API_KEY;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("CJ_API_KEY no está configurada");
        }

        String requestBody = "{\"apiKey\":\"" + JsonUtils.escapar(apiKey) + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CJ_AUTH_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> json = (Map<String, Object>) JsonParser.parse(response.body());

        if (JsonParser.getInt(json, "code", -1) == 200 && JsonParser.getBoolean(json, "result", false)) {
            Map<String, Object> data = JsonParser.getMap(json, "data");
            accessTokenCacheado = JsonParser.getString(data, "accessToken");
            return accessTokenCacheado;
        }

        throw new RuntimeException("CJ no devolvió un accessToken válido: " + JsonParser.getString(json, "message"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> get(String url) throws Exception {
        String token = obtenerAccessToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("CJ-Access-Token", token)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return (Map<String, Object>) JsonParser.parse(response.body());
    }

    // 🆕 Llamada POST genérica a CJ, agregando el header CJ-Access-Token.
    // La usa CJFreightTestServlet (y en el futuro cualquier otro endpoint
    // de CJ que necesite mandar un body, como sourcing/create).
    @SuppressWarnings("unchecked")
    public static Map<String, Object> post(String url, String jsonBody) throws Exception {
        String token = obtenerAccessToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("CJ-Access-Token", token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return (Map<String, Object>) JsonParser.parse(response.body());
    }
}