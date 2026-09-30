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
import entidades.SesionPersistente;
import entidades.Usuario;
import entidades.VRegistro;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Registro / ingreso de COMPRADOR con "Continuar con Google".
 *
 * Recibe por POST solo: credential (el ID token de Google).
 * El tipo "Comprador" se fija aquí, en el servidor.
 *
 * Vincula el usuario con su Google (UsuarioGoogle) y crea la sesión larga.
 * "cuentaGoogle" solo se marca en cuentas creadas con Google (solo_google = 1),
 * así un comprador con contraseña sigue viendo "Cambiar contraseña".
 */
@WebServlet("/registroCompradorGoogle")
public class RegistroCompradorGoogleServlet extends HttpServlet {

    private static final String TIPO = "Comprador";

    // ⚠️ Nombre según tu sesion_persistente.sql. Si difiere, cámbialo solo aquí.
    private static final String COL_USUARIO = "usuario_id";

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

            // 1) Verificar el token con Google
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

            // 2) Solo correos verificados por Google
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"usuarioId\":-4, \"mensaje\":\"Tu correo de Google no está verificado\"}");
                return;
            }

            String correo = payload.getEmail();
            String sub = payload.getSubject();
            String nombre = (String) payload.get("name");
            if (nombre == null || nombre.isBlank()) {
                nombre = correo.substring(0, correo.indexOf('@'));
            }

            try {
                // 3) Crear el usuario con una contraseña aleatoria que nadie conoce
                Usuario oUsuario = new Usuario(0, nombre, "", correo, UUID.randomUUID().toString(), TIPO, "");
                String hashAleatorio = oUsuario.getContraseña();

                VRegistro registro = new VRegistro();
                int usuarioId = registro.registrarUsuario(nombre, correo, hashAleatorio, TIPO);

                if (usuarioId > 0) {
                    iniciarSesion(request, usuarioId, nombre, correo);
                    // Cuenta nueva creada con Google: sin contraseña conocida.
                    vincularYRecordar(request, response, usuarioId, sub, true);
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.print("{\"usuarioId\":" + usuarioId + ",\"sesionIniciada\":true}");
                    System.out.println("[RegistroCompradorGoogleServlet] ✅ Comprador registrado con Google, id=" + usuarioId);

                    EmailService.enviarBienvenidaComprador(correo, nombre);

                } else if (usuarioId == -1) {
                    String[] existente = buscarPorCorreo(correo); // {id, tipo, nombre}

                    if (existente == null) {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        out.print("{\"usuarioId\":-2, \"mensaje\":\"Error al verificar el registro existente\"}");

                    } else if (!existente[1].equalsIgnoreCase(TIPO)) {
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        out.print("{\"usuarioId\":-1, \"rolDistinto\":true"
                                + ", \"mensaje\":\"Ya tienes una cuenta registrada con este correo como " + esc(existente[1])
                                + ". Para registrarte con un rol diferente, primero inicia sesión y elimina tu cuenta actual desde tu perfil.\"}");

                    } else {
                        // Ya era comprador: Google confirmó que es él, entra directo.
                        int idExistente = Integer.parseInt(existente[0]);
                        iniciarSesion(request, idExistente, existente[2], correo);
                        // Vincula con Google sin tocar su contraseña ni sus pedidos.
                        vincularYRecordar(request, response, idExistente, sub, false);
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

    /**
     * Vincula con Google, crea la sesión larga y ajusta "cuentaGoogle" según solo_google.
     * Nunca rompe el login si algo falla.
     */
    private static void vincularYRecordar(HttpServletRequest request, HttpServletResponse response,
                                          int usuarioId, String sub, boolean soloGoogle) {
        boolean marcar = soloGoogle; // valor de respaldo si la consulta falla
        try (Connection conn = DatabaseConnection.getConnection()) {
            SesionPersistente.vincularGoogle(conn, usuarioId, sub, soloGoogle);
            SesionPersistente.crear(conn, usuarioId, request, response);
            marcar = esSoloGoogle(conn, usuarioId, soloGoogle);
        } catch (SQLException e) {
            System.out.println("[RegistroCompradorGoogleServlet] ⚠️ No se pudo guardar la sesión larga: " + e.getMessage());
        }
        HttpSession session = request.getSession();
        if (marcar) {
            session.setAttribute("cuentaGoogle", true);
        } else {
            session.removeAttribute("cuentaGoogle");
        }
    }

    /** true si UsuarioGoogle.solo_google = 1. Si falla la consulta, devuelve el valor de respaldo. */
    private static boolean esSoloGoogle(Connection conn, int usuarioId, boolean respaldo) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT solo_google FROM UsuarioGoogle WHERE " + COL_USUARIO + " = ?")) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("solo_google") == 1 : respaldo;
            }
        } catch (SQLException e) {
            System.out.println("[RegistroCompradorGoogleServlet] ⚠️ No se pudo leer solo_google: " + e.getMessage());
            return respaldo;
        }
    }

    /** Sesión base (sin "cuentaGoogle": se decide después en vincularYRecordar). */
    private static void iniciarSesion(HttpServletRequest request, int usuarioId, String nombre, String correo) {
        HttpSession session = request.getSession();
        session.setAttribute("usuarioId", usuarioId);
        session.setAttribute("nombreUsuario", nombre);
        session.setAttribute("correoUsuario", correo);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}