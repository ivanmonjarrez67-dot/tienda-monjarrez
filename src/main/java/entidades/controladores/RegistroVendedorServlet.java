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
                out.print("{\"usuarioId\":-3, \"mensaje\":\"Faltan datos requeridos\"}");
                System.out.println("[RegistroVendedorServlet] ❌ Faltan datos requeridos en el formulario");
                return;
            }

            // Crear objeto Usuario
            Usuario oUsuario = new Usuario(0, nombre, "", correo, contraseña, tipo, "");
            String contraseñaHash = oUsuario.getContraseña();

            // Validar que el correo tenga formato válido y que el dominio exista
            if (!ValidacionUtil.esCorreoValido(correo)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"usuarioId\":-4, \"mensaje\":\"El correo ingresado no parece ser válido\"}");
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

            if (usuarioId > 0) {
                response.setStatus(HttpServletResponse.SC_OK);
                out.print("{\"usuarioId\":" + usuarioId + "}");
                System.out.println("[RegistroVendedorServlet] ✅ Usuario registrado correctamente.");

                // 📩 Correo de bienvenida (no bloqueante, corre en segundo plano)
                EmailService.enviarBienvenidaVendedor(correo, nombre);

            } else if (usuarioId == -1) {
                // 🆕 El correo ya existe. Antes esto terminaba siempre en un
                // error para el usuario, aunque fuera él mismo retomando un
                // registro que dejó a medias (por ejemplo envió la Solicitud
                // pero nunca llegó a la Suscripción). Ahora, si la contraseña
                // enviada coincide (mismo hash) con la guardada, asumimos que
                // es la misma persona y le devolvemos su usuario_id real más
                // el estado de qué pasos ya completó, para que el frontend lo
                // mande directo al paso que le falta en vez de bloquearlo.
                // Si la contraseña NO coincide, es otra persona intentando
                // usar un correo ajeno: se mantiene el error de siempre, así
                // que la protección contra duplicados no se pierde.
                try {
                    EstadoReanudacion estado = buscarEstadoReanudacion(correo, contraseñaHash);

                    if (estado != null) {
                        response.setStatus(HttpServletResponse.SC_OK);
                        out.print("{\"usuarioId\":" + estado.usuarioId
                                + ",\"reanudado\":true"
                                + ",\"solicitudEnviada\":" + estado.solicitudEnviada
                                + ",\"suscripcionEnviada\":" + estado.suscripcionEnviada
                                + ",\"mensaje\":\"Retomando un registro que había quedado pendiente.\"}");
                        System.out.println("[RegistroVendedorServlet] 🔁 Registro incompleto reanudado para usuario_id=" + estado.usuarioId
                                + " (solicitudEnviada=" + estado.solicitudEnviada
                                + ", suscripcionEnviada=" + estado.suscripcionEnviada + ")");
                    } else {
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        out.print("{\"usuarioId\":-1, \"mensaje\":\"El correo ya está registrado\"}");
                        System.out.println("[RegistroVendedorServlet] ⚠️ El correo ya existe en la base de datos (contraseña no coincide).");
                    }
                } catch (SQLException e) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.print("{\"usuarioId\":-2, \"mensaje\":\"Error al verificar el registro existente\"}");
                    System.out.println("[RegistroVendedorServlet] 💥 Error verificando reanudación: " + e.getMessage());
                }

            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"usuarioId\":-2, \"mensaje\":\"Error al registrar el usuario\"}");
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

    /**
     * 🆕 Busca si el correo pertenece a un usuario existente cuya
     * contraseña (ya hasheada con el mismo método que usa Usuario)
     * coincide con la ingresada. Si coincide, arma el estado de qué pasos
     * del registro de vendedor ya completó (Solicitud / Suscripción) para
     * que el frontend pueda saltar directo al que falta.
     *
     * Devuelve null si el correo no existe, o si existe pero la
     * contraseña NO coincide (en ese caso es otra persona y debe seguir
     * viendo el error de "correo ya registrado").
     */
    private EstadoReanudacion buscarEstadoReanudacion(String correo, String contraseñaHashIngresada) throws SQLException {
        String sqlUsuario = "SELECT id, contraseña FROM Usuarios WHERE correo = ?";
        String sqlSolicitud = "SELECT COUNT(*) FROM SolicitudesDeVendedor WHERE usuario_id = ?";
        String sqlVendedor = "SELECT COUNT(*) FROM Vendedores WHERE usuario_id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {

            int usuarioIdExistente = -1;
            String hashGuardado = null;
            try (PreparedStatement ps = conn.prepareStatement(sqlUsuario)) {
                ps.setString(1, correo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        usuarioIdExistente = rs.getInt("id");
                        hashGuardado = rs.getString("contraseña");
                    }
                }
            }

            if (usuarioIdExistente <= 0 || hashGuardado == null || !hashGuardado.equals(contraseñaHashIngresada)) {
                return null;
            }

            boolean solicitudEnviada;
            try (PreparedStatement ps = conn.prepareStatement(sqlSolicitud)) {
                ps.setInt(1, usuarioIdExistente);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    solicitudEnviada = rs.getInt(1) > 0;
                }
            }

            boolean suscripcionEnviada;
            try (PreparedStatement ps = conn.prepareStatement(sqlVendedor)) {
                ps.setInt(1, usuarioIdExistente);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    suscripcionEnviada = rs.getInt(1) > 0;
                }
            }

            return new EstadoReanudacion(usuarioIdExistente, solicitudEnviada, suscripcionEnviada);
        }
    }

    private static class EstadoReanudacion {
        final int usuarioId;
        final boolean solicitudEnviada;
        final boolean suscripcionEnviada;

        EstadoReanudacion(int usuarioId, boolean solicitudEnviada, boolean suscripcionEnviada) {
            this.usuarioId = usuarioId;
            this.solicitudEnviada = solicitudEnviada;
            this.suscripcionEnviada = suscripcionEnviada;
        }
    }
}