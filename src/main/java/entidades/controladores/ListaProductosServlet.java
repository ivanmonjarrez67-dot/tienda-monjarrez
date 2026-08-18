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

        // 🆕 LEFT JOIN con Descuentos (precio_anterior, para el tachado de
        // rebajas) e ImagenesAdicionalesProducto (imagen2/imagen3, hasta 2
        // fotos extra). Son LEFT JOIN a propósito: la enorme mayoría de
        // productos no van a tener fila en ninguna de las 2 tablas nuevas,
        // y en ese caso los campos simplemente llegan NULL.
        String sql = "SELECT p.id, p.nombre, p.descripcion, p.imagen, p.precio, p.Nombre_Empresa, "
                   + "p.telefono, p.correo, p.provincia, p.ciudad, "
                   + "d.precio_anterior, ia.imagen2, ia.imagen3 "
                   + "FROM Productos p "
                   + "LEFT JOIN Descuentos d ON d.producto_id = p.id "
                   + "LEFT JOIN ImagenesAdicionalesProducto ia ON ia.producto_id = p.id";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

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
                out.print("\"ciudad\":\"" + JsonUtils.escapar(rs.getString("ciudad")) + "\",");

                double precioAnterior = rs.getDouble("precio_anterior");
                out.print("\"precio_anterior\":" + (rs.wasNull() ? "null" : precioAnterior) + ",");
                String imagen2 = rs.getString("imagen2");
                out.print("\"imagen2\":" + (imagen2 == null ? "null" : "\"" + JsonUtils.escapar(imagen2) + "\"") + ",");
                String imagen3 = rs.getString("imagen3");
                out.print("\"imagen3\":" + (imagen3 == null ? "null" : "\"" + JsonUtils.escapar(imagen3) + "\""));

                out.print("}");
            }

            out.println("]");
        } catch (Exception e) {
            e.printStackTrace(response.getWriter());
        }
    }
}