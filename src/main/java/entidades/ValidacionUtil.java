package entidades;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.regex.Pattern;

/**
 * Utilidad simple para validar correos antes de registrarlos.
 *
 * Dos niveles de validación:
 *  - tieneFormatoValido: solo revisa que el texto tenga forma de correo.
 *  - dominioExiste: además consulta si el dominio tiene registro MX
 *    (es decir, si realmente puede recibir correos). Esto detecta
 *    typos como "gmail.co" en vez de "gmail.com".
 *
 * NOTA: esto NO garantiza que la casilla específica exista (ej. que
 * "usuario123@gmail.com" sea una cuenta real) — solo que el dominio
 * puede recibir correo. Para verificar la casilla exacta se necesita
 * un correo de confirmación con enlace (doble opt-in).
 */
public class ValidacionUtil {

    private static final Pattern PATRON_EMAIL = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public static boolean tieneFormatoValido(String correo) {
        if (correo == null) return false;
        return PATRON_EMAIL.matcher(correo.trim()).matches();
    }

    public static boolean dominioExiste(String correo) {
        if (!tieneFormatoValido(correo)) return false;
        String dominio = correo.substring(correo.indexOf('@') + 1).trim();
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "2000"); // 2s, para no colgar el registro
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            InitialDirContext ictx = new InitialDirContext(env);
            Attributes attrs = ictx.getAttributes(dominio, new String[]{"MX"});
            Attribute attrMx = attrs.get("MX");
            if (attrMx != null && attrMx.size() > 0) {
                return true;
            }
            // Algunos dominios (pocos) reciben correo sin registro MX explícito,
            // usando el registro A del propio dominio. Lo aceptamos como respaldo.
            Attributes attrsA = ictx.getAttributes(dominio, new String[]{"A"});
            Attribute attrA = attrsA.get("A");
            return attrA != null && attrA.size() > 0;
        } catch (NamingException e) {
            return false;
        }
    }

    /** Validación completa recomendada antes de registrar un usuario. */
    public static boolean esCorreoValido(String correo) {
        return tieneFormatoValido(correo) && dominioExiste(correo);
    }
}