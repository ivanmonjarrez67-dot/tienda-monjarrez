package entidades.controladores;


import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import entidades.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;





@WebServlet("/api/productos-mi-tienda")
public class ProductosMiTiendaServlet extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String cedula = request.getParameter("cedula");
        response.setContentType("application/json;charset=UTF-8");

        // 🔧 Se agregó "id" al SELECT y al JSON de salida (mismo motivo
        // que en los otros servlets de listado de productos).
        String sql = "SELECT id, nombre, descripcion, imagen, precio, Nombre_Empresa, telefono, correo, provincia, ciudad " +
                     "FROM Productos WHERE Cedula_Vendedor=?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, cedula);
            ResultSet rs = stmt.executeQuery();

            PrintWriter out = response.getWriter();
            out.println("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) out.println(",");
                first = false;

                out.print("  {");
                out.print("\"id\":" + rs.getInt("id") + ",");
                out.print("\"nombre\":\"" + rs.getString("nombre") + "\",");
                out.print("\"descripcion\":\"" + rs.getString("descripcion") + "\",");
                out.print("\"imagen\":\"" + rs.getString("imagen") + "\",");
                out.print("\"precio\":" + rs.getDouble("precio") + ",");
                out.print("\"empresa\":\"" + rs.getString("Nombre_Empresa") + "\",");
                out.print("\"telefono\":\"" + rs.getString("telefono") + "\",");
                out.print("\"correo\":\"" + rs.getString("correo") + "\",");
                out.print("\"provincia\":\"" + rs.getString("provincia") + "\",");
                out.print("\"ciudad\":\"" + rs.getString("ciudad") + "\"");
                out.print("}");
            }
            out.println("]");

        } catch (Exception e) {
            e.printStackTrace(response.getWriter());
        }
    }
}