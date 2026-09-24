package entidades.controladores;

/*
 * Dependencia Maven necesaria (para verificar el token de Google):
 *
 * <dependency>
 *   <groupId>com.google.api-client</groupId>
 *   <artifactId>google-api-client</artifactId>
 *   <version>2.7.0</version>
 * </dependency>
 */

import java.io.IOException;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.UUID;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import config.Config;
import entidades.DatabaseConnection;
import entidades.EmailService;
import entidades.Usuario;
import entidades.VRegistro;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Registro de COMPRADOR con "Continuar con Google".
 *
 * Recibe por POST (x-www-form-urlencoded) solo:
 *   - credential : el ID token (JWT) que entrega Google en el navegador
 *
 * El tipo "Comprador" se fija aquí, en el servidor: el navegador no puede
 * pedir otro rol. Responde el mismo formato JSON que RegistroCompradorServlet
 * y, como Google ya comprobó la identidad, deja la sesión iniciada
 * ("sesionIniciada":true) con los mismos atributos que LoginCompradorServlet.
 */
@WebServlet("/registroCompradorGoogle")
public class RegistroCompradorGoogleServlet extends HttpServlet {

    private static final String TIPO = "Comprador";

    // Client ID de Google: viene de la variable de entorno GOOGLE_CLIENT_ID (Render), vía Config.
    private static final String GOOGLE_CLIENT_ID = Config.GOOGLE_CLIENT_ID;

    private static final GoogleIdTokenVerifier VERIFIER =
            new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(GOOGLE_CLIENT_ID))
                    .build();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        request.setCharacterEncoding("UTF-8");

        String credential = request.getParameter("credential");

        try (PrintWriter out = response.getWriter()) {

            if (credential == null || credential.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"usuarioId\":-3, \"mensaje\":\"Faltan datos requeridos\"}");
                return;
            }

            // 1) Verificar el token con Google (firma, vencimiento y audiencia).
            GoogleIdToken idToken;
            try {
                idToken = VERIFIER.verify(credential);
            } catch (GeneralSecurityException | IOException e) {
                System.out.println("[RegistroCompradorGoogleServlet] 💥 Error verificando token: " + e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print("{\"usuarioId\":-5, \"mensaje\":\"No se pudo verificar tu cuenta de Google\"}");
                return;
            }
            if (idToken == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print("{\"usuarioId\":-5, \"mensaje\":\"Token de Google inválido o vencido\"}");
                return;
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            // 2) Solo se aceptan correos que Google ya verificó.
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"usuarioId\":-4, \"mensaje\":\"Tu correo de Google no está verificado\"}");
                return;
            }

            String correo = payload.getEmail();
            String nombre = (String) payload.get("name");
            if (nombre == null || nombre.isBlank()) {
                nombre = correo.substring(0, correo.indexOf('@'));
            }

            try {
                // 3) Crear el usuario. No hay contraseña: se guarda el hash de una
                //    aleatoria (mismo hash que usa Usuario, así no cambia la tabla).
                //    Nadie la conoce; estas cuentas entran solo con Google.
                Usuario oUsuario = new Usuario(0, nombre, "", correo, UUID.randomUUID().toString(), TIPO, "");
                String hashAleatorio = oUsuario.getContraseña();

                VRegistro registro = new VRegistro();
                int usuarioId = registro.registrarUsuario(nombre, correo, hashAleatorio, TIPO);

                if (usuarioId > 0) {
                    iniciarSesion(request, usuarioId, nombre, correo);
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.print("{\"usuarioId\":" + usuarioId + ",\"sesionIniciada\":true}");
                    System.out.println("[RegistroCompradorGoogleServlet] ✅ Comprador registrado con Google, id=" + usuarioId);

                    // Correo de bienvenida (no bloqueante)
                    EmailService.enviarBienvenidaComprador(correo, nombre);

                } else if (usuarioId == -1) {
                    // El correo ya existe. No se compara contraseña: Google ya
                    // comprobó que la persona es dueña de ese correo.
                    String[] existente = buscarPorCorreo(correo); // {id, tipo, nombre}

                    if (existente == null) {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        out.print("{\"usuarioId\":-2, \"mensaje\":\"Error al verificar el registro existente\"}");

                    } else if (!existente[1].equalsIgnoreCase(TIPO)) {
                        // Ya tiene cuenta con otro rol (Vendedor)
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        out.print("{\"usuarioId\":-1, \"rolDistinto\":true"
                                + ", \"mensaje\":\"Ya tienes una cuenta registrada con este correo como " + esc(existente[1])
                                + ". Para registrarte con un rol diferente, primero inicia sesión y elimina tu cuenta actual desde tu perfil.\"}");

                    } else {
                        // Ya era comprador: Google confirmó que es él, así que entra directo.
                        iniciarSesion(request, Integer.parseInt(existente[0]), existente[2], correo);
                        response.setStatus(HttpServletResponse.SC_OK);
                        out.print("{\"usuarioId\":" + existente[0]
                                + ",\"sesionIniciada\":true"
                                + ",\"yaRegistrado\":true"
                                + ",\"mensaje\":\"Ya tienes una cuenta de comprador registrada con este correo.\"}");
                    }

                } else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.print("{\"usuarioId\":-2, \"mensaje\":\"Error al registrar el usuario\"}");
                }

            } catch (SQLException e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"usuarioId\":-2, \"mensaje\":\"Error al verificar el registro existente\"}");
                System.out.println("[RegistroCompradorGoogleServlet] 💥 SQL: " + e.getMessage());
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"usuarioId\":-2, \"mensaje\":\"Error interno\"}");
            System.out.println("[RegistroCompradorGoogleServlet] 💥 Excepción: " + e.getMessage());
        }
    }

    /** Devuelve {id, tipo, nombre} del usuario con ese correo, o null si no existe. */
    private String[] buscarPorCorreo(String correo) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, tipo, nombre FROM Usuarios WHERE correo = ?")) {
            ps.setString(1, correo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString("tipo") != null) {
                    return new String[] { String.valueOf(rs.getInt("id")), rs.getString("tipo"), rs.getString("nombre") };
                }
            }
        }
        return null;
    }

    /** Misma sesión que crea LoginCompradorServlet. */
    private static void iniciarSesion(HttpServletRequest request, int usuarioId, String nombre, String correo) {
        HttpSession session = request.getSession();
        session.setAttribute("usuarioId", usuarioId);
        session.setAttribute("nombreUsuario", nombre);
        session.setAttribute("correoUsuario", correo);
        session.setAttribute("cuentaGoogle", true); // PerfilServlet lo usa para esconder "Cambiar contraseña"
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}