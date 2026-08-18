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

        // 🆕 Se agregó "p.id" (faltaba en el SELECT original, y sin él no
        // se puede armar el botón "Compartir" ni abrirProductoDesdeUrl()
        // para los productos que llegan filtrados). También se agrega el
        // mismo LEFT JOIN que los demás listados de productos.
        String sql = "SELECT p.id, p.nombre, p.descripcion, p.imagen, p.precio, p.Nombre_Empresa, "
                   + "p.telefono, p.correo, p.provincia, p.ciudad, "
                   + "d.precio_anterior, ia.imagen2, ia.imagen3 "
                   + "FROM Productos p "
                   + "LEFT JOIN Descuentos d ON d.producto_id = p.id "
                   + "LEFT JOIN ImagenesAdicionalesProducto ia ON ia.producto_id = p.id";

        List<String> condiciones = new ArrayList<>();
        List<String> parametros = new ArrayList<>();

        if (categoria != null && !categoria.equalsIgnoreCase("Todo") && !categoria.trim().isEmpty()) {
            condiciones.add("p.categoria = ?");
            parametros.add(categoria);
        }

        // Agrega ORDER BY según el filtro seleccionado
        if ("Novedades".equalsIgnoreCase(filtro)) {
            sql += condiciones.isEmpty() ? " ORDER BY p.id DESC"
                    : " WHERE " + String.join(" AND ", condiciones) + " ORDER BY p.id DESC";
        } else if ("Descuentos".equalsIgnoreCase(filtro)) {
            // 🆕 Prioriza primero los productos que sí tienen fila en la tabla
            // Descuentos (d.precio_anterior no es NULL); como criterio
            // secundario, se mantiene el orden por precio ascendente.
            String ordenDescuentos = " ORDER BY CASE WHEN d.precio_anterior IS NULL THEN 1 ELSE 0 END, p.precio ASC";
            sql += condiciones.isEmpty() ? ordenDescuentos
                    : " WHERE " + String.join(" AND ", condiciones) + ordenDescuentos;
        } else if ("Recomendados".equalsIgnoreCase(filtro)) {
            sql += condiciones.isEmpty() ? " ORDER BY p.precio DESC"
                    : " WHERE " + String.join(" AND ", condiciones) + " ORDER BY p.precio DESC";
        } else {
            sql += condiciones.isEmpty() ? " ORDER BY p.nombre"
                    : " WHERE " + String.join(" AND ", condiciones) + " ORDER BY p.nombre";
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