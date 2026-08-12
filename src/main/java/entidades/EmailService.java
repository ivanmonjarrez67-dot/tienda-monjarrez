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
 */
public class EmailService {

    // 🔧 API key de Brevo (cuenta: monjarrez-prod)
    private static final String BREVO_API_KEY = Config.BREVO_API_KEY;
    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    // 🔧 Remitente verificado en Brevo (correo dedicado de la tienda)
    private static final String REMITENTE_EMAIL = Config.REMITENTE_EMAIL;
    private static final String REMITENTE_NOMBRE = Config.REMITENTE_NOMBRE;

    // 🔧 Correo de soporte que se muestra dentro de los correos (mismo buzón por ahora)
    private static final String SOPORTE_EMAIL = Config.SOPORTE_EMAIL;

    // 🔧 URL de la tienda para el botón de los correos — actualizar cuando el sitio esté publicado
    private static final String URL_TIENDA = Config.URL_TIENDA;

    // 🔧 Pega aquí el mismo string base64 del logo que ya tenías en tu clase.
    // Lo dejé como placeholder corto para no reventar el archivo con miles
    // de caracteres — reemplázalo por el LOGO_BASE64 completo que ya tenías.
    private static final String LOGO_BASE64 =
        "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";

    private static final HttpClient client = HttpClient.newHttpClient();

    // ---------------------------------------------------------
    // Envío genérico (asíncrono, a prueba de fallos)
    // ---------------------------------------------------------
    public static void enviarAsync(String destinatarioEmail, String destinatarioNombre,
                                    String asunto, String htmlContenido) {
        Thread hilo = new Thread(() -> enviar(destinatarioEmail, destinatarioNombre, asunto, htmlContenido));
        hilo.setDaemon(true); // no bloquea el apagado del servidor
        hilo.start();
    }

    private static void enviar(String destinatarioEmail, String destinatarioNombre,
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
                escapeJson(REMITENTE_NOMBRE), escapeJson(REMITENTE_EMAIL),
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
                System.out.println("[EmailService] ✅ Correo enviado a " + destinatarioEmail);
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
    // Plantilla visual — logo + tarjeta blanca + botón + pie de página
    // 🔧 ESTE método antes existía pero NADA lo llamaba — por eso los
    // correos seguían viéndose planos aunque ya lo tenías escrito.
    // Ahora sí lo usan todos los métodos de conveniencia de abajo.
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
                <div style="background:#111111;padding:20px;text-align:center;">
                  <img src="data:image/png;base64,%s" width="48" height="48" alt="Tienda Monjarrez"
                       style="display:block;margin:0 auto 8px auto;border-radius:50%%;" />
                  <p style="color:#ffffff;margin:0;font-size:14px;font-weight:bold;">Tienda Monjarrez</p>
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
            """, LOGO_BASE64, titulo, cuerpoHtml, botonHtml, SOPORTE_EMAIL);
    }

    // ---------------------------------------------------------
    // Plantillas de conveniencia — un método por cada tipo de correo
    // ---------------------------------------------------------

    public static void enviarBienvenidaComprador(String email, String nombre) {
        String cuerpo =
              "<p>¡Nos alegra mucho que formes parte de nuestra comunidad!</p>"
            + "<p>Tu cuenta ya está lista. Desde este momento puedes explorar una gran variedad de "
            + "productos, descubrir nuevas ofertas y realizar tus compras de forma segura.</p>"
            + "<p>Esperamos que disfrutes tu experiencia en Tienda Monjarrez. 🛒</p>";

        String html = plantillaBase("🎉 ¡Bienvenido, " + nombre + "!", cuerpo, "Ir a la tienda", URL_TIENDA);
        enviarAsync(email, nombre, "🎉 ¡Bienvenido a Tienda Monjarrez!", html);
    }

    public static void enviarBienvenidaVendedor(String email, String nombre) {
        String cuerpo =
              "<p>Gracias por registrarte como vendedor.</p>"
            + "<p>Estás muy cerca de comenzar a ofrecer tus productos a cientos de compradores.</p>"
            + "<p>Solo debes completar tu solicitud y el proceso de suscripción para habilitar tu "
            + "tienda y empezar a publicar.</p>"
            + "<p>¡Te deseamos mucho éxito en esta nueva etapa! 💼✨</p>";

        String html = plantillaBase("🚀 ¡Bienvenido, " + nombre + "!", cuerpo, "Completar registro", URL_TIENDA);
        enviarAsync(email, nombre, "🛍️ ¡Bienvenido como vendedor a Tienda Monjarrez!", html);
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
        enviarAsync(email, nombre, "⚠️ Actividad inusual detectada en tu cuenta", html);
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
        enviarAsync(email, nombre, "⚠️ Actividad inusual detectada en tu cuenta", html);
    }


public static void enviarCodigoRecuperacion(String email, String nombre, String codigo) {
    String cuerpo =
          "<p>Recibimos una solicitud para restablecer tu contraseña en Tienda Monjarrez.</p>"
        + "<p>Tu código de verificación es:</p>"
        + "<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px;color:#1a1a1a;text-align:center;\">"
        + codigo + "</p>"
        + "<p>Este código vence en 10 minutos. Si no solicitaste este cambio, puedes ignorar este mensaje.</p>";

    String html = plantillaBase("🔑 Recupera tu contraseña", cuerpo, null, null);
    enviarAsync(email, nombre, "🔑 Código para restablecer tu contraseña", html);
}



    public static void enviarAvisoNuevoProducto(String email, String nombre, String nombreProducto, String nombreVendedor) {
        String cuerpo =
              "<p><strong>" + nombreVendedor + "</strong> acaba de publicar un nuevo producto que "
            + "podría interesarte:</p>"
            + "<p style=\"font-size:16px;font-weight:bold;color:#1a1a1a;\">" + nombreProducto + "</p>"
            + "<p>Entra a Tienda Monjarrez y descubre todos sus detalles. ¡No te lo pierdas! 👀</p>";

        String html = plantillaBase("🆕 ¡Nuevo producto disponible!", cuerpo, "Ver producto", URL_TIENDA);
        enviarAsync(email, nombre, "🆕 Nuevo producto disponible: " + nombreProducto, html);
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
        enviarAsync(email, nombre, "🕒 Tu suscripción está siendo revisada", html);
    }
}