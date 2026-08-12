package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;

import entidades.VRegistro;
import entidades.Usuario;
import entidades.EmailService;
import entidades.ValidacionUtil;
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
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                out.print("{\"usuarioId\":-1, \"mensaje\":\"El correo ya está registrado\"}");
                System.out.println("[RegistroCompradorServlet] ⚠️ El correo ya existe.");
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
}