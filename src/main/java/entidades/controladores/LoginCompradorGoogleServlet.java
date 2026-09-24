package entidades.controladores;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import config.Config;
import entidades.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Login de COMPRADOR con "Continuar con Google".
 *
 * Recibe por POST: credential (el ID token de Google).
 * Responde igual que LoginCompradorServlet: 200 + "OK" si entra, o un
 * texto de error con el código correspondiente. Crea la sesión con los
 * mismos atributos que el login normal.
 */
@WebServlet("/LoginCompradorGoogleServlet")
public class LoginCompradorGoogleServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // Client ID de Google: viene de la variable de entorno GOOGLE_CLIENT_ID (Render), vía Config.
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

        // 1) Verificar el token con Google (firma, vencimiento y audiencia)
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

        // 2) Buscar al usuario por correo (Google ya comprobó que es el dueño)
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, nombre, correo, tipo FROM Usuarios WHERE correo = ?")) {

            ps.setString(1, correo);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.getWriter().write(
                            "No encontramos una cuenta con ese correo. Toca \"Registrarme\" para crearla.");
                    return;
                }

                String tipo = rs.getString("tipo");
                if (tipo == null || !tipo.equalsIgnoreCase("Comprador")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write(
                            "Ese correo pertenece a una cuenta de vendedor. Entra como vendedor con tu cédula y contraseña.");
                    return;
                }

                // 3) Misma sesión que crea LoginCompradorServlet
                HttpSession session = request.getSession();
                session.setAttribute("usuarioId", rs.getInt("id"));
                session.setAttribute("nombreUsuario", rs.getString("nombre"));
                session.setAttribute("correoUsuario", rs.getString("correo"));
                session.setAttribute("cuentaGoogle", true); // PerfilServlet lo usa para esconder "Cambiar contraseña"

                System.out.println("[LoginCompradorGoogleServlet] ✅ Login con Google, id=" + rs.getInt("id"));
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write("OK");
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error en el servidor");
        }
    }
}