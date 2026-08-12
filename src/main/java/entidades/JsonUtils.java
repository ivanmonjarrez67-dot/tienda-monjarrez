package entidades;

/**
 * 🆕 Utilidad para escapar texto de forma segura cuando se arma JSON "a
 * mano" con concatenación de Strings (como en FiltrosProductosServlet,
 * ListaProductosServlet y ListaProductosServletBusquda).
 *
 * Antes, cada servlet hacía algo como:
 *   "\"nombre\":\"" + rs.getString("nombre") + "\""
 *
 * Eso rompe el JSON completo apenas el texto guardado en la base de datos
 * trae una comilla ("), un backslash (\), un salto de línea, o cualquier
 * otro carácter especial (ej: "Bicicleta 16\" Huffy"). Usando
 * JsonUtils.escapar(...) alrededor de cada valor de texto, esos caracteres
 * se convierten en su forma segura para JSON y el catálogo nunca más se
 * rompe sin importar qué escriba un usuario en nombre/descripción/etc.
 */
public class JsonUtils {

    // No instanciable: solo métodos estáticos.
    private JsonUtils() {}

    /**
     * Escapa un valor de texto para insertarlo de forma segura dentro de
     * un string JSON. Si el valor es null, devuelve "" (texto vacío) en
     * vez de la palabra "null" literal, que quedaría rara dentro de las
     * comillas del JSON.
     */
    public static String escapar(String valor) {
        if (valor == null) return "";

        StringBuilder sb = new StringBuilder(valor.length());
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    // Cualquier otro carácter de control (< 0x20) se
                    // escapa como \\uXXXX; el resto se deja tal cual
                    // (incluye tildes, ñ, emojis, etc. — UTF-8 normal).
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}