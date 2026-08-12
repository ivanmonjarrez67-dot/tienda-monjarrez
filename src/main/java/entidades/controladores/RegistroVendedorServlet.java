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

@WebServlet("/registroVendedor")
public class RegistroVendedorServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        request.setCharacterEncoding("UTF-8");

        // Capturar los datos enviados por formulario (x-www-form-urlencoded)
        String nombre = request.getParameter("nombre");
        String correo = request.getParameter("correo");
        String contraseña = request.getParameter("contraseña");
        String tipo = "Vendedor";

        try (PrintWriter out = response.getWriter()) {
            if (nombre == null || correo == null || contraseña == null ||
                nombre.isEmpty() || correo.isEmpty() || contraseña.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"usuario_id\":-3, \"mensaje\":\"Faltan datos requeridos\"}");
                System.out.println("[RegistroVendedorServlet] ❌ Faltan datos requeridos en el formulario");
                return;
            }

            // Crear objeto Usuario
            Usuario oUsuario = new Usuario(0, nombre, "", correo, contraseña, tipo, "");
            String contraseñaHash = oUsuario.getContraseña();

            // Validar que el correo tenga formato válido y que el dominio exista
            if (!ValidacionUtil.esCorreoValido(correo)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"usuario_id\":-4, \"mensaje\":\"El correo ingresado no parece ser válido\"}");
                System.out.println("[RegistroVendedorServlet] ❌ Correo inválido o dominio inexistente: " + correo);
                return;
            }

            // Registrar usuario en la BD
            VRegistro registro = new VRegistro();
            int usuarioId = registro.registrarUsuario(nombre, correo, contraseñaHash, tipo);

            // Log en consola para verificar el flujo
            System.out.println("[RegistroVendedorServlet] Resultado del registro:");
            System.out.println("  → Nombre: " + nombre);
            System.out.println("  → Correo: " + correo);
            System.out.println("  → Tipo: " + tipo);
            System.out.println("  → ID generado: " + usuarioId);

            // Enviar respuesta JSON manual (en formato compatible con JS)
           if (usuarioId > 0) {
    response.setStatus(HttpServletResponse.SC_OK);
    out.print("{\"usuarioId\":" + usuarioId + "}"); // 👈 corregido
    System.out.println("[RegistroVendedorServlet] ✅ Usuario registrado correctamente.");

    // 📩 Correo de bienvenida (no bloqueante, corre en segundo plano)
    EmailService.enviarBienvenidaVendedor(correo, nombre);

} else if (usuarioId == -1) {
    response.setStatus(HttpServletResponse.SC_CONFLICT);
    out.print("{\"usuarioId\":-1, \"mensaje\":\"El correo ya está registrado\"}"); // 👈 corregido
    System.out.println("[RegistroVendedorServlet] ⚠️ El correo ya existe en la base de datos.");
} else {
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    out.print("{\"usuarioId\":-2, \"mensaje\":\"Error al registrar el usuario\"}"); // 👈 corregido
    System.out.println("[RegistroVendedorServlet] ❌ Error al registrar usuario en la BD.");
}


        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print(
                "{\"usuario_id\":-2, \"mensaje\":\"Error interno: " + e.getMessage().replace("\"", "'") + "\"}"
            );
            System.out.println("[RegistroVendedorServlet] 💥 Excepción: " + e.getMessage());
        }
    }
}