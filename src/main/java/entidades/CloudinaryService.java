package entidades;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Borra imágenes de Cloudinary desde el SERVIDOR (firmado con la API Secret).
 * El preset "unsigned" de subida no puede borrar, por eso hace falta esto.
 *
 * Variables de entorno en Render:
 *   CLOUDINARY_CLOUD_NAME   (ya existe)
 *   CLOUDINARY_API_KEY      (nueva)
 *   CLOUDINARY_API_SECRET   (nueva — NUNCA al frontend ni al repositorio)
 *
 * Es "mejor esfuerzo": si algo falla se registra en consola y se sigue,
 * porque el producto/cuenta ya se borró de la base de datos.
 */
public class CloudinaryService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    // .../upload/[transformaciones/][v123/]carpeta/nombre.ext
    private static final Pattern UPLOAD = Pattern.compile("^https?://res\\.cloudinary\\.com/[^/]+/image/upload/(.+)$");
    private static final Pattern VERSION = Pattern.compile("^v\\d+$");

    private CloudinaryService() {}

    /** Devuelve el public_id de una URL de Cloudinary, o null si no es una (p. ej. URLs viejas locales). */
    public static String publicIdDesdeUrl(String url) {
        if (url == null) return null;
        Matcher m = UPLOAD.matcher(url.trim());
        if (!m.matches()) return null;
        String resto = m.group(1);
        int q = resto.indexOf('?');
        if (q >= 0) resto = resto.substring(0, q);
        String[] partes = resto.split("/");
        int inicio = 0;
        for (int i = 0; i < partes.length; i++) {
            if (VERSION.matcher(partes[i]).matches()) { inicio = i + 1; break; }
        }
        // Sin versión: asumimos que no hay transformaciones antepuestas
        StringBuilder sb = new StringBuilder();
        for (int i = inicio; i < partes.length; i++) {
            if (sb.length() > 0) sb.append('/');
            sb.append(partes[i]);
        }
        String id = sb.toString();
        int punto = id.lastIndexOf('.');
        if (punto > id.lastIndexOf('/')) id = id.substring(0, punto);
        id = URLDecoder.decode(id, StandardCharsets.UTF_8);
        return id.isEmpty() ? null : id;
    }

    /** Borra todas las URLs dadas (ignora las que no son de Cloudinary y las repetidas). */
    public static void borrarPorUrls(Collection<String> urls) {
        if (urls == null) return;
        Set<String> ids = new LinkedHashSet<>();
        for (String u : urls) {
            String id = publicIdDesdeUrl(u);
            if (id != null) ids.add(id);
        }
        for (String id : ids) borrar(id);
    }

    private static void borrar(String publicId) {
        String cloud = System.getenv("CLOUDINARY_CLOUD_NAME");
        String key = System.getenv("CLOUDINARY_API_KEY");
        String secret = System.getenv("CLOUDINARY_API_SECRET");
        if (cloud == null || key == null || secret == null) {
            System.out.println("[Cloudinary] Faltan variables de entorno; no se borró: " + publicId);
            return;
        }
        try {
            long ts = System.currentTimeMillis() / 1000;
            // Parámetros firmados en orden alfabético: invalidate, public_id, timestamp
            String aFirmar = "invalidate=true&public_id=" + publicId + "&timestamp=" + ts + secret;
            String firma = sha1(aFirmar);
            String body = "public_id=" + enc(publicId) + "&invalidate=true&timestamp=" + ts
                    + "&api_key=" + enc(key) + "&signature=" + firma;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cloudinary.com/v1_1/" + cloud + "/image/destroy"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("[Cloudinary] destroy " + publicId + " -> " + res.statusCode() + " " + res.body());
        } catch (Exception e) {
            System.out.println("[Cloudinary] Error borrando " + publicId + ": " + e.getMessage());
        }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String sha1(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}