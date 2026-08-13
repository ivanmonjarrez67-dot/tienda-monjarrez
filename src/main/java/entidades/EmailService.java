package entidades;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import config.Config;

/**
 * Servicio centralizado para enviar correos vía la API de Brevo.
 * Usa java.net.http.HttpClient (incluido desde Java 11), así que no
 * requiere agregar ninguna dependencia nueva al proyecto.
 *
 * Todos los envíos son ASÍNCRONOS (corren en un hilo aparte) y NUNCA
 * lanzan excepciones hacia quien los llama — si Brevo falla o no hay
 * internet, solo se imprime un mensaje en consola. Así, aunque el correo
 * no llegue, el registro/login/etc. del usuario nunca se rompe por esto.
 *
 * 🆕 Ahora que tiendamonjarrez.com está autenticado en Brevo (DKIM/DMARC
 * configurados) y los remitentes están verificados, cada tipo de correo
 * sale desde la dirección que le corresponde en vez de un único remitente
 * genérico. Esto mejora la reputación del dominio y es más profesional:
 * un correo de seguridad no debería salir del mismo buzón que un aviso
 * de "nuevo producto", por ejemplo.
 *
 * 🆕 Se quitó el logo embebido (base64) de la plantilla de correo. Outlook
 * lo mostraba bien, pero Gmail lo bloquea/rompe por defecto y se veía
 * como una imagen partida — mejor una plantilla 100% texto, que se ve
 * igual de bien (y consistente) en cualquier cliente de correo.
 */
public class EmailService {

    // 🔧 API key de Brevo (cuenta: monjarrez-prod) — sin cambios
    private static final String BREVO_API_KEY = Config.BREVO_API_KEY;
    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    // 🔧 Correo de soporte que se muestra dentro de los correos (pie de página)
    private static final String SOPORTE_EMAIL = Config.SOPORTE_EMAIL;

    // 🔧 URL de la tienda para el botón de los correos
    private static final String URL_TIENDA = Config.URL_TIENDA;

    // ---------------------------------------------------------
    // 🆕 Remitentes por tipo de correo (dominio ya verificado en Brevo)
    // ---------------------------------------------------------
    private static final String EMAIL_NO_REPLY       = "no-reply@tiendamonjarrez.com";
    private static final String EMAIL_SOPORTE         = "soporte@tiendamonjarrez.com";
    private static final String EMAIL_SEGURIDAD       = "seguridad@tiendamonjarrez.com";
    private static final String EMAIL_NOTIFICACIONES  = "notificaciones@tiendamonjarrez.com";

    private static final String NOMBRE_GENERICO       = "Tienda Monjarrez";
    private static final String NOMBRE_SEGURIDAD      = "Tienda Monjarrez - Seguridad";
    private static final String NOMBRE_NOTIFICACIONES = "Tienda Monjarrez - Notificaciones";
    private static final String NOMBRE_SOPORTE        = "Tienda Monjarrez - Soporte";

    private static final HttpClient client = HttpClient.newHttpClient();

    // ---------------------------------------------------------
    // Envío genérico (asíncrono, a prueba de fallos)
    // 🆕 Ahora recibe también el remitente (email + nombre), en vez de
    // usar siempre uno fijo.
    // ---------------------------------------------------------
    public static void enviarAsync(String remitenteEmail, String remitenteNombre,
                                    String destinatarioEmail, String destinatarioNombre,
                                    String asunto, String htmlContenido) {
        Thread hilo = new Thread(() ->
            enviar(remitenteEmail, remitenteNombre, destinatarioEmail, destinatarioNombre, asunto, htmlContenido)
        );
        hilo.setDaemon(true); // no bloquea el apagado del servidor
        hilo.start();
    }

