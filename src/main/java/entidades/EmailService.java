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
    //private static final String EMAIL_SOPORTE         = "soporte@tiendamonjarrez.com";
    private static final String EMAIL_SEGURIDAD       = "seguridad@tiendamonjarrez.com";
    private static final String EMAIL_NOTIFICACIONES  = "notificaciones@tiendamonjarrez.com";

    // 🆕 Correo principal (bandeja real de Gmail) — se usa para el correo de
    // despedida al eliminar la cuenta, para que se sienta como un mensaje
    // final "oficial" y no automatizado desde el dominio.
    private static final String EMAIL_PRINCIPAL       = "tiendamonjarrez@gmail.com";

    private static final String NOMBRE_GENERICO       = "Tienda Monjarrez";
    private static final String NOMBRE_SEGURIDAD      = "Tienda Monjarrez - Seguridad";
    private static final String NOMBRE_NOTIFICACIONES = "Tienda Monjarrez - Notificaciones";
    //private static final String NOMBRE_SOPORTE        = "Tienda Monjarrez - Soporte";
    private static final String NOMBRE_PRINCIPAL      = "Tienda Monjarrez";

    private static final HttpClient client = HttpClient.newHttpClient();

    // ---------------------------------------------------------
    // 🆕 Íconos planos (PNG 44x44 embebidos en base64), un color sólido
    // por tipo de correo, mismo criterio visual que la plantilla de
    // "Novedades". Reemplazan a los emojis en título y asunto.
    // ---------------------------------------------------------
    private static final String ICON_BIENVENIDA   = "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";
    private static final String ICON_VENDEDOR     = "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";
    private static final String ICON_ALERTA       = "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";
    private static final String ICON_RECUPERACION = "iVBORw0KGgoAAAANSUhEUgAAACwAAAAsCAYAAAAehFoBAAAE1ElEQVR4nO2XW2hcRRjH//+Zc3Y3m1uNrZfSJl5q2iYq1Sj4pClFRBAUZIO1DwqCUiGJWHzog5wuij4IahorKD70RZFdXwShiGDaB4svtUrtJiGtNNvW2jamqZtks7sz8/mwm2tzKaW2COcH52HnnG/Ob76Z+c4sEBISEhISEjILb7bAyiRSGkGfB0hZVoQI+jwEom6y2UKEV0hxgWMipW+gUFlh8WYhQAGAu7oHH4fyXhBxbSRrBTIq1D8T5svhD5uPVp4FALk5wiIEKbfv+rU65mr3UfsvUfsQUwTEAkqDOgIp5Z1z5qPsx/e9hQBEEjI9yBsoLEQirZrij/pSXzjgVa9ut5MXrQCGAh9KK4it/Kava9YoM3H+q2xPyw4kUhrpDof/ONPzhVOi0UHb2NX/nldz224zfqFIwFPRWuWK44BglFo30IvBFXIOUEZXr46YiYuvZfdu/rwl+D2SQau5LmZJuuWFg0AhmXT3vjm03lg3CKUicAb04xqm+DWdfd/38GdB1AYI36UX2eaKk0Z5USW2+Ff1KnN3Jnl/8brILsMcYfGQpGnqHtipYqs+tVOXCtqvidrSxKFsz6b2uUFtr37mX4xvPaa0v1FKeQftKzh7BCKXhSRFrm1ZEEIdo7OF09meTS/PcZzpz5t9+iAAQARbIK68gZQCKJ9AhNhz3EeytbShcyhypLe50NjZ/wWjdR9IqeBgDenH26iupjTPXYUyv10s6FfDTU6dWSraW9ggkKryWKHEGVAwgo60AhIWoJxo6LNIiFZ68DykssxIislbqVSY5YUX3ubcdkdxCoLcUtEzKXkC7ZVwngGVAOKoI4CTbUh32A13DHkIxGs7106kaa2TJ2dfRIBKg1QA9PIXF1xz2mXm/pJTNZPhQ5nK0JX9UVxpNwHPFcYFXrSrsbP/wIne5sMAcARAU/fAc1D+dlfIOZAKhFD4NCCnBEoRbt4OF03Sihir6pW2P5BeHZUHMcVhUp5ywv06En/MlSZFbAFQeB0AEIhaWC1ml0SaFkGgspmWvqY7B35R0ZqH3VTO0IvWwfMONnYPpAkOCdwWav9ZcQ4itqSrbvFtfuzb4b0bv18qK9OsS2SruHaiUvYIAIVTPZsHG7sGLgPKALDinJD4Y8UMAwAyrUSaVjr7O8WZn6h9z9liiaSvo7UvghpwBq6QE4EY5Vf5rjiZE9G7kEhptCQI4Mr62QriOMSNnI3rubuOIAJRHBtoUFX1HggPSkMVRquvTjjdYZFI6Wzv5sNNXce2w6/dryPxqCvkYKf+KZZlqEgV0bE6X8zUKIqTz5/e98DJyvTZRd8iQnRQvJ3DDoCBwEAAERgk6aRr4BuXv5SxJm/hRLlI/G8AwB4IkvO7WvzwkxCNNG1T5/GH4Fe9Q3Hb6MViUApwFs4UcwS/s2b07dO9j5ysfJYXl50WJmXdK9kGXTM+omK3ktqHHb9wbnjvprVLxi3CFWUNQHk9J1J6uLf1KIBnGt8Yvoc2/yBdpN5ZM2Kt/e1sb3O5Vi6X2Wkqpa5u3frxibH+HW5qLAodAcDL5QSlNFrWzCYvudXims4kgaglD+qJlEYQ3PBD/NX95QlElTfOQSLTLmjZI0gmFz2crNxX3+ysZtoF6RVmJyQkJCQkJCQk5H/Ev3XpNIl9bimnAAAAAElFTkSuQmCC";
    private static final String ICON_PRODUCTO     = "iVBORw0KGgoAAAANSUhEUgAAACwAAAAsCAYAAAAehFoBAAAE70lEQVR4nO3ZT2wUVRwH8O/vvTc7zP4ptYXYIraARnBpgoSaeNAEgjfUG8SDPZiYYpTWABdNTHb34EEDGKkhpgcTDiZGjtqoUaRRLiYQTWy3YGgiJWYLQlna/Tc7M+/nYbdlaem2uzsFTfpLNpvse5n5zO/9nwVWYzXuCWqwfDnBPlzjQQYBMRYPW7G8YL7bQj6h5zc5odyEHe+MfUvS2sxuXgNU+82IWQjFTCqjC+nXr53aOYrYOYXEHndlwP1jf0nrkU52cqUS1vXdwAiCnfykZ0+/6Ae6SuYox05es1twwRpkWCBllb4rP4EQyIyAAuEFZSAJL58uQqg2aTb92NE3EkVij4vYOVUvuEqGL10SKrAVrFl77jiA8wALgPTdyixAmGbWRYJUTLwWTDxXj7BDmpGdXuGOLYyQCe1Oaje/d2KgK1lvpqs/KWtQIEjIT/80cXLbwftV2XTo8g4RbnnBzd3+/donT5+vLGvv/TsIkR2SVstuL3/LFka4TSjrbEffyN6JRH3o6hmWaisZFjx75vREc+qN8gO6T049Jq+0fOF03n71XRla9wGzBgkD7syNzyZatr4dxaiyUgW+ONjttPdeCBqh5iEZCM+iG8r08kY/g8sXdRHf7V0ZeMrecr1nHYD3deEOe7mpopf7xxNG4M2Nt8eiyURX8WL7Lg8xFqnB7pyTTe/ziplhabWa2snYEGo20zX36dqnq3ipVUgUmiCExZ7rEZFk7WlIBSVVKwAgCUKCtN/o2sFlxPip7eOs9XeqqV2BpFThNoPt7ChPGb+CmXCmPDh9Rte3+sTBAMFR1ONmbw6SNEd1If0la3r56unNBcTjc2PBb3R98yERA0DqxLabAA5WjFUAXOoK82MWnaBce++FfQCGpNVa8+zR2PrOTNjPEmBg/1cSsZgAaPHdmQ+ZbgxMxDhDHgDgzAEPicTS6/c8NBczwyq43tRO1lmIXrhhejjbvgr0zJ3JVzx7+mthBA12cg6EbBNG8IfOw38+hzh4Pvrh7VMTpKOxkcCtz5+fYcf+hYQCQMSuXRRm0wb23G4QMbbfu7gtD0wggP04fdyNGKtkoqvY2T92VAabP9LFjAciCDMS8LI3jkycjH6KGAscKHe5JcHEpcFDBDDbADE2RPxBx1ghQW5nX/KIWLP2mLYzLkAszIjS+fTRqyejH5dmioWzTbXtpQTA7BSYhLHr0de+D+Fgt9PwyaESazUf1/aMCwBz2IHoiVKd5U5rc8cazpERJO0UHBGwdpnrn/imvfdCcHbArCyWFt0Mzb8xIw4CMymBHnbzKWGGAl4hbctAeLcRah6qG+0DFljsGB9jgQTpjr6RqFDWWQjVpp2MLa1W0ytmhp1sel9qsDs3W+9BYYHF+nCCNGLn1MRAV1K7+b3Q7qQwwqaXv1V7pn3ELg4GMLs8NoT2GVsd3Ch6BbBLg+tFrxAWqOXdWXnLt9RAjMZGAslEV3ElsLWBl0BzMTOcF5mXrh9/JtvZf/moWBM55je2dnAVtAqtN73c1BAzfpZrIh+Wllt/sfWBF0VnHWEEDRIS2s5okNB+Y+sHL4rOOQSi8q7Ld2xj4PuiZZt27aI0I4GVwPoT5bPX42/9tn3T4fHUlvducmd/8nCpjOt+6beyUUZv7P/j2Y7+y4cqf/vvRuVK97/5iyDGonTsX43VeGDxL2iB45BePVUgAAAAAElFTkSuQmCC";
    private static final String ICON_SUSCRIPCION  = "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";
    private static final String ICON_ELIMINADA    = "iVBORw0KGgoAAAANSUhEUgAAACwAAAAsCAYAAAAehFoBAAAEWklEQVR4nO2Zz2skRRTHv696unqySzasWcOKB1kV1ntgwYvtMYqHZCYlKsK6JwUVRMWbtH0QT4L/gaIhq6mIB1fxIGLjea5BD+JV5mDWrPnRVV31PEzPkkymJzObmR2UfGGY5lV39adfVb969Ro402RFVQ1KqaDdble2D6Msy4rTXP+/UK8HCQADgFLqoyAILltrWQgxkqeJCM65vZmZmffW1tZ2D/d7WtUGtL0ipbwshBipQyICEeHg4IDzPH8fwO6pCHs0CPgvY8wl55wHIIcB996Dma0QgohoOwzDsXj1sAYBB2W7B/C39/7Pkzpj5nkhxDwAYuZBfd+zBnbKzAjDUFhrv9Fa34jjuNbvze/aV1dXkzAMPzDGTIIVADDSBF1YWOg7xFX2SWiUYaMyLh+LGFX2SWgUYO75r2qfqEaLWRNSkiQCQ47Q1IGTJBFpmnp0RuhE6KkCK6WCNE19o9F4ZGlpKcIQ0FMDTpJEaK1do9F4QkrZmp2dXS/tA1/gqQCX04CVUtfCMPyZmeellA2l1M3SXjmnpwK8tbVFAJiILhDRRWZmY0weRdELq6urn2utXRX0OIGJmft5hRYXF2txHN/9tdttUkrJjY2NH51zzwghbBAEUZ7nJoqil5VSH1dBj3O9ZwBH1mRrLQHgVqtlqy7SWv+0srJyLYqi74joIWOMjaLobaUUa63fLadPt/+xAIssy5xS6ooQ4oa11hORQGf0/lFKPRmG4avGGFfajz4lc42I7hRFcTsIgoeZGXme23q9/o5S6lyapq8nSUJpmo4HOI5jkWVZwcxvSSkf39/ft6KTiwqttVFKXZVSXmdmEPV/+YkIRVHAe4/yQWGt3WPm33vPnUgK2CPnnINzrhIY6GSG3UMiImbeZeYf0BObTw2cZZkHQET0iTHmWSHEo90bK6UC59xt59xvzFxU5MgCnV3JFSKaY2Yws52ZmXlwb2/vTQCvlVFlPMAAfBzHNa31H81m89N6vf6hMQbM7KSUF7TW3wK4hT7J0eLiYthqtWyz2Xw6DMOvfWdOFFEUyf39/ZvtdvuNQ0v32IC7IgCyoq1fJidarZZdXl6Oa7XaLWY+z8wmiiKZ5/mXWuuXAFCWZUeuH2ccZiKqSjG79yEApJQKAHCz2XyuXq9/D+C89/4w7ItlBncEdtzAg3Q4l757LIS4SETnvPdWSnkEtgxjxxwwlaVZa+3K5OcLa+3zUsrQWvvVSbDAFLO1NE29UirY3NzUBwcHT+3s7Fwv7ZWwwP2Jw5XqejpN019K04kVoqnvOLqexpDlrKl6uCuttRv23JG2+T3/Ve0T1Ujb/LJgcmzYquyT0EhzuKrAfdrC9ygaVB/+NQiCq2X18g6AkYqB3vttKeVj6+vr27hP9WEHoECnejknhJg7qTPvPbz33XLrRD4XDAJ+QEpZK4rh7xsEAYgoLAval8ot0lg1CPgzY8w9fzJg5r16vZ6fku+/r7PPXmfq0b9eMildNgcA5wAAAABJRU5ErkJggg==";

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
     * @param iconoBase64 PNG (base64, sin prefijo data:) mostrado arriba del
     *                    título, o null/vacío para no mostrar ícono
     * @param titulo      título corto (ej. "¡Bienvenido, Ana!")
     * @param cuerpoHtml  párrafos HTML del cuerpo del mensaje (ej. "<p>...</p><p>...</p>")
     * @param textoBoton  texto del botón, o null/vacío para no mostrar botón
     * @param urlBoton    a dónde apunta el botón
     */
    private static String plantillaBase(String iconoBase64, String titulo, String cuerpoHtml,
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
        if (iconoBase64 != null && !iconoBase64.isEmpty()) {
            iconoHtml = String.format(
                "<img src=\"data:image/png;base64,%s\" width=\"40\" height=\"40\" alt=\"\" " +
                "style=\"display:block;margin:0 auto 12px auto;\">",
                iconoBase64
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
    // 🆕 Confirmación de eliminación de cuenta.
    // Se envía justo después de borrar la cuenta (comprador o vendedor),
    // usando los datos del perfil por última vez antes de perderse.
    // Sale desde el correo principal (bandeja real), no desde el dominio,
    // para que se sienta como el cierre "oficial" y personal del proceso.
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
}