package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import entidades.VRegistro;
import entidades.Usuario;
import entidades.EmailService;
import entidades.ValidacionUtil;
import entidades.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/registroComprador")
public class RegistroCompradorServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        request.setCharacterEncoding("UTF-8");

        // Capturar los datos del formulario
        String nombre = request.getParameter("nombre");
        String correo = request.getParameter("correo");
        String contraseña = request.getParameter("contraseña");
        String tipo = "Comprador";

        try (PrintWriter out = response.getWriter()) {
            if (nombre == null || correo == null || contraseña == null ||
                nombre.isEmpty() || correo.isEmpty() || contraseña.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"usuarioId\":-3, \"mensaje\":\"Faltan datos requeridos\"}");
                System.out.println("[RegistroCompradorServlet] ❌ Faltan datos requeridos");
                return;
            }

            // Crear objeto Usuario y generar hash
            Usuario oUsuario = new Usuario(0, nombre, "", correo, contraseña, tipo, "");
            String contraseñaHash = oUsuario.getContraseña();

            // Validar que el correo tenga formato válido y que el dominio exista
            if (!ValidacionUtil.esCorreoValido(correo)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"usuarioId\":-4, \"mensaje\":\"El correo ingresado no parece ser válido\"}");
                System.out.println("[RegistroCompradorServlet] ❌ Correo inválido o dominio inexistente: " + correo);
                return;
            }

            // Registrar usuario en BD
            VRegistro registro = new VRegistro();
            int usuarioId = registro.registrarUsuario(nombre, correo, contraseñaHash, tipo);

            // Log en consola
            System.out.println("[RegistroCompradorServlet] Resultado del registro:");
            System.out.println("  → Nombre: " + nombre);
            System.out.println("  → Correo: " + correo);
            System.out.println("  → Tipo: " + tipo);
            System.out.println("  → ID generado: " + usuarioId);

            // Respuesta JSON
            if (usuarioId > 0) {
                response.setStatus(HttpServletResponse.SC_OK);
                out.print("{\"usuarioId\":" + usuarioId + "}");
                System.out.println("[RegistroCompradorServlet] ✅ Comprador registrado correctamente.");

                // 📩 Correo de bienvenida (no bloqueante, corre en segundo plano)
                EmailService.enviarBienvenidaComprador(correo, nombre);

            } else if (usuarioId == -1) {
                // 🆕 El correo ya existe. Igual que en RegistroVendedorServlet:
                // si la contraseña enviada coincide (mismo hash) con la
                // guardada, es la misma persona.
                //   - si ya tenía tipo "Comprador", su registro ya estaba
                //     completo desde antes (a diferencia de Vendedor, el
                //     registro de Comprador es un solo paso), así que se le
                //     avisa que inicie sesión en vez de repetir el registro.
                //   - si ya tenía otro tipo (Vendedor), se le explica que
                //     debe borrar su cuenta actual desde su perfil antes de
                //     poder registrarse con el rol distinto.
                // Si la contraseña NO coincide, es otra persona con un
                // correo ajeno: se mantiene el error de siempre.
                try {
                    EstadoCuentaExistente estado = buscarEstadoCuentaExistente(correo, contraseñaHash);

                    if (estado == null) {
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        out.print("{\"usuarioId\":-1, \"mensaje\":\"El correo ya está registrado\"}");
                        System.out.println("[RegistroCompradorServlet] ⚠️ El correo ya existe (contraseña no coincide).");
                    } else if (estado.rolDistinto) {
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        out.print("{\"usuarioId\":-1, \"rolDistinto\":true"
                                + ", \"mensaje\":\"Ya tienes una cuenta registrada con este correo como " + estado.tipoExistente
                                + ". Para registrarte con un rol diferente, primero inicia sesión y elimina tu cuenta actual desde tu perfil.\"}");
                        System.out.println("[RegistroCompradorServlet] ⚠️ Intento de registro con rol distinto para usuario_id=" + estado.usuarioId
                                + " (tipo existente: " + estado.tipoExistente + ")");
                    } else {
                        response.setStatus(HttpServletResponse.SC_OK);
                        out.print("{\"usuarioId\":" + estado.usuarioId
                                + ",\"yaRegistrado\":true"
                                + ",\"mensaje\":\"Ya tienes una cuenta de comprador registrada con este correo.\"}");
                        System.out.println("[RegistroCompradorServlet] 🔁 Cuenta de comprador ya existente reconocida, usuario_id=" + estado.usuarioId);
                    }
                } catch (SQLException e) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.print("{\"usuarioId\":-2, \"mensaje\":\"Error al verificar el registro existente\"}");
                    System.out.println("[RegistroCompradorServlet] 💥 Error verificando cuenta existente: " + e.getMessage());
                }

            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"usuarioId\":-2, \"mensaje\":\"Error al registrar el usuario\"}");
                System.out.println("[RegistroCompradorServlet] ❌ Error al registrar comprador en BD.");
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print(
                "{\"usuarioId\":-2, \"mensaje\":\"Error interno: " + e.getMessage().replace("\"", "'") + "\"}"
            );
            System.out.println("[RegistroCompradorServlet] 💥 Excepción: " + e.getMessage());
        }
    }

    /**
     * 🆕 Busca si el correo pertenece a un usuario existente cuya
     * contraseña (ya hasheada con el mismo método que usa Usuario)
     * coincide con la ingresada, y en ese caso indica si ese usuario ya
     * era Comprador (cuenta ya completa) o de un rol distinto.
     *
     * Devuelve null si el correo no existe, o si existe pero la
     * contraseña NO coincide (en ese caso es otra persona y debe seguir
     * viendo el error de "correo ya registrado").
     */
    private EstadoCuentaExistente buscarEstadoCuentaExistente(String correo, String contraseñaHashIngresada) throws SQLException {
        String sqlUsuario = "SELECT id, contraseña, tipo FROM Usuarios WHERE correo = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {

            int usuarioIdExistente = -1;
            String hashGuardado = null;
            String tipoExistente = null;
            try (PreparedStatement ps = conn.prepareStatement(sqlUsuario)) {
                ps.setString(1, correo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        usuarioIdExistente = rs.getInt("id");
                        hashGuardado = rs.getString("contraseña");
                        tipoExistente = rs.getString("tipo");
                    }
                }
            }

            if (usuarioIdExistente <= 0 || hashGuardado == null || !hashGuardado.equals(contraseñaHashIngresada)) {
                return null;
            }

            boolean esComprador = tipoExistente != null && tipoExistente.equalsIgnoreCase("Comprador");
            return new EstadoCuentaExistente(usuarioIdExistente, !esComprador, tipoExistente);
        }
    }

    private static class EstadoCuentaExistente {
        final int usuarioId;
        final boolean rolDistinto;
        final String tipoExistente;

        EstadoCuentaExistente(int usuarioId, boolean rolDistinto, String tipoExistente) {
            this.usuarioId = usuarioId;
            this.rolDistinto = rolDistinto;
            this.tipoExistente = tipoExistente;
        }
    }
}