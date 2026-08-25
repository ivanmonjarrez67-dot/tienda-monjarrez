package entidades;

import java.util.*;

// 🔧 Parser JSON mínimo, sin librerías externas. Convierte un String
// JSON en estructuras nativas de Java: Map<String,Object> para objetos,
// List<Object> para arrays, String, Double, Boolean o null para el resto.
// No es un parser JSON completo/robusto (no valida errores raros de
// formato), pero cubre bien las respuestas de CJ que son JSON estándar.
public class JsonParser {

    private final String s;
    private int pos;

    private JsonParser(String s) {
        this.s = s;
        this.pos = 0;
    }

    public static Object parse(String json) {
        return new JsonParser(json).parseValue();
    }

    private void skipWs() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
    }

    private Object parseValue() {
        skipWs();
        char c = s.charAt(pos);
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '"') return parseString();
        if (c == 't' || c == 'f') return parseBoolean();
        if (c == 'n') { pos += 4; return null; }
        return parseNumber();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWs();
        if (s.charAt(pos) == '}') { pos++; return map; }
        while (true) {
            skipWs();
            String key = parseString();
            skipWs();
            pos++; // :
            map.put(key, parseValue());
            skipWs();
            if (s.charAt(pos) == ',') { pos++; continue; }
            pos++; // }
            break;
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWs();
        if (s.charAt(pos) == ']') { pos++; return list; }
        while (true) {
            list.add(parseValue());
            skipWs();
            if (s.charAt(pos) == ',') { pos++; continue; }
            pos++; // ]
            break;
        }
        return list;
    }

    private String parseString() {
        pos++; // comilla de apertura
        StringBuilder sb = new StringBuilder();
        while (s.charAt(pos) != '"') {
            char c = s.charAt(pos);
            if (c == '\\') {
                pos++;
                char esc = s.charAt(pos);
                switch (esc) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(pos + 1, pos + 5), 16));
                        pos += 4;
                        break;
                    default: sb.append(esc);
                }
            } else {
                sb.append(c);
            }
            pos++;
        }
        pos++; // comilla de cierre
        return sb.toString();
    }

    private Boolean parseBoolean() {
        if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
        pos += 5;
        return Boolean.FALSE;
    }

    private Double parseNumber() {
        int start = pos;
        while (pos < s.length() && "-+.eE0123456789".indexOf(s.charAt(pos)) >= 0) pos++;
        return Double.parseDouble(s.substring(start, pos));
    }

    // --- Helpers para extraer valores con seguridad (evitan NPE/ClassCastException) ---

    public static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    public static double getDouble(Map<String, Object> map, String key, double def) {
        Object v = map.get(key);
        return (v instanceof Double) ? (Double) v : def;
    }

    public static int getInt(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        return (v instanceof Double) ? ((Double) v).intValue() : def;
    }

    public static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        return (v instanceof Boolean) ? (Boolean) v : def;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return (v instanceof Map) ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return (v instanceof List) ? (List<Object>) v : null;
    }
}