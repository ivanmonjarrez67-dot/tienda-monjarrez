package entidades.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import entidades.DatabaseConnection;
import entidades.JsonUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

// Teléfono y dirección de entrega del usuario logueado (Comprador o
// Vendedor comprando). Se leen/guardan en la tabla DatosEnvioUsuario.
// Igual que /api/perfil, la identidad sale de la SESIÓN, nunca del cliente.
//
// GET  /api/perfil/envio                       -> {"telefono":..., "direccion":...}
// POST /api/perfil/envio (telefono, direccion) -> guarda (si ambos vienen vacíos, borra)
@WebServlet("/api/perfil/envio")
public class PerfilEnvioServlet extends HttpServlet {

    private Integer usuarioIdDeSesion(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object id = session.getAttribute("usuarioId");
        return (id instanceof Integer) ? (Integer) id : null;
    }

    /** Deja solo dígitos (y un + inicial). Devuelve null si no es un teléfono razonable (8 a 15 dígitos). */
    static String limpiarTelefono(String crudo) {
        if (crudo == null) return null;
        String soloDigitos = crudo.replaceAll("\\D", "");
        if (soloDigitos.length() < 8 || soloDigitos.length() > 15) return null;
        return soloDigitos;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        Integer usuarioId = usuarioIdDeSesion(request);
        if (usuarioId == null) {
            response.setStatus(401);
            response.getWriter().print("{\"error\":\"No hay sesión activa\"}");
            return;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT telefono, direccion FROM DatosEnvioUsuario WHERE usuario_id = ?")) {
            stmt.setInt(1, usuarioId);
            String telefono = null, direccion = null;
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    telefono = rs.getString("telefono");
                    direccion = rs.getString("direccion");
                }
            }
            response.getWriter().print("{"
                    + "\"telefono\":" + (telefono == null ? "null" : "\"" + JsonUtils.escapar(telefono) + "\"") + ","
                    + "\"direccion\":" + (direccion == null ? "null" : "\"" + JsonUtils.escapar(direccion) + "\"")
                    + "}");
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            response.getWriter().print("{\"error\":\"Error en el servidor\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        Integer usuarioId = usuarioIdDeSesion(request);
        if (usuarioId == null) {
            response.setStatus(401);
            response.getWriter().print("{\"error\":\"No hay sesión activa\"}");
            return;
        }

        String telefonoCrudo = request.getParameter("telefono");
        String direccion = request.getParameter("direccion");
        boolean telVacio = telefonoCrudo == null || telefonoCrudo.trim().isEmpty();
        boolean dirVacia = direccion == null || direccion.trim().isEmpty();

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (telVacio && dirVacia) {
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM DatosEnvioUsuario WHERE usuario_id = ?")) {
                    del.setInt(1, usuarioId);
                    del.executeUpdate();
                }
                response.getWriter().print("{\"ok\":true}");
                return;
            }

            String telefono = null;
            if (!telVacio) {
                telefono = limpiarTelefono(telefonoCrudo);
                if (telefono == null) {
                    response.setStatus(400);
                    response.getWriter().print("{\"error\":\"El teléfono no es válido (mínimo 8 dígitos).\"}");
                    return;
                }
            }
            if (!dirVacia) {
                direccion = direccion.trim();
                if (direccion.length() > 400) {
                    response.setStatus(400);
                    response.getWriter().print("{\"error\":\"La dirección es demasiado larga (máx. 400 caracteres).\"}");
                    return;
                }
            } else {
                direccion = null;
            }

            try (PreparedStatement upsert = conn.prepareStatement(
                    "MERGE DatosEnvioUsuario AS d "
                  + "USING (SELECT ? AS usuario_id) AS o ON d.usuario_id = o.usuario_id "
                  + "WHEN MATCHED THEN UPDATE SET telefono = ?, direccion = ?, fecha_actualizacion = SYSDATETIME() "
                  + "WHEN NOT MATCHED THEN INSERT (usuario_id, telefono, direccion) VALUES (?, ?, ?);")) {
                upsert.setInt(1, usuarioId);
                upsert.setString(2, telefono);
                upsert.setString(3, direccion);
                upsert.setInt(4, usuarioId);
                upsert.setString(5, telefono);
                upsert.setString(6, direccion);
                upsert.executeUpdate();
            }
            response.getWriter().print("{\"ok\":true}");
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            response.getWriter().print("{\"error\":\"Error en el servidor\"}");
        }
    }
}