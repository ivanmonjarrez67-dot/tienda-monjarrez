package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import entidades.DatabaseConnection;
import entidades.JsonUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/productos")
public class ListaProductosServlet extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT nombre, descripcion, imagen, precio, Nombre_Empresa, telefono, correo, provincia, ciudad FROM Productos")) {

            PrintWriter out = response.getWriter();
            out.println("[");

            boolean first = true;
            while (rs.next()) {
                if (!first) out.println(",");
                first = false;

                out.print("  {");
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