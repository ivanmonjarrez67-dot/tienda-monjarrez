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

        // 🆕 LEFT JOIN con ProductosExtranjeros: tabla nueva, solo con
        // producto_id (sin columna extra en Productos). Si existe fila,
        // el producto se marca como internacional; si no, "pe.producto_id"
        // llega NULL y se traduce a "false" al armar el JSON.
        String sql = "SELECT p.id, p.nombre, p.descripcion, p.imagen, p.precio, p.Nombre_Empresa, "
                   + "p.telefono, p.correo, p.provincia, p.ciudad, pe.producto_id AS extranjero_id, "
                   + "d.precio_anterior, ia.imagen2, ia.imagen3 "
                   + "FROM Productos p "
                   + "LEFT JOIN Descuentos d ON d.producto_id = p.id "
                   + "LEFT JOIN ImagenesAdicionalesProducto ia ON ia.producto_id = p.id "
                   + "LEFT JOIN ProductosExtranjeros pe ON pe.producto_id = p.id "
                   + "ORDER BY NEWID()";

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
                out.print("\"es_extranjero\":" + (rs.getObject("extranjero_id") != null) + ",");

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