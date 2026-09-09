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
 *
 * 🆕 Se reemplazaron todos los emojis por íconos planos (PNG embebidos en
 * base64, sin dependencias externas), con el mismo estilo del correo de
 * "Novedades" que ya funciona bien en Gmail/Outlook/Apple Mail.
 *
 * 🆕 Se agregó el correo de confirmación de eliminación de cuenta, enviado
 * desde el correo principal (tiendamonjarrez@gmail.com) ya que es el
 * último contacto que tendrá el usuario con la tienda.
 *
 * 🆕 Se agregó el correo de "suscripción aprobada" (enviarSuscripcionAprobada),
 * que avisa al vendedor cuando el admin aprueba su suscripción desde
 * panelAdmin — antes no existía ninguna notificación de esto y el vendedor
 * se enteraba solo si entraba a revisar manualmente. Sale desde
 * soporte@tiendamonjarrez.com (se reactivó ese remitente, que estaba
 * comentado) porque es el equipo que aprueba las solicitudes, y así el
 * vendedor puede responder ese mismo correo si tiene dudas. El botón
 * "Ir a mi tienda" no lleva a la portada genérica: usa el parámetro
 * ?accion=login-vendedor (ver index.html) para abrir directo el login de
 * Vendedor/a.
 *
 * 🆕 Se agregó enviarAlertaNuevaSolicitudVendedor(): alerta interna (no de
 * marca) que le llega al correo personal del admin apenas se registra una
 * nueva solicitud de vendedor, para no tener que revisar panelAdmin
 * manualmente para saber si hay solicitudes pendientes.
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
    // Remitentes por tipo de correo (dominio ya verificado en Brevo)
    // ---------------------------------------------------------
    private static final String EMAIL_NO_REPLY       = "no-reply@tiendamonjarrez.com";
    // 🆕 Reactivado: lo necesita enviarSuscripcionAprobada() para que el
    // vendedor pueda responder directo al equipo que aprobó su solicitud.
    private static final String EMAIL_SOPORTE         = "soporte@tiendamonjarrez.com";
    private static final String EMAIL_SEGURIDAD       = "seguridad@tiendamonjarrez.com";
    private static final String EMAIL_NOTIFICACIONES  = "notificaciones@tiendamonjarrez.com";

    // 🆕 Correo principal (bandeja real de Gmail) — se usa para el correo de
    // despedida al eliminar la cuenta, para que se sienta como un mensaje
    // final "oficial" y no automatizado desde el dominio.
    private static final String EMAIL_PRINCIPAL       = "tiendamonjarrez@gmail.com";

    // 🆕 Correo personal del admin, solo para avisos internos (ej. nueva
    // solicitud de vendedor). Nunca se usa como remitente ni se muestra
    // al cliente/vendedor en ningún correo saliente.
    private static final String EMAIL_ADMIN_PERSONAL  = "ivanmonjarrez67@gmail.com";

    // ---------------------------------------------------------
    // 🆕 Base donde viven los íconos de los correos, como ARCHIVOS reales
    // (no base64). IMPORTANTE: Gmail bloquea/rompe las imágenes embebidas
    // en base64 (data:image/...) cuando llegan por correo real vía API —
    // por eso antes se quitó el logo. Un <img src="https://..."> normal
    // sí lo respeta cualquier cliente de correo.
    //
    // Ajusta esta ruta según dónde sirvas los archivos estáticos en tu
    // proyecto (carpeta "images", "recursos", un CDN, etc.). Debe ser una
    // URL PÚBLICA y accesible desde internet, no localhost.
    // 🆕 Los archivos viven en webapp/imagenes/, así que apuntamos ahí.
    // ---------------------------------------------------------
    private static final String ICONOS_BASE_URL = URL_TIENDA + "/imagenes/";

    private static final String NOMBRE_GENERICO       = "Tienda Monjarrez";
    private static final String NOMBRE_SEGURIDAD      = "Tienda Monjarrez - Seguridad";
    private static final String NOMBRE_NOTIFICACIONES = "Tienda Monjarrez - Notificaciones";
    // 🆕 Reactivado junto con EMAIL_SOPORTE.
    private static final String NOMBRE_SOPORTE        = "Tienda Monjarrez - Soporte";
    private static final String NOMBRE_PRINCIPAL      = "Tienda Monjarrez";

    private static final HttpClient client = HttpClient.newHttpClient();

    // ---------------------------------------------------------
    // 🆕 Íconos planos (PNG 44x44), un color sólido por tipo de correo,
    // mismo criterio visual que la plantilla de "Novedades". Reemplazan
    // a los emojis en título y asunto.
    //
    // IMPORTANTE: se referencian por NOMBRE DE ARCHIVO, no en base64.
    // Gmail bloquea/rompe las imágenes embebidas en base64 cuando llegan
    // por un correo real (API/SMTP) — por eso antes se quitó el logo de
    // la plantilla. Sube estos 7 archivos PNG (vienen aparte) a la ruta
    // que apunta ICONOS_BASE_URL, con estos nombres exactos.
    // ---------------------------------------------------------
    private static final String ICON_BIENVENIDA   = "icono-bienvenida.png";
    private static final String ICON_VENDEDOR     = "icono-vendedor.png";
    private static final String ICON_ALERTA       = "icono-alerta.png";
    private static final String ICON_RECUPERACION = "icono-recuperacion.png";
    private static final String ICON_PRODUCTO     = "icono-producto.png";
    private static final String ICON_SUSCRIPCION  = "icono-suscripcion.png";
    private static final String ICON_ELIMINADA    = "icono-cuenta-eliminada.png";
    // 🆕 Reutiliza el ícono de vendedor (no hace falta subir un PNG nuevo):
    // la aprobación es, en esencia, "ya eres vendedor activo".
    private static final String ICON_APROBADA     = ICON_VENDEDOR;

    // ---------------------------------------------------------
    // Envío genérico (asíncrono, a prueba de fallos)
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
                System.out.println("[EmailService] Correo enviado a " + destinatarioEmail + " (desde " + remitenteEmail + ")");
            } else {
                System.out.println("[EmailService] Brevo respondió " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            System.out.println("[EmailService] Error enviando correo: " + e.getMessage());
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
    // Plantilla visual — sin logo, tarjeta blanca + ícono + botón +
    // pie de página. Se ve igual en Gmail, Outlook, Apple Mail, etc.
    // ---------------------------------------------------------

    /**
     * @param iconoArchivo nombre del archivo del ícono (ej. "icono-bienvenida.png"),
     *                     servido como imagen real desde ICONOS_BASE_URL — NUNCA
     *                     en base64 (Gmail lo bloquea al llegar por API/SMTP).
     *                     null/vacío para no mostrar ícono.
     * @param titulo      título corto (ej. "¡Bienvenido, Ana!")
     * @param cuerpoHtml  párrafos HTML del cuerpo del mensaje (ej. "<p>...</p><p>...</p>")
     * @param textoBoton  texto del botón, o null/vacío para no mostrar botón
     * @param urlBoton    a dónde apunta el botón
     */
    private static String plantillaBase(String iconoArchivo, String titulo, String cuerpoHtml,
                                         String textoBoton, String urlBoton) {
        String botonHtml = "";
        if (textoBoton != null && !textoBoton.isEmpty()) {
            botonHtml = String.format(
                "<a href=\"%s\" style=\"display:inline-block;background:#1f6fd8;color:#ffffff;" +
                "text-decoration:none;padding:10px 26px;border-radius:8px;font-size:14px;" +
                "font-weight:bold;margin-top:16px;\">%s</a>",
                urlBoton, textoBoton
            );
        }

        String iconoHtml = "";
        if (iconoArchivo != null && !iconoArchivo.isEmpty()) {
            iconoHtml = String.format(
                "<img src=\"%s%s\" width=\"40\" height=\"40\" alt=\"\" " +
                "style=\"display:block;margin:0 auto 12px auto;\">",
                ICONOS_BASE_URL, iconoArchivo
            );
        }

        return String.format("""
            <div style="background-color:#f2f2f2;padding:32px 16px;font-family:Arial,Helvetica,sans-serif;">
              <div style="max-width:420px;margin:0 auto;background:#ffffff;border-radius:12px;overflow:hidden;">
                <div style="background:#111111;padding:22px;text-align:center;">
                  <p style="color:#ffffff;margin:0;font-size:18px;font-weight:bold;letter-spacing:0.3px;">Tienda Monjarrez</p>
                </div>
                <div style="padding:28px 24px;text-align:center;color:#333333;">
                  %s
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
            """, iconoHtml, titulo, cuerpoHtml, botonHtml, SOPORTE_EMAIL);
    }

    // ---------------------------------------------------------
    // Plantillas de conveniencia — un método por cada tipo de correo.
    // ---------------------------------------------------------

    public static void enviarBienvenidaComprador(String email, String nombre) {
        String cuerpo =
              "<p>¡Nos alegra mucho que formes parte de nuestra comunidad!</p>"
            + "<p>Tu cuenta ya está lista. Desde este momento puedes explorar una gran variedad de "
            + "productos, descubrir nuevas ofertas y realizar tus compras de forma segura.</p>"
            + "<p>Esperamos que disfrutes tu experiencia en Tienda Monjarrez.</p>";

        String html = plantillaBase(ICON_BIENVENIDA, "¡Bienvenido, " + nombre + "!", cuerpo, "Ir a la tienda", URL_TIENDA);
        enviarAsync(EMAIL_NO_REPLY, NOMBRE_GENERICO, email, nombre,
                "¡Bienvenido a Tienda Monjarrez!", html);
    }

    public static void enviarBienvenidaVendedor(String email, String nombre) {
        String cuerpo =
              "<p>Gracias por registrarte como vendedor.</p>"
            + "<p>Estás muy cerca de comenzar a ofrecer tus productos a cientos de compradores.</p>"
            + "<p>Solo debes completar tu solicitud y el proceso de suscripción para habilitar tu "
            + "tienda y empezar a publicar.</p>"
            + "<p>¡Te deseamos mucho éxito en esta nueva etapa!</p>";

        String html = plantillaBase(ICON_VENDEDOR, "¡Bienvenido, " + nombre + "!", cuerpo, "Completar registro", URL_TIENDA);
        enviarAsync(EMAIL_NO_REPLY, NOMBRE_GENERICO, email, nombre,
                "¡Bienvenido como vendedor a Tienda Monjarrez!", html);
    }

    public static void enviarAlertaLoginSospechoso(String email, String nombre, String cedula) {
        String cuerpo =
              "<p>Detectamos varios intentos fallidos de inicio de sesión en tu cuenta de "
            + "<strong>Mi Tienda</strong>, asociada a la cédula <strong>" + cedula + "</strong>.</p>"
            + "<p>Si realizaste estos intentos, puedes ignorar este mensaje.</p>"
            + "<p>Si no reconoces esta actividad, te recomendamos cambiar tu contraseña lo antes "
            + "posible y contactar a nuestro equipo de soporte.</p>"
            + "<p>Tu seguridad es muy importante para nosotros.</p>";

        String html = plantillaBase(ICON_ALERTA, "Actividad inusual detectada", cuerpo, null, null);
        enviarAsync(EMAIL_SEGURIDAD, NOMBRE_SEGURIDAD, email, nombre,
                "Actividad inusual detectada en tu cuenta", html);
    }

    // Overload sin cédula — para logins que no usan cédula (ej. comprador,
    // que entra con correo). Usado por LoginCompradorServlet.
    public static void enviarAlertaLoginSospechoso(String email, String nombre) {
        String cuerpo =
              "<p>Detectamos varios intentos fallidos de inicio de sesión en tu cuenta de "
            + "Tienda Monjarrez.</p>"
            + "<p>Si realizaste estos intentos, puedes ignorar este mensaje.</p>"
            + "<p>Si no reconoces esta actividad, te recomendamos cambiar tu contraseña lo antes "
            + "posible y contactar a nuestro equipo de soporte.</p>"
            + "<p>Tu seguridad es muy importante para nosotros.</p>";

        String html = plantillaBase(ICON_ALERTA, "Actividad inusual detectada", cuerpo, null, null);
        enviarAsync(EMAIL_SEGURIDAD, NOMBRE_SEGURIDAD, email, nombre,
                "Actividad inusual detectada en tu cuenta", html);
    }

    public static void enviarCodigoRecuperacion(String email, String nombre, String codigo) {
        String cuerpo =
              "<p>Recibimos una solicitud para restablecer tu contraseña en Tienda Monjarrez.</p>"
            + "<p>Tu código de verificación es:</p>"
            + "<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px;color:#1a1a1a;text-align:center;\">"
            + codigo + "</p>"
            + "<p>Este código vence en 10 minutos. Si no solicitaste este cambio, puedes ignorar este mensaje.</p>";

        String html = plantillaBase(ICON_RECUPERACION, "Recupera tu contraseña", cuerpo, null, null);
        enviarAsync(EMAIL_NO_REPLY, NOMBRE_GENERICO, email, nombre,
                "Código para restablecer tu contraseña", html);
    }

    public static void enviarAvisoNuevoProducto(String email, String nombre, String nombreProducto, String nombreVendedor) {
        String cuerpo =
              "<p><strong>" + nombreVendedor + "</strong> acaba de publicar un nuevo producto que "
            + "podría interesarte:</p>"
            + "<p style=\"font-size:16px;font-weight:bold;color:#1a1a1a;\">" + nombreProducto + "</p>"
            + "<p>Entra a Tienda Monjarrez y descubre todos sus detalles. ¡No te lo pierdas!</p>";

        String html = plantillaBase(ICON_PRODUCTO, "¡Nuevo producto disponible!", cuerpo, "Ver producto", URL_TIENDA);
        enviarAsync(EMAIL_NOTIFICACIONES, NOMBRE_NOTIFICACIONES, email, nombre,
                "Nuevo producto disponible: " + nombreProducto, html);
    }

    public static void enviarSuscripcionEnRevision(String email, String nombre, String tipoSuscripcion) {
        String cuerpo =
              "<p>Hemos recibido correctamente tu solicitud para la suscripción "
            + "<strong>" + tipoSuscripcion + "</strong>.</p>"
            + "<p>Ahora nuestro equipo revisará y validará manualmente el pago realizado.</p>"
            + "<p>En cuanto el proceso finalice, recibirás otro correo con el resultado y, si todo "
            + "está correcto, podrás acceder a <strong>Mi Tienda</strong> para comenzar a publicar "
            + "tus productos.</p>"
            + "<p>¡Gracias por confiar en Tienda Monjarrez y por querer crecer junto a nosotros!</p>";

        String html = plantillaBase(ICON_SUSCRIPCION, "¡Recibimos tu solicitud!", cuerpo, null, null);
        enviarAsync(EMAIL_NOTIFICACIONES, NOMBRE_NOTIFICACIONES, email, nombre,
                "Tu suscripción está siendo revisada", html);
    }

    // ---------------------------------------------------------
    // 🆕 Confirmación de suscripción APROBADA.
    // Se envía justo después de que el admin aprueba la solicitud desde
    // panelAdmin (botón "Aprobar" / "Renovar"). Antes de esto, el vendedor
    // no recibía ningún aviso — solo se enteraba si entraba a revisar
    // manualmente.
    //
    // Sale desde EMAIL_SOPORTE (no desde no-reply/notificaciones) porque
    // es el equipo de soporte quien aprueba las solicitudes, así que si el
    // vendedor responde el correo con una duda, cae en la bandeja correcta.
    //
    // El botón "Ir a mi tienda" NO apunta a la portada genérica: usa
    // ?accion=login-vendedor, que index.html intercepta (ver el <script>
    // agregado antes de </body>) para abrir directo el login de
    // Vendedor/a, en vez de que la persona tenga que buscarlo en el menú.
    // ---------------------------------------------------------
    public static void enviarSuscripcionAprobada(String email, String nombre, String tipoSuscripcion) {
        String cuerpo =
              "<p>¡Buenas noticias! Tu suscripción <strong>" + tipoSuscripcion + "</strong> fue "
            + "revisada y <strong>aprobada</strong>.</p>"
            + "<p>Tu cuenta de vendedor ya está activa: puedes ingresar a <strong>Mi Tienda</strong> "
            + "y comenzar a publicar tus productos ahora mismo.</p>"
            + "<p>¡Felicidades y mucho éxito en Tienda Monjarrez!</p>";

        String html = plantillaBase(ICON_APROBADA, "¡Felicidades, " + nombre + "!", cuerpo,
                "Ir a mi tienda", URL_TIENDA + "/?accion=login-vendedor");
        enviarAsync(EMAIL_SOPORTE, NOMBRE_SOPORTE, email, nombre,
                "¡Tu suscripción en Tienda Monjarrez fue aprobada!", html);
    }

    // ---------------------------------------------------------
    // 🆕 Confirmación de eliminación de cuenta.
    // Se envía justo después de borrar la cuenta (comprador o vendedor),
    // usando los datos del perfil por última vez antes de perderse.
    // Sale desde el correo principal (tiendamonjarrez@gmail.com), no desde
    // el dominio, para que se sienta como el cierre "oficial" y personal
    // del proceso.
    //
    // @param rol "comprador" o "vendedor" (cualquier otro valor se trata
    //            como "comprador" por defecto)
    // ---------------------------------------------------------
    public static void enviarCuentaEliminada(String email, String nombre, String rol) {
        String rolTexto = "vendedor".equalsIgnoreCase(rol) ? "vendedor" : "comprador";

        String cuerpo =
              "<p>Te confirmamos que tu cuenta de Tienda Monjarrez, registrada como <strong>"
            + rolTexto + "</strong>, fue eliminada de forma definitiva a tu solicitud.</p>"
            + "<p>Todos los datos asociados a tu perfil ya no están disponibles en nuestra plataforma "
            + "y esta es la última comunicación que te enviamos al respecto.</p>"
            + "<p>Fue un gusto tenerte con nosotros, " + nombre + ". Si en algún momento quieres volver, "
            + "las puertas de Tienda Monjarrez van a estar abiertas para ti.</p>"
            + "<p>¡Esperamos verte pronto de nuevo!</p>";

        String html = plantillaBase(ICON_ELIMINADA, "Cuenta eliminada", cuerpo, "Volver a la tienda", URL_TIENDA);
        enviarAsync(EMAIL_PRINCIPAL, NOMBRE_PRINCIPAL, email, nombre,
                "Tu cuenta en Tienda Monjarrez ha sido eliminada", html);
    }

    // ---------------------------------------------------------
    // 🆕 Alerta interna: nueva solicitud de vendedor recibida.
    // No es un correo "de marca" para el cliente ni el vendedor — es solo
    // para que el admin se entere en tiempo real de que llegó una
    // solicitud nueva, sin tener que estar revisando panelAdmin
    // manualmente para saberlo.
    //
    // Va directo al correo personal del admin (EMAIL_ADMIN_PERSONAL),
    // nunca se muestra al vendedor ni sale desde una dirección "de cara
    // al público" distinta a las que ya existen. Reutiliza el remitente
    // EMAIL_NOTIFICACIONES porque semánticamente es eso: una notificación
    // automática del sistema.
    //
    // Debe llamarse justo después de insertar exitosamente la fila nueva
    // en SolicitudesDeVendedor (paso 2 del registro de vendedor).
    // ---------------------------------------------------------
    public static void enviarAlertaNuevaSolicitudVendedor(String nombreVendedor, String correoVendedor,
                                                           String provincia, String canton) {
        String cuerpo =
              "<p>Llegó una nueva solicitud de vendedor en Tienda Monjarrez.</p>"
            + "<p><strong>Nombre:</strong> " + nombreVendedor + "<br>"
            + "<strong>Correo:</strong> " + correoVendedor + "<br>"
            + "<strong>Ubicación:</strong> " + canton + ", " + provincia + "</p>"
            + "<p>Revísala en el panel de administración para aprobar o rechazar.</p>";

        String html = plantillaBase(ICON_VENDEDOR, "Nueva solicitud de vendedor", cuerpo,
                "Ir al panel", "https://tiendamonjarrez.com/panelAdmin.html");
        enviarAsync(EMAIL_NOTIFICACIONES, NOMBRE_NOTIFICACIONES, EMAIL_ADMIN_PERSONAL, "Admin",
                "🔔 Nueva solicitud de vendedor: " + nombreVendedor, html);
    }
}