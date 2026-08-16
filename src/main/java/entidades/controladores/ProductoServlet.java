package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

import entidades.DatabaseConnection;

// 🔗 Sirve la URL individual de cada producto (la que genera el botón
// "Compartir" del frontend: /producto?id=123) con las etiquetas Open
// Graph ya rellenas con los datos reales del producto, para que
// WhatsApp/Facebook/Telegram muestren imagen + nombre en la vista
// previa del enlace. Esas apps NO ejecutan JavaScript al leer el link,
// solo el HTML crudo que devuelve el servidor — por eso esto no se
// puede resolver solo en el index.html estático de la SPA.
@WebServlet("/producto")
public class ProductoServlet extends HttpServlet {

    // TODO: confirmar el dominio real en producción.
    private static final String DOMINIO = "https://tiendamonjarrez.com";

    private String escaparHtml(String valor) {
        if (valor == null) return "";
        return valor.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html; charset=UTF-8");

        String idParam = request.getParameter("id");
        int id;
        try {
            id = Integer.parseInt(idParam.trim());
        } catch (Exception e) {
            response.sendRedirect(DOMINIO + "/index.html");
            return;
        }

        String nombre = null, descripcion = null, imagen = null, empresa = null;

        // 🔧 Nombre_Empresa (no "empresa") es el nombre real de la columna,
        // igual que en GuardarProductoServlet.
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT nombre, descripcion, imagen, Nombre_Empresa FROM Productos WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nombre = rs.getString("nombre");
                    descripcion = rs.getString("descripcion");
                    imagen = rs.getString("imagen");
                    empresa = rs.getString("Nombre_Empresa");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendRedirect(DOMINIO + "/index.html");
            return;
        }

        if (nombre == null) {
            // No existe ese producto (por ejemplo, ya fue eliminado).
            response.sendRedirect(DOMINIO + "/index.html");
            return;
        }

        String urlProducto = DOMINIO + "/producto?id=" + id;
        String titulo = escaparHtml(nombre) + " - Tienda Monjarrez";
        String desc = escaparHtml(descripcion != null && !descripcion.isBlank()
                ? descripcion
                : ("Producto de " + (empresa != null ? empresa : "Tienda Monjarrez")));
        String img = escaparHtml(imagen != null && !imagen.isBlank()
                ? imagen
                : (DOMINIO + "/logo-tienda.png"));

        // A donde se manda a la persona real (no al bot de vista previa).
        // TODO: si tu script.js aún no lee ?producto=ID al cargar para
        // abrir automáticamente el panel "Ver detalles" de ese producto,
        // esta redirección de todos modos deja al usuario en la tienda;
        // solo no abrirá el panel solo. Es un ajuste pequeño de agregar
        // después si lo quieres.
        String urlSpaConProducto = DOMINIO + "/index.html?producto=" + id;

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"es\">");
            out.println("<head>");
            out.println("<meta charset=\"UTF-8\">");
            out.println("<title>" + titulo + "</title>");
            out.println("<meta property=\"og:title\" content=\"" + titulo + "\">");
            out.println("<meta property=\"og:description\" content=\"" + desc + "\">");
            out.println("<meta property=\"og:image\" content=\"" + img + "\">");
            out.println("<meta property=\"og:url\" content=\"" + urlProducto + "\">");
            out.println("<meta property=\"og:type\" content=\"product\">");
            out.println("<meta property=\"og:site_name\" content=\"Tienda Monjarrez\">");
            out.println("<meta name=\"twitter:card\" content=\"summary_large_image\">");
            out.println("<meta http-equiv=\"refresh\" content=\"0; url=" + urlSpaConProducto + "\">");
            out.println("</head>");
            out.println("<body>");
            out.println("<p>Redirigiendo a <a href=\"" + urlSpaConProducto + "\">" + titulo + "</a>...</p>");
            out.println("</body></html>");
        }
    }
}