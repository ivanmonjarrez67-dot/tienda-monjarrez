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
import entidades.JsonUtils;

// 🔍 Servlet de INSPECCIÓN: pide el detalle crudo de una variante a CJ
// (sin recortar ni traducir campos) para poder ver exactamente qué
// estructura usar al armar createOrderV2. Solo lectura, no crea nada.
@WebServlet("/admin/cjVariantRaw")
public class CJVariantRawServlet extends HttpServlet {

    private static final String VARIANT_URL_BASE =
            "https://developers.cjdropshipping.com/api2.0/v1/product/variant/queryByVid?vid=";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String vid = request.getParameter("vid");
        if (vid == null || vid.isBlank()) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"error\":\"Falta el parametro vid\"}");
            return;
        }

        try {
            String vidCodificado = URLEncoder.encode(vid, StandardCharsets.UTF_8);
            String url = VARIANT_URL_BASE + vidCodificado + "&features=enable_inventory";

            // No parseamos ni recortamos nada: devolvemos el JSON crudo de CJ
            // tal cual, envuelto solo para que el content-type sea correcto.
            Map<String, Object> resp = CJApiService.get(url);

            out.print(mapToJsonCrudo(resp));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar("Excepcion: " + e.getMessage()) + "\"}");
        }
    }

    // Reconstruye el Map en JSON de forma genérica y recursiva (soporta
    // Map, List, String, Double, Boolean y null) — así mostramos TODO
    // lo que vino de CJ sin decidir de antemano qué campos importan.
    @SuppressWarnings("unchecked")
    private String mapToJsonCrudo(Object valor) {
        if (valor == null) return "null";
        if (valor instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) valor).entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(JsonUtils.escapar(e.getKey())).append("\":");
                sb.append(mapToJsonCrudo(e.getValue()));
            }
            return sb.append("}").toString();
        }
        if (valor instanceof java.util.List) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (java.util.List<Object>) valor) {
                if (!first) sb.append(",");
                first = false;
                sb.append(mapToJsonCrudo(item));
            }
            return sb.append("]").toString();
        }
        if (valor instanceof String) {
            return "\"" + JsonUtils.escapar((String) valor) + "\"";
        }
        // Double, Boolean, etc. — se imprimen tal cual (formato JSON válido)
        return valor.toString();
    }
}