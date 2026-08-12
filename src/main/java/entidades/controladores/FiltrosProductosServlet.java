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

@WebServlet("/api/productos-filtrados")
public class FiltrosProductosServlet extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String filtro = request.getParameter("filtro");
        String categoria = request.getParameter("categoria");

        response.setContentType("application/json;charset=UTF-8");

        String sql = "SELECT nombre, descripcion, imagen, precio, Nombre_Empresa, telefono, correo, provincia, ciudad FROM Productos";

        List<String> condiciones = new ArrayList<>();
        List<String> parametros = new ArrayList<>();

        if (categoria != null && !categoria.equalsIgnoreCase("Todo") && !categoria.trim().isEmpty()) {
            condiciones.add("categoria = ?");
            parametros.add(categoria);
        }

        // Agrega ORDER BY según el filtro seleccionado
        if ("Novedades".equalsIgnoreCase(filtro)) {
            sql += condiciones.isEmpty() ? " ORDER BY id DESC"
                    : " WHERE " + String.join(" AND ", condiciones) + " ORDER BY id DESC";
        } else if ("Descuentos".equalsIgnoreCase(filtro)) {
            sql += condiciones.isEmpty() ? " ORDER BY precio ASC"
                    : " WHERE " + String.join(" AND ", condiciones) + " ORDER BY precio ASC";
        } else if ("Recomendados".equalsIgnoreCase(filtro)) {
            sql += condiciones.isEmpty() ? " ORDER BY precio DESC"
                    : " WHERE " + String.join(" AND ", condiciones) + " ORDER BY precio DESC";
        } else {
            sql += condiciones.isEmpty() ? " ORDER BY nombre"
                    : " WHERE " + String.join(" AND ", condiciones) + " ORDER BY nombre";
        }

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
                if (!first)
                    out.println(",");
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