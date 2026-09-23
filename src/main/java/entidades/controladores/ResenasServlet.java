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
 * GET  /api/resenas?producto_id=123
 *   -> resumen (promedio, total, distribución 1-5 estrellas) + el listado
 *      de reseñas de ese producto, más nueva primero.
 *
 * POST /api/resenas   (form-urlencoded: producto_id, calificacion, comentario)
 *   -> Crea o actualiza (upsert) la reseña del usuario logueado para ese
 *      producto. Igual que PerfilServlet, el usuario_id SIEMPRE se lee de
 *      la sesión, nunca de un parámetro — así nadie puede calificar en
 *      nombre de otra persona.
 *
 * Requiere la tabla Resenas (ver crear_tabla_resenas.sql) y que exista
 * session.setAttribute("usuarioId", ...), igual que PerfilServlet.
 */
@WebServlet("/api/resenas")
public class ResenasServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");

        String productoIdParam = request.getParameter("producto_id");
        if (productoIdParam == null || productoIdParam.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Falta producto_id\"}");
            return;
        }

        int productoId;
        try {
            productoId = Integer.parseInt(productoIdParam);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"producto_id inválido\"}");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {

            // Trae nombre del usuario junto con cada reseña. El nombre se
            // enmascara del lado del servidor (ej. "Maria Gonzalez" ->
            // "Ma***ez") para no exponer nombres completos, igual que hace
            // Temu con sus reseñas ("Am***ez").
            String sql = "SELECT r.calificacion, r.comentario, r.fecha, u.nombre "
                       + "FROM Resenas r "
                       + "INNER JOIN Usuarios u ON u.id = r.usuario_id "
                       + "WHERE r.producto_id = ? "
                       + "ORDER BY r.fecha DESC";

            List<String> resenasJson = new ArrayList<>();
            int[] distribucion = new int[6]; // índices 1..5
            int total = 0;
            double sumaCalificaciones = 0;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, productoId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int calificacion = rs.getInt("calificacion");
                        String comentario = rs.getString("comentario");
                        String fecha = rs.getString("fecha");
                        String nombre = rs.getString("nombre");

                        total++;
                        sumaCalificaciones += calificacion;
                        if (calificacion >= 1 && calificacion <= 5) {
                            distribucion[calificacion]++;
                        }

                        String nombreEnmascarado = enmascararNombre(nombre);
                        StringBuilder item = new StringBuilder();
                        item.append("{");
                        item.append("\"nombre\":\"").append(escapeJson(nombreEnmascarado)).append("\",");
                        item.append("\"inicial\":\"").append(escapeJson(inicialAvatar(nombre))).append("\",");
                        item.append("\"calificacion\":").append(calificacion).append(",");
                        item.append("\"comentario\":\"").append(escapeJson(comentario)).append("\",");
                        item.append("\"fecha\":\"").append(escapeJson(fecha)).append("\"");
                        item.append("}");
                        resenasJson.add(item.toString());
                    }
                }
            }

            double promedio = total > 0 ? sumaCalificaciones / total : 0;

            try (PrintWriter out = response.getWriter()) {
                out.print("{");
                out.print("\"promedio\":" + String.format("%.1f", promedio) + ",");
                out.print("\"total\":" + total + ",");
                out.print("\"distribucion\":{");
                for (int estrellas = 5; estrellas >= 1; estrellas--) {
                    out.print("\"" + estrellas + "\":" + distribucion[estrellas]);
                    if (estrellas > 1) out.print(",");
                }
                out.print("},");
                out.print("\"resenas\":[" + String.join(",", resenasJson) + "]");
                out.print("}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Error en el servidor\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");

        HttpSession session = request.getSession(false);
        Object usuarioIdObj = (session != null) ? session.getAttribute("usuarioId") : null;

        if (usuarioIdObj == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Debes iniciar sesión para dejar una reseña\"}");
            return;
        }
        int usuarioId = (Integer) usuarioIdObj;

        String productoIdParam = request.getParameter("producto_id");
        String calificacionParam = request.getParameter("calificacion");
        String comentario = request.getParameter("comentario");

        int productoId;
        int calificacion;
        try {
            productoId = Integer.parseInt(productoIdParam);
            calificacion = Integer.parseInt(calificacionParam);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"producto_id o calificacion inválidos\"}");
            return;
        }
        if (calificacion < 1 || calificacion > 5) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"La calificación debe ser entre 1 y 5\"}");
            return;
        }
        if (comentario != null && comentario.length() > 1000) {
            comentario = comentario.substring(0, 1000);
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Upsert manual: ¿ya existe una reseña de este usuario para
            // este producto? Si sí, se actualiza (el usuario "edita" su
            // reseña); si no, se inserta una nueva.
            String sqlBuscar = "SELECT id FROM Resenas WHERE producto_id = ? AND usuario_id = ?";
            Integer resenaExistenteId = null;
            try (PreparedStatement stmt = conn.prepareStatement(sqlBuscar)) {
                stmt.setInt(1, productoId);
                stmt.setInt(2, usuarioId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) resenaExistenteId = rs.getInt("id");
                }
            }

            if (resenaExistenteId != null) {
                String sqlUpdate = "UPDATE Resenas SET calificacion = ?, comentario = ?, fecha = GETDATE() WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sqlUpdate)) {
                    stmt.setInt(1, calificacion);
                    stmt.setString(2, comentario);
                    stmt.setInt(3, resenaExistenteId);
                    stmt.executeUpdate();
                }
            } else {
                String sqlInsert = "INSERT INTO Resenas (producto_id, usuario_id, calificacion, comentario) VALUES (?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sqlInsert)) {
                    stmt.setInt(1, productoId);
                    stmt.setInt(2, usuarioId);
                    stmt.setInt(3, calificacion);
                    stmt.setString(4, comentario);
                    stmt.executeUpdate();
                }
            }

            response.getWriter().write("{\"ok\":true}");
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Error en el servidor\"}");
        }
    }

    /** "Maria Gonzalez" -> "Ma***ez", igual que las reseñas de Temu. */
    private String enmascararNombre(String nombre) {
        if (nombre == null) return "Usuario";
        String limpio = nombre.trim();
        if (limpio.isEmpty()) return "Usuario";
        String primerNombre = limpio.split("\\s+")[0];
        if (primerNombre.length() <= 3) return primerNombre + "***";
        String inicio = primerNombre.substring(0, 2);
        String fin = primerNombre.substring(primerNombre.length() - 2);
        return inicio + "***" + fin;
    }

    /** Primera letra del nombre, para el avatar morado (igual que Temu). */
    private String inicialAvatar(String nombre) {
        if (nombre == null || nombre.isBlank()) return "?";
        return nombre.trim().substring(0, 1).toUpperCase();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "");
    }
}
