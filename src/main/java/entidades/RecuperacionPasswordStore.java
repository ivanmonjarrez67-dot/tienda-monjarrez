package entidades;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Guarda en memoria los códigos de recuperación de contraseña por correo.
 * NOTA: igual que los contadores de intentos fallidos de login, se reinicia
 * si el servidor se reinicia — suficiente para este caso de uso.
 */
public class RecuperacionPasswordStore {

    private static class Codigo {
        final String hash;
        final long expiracion;
        final AtomicInteger intentos = new AtomicInteger(0);

        Codigo(String hash, long expiracion) {
            this.hash = hash;
            this.expiracion = expiracion;
        }
    }

    private static final long DURACION_MS = 10 * 60 * 1000; // 10 minutos
    private static final int MAX_INTENTOS = 5;

    private static final ConcurrentHashMap<String, Codigo> codigos = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> ultimoEnvio = new ConcurrentHashMap<>();

    /** true si ya pasó el cooldown desde la última solicitud para ese correo. */
    public static boolean puedeSolicitar(String correoClave, long cooldownMs) {
        long ahora = System.currentTimeMillis();
        Long anterior = ultimoEnvio.get(correoClave);
        if (anterior != null && (ahora - anterior) < cooldownMs) {
            return false;
        }
        ultimoEnvio.put(correoClave, ahora);
        return true;
    }

    public static void guardarCodigo(String correoClave, String codigoEnClaro) {
        codigos.put(correoClave, new Codigo(hashSHA256(codigoEnClaro), System.currentTimeMillis() + DURACION_MS));
    }

    /**
     * @return "OK", "EXPIRADO" (venció o no existe), "BLOQUEADO" (muchos
     *         intentos) o "INCORRECTO".
     */
    public static String validarCodigo(String correoClave, String codigoIngresadoEnClaro) {
        Codigo codigo = codigos.get(correoClave);
        if (codigo == null) return "EXPIRADO";

        if (System.currentTimeMillis() > codigo.expiracion) {
            codigos.remove(correoClave);
            return "EXPIRADO";
        }

        if (codigo.intentos.get() >= MAX_INTENTOS) {
            codigos.remove(correoClave);
            return "BLOQUEADO";
        }

        String hashIngresado = hashSHA256(codigoIngresadoEnClaro);
        boolean coincide = MessageDigest.isEqual(
                codigo.hash.getBytes(StandardCharsets.UTF_8),
                hashIngresado.getBytes(StandardCharsets.UTF_8));

        if (coincide) return "OK";

        codigo.intentos.incrementAndGet();
        return "INCORRECTO";
    }

    /** Se llama tras un cambio de contraseña exitoso, para que el código no se reutilice. */
    public static void invalidar(String correoClave) {
        codigos.remove(correoClave);
    }

    private static String hashSHA256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}