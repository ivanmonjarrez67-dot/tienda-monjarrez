package entidades.controladores;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import entidades.CloudinaryService;
import entidades.DatabaseConnection;

/**
 * 🧹 Limpieza ÚNICA de imágenes huérfanas en Cloudinary.
 *
 *   GET  /admin/limpiar-cloudinary?token=XXXX[&prefix=productos/]
 *        → SOLO LISTA lo que se borraría. No borra nada.
 *   POST /admin/limpiar-cloudinary  (token, prefix, confirmar=BORRAR)
 *        → borra las huérfanas (el botón aparece al final del reporte).
 *
 * Cómo decide qué es huérfana: lista todas las imágenes de Cloudinary y
 * revisa TODAS las columnas de texto de TODAS las tablas de la base de
 * datos buscando URLs de Cloudinary. Lo que no aparezca en ninguna tabla
 * es huérfano. Además NUNCA toca imágenes subidas en las últimas 24 horas
 * (un vendedor puede estar a mitad de subir un producto).
 *
 * ⚠️ Imágenes que usa el SITIO pero no la base de datos (logo, mascota Monji,
 * banners escritos a mano en index.html/script.js) saldrían como huérfanas.
 * Por eso: usa "prefix" con la carpeta donde se suben los productos, y
 * revisa el reporte antes de confirmar.
 *
 * Variables de entorno en Render: CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY,
 * CLOUDINARY_API_SECRET y LIMPIEZA_TOKEN (una clave larga que tú inventes).
 * Cuando termines, borra este servlet o la variable LIMPIEZA_TOKEN.
 */
