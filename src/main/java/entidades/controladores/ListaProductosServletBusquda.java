package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import entidades.DatabaseConnection;
import entidades.JsonUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/busqueda-productos")
public class ListaProductosServletBusquda extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        String busqueda = request.getParameter("q");

        List<String> condiciones = new ArrayList<>();
        List<String> parametros = new ArrayList<>();

        if (busqueda != null && !busqueda.trim().isEmpty()) {
            condiciones.add("(nombre LIKE ? OR descripcion LIKE ? OR Nombre_Empresa LIKE ? OR provincia LIKE ?)");
            String valor = "%" + busqueda.trim() + "%";
            parametros.add(valor);
            parametros.add(valor);
            parametros.add(valor);
            parametros.add(valor);
        }

        // 🔧 Se agregó "id" al SELECT y al JSON de salida (mismo motivo
        // que en ListaProductosServlet: sin esto el botón "Compartir" no
        // se genera para los productos que aparecen tras una búsqueda).
        String sql = "SELECT id, nombre, descripcion, imagen, precio, Nombre_Empresa, telefono, correo, provincia, ciudad FROM Productos";

        if (!condiciones.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", condiciones);
        }

        sql += " ORDER BY nombre";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < parametros.size(); i++) {
                stmt.setString(i + 1, parametros.get(i));
            }

            ResultSet rs = stmt.executeQuery();
            PrintWriter out = response.getWriter();
            out.println("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) out.println(",");
                first = false;

                out.print("  {");
                out.print("\"id\":" + rs.getInt("id") + ",");
                out.print("\"nombre\":\"" + JsonUtils.escapar(rs.getString("nombre")) + "\",");
                out.print("\"descripcion\":\"" + JsonUtils.escapar(rs.getString("descripcion")) + "\",");
                out.print("\"imagen\":\"" + JsonUtils.escapar(rs.getString("imagen")) + "\",");
                out.print("\"precio\":" + rs.getDouble("precio") + ",");
                out.print("\"empresa\":\"" + JsonUtils.escapar(rs.getString("Nombre_Empresa")) + "\",");
                out.print("\"telefono\":\"" + JsonUtils.escapar(rs.getString("telefono")) + "\",");
                out.print("\"correo\":\"" + JsonUtils.escapar(rs.getString("correo")) + "\",");
                out.print("\"provincia\":\"" + JsonUtils.escapar(rs.getString("provincia")) + "\",");
                out.print("\"ciudad\":\"" + JsonUtils.escapar(rs.getString("ciudad")) + "\"");
                out.print("}");
            }

            out.println("]");
        } catch (Exception e) {
            e.printStackTrace(response.getWriter());
        }
    }
}