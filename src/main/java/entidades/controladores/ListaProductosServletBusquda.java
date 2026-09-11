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
            condiciones.add("(p.nombre LIKE ? OR p.descripcion LIKE ? OR p.Nombre_Empresa LIKE ? OR p.provincia LIKE ?)");
            String valor = "%" + busqueda.trim() + "%";
            parametros.add(valor);
            parametros.add(valor);
            parametros.add(valor);
            parametros.add(valor);
        }

        // 🆕 Mismo LEFT JOIN que ListaProductosServlet, para traer también
        // precio_anterior/imagen2/imagen3 en los resultados de búsqueda.
        // 🆕 LEFT JOIN con ProductosExtranjeros (tabla nueva, solo
        // producto_id) para la bandera de "producto internacional".
        // 🆕 Se agrega p.categoria al SELECT, por el mismo motivo que en
        // ListaProductosServlet: para que el producto (venga de una
        // búsqueda o del catálogo completo) siempre traiga su categoría.
        // 🆕 Se agrega p.usuario_id y el mismo enlace Productos ->
        // Vendedores -> IconosVendedor que en ListaProductosServlet, para
        // que los resultados de búsqueda también traigan el icono del
        // vendedor.
        String sql = "SELECT p.id, p.nombre, p.descripcion, p.imagen, p.precio, p.Nombre_Empresa, "
                   + "p.telefono, p.correo, p.provincia, p.ciudad, p.categoria, p.usuario_id, "
                   + "pe.producto_id AS extranjero_id, "
                   + "d.precio_anterior, ia.imagen2, ia.imagen3, iv.icono AS icono_vendedor "
                   + "FROM Productos p "
                   + "LEFT JOIN Descuentos d ON d.producto_id = p.id "
                   + "LEFT JOIN ImagenesAdicionalesProducto ia ON ia.producto_id = p.id "
                   + "LEFT JOIN ProductosExtranjeros pe ON pe.producto_id = p.id "
                   + "LEFT JOIN Vendedores v ON v.usuario_id = p.usuario_id "
                   + "LEFT JOIN IconosVendedor iv ON iv.vendedor_id = v.id";

        if (!condiciones.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", condiciones);
        }

        sql += " ORDER BY p.nombre";

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
                out.print("\"ciudad\":\"" + JsonUtils.escapar(rs.getString("ciudad")) + "\",");
                out.print("\"categoria\":\"" + JsonUtils.escapar(rs.getString("categoria")) + "\",");
                out.print("\"es_extranjero\":" + (rs.getObject("extranjero_id") != null) + ",");

                double precioAnterior = rs.getDouble("precio_anterior");
                out.print("\"precio_anterior\":" + (rs.wasNull() ? "null" : precioAnterior) + ",");
                String imagen2 = rs.getString("imagen2");
                out.print("\"imagen2\":" + (imagen2 == null ? "null" : "\"" + JsonUtils.escapar(imagen2) + "\"") + ",");
                String imagen3 = rs.getString("imagen3");
                out.print("\"imagen3\":" + (imagen3 == null ? "null" : "\"" + JsonUtils.escapar(imagen3) + "\"") + ",");
                String iconoVendedor = rs.getString("icono_vendedor");
                out.print("\"iconoVendedor\":" + (iconoVendedor == null ? "null" : "\"" + JsonUtils.escapar(iconoVendedor) + "\""));

                out.print("}");
            }

            out.println("]");
        } catch (Exception e) {
            e.printStackTrace(response.getWriter());
        }
    }
}