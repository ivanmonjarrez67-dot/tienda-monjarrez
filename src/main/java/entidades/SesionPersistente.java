package entidades;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Sesión larga (cookie HttpOnly de 90 días) para COMPRADORES y VENDEDORES.
 *
 * - Comprador: se restaura con los mismos atributos que el login normal.
 * - Vendedor: se restaura una sesión LIMITADA (solo perfil): usuarioId, nombre y
 *   correo. NO se restauran vendedorId ni cedulaVendedor, y la sesión queda marcada
 *   con "sesionLimitada". "Mi tienda" siempre vuelve a pedir cédula y contraseña.
 */
public final class SesionPersistente {

    public static final String COOKIE = "rmt";
    private static final int DIAS = 90;
    private static final SecureRandom RNG = new SecureRandom();

    private SesionPersistente() {}

    /** Crea el registro y la cookie. Nunca lanza: si falla, el login sigue normal. */
    public static void crear(Connection conn, int usuarioId, HttpServletRequest req, HttpServletResponse resp) {
        try {
            byte[] raw = new byte[32];
            RNG.nextBytes(raw);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

            String ua = req.getHeader("User-Agent");
            if (ua != null && ua.length() > 200) ua = ua.substring(0, 200);

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM dbo.SesionPersistente WHERE expira_en < SYSUTCDATETIME()")) {
                ps.executeUpdate(); // limpieza de vencidas
            }
            // 🆕 Si este dispositivo ya tenía una sesión larga, se reemplaza (evita
            // acumular filas cuando alguien vuelve a iniciar sesión en el mismo equipo).
            String anterior = leerCookie(req);
            if (anterior != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM dbo.SesionPersistente WHERE token_hash = ?")) {
                    ps.setString(1, sha256(anterior));
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO dbo.SesionPersistente (usuario_id, token_hash, expira_en, dispositivo) "
                  + "VALUES (?, ?, DATEADD(DAY, " + DIAS + ", SYSUTCDATETIME()), ?)")) {
                ps.setInt(1, usuarioId);
                ps.setString(2, sha256(token));
                ps.setString(3, ua);
                ps.executeUpdate();
            }
            ponerCookie(resp, token, DIAS * 86400);
        } catch (Exception e) {
            System.out.println("[SesionPersistente] ⚠️ No se pudo crear: " + e.getMessage());
        }
    }

    /** Si la cookie es válida, deja la sesión iniciada (completa para comprador, limitada para vendedor). */
    public static boolean restaurar(HttpServletRequest req, HttpServletResponse resp) {
        String token = leerCookie(req);
        if (token == null) return false;

        String sql = """
            SELECT u.id, u.nombre, u.correo, u.tipo, ISNULL(g.solo_google, 0) AS solo_google
            FROM dbo.SesionPersistente s
            JOIN dbo.Usuarios u ON u.id = s.usuario_id
            LEFT JOIN dbo.UsuarioGoogle g ON g.usuario_id = u.id
            WHERE s.token_hash = ? AND s.expira_en > SYSUTCDATETIME()
              AND u.tipo IN ('Comprador', 'Vendedor')
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String hash = sha256(token);
            ps.setString(1, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    HttpSession session = req.getSession();
                    session.setAttribute("usuarioId", rs.getInt("id"));

                    if ("Vendedor".equalsIgnoreCase(rs.getString("tipo"))) {
                        // 🆕 Sesión LIMITADA de vendedor: solo lo necesario para el perfil.
                        session.setAttribute("nombreVendedor", rs.getString("nombre"));
                        session.setAttribute("correoVendedor", rs.getString("correo"));
                        session.setAttribute("sesionLimitada", true);
                    } else {
                        session.setAttribute("nombreUsuario", rs.getString("nombre"));
                        session.setAttribute("correoUsuario", rs.getString("correo"));
                        if (rs.getBoolean("solo_google")) session.setAttribute("cuentaGoogle", true);
                    }

                    // Renovar 90 días desde hoy
                    try (PreparedStatement up = conn.prepareStatement(
                            "UPDATE dbo.SesionPersistente SET ultimo_uso = SYSUTCDATETIME(), "
                          + "expira_en = DATEADD(DAY, " + DIAS + ", SYSUTCDATETIME()) WHERE token_hash = ?")) {
                        up.setString(1, hash);
                        up.executeUpdate();
                    }
                    ponerCookie(resp, token, DIAS * 86400);
                    return true;
                }
            }
        } catch (Exception e) {
            // Error de BD: no se borra la cookie, puede ser algo temporal.
            System.out.println("[SesionPersistente] ⚠️ No se pudo restaurar: " + e.getMessage());
            return false;
        }
        borrarCookie(resp); // cookie inválida o vencida
        return false;
    }

    /** Cerrar sesión: borra la fila y la cookie. */
    public static void cerrar(HttpServletRequest req, HttpServletResponse resp) {
        String token = leerCookie(req);
        if (token != null) {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM dbo.SesionPersistente WHERE token_hash = ?")) {
                ps.setString(1, sha256(token));
                ps.executeUpdate();
            } catch (Exception e) {
                System.out.println("[SesionPersistente] ⚠️ No se pudo borrar: " + e.getMessage());
            }
        }
        borrarCookie(resp);
    }

    /** Vincula el usuario con su cuenta de Google (si aún no está vinculado). */
    public static void vincularGoogle(Connection conn, int usuarioId, String googleSub, boolean soloGoogle) {
        String sql = """
            IF NOT EXISTS (SELECT 1 FROM dbo.UsuarioGoogle WHERE usuario_id = ? OR google_sub = ?)
                INSERT INTO dbo.UsuarioGoogle (usuario_id, google_sub, solo_google) VALUES (?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            ps.setString(2, googleSub);
            ps.setInt(3, usuarioId);
            ps.setString(4, googleSub);
            ps.setBoolean(5, soloGoogle);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[SesionPersistente] ⚠️ No se pudo vincular Google: " + e.getMessage());
        }
    }

    // ---------- utilidades ----------

    private static String leerCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                return c.getValue();
            }
        }
        return null;
    }

    private static void ponerCookie(HttpServletResponse resp, String token, int maxAge) {
        resp.addHeader("Set-Cookie", COOKIE + "=" + token + "; Max-Age=" + maxAge
                + "; Path=/; HttpOnly; Secure; SameSite=Lax");
        // Bandera visible para JavaScript (sin datos sensibles): así la página solo
        // consulta al servidor si este dispositivo tiene una sesión larga.
        resp.addHeader("Set-Cookie", "rmt_ok=1; Max-Age=" + maxAge + "; Path=/; Secure; SameSite=Lax");
    }

    private static void borrarCookie(HttpServletResponse resp) {
        resp.addHeader("Set-Cookie", COOKIE + "=; Max-Age=0; Path=/; HttpOnly; Secure; SameSite=Lax");
        resp.addHeader("Set-Cookie", "rmt_ok=; Max-Age=0; Path=/; Secure; SameSite=Lax");
    }

    private static String sha256(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}