@WebServlet("/admin/limpiar-cloudinary")
public class LimpiarCloudinaryServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Pattern RECURSO = Pattern.compile(
            "\"public_id\":\"((?:[^\"\\\\]|\\\\.)*)\".*?\"created_at\":\"([^\"]+)\"", Pattern.DOTALL);
    private static final Pattern CURSOR = Pattern.compile("\"next_cursor\":\"([^\"]+)\"");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!autorizado(req, resp)) return;
        procesar(req, resp, false);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!autorizado(req, resp)) return;
        boolean borrar = "BORRAR".equals(req.getParameter("confirmar"));
        procesar(req, resp, borrar);
    }

    private boolean autorizado(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String esperado = System.getenv("LIMPIEZA_TOKEN");
        String recibido = req.getParameter("token");
        if (esperado == null || esperado.length() < 12 || recibido == null || !esperado.equals(recibido)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "No autorizado.");
            return false;
        }
        return true;
    }

    private void procesar(HttpServletRequest req, HttpServletResponse resp, boolean borrar) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        String prefix = req.getParameter("prefix") == null ? "" : req.getParameter("prefix").trim();
        String token = req.getParameter("token");

        String cloud = System.getenv("CLOUDINARY_CLOUD_NAME");
        String key = System.getenv("CLOUDINARY_API_KEY");
        String secret = System.getenv("CLOUDINARY_API_SECRET");
        if (cloud == null || key == null || secret == null) {
            out.println("Faltan variables de entorno de Cloudinary.");
            return;
        }
        String auth = "Basic " + Base64.getEncoder().encodeToString((key + ":" + secret).getBytes(StandardCharsets.UTF_8));

        try {
            // 1) Todo lo que la base de datos usa
            Set<String> usados = idsUsadosEnBaseDeDatos();

            // 2) Todo lo que hay en Cloudinary (public_id -> fecha de subida)
            Map<String, Instant> enCloudinary = listarCloudinary(cloud, auth, prefix);

            // 3) Huérfanas = en Cloudinary, no usadas, con más de 24 h
            Instant limite = Instant.now().minus(Duration.ofHours(24));
            List<String> huerfanas = new ArrayList<>();
            int recientes = 0;
            for (Map.Entry<String, Instant> e : enCloudinary.entrySet()) {
                if (usados.contains(e.getKey())) continue;
                if (e.getValue().isAfter(limite)) { recientes++; continue; }
                huerfanas.add(e.getKey());
            }

            out.println("<html><body style='font-family:sans-serif'>");
            out.println("<h2>Limpieza de Cloudinary " + (borrar ? "(BORRANDO)" : "(solo reporte)") + "</h2>");
            out.println("<p>Prefijo: <b>" + esc(prefix.isEmpty() ? "(todo)" : prefix) + "</b><br>"
                    + "En Cloudinary: " + enCloudinary.size() + " · En uso por la base de datos: " + usados.size()
                    + " · Subidas hace menos de 24 h (se respetan): " + recientes
                    + " · <b>Huérfanas: " + huerfanas.size() + "</b></p>");

            if (borrar) {
                int borradas = 0;
                for (int i = 0; i < huerfanas.size(); i += 100) {
                    List<String> lote = huerfanas.subList(i, Math.min(i + 100, huerfanas.size()));
                    StringBuilder body = new StringBuilder("invalidate=true");
                    for (String id : lote) body.append("&public_ids[]=").append(URLEncoder.encode(id, StandardCharsets.UTF_8));
                    HttpRequest r = HttpRequest.newBuilder()
                            .uri(URI.create("https://api.cloudinary.com/v1_1/" + cloud + "/resources/image/upload"))
                            .timeout(Duration.ofSeconds(60)).header("Authorization", auth)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .method("DELETE", HttpRequest.BodyPublishers.ofString(body.toString())).build();
                    HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() == 200) borradas += lote.size();
                    out.println("<p>Lote " + (i / 100 + 1) + ": HTTP " + res.statusCode() + "</p>");
                }
                out.println("<p><b>Listo. Enviadas a borrar: " + borradas + "</b></p>");
            } else {
                out.println("<ol style='font-size:13px'>");
                for (String id : huerfanas) out.println("<li>" + esc(id) + "</li>");
                out.println("</ol>");
                if (!huerfanas.isEmpty()) {
                    out.println("<form method='POST' onsubmit=\"return confirm('Esto borra " + huerfanas.size()
                            + " imágenes de forma permanente. ¿Continuar?')\">"
                            + "<input type='hidden' name='token' value='" + esc(token) + "'>"
                            + "<input type='hidden' name='prefix' value='" + esc(prefix) + "'>"
                            + "<input type='hidden' name='confirmar' value='BORRAR'>"
                            + "<button style='padding:10px 16px;background:#b33a3a;color:#fff;border:0;border-radius:6px'>"
                            + "Borrar estas " + huerfanas.size() + " imágenes</button></form>");
                }
            }
            out.println("</body></html>");
        } catch (Exception e) {
            e.printStackTrace();
            out.println("Error: " + esc(String.valueOf(e.getMessage())));
        }
    }

    /** Recorre TODAS las columnas de texto de TODAS las tablas y junta los public_id de URLs de Cloudinary. */
    private Set<String> idsUsadosEnBaseDeDatos() throws Exception {
        Set<String> ids = new HashSet<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            List<String[]> columnas = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                         + "WHERE DATA_TYPE IN ('varchar','nvarchar','text','ntext') "
                         + "AND TABLE_NAME IN (SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='BASE TABLE')")) {
                while (rs.next()) columnas.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
            }
            for (String[] c : columnas) {
                String sql = "SELECT CAST([" + c[2].replace("]", "]]") + "] AS NVARCHAR(MAX)) FROM ["
                        + c[0].replace("]", "]]") + "].[" + c[1].replace("]", "]]") + "] "
                        + "WHERE CAST([" + c[2].replace("]", "]]") + "] AS NVARCHAR(MAX)) LIKE '%res.cloudinary.com%'";
                try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        // Un campo puede traer varias URLs (JSON o separadas por coma)
                        Matcher m = Pattern.compile("https?://res\\.cloudinary\\.com/[^\\s\"',;<>)\\]]+").matcher(rs.getString(1));
                        while (m.find()) {
                            String id = CloudinaryService.publicIdDesdeUrl(m.group());
                            if (id != null) ids.add(id);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[Limpieza] Columna omitida " + c[1] + "." + c[2] + ": " + e.getMessage());
                }
            }
        }
        return ids;
    }

    private Map<String, Instant> listarCloudinary(String cloud, String auth, String prefix) throws Exception {
        Map<String, Instant> todo = new LinkedHashMap<>();
        String cursor = null;
        do {
            String url = "https://api.cloudinary.com/v1_1/" + cloud + "/resources/image/upload?max_results=500"
                    + (prefix.isEmpty() ? "" : "&prefix=" + URLEncoder.encode(prefix, StandardCharsets.UTF_8))
                    + (cursor == null ? "" : "&next_cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
            HttpRequest r = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(60))
                    .header("Authorization", auth).GET().build();
            HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new IOException("Cloudinary respondió " + res.statusCode() + ": " + res.body());
            Matcher m = RECURSO.matcher(res.body());
            while (m.find()) {
                String id = m.group(1).replace("\\/", "/");
                todo.put(id, Instant.parse(m.group(2)));
            }
            Matcher c = CURSOR.matcher(res.body());
            cursor = c.find() ? c.group(1) : null;
        } while (cursor != null);
        return todo;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;");
    }
}