package entidades.controladores;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import config.Config;
import entidades.DatabaseConnection;
import entidades.SesionPersistente;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Login de COMPRADOR con "Continuar con Google" (botón de Google y aviso
 * "Continuar como tu-correo" para invitados).
 *
 * Recibe por POST: credential (el ID token de Google).
 * Responde: 200 + "OK" si entra, o un texto de error con su código.
 *
 * Entra a la MISMA cuenta por correo, la haya creado con contraseña o con Google.
 * Vincula la cuenta con Google (UsuarioGoogle) y crea la sesión larga.
 * "cuentaGoogle" solo se marca si la cuenta se creó con Google (solo_google = 1),
 * así un comprador con contraseña sigue viendo "Cambiar contraseña".
 */
@WebServlet("/LoginCompradorGoogleServlet")
public class LoginCompradorGoogleServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // ⚠️ Nombres según tu sesion_persistente.sql. Si tu columna de usuario se llama
    // distinto en UsuarioGoogle, cámbiala solo aquí.
    private static final String COL_USUARIO = "usuario_id";

    private static final String GOOGLE_CLIENT_ID = Config.GOOGLE_CLIENT_ID;

    private static final GoogleIdTokenVerifier VERIFIER =
            new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(GOOGLE_CLIENT_ID))
                    .build();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        String credential = request.getParameter("credential");

        if (credential == null || credential.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Faltan datos requeridos");
            return;
        }

        // 1) Verificar el token con Google
        GoogleIdToken idToken;
        try {
            idToken = VERIFIER.verify(credential);
        } catch (GeneralSecurityException | IOException e) {
            System.out.println("[LoginCompradorGoogleServlet] 💥 Error verificando token: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("No se pudo verificar tu cuenta de Google");
            return;
        }
        if (idToken == null || !Boolean.TRUE.equals(idToken.getPayload().getEmailVerified())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Tu cuenta de Google no pudo ser verificada");
            return;
        }

        String correo = idToken.getPayload().getEmail();
        String sub = idToken.getPayload().getSubject();

        // 2) Buscar al usuario por correo
        try (Connection conn = DatabaseConnection.getConnection()) {

            int usuarioId;
            String nombre;
            String correoBD;
            String tipo;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, nombre, correo, tipo FROM Usuarios WHERE correo = ?")) {
                ps.setString(1, correo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        response.getWriter().write(
                                "No encontramos una cuenta con ese correo. Toca \"Registrarme\" para crearla.");
                        return;
                    }
                    usuarioId = rs.getInt("id");
                    nombre = rs.getString("nombre");
                    correoBD = rs.getString("correo");
                    tipo = rs.getString("tipo");
                }
            }

            if (tipo == null || !tipo.equalsIgnoreCase("Comprador")) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write(
                        "Ese correo pertenece a una cuenta de vendedor. Entra como vendedor con tu cédula y contraseña.");
                return;
            }

            // 3) Vincular con Google y crear la sesión larga (no rompe el login si falla)
            // Ambos métodos capturan sus propios errores y nunca lanzan.
            SesionPersistente.vincularGoogle(conn, usuarioId, sub, false);
            SesionPersistente.crear(conn, usuarioId, request, response);

            // 4) Sesión: "cuentaGoogle" solo si la cuenta es solo-Google
            HttpSession session = request.getSession();
            session.setAttribute("usuarioId", usuarioId);
            session.setAttribute("nombreUsuario", nombre);
            session.setAttribute("correoUsuario", correoBD);
            if (esSoloGoogle(conn, usuarioId)) {
                session.setAttribute("cuentaGoogle", true);
            } else {
                session.removeAttribute("cuentaGoogle");
            }

            System.out.println("[LoginCompradorGoogleServlet] ✅ Login con Google, id=" + usuarioId);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("OK");

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error en el servidor");
        }
    }

    /** true si la cuenta se creó con Google (UsuarioGoogle.solo_google = 1). Si falla, false. */
    private static boolean esSoloGoogle(Connection conn, int usuarioId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT solo_google FROM UsuarioGoogle WHERE " + COL_USUARIO + " = ?")) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("solo_google") == 1;
            }
        } catch (SQLException e) {
            System.out.println("[LoginCompradorGoogleServlet] ⚠️ No se pudo leer solo_google: " + e.getMessage());
            return false;
        }
    }
}