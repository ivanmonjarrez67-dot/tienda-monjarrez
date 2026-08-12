package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import entidades.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Devuelve la información del usuario actualmente logueado (nombre, correo,
 * tipo, si es un "Vendedor Destacado", sus intereses de notificación por
 * categoría, y sus provincias de preferencia — ambos leídos de la tabla
 * aparte Intereses, que tiene columnas separadas "interes" y "provincia"
 * (cada fila usa solo una de las dos, la otra queda NULL).
 *
 * IMPORTANTE: lee el usuario_id de la SESIÓN del servidor (HttpSession),
 * nunca de un parámetro que mande el cliente — así nadie puede pedir el
 * perfil de otra persona cambiando un id en la URL.
 *
 * Requiere que exista session.setAttribute("usuarioId", ...) — esto ya lo
 * hacen LoginCompradorServlet y LoginVendedorServlet en el login general
 * del sitio.
 */
@WebServlet("/api/perfil")
public class PerfilServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");

        HttpSession session = request.getSession(false); // false = no crear una nueva
        Object usuarioIdObj = (session != null) ? session.getAttribute("usuarioId") : null;

        if (usuarioIdObj == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"No hay sesión activa\"}");
            return;
        }

        int usuarioId = (Integer) usuarioIdObj;

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT nombre, correo, tipo FROM Usuarios WHERE id = ?";

            String nombre;
            String correo;
            String tipo;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, usuarioId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        response.getWriter().write("{\"error\":\"Usuario no encontrado\"}");
                        return;
                    }

                    nombre = rs.getString("nombre");
                    correo = rs.getString("correo");
                    tipo = rs.getString("tipo");
                }
            }

            boolean esDestacado = false;
            String tipoSuscripcion = null;
            boolean suscrito = false;

            // Si es Vendedor, revisamos su suscripción
            if ("Vendedor".equalsIgnoreCase(tipo)) {
                String sqlVendedor = "SELECT suscrito, tipo_suscripcion FROM Vendedores WHERE usuario_id = ?";
                try (PreparedStatement stmtV = conn.prepareStatement(sqlVendedor)) {
                    stmtV.setInt(1, usuarioId);
                    try (ResultSet rsV = stmtV.executeQuery()) {
                        if (rsV.next()) {
                            int suscritoInt = rsV.getInt("suscrito");
                            suscrito = suscritoInt == 1;
                            tipoSuscripcion = rsV.getString("tipo_suscripcion"); // "Básica" o "Avanzada"
                            esDestacado = suscrito && "Avanzada".equalsIgnoreCase(tipoSuscripcion);
                        }
                    }
                }
            }

            // Leer los intereses (categorías) y las provincias de preferencia
            // del usuario desde la tabla Intereses. Cada fila trae solo una
            // de las dos columnas con valor — la otra viene NULL.
            List<String> intereses = new ArrayList<>();
            List<String> provincias = new ArrayList<>();
            String sqlIntereses = "SELECT interes, provincia FROM Intereses WHERE usuario_id = ?";
            try (PreparedStatement stmtI = conn.prepareStatement(sqlIntereses)) {
                stmtI.setInt(1, usuarioId);
                try (ResultSet rsI = stmtI.executeQuery()) {
                    while (rsI.next()) {
                        String interes = rsI.getString("interes");
                        String provincia = rsI.getString("provincia");
                        if (interes != null) {
                            intereses.add(interes);
                        }
                        if (provincia != null) {
                            provincias.add(provincia);
                        }
                    }
                }
            }

            try (PrintWriter out = response.getWriter()) {
                out.print("{");
                out.print("\"nombre\":\"" + escapeJson(nombre) + "\",");
                out.print("\"correo\":\"" + escapeJson(correo) + "\",");
                out.print("\"tipo\":\"" + escapeJson(tipo) + "\",");
                out.print("\"esDestacado\":" + esDestacado + ",");
                out.print("\"suscrito\":" + suscrito + ",");
                out.print("\"tipoSuscripcion\":" + (tipoSuscripcion != null ? "\"" + escapeJson(tipoSuscripcion) + "\"" : "null") + ",");
                out.print("\"intereses\":" + stringArrayJson(intereses) + ",");
                out.print("\"provincias\":" + stringArrayJson(provincias));
                out.print("}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Error en el servidor\"}");
        }
    }

    /** Convierte una lista de strings en un arreglo JSON: ["Damas","Niños"] */
    private String stringArrayJson(List<String> valores) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(valores.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "");
    }
}