    private static void enviar(String remitenteEmail, String remitenteNombre,
                                String destinatarioEmail, String destinatarioNombre,
                                String asunto, String htmlContenido) {
        try {
            String json = String.format("""
                {
                  "sender": {"name": "%s", "email": "%s"},
                  "to": [{"email": "%s", "name": "%s"}],
                  "subject": "%s",
                  "htmlContent": "%s"
                }
                """,
                escapeJson(remitenteNombre), escapeJson(remitenteEmail),
                escapeJson(destinatarioEmail), escapeJson(destinatarioNombre),
                escapeJson(asunto), escapeJson(htmlContenido)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_URL))
                    .header("accept", "application/json")
                    .header("api-key", BREVO_API_KEY)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("[EmailService] ✅ Correo enviado a " + destinatarioEmail + " (desde " + remitenteEmail + ")");
            } else {
                System.out.println("[EmailService] ⚠️ Brevo respondió " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            System.out.println("[EmailService] ❌ Error enviando correo: " + e.getMessage());
        }
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "");
    }

    // ---------------------------------------------------------
    // Plantilla visual — SIN logo (solo texto), tarjeta blanca + botón +
    // pie de página. Se ve igual en Gmail, Outlook, Apple Mail, etc.
    // ---------------------------------------------------------

    /**
     * @param titulo      título corto (ej. "¡Bienvenido, Ana!")
     * @param cuerpoHtml  párrafos HTML del cuerpo del mensaje (ej. "<p>...</p><p>...</p>")
     * @param textoBoton  texto del botón, o null/vacío para no mostrar botón
     * @param urlBoton    a dónde apunta el botón
     */
    private static String plantillaBase(String titulo, String cuerpoHtml, String textoBoton, String urlBoton) {
        String botonHtml = "";
        if (textoBoton != null && !textoBoton.isEmpty()) {
            botonHtml = String.format(
                "<a href=\"%s\" style=\"display:inline-block;background:#1f6fd8;color:#ffffff;" +
                "text-decoration:none;padding:10px 26px;border-radius:8px;font-size:14px;" +
                "font-weight:bold;margin-top:16px;\">%s</a>",
                urlBoton, textoBoton
            );
        }

        return String.format("""
            <div style="background-color:#f2f2f2;padding:32px 16px;font-family:Arial,Helvetica,sans-serif;">
              <div style="max-width:420px;margin:0 auto;background:#ffffff;border-radius:12px;overflow:hidden;">
                <div style="background:#111111;padding:22px;text-align:center;">
                  <p style="color:#ffffff;margin:0;font-size:18px;font-weight:bold;letter-spacing:0.3px;">Tienda Monjarrez</p>
                </div>
                <div style="padding:28px 24px;text-align:center;color:#333333;">
                  <h2 style="font-size:18px;margin:0 0 10px 0;color:#1a1a1a;">%s</h2>
                  <div style="font-size:14px;color:#555555;line-height:1.6;text-align:left;">%s</div>
                  %s
                </div>
                <div style="border-top:1px solid #eeeeee;padding:14px;text-align:center;">
                  <p style="font-size:12px;color:#999999;margin:0;">Gracias por confiar en Tienda Monjarrez</p>
                  <p style="font-size:12px;color:#999999;margin:4px 0 0 0;">¿Dudas? Escríbenos a %s</p>
                </div>
              </div>
            </div>
            """, titulo, cuerpoHtml, botonHtml, SOPORTE_EMAIL);
    }

    // ---------------------------------------------------------
    // Plantillas de conveniencia — un método por cada tipo de correo.
    // 🆕 Cada una ahora usa el remitente que le corresponde.
    // ---------------------------------------------------------

    public static void enviarBienvenidaComprador(String email, String nombre) {
        String cuerpo =
              "<p>¡Nos alegra mucho que formes parte de nuestra comunidad!</p>"
            + "<p>Tu cuenta ya está lista. Desde este momento puedes explorar una gran variedad de "
            + "productos, descubrir nuevas ofertas y realizar tus compras de forma segura.</p>"
            + "<p>Esperamos que disfrutes tu experiencia en Tienda Monjarrez. 🛒</p>";

        String html = plantillaBase("🎉 ¡Bienvenido, " + nombre + "!", cuerpo, "Ir a la tienda", URL_TIENDA);
        enviarAsync(EMAIL_NO_REPLY, NOMBRE_GENERICO, email, nombre,
                "🎉 ¡Bienvenido a Tienda Monjarrez!", html);
    }

    public static void enviarBienvenidaVendedor(String email, String nombre) {
        String cuerpo =
              "<p>Gracias por registrarte como vendedor.</p>"
            + "<p>Estás muy cerca de comenzar a ofrecer tus productos a cientos de compradores.</p>"
            + "<p>Solo debes completar tu solicitud y el proceso de suscripción para habilitar tu "
            + "tienda y empezar a publicar.</p>"
            + "<p>¡Te deseamos mucho éxito en esta nueva etapa! 💼✨</p>";

        String html = plantillaBase("🚀 ¡Bienvenido, " + nombre + "!", cuerpo, "Completar registro", URL_TIENDA);
        enviarAsync(EMAIL_NO_REPLY, NOMBRE_GENERICO, email, nombre,
                "🛍️ ¡Bienvenido como vendedor a Tienda Monjarrez!", html);
    }

    public static void enviarAlertaLoginSospechoso(String email, String nombre, String cedula) {
        String cuerpo =
              "<p>Detectamos varios intentos fallidos de inicio de sesión en tu cuenta de "
            + "<strong>Mi Tienda</strong>, asociada a la cédula <strong>" + cedula + "</strong>.</p>"
            + "<p>Si realizaste estos intentos, puedes ignorar este mensaje.</p>"
            + "<p>Si no reconoces esta actividad, te recomendamos cambiar tu contraseña lo antes "
            + "posible y contactar a nuestro equipo de soporte.</p>"
            + "<p>Tu seguridad es muy importante para nosotros. 🔒</p>";

        String html = plantillaBase("⚠️ Actividad inusual detectada", cuerpo, null, null);
        enviarAsync(EMAIL_SEGURIDAD, NOMBRE_SEGURIDAD, email, nombre,
                "⚠️ Actividad inusual detectada en tu cuenta", html);
    }

    // 🆕 Overload sin cédula — para logins que no usan cédula (ej. comprador,
    // que entra con correo). Usado por LoginCompradorServlet.
    public static void enviarAlertaLoginSospechoso(String email, String nombre) {
        String cuerpo =
              "<p>Detectamos varios intentos fallidos de inicio de sesión en tu cuenta de "
            + "Tienda Monjarrez.</p>"
            + "<p>Si realizaste estos intentos, puedes ignorar este mensaje.</p>"
            + "<p>Si no reconoces esta actividad, te recomendamos cambiar tu contraseña lo antes "
            + "posible y contactar a nuestro equipo de soporte.</p>"
            + "<p>Tu seguridad es muy importante para nosotros. 🔒</p>";

        String html = plantillaBase("⚠️ Actividad inusual detectada", cuerpo, null, null);
        enviarAsync(EMAIL_SEGURIDAD, NOMBRE_SEGURIDAD, email, nombre,
                "⚠️ Actividad inusual detectada en tu cuenta", html);
    }

    public static void enviarCodigoRecuperacion(String email, String nombre, String codigo) {
        String cuerpo =
              "<p>Recibimos una solicitud para restablecer tu contraseña en Tienda Monjarrez.</p>"
            + "<p>Tu código de verificación es:</p>"
            + "<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px;color:#1a1a1a;text-align:center;\">"
            + codigo + "</p>"
            + "<p>Este código vence en 10 minutos. Si no solicitaste este cambio, puedes ignorar este mensaje.</p>";

        String html = plantillaBase("🔑 Recupera tu contraseña", cuerpo, null, null);
        enviarAsync(EMAIL_NO_REPLY, NOMBRE_GENERICO, email, nombre,
                "🔑 Código para restablecer tu contraseña", html);
    }

    public static void enviarAvisoNuevoProducto(String email, String nombre, String nombreProducto, String nombreVendedor) {
        String cuerpo =
              "<p><strong>" + nombreVendedor + "</strong> acaba de publicar un nuevo producto que "
            + "podría interesarte:</p>"
            + "<p style=\"font-size:16px;font-weight:bold;color:#1a1a1a;\">" + nombreProducto + "</p>"
            + "<p>Entra a Tienda Monjarrez y descubre todos sus detalles. ¡No te lo pierdas! 👀</p>";

        String html = plantillaBase("🆕 ¡Nuevo producto disponible!", cuerpo, "Ver producto", URL_TIENDA);
        enviarAsync(EMAIL_NOTIFICACIONES, NOMBRE_NOTIFICACIONES, email, nombre,
                "🆕 Nuevo producto disponible: " + nombreProducto, html);
    }

    public static void enviarSuscripcionEnRevision(String email, String nombre, String tipoSuscripcion) {
        String cuerpo =
              "<p>Hemos recibido correctamente tu solicitud para la suscripción "
            + "<strong>" + tipoSuscripcion + "</strong>.</p>"
            + "<p>Ahora nuestro equipo revisará y validará manualmente el pago realizado.</p>"
            + "<p>En cuanto el proceso finalice, recibirás otro correo con el resultado y, si todo "
            + "está correcto, podrás acceder a <strong>Mi Tienda</strong> para comenzar a publicar "
            + "tus productos. 🚀</p>"
            + "<p>¡Gracias por confiar en Tienda Monjarrez y por querer crecer junto a nosotros!</p>";

        String html = plantillaBase("🕒 ¡Recibimos tu solicitud!", cuerpo, null, null);
        enviarAsync(EMAIL_NOTIFICACIONES, NOMBRE_NOTIFICACIONES, email, nombre,
                "🕒 Tu suscripción está siendo revisada", html);
    }
}