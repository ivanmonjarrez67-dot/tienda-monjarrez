package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import entidades.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

@WebServlet("/MisProductosServlet")
public class MisProductosServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // 🆕 Misma consulta base usada en doGet y doPost: además de los campos
    // de siempre, trae precio_anterior (rebaja) e imagen2/imagen3
    // (fotos adicionales) vía LEFT JOIN — antes este servlet no las traía,
    // así que en "Mi tienda" nunca se veían aunque el producto sí las
    // tuviera guardadas. También trae pe.producto_id (LEFT JOIN con
    // ProductosExtranjeros) para la nota de "producto internacional".
    private static final String SQL_PRODUCTOS =
        "SELECT p.id, p.nombre, p.descripcion, p.imagen, p.precio, p.Nombre_Empresa, p.categoria, "
      + "p.telefono, p.correo, p.provincia, p.ciudad, pe.producto_id AS extranjero_id, "
      + "d.precio_anterior, ia.imagen2, ia.imagen3 "
      + "FROM Productos p "
      + "LEFT JOIN Descuentos d ON d.producto_id = p.id "
      + "LEFT JOIN ImagenesAdicionalesProducto ia ON ia.producto_id = p.id "
      + "LEFT JOIN ProductosExtranjeros pe ON pe.producto_id = p.id "
      + "WHERE p.usuario_id = ? ORDER BY p.id DESC";

    // 🆕 Escribe una fila de producto en el JSON, con todos los campos
    // (antes duplicado a mano en doGet y doPost, ahora un solo lugar).
    private void escribirProducto(PrintWriter out, ResultSet rs) throws java.sql.SQLException {
        out.print("  {");
        out.print("\"id\":" + rs.getInt("id") + ",");
        out.print("\"nombre\":\"" + escapeJson(rs.getString("nombre")) + "\",");
        out.print("\"descripcion\":\"" + escapeJson(rs.getString("descripcion")) + "\",");
        out.print("\"imagen\":\"" + escapeJson(rs.getString("imagen")) + "\",");
        out.print("\"precio\":" + rs.getDouble("precio") + ",");
        out.print("\"empresa\":\"" + escapeJson(rs.getString("Nombre_Empresa")) + "\",");
        out.print("\"categoria\":\"" + escapeJson(rs.getString("categoria")) + "\",");
        out.print("\"telefono\":\"" + escapeJson(rs.getString("telefono")) + "\",");
        out.print("\"correo\":\"" + escapeJson(rs.getString("correo")) + "\",");
        out.print("\"provincia\":\"" + escapeJson(rs.getString("provincia")) + "\",");
        out.print("\"ciudad\":\"" + escapeJson(rs.getString("ciudad")) + "\",");
        out.print("\"es_extranjero\":" + (rs.getObject("extranjero_id") != null) + ",");

        double precioAnterior = rs.getDouble("precio_anterior");
        out.print("\"precio_anterior\":" + (rs.wasNull() ? "null" : precioAnterior) + ",");
        String imagen2 = rs.getString("imagen2");
        out.print("\"imagen2\":" + (imagen2 == null ? "null" : "\"" + escapeJson(imagen2) + "\"") + ",");
        String imagen3 = rs.getString("imagen3");
        out.print("\"imagen3\":" + (imagen3 == null ? "null" : "\"" + escapeJson(imagen3) + "\""));
        out.print("}");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");

        String usuarioIdParam = request.getParameter("usuario_id");

        if (usuarioIdParam == null || usuarioIdParam.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print("{\"error\":\"Falta usuario_id\"}");
            return;
        }

        try {
            int usuarioId = Integer.parseInt(usuarioIdParam);

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SQL_PRODUCTOS)) {

                stmt.setInt(1, usuarioId);

                try (ResultSet rs = stmt.executeQuery();
                     PrintWriter out = response.getWriter()) {

                    out.println("[");
                    boolean first = true;

                    while (rs.next()) {
                        if (!first) out.println(",");
                        first = false;
                        escribirProducto(out, rs);
                    }

                    out.println("]");
                }
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print("{\"error\":\"usuario_id inválido\"}");
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");

        // ✅ Recibir la cédula y contraseña desde el frontend
        String cedula = request.getParameter("cedula");
        String contraseña = request.getParameter("password");

        if (cedula == null || cedula.isEmpty() || contraseña == null || contraseña.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print("{\"error\":\"Debe ingresar la cédula y la contraseña\"}");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String hashedPassword = hashContraseña(contraseña);

            // 🔹 Buscar el usuario_id correspondiente a esa cédula y contraseña
            String sqlUsuario = """
                SELECT u.id AS usuario_id
                FROM Usuarios u
                INNER JOIN Vendedores v ON v.usuario_id = u.id
                WHERE v.cedula = ?
                  AND u.contraseña = ?
                  AND u.tipo = 'Vendedor'
            """;

            int usuarioId = -1;

            try (PreparedStatement psUser = conn.prepareStatement(sqlUsuario)) {
                psUser.setString(1, cedula);
                psUser.setString(2, hashedPassword);

                try (ResultSet rs = psUser.executeQuery()) {
                    if (rs.next()) {
                        usuarioId = rs.getInt("usuario_id");
                    } else {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().print("{\"error\":\"Credenciales incorrectas\"}");
                        return;
                    }
                }
            }

            // 🔹 Obtener los productos asociados al usuario_id (misma
            // consulta con JOINs que doGet, para que este flujo también
            // traiga rebaja/fotos adicionales/bandera de internacional).
            try (PreparedStatement stmt = conn.prepareStatement(SQL_PRODUCTOS)) {

                stmt.setInt(1, usuarioId);

                try (ResultSet rs = stmt.executeQuery();
                     PrintWriter out = response.getWriter()) {

                    out.println("[");
                    boolean first = true;

                    while (rs.next()) {
                        if (!first) out.println(",");
                        first = false;
                        escribirProducto(out, rs);
                    }

                    out.println("]");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    // 🔹 Encriptar la contraseña igual que el login
    private String hashContraseña(String contraseña) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contraseña.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // 🔹 Escapar caracteres especiales JSON
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
    }
}