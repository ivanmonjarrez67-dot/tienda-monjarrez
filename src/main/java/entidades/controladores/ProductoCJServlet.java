package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

import entidades.DatabaseConnection;

// 🔗 Igual que ProductoServlet, pero para productos importados de CJ
// (tabla ProductosCJ en vez de Productos). Sirve la URL corta que
// genera el botón "Compartir" de detalle-producto.html: /producto-cj?id=123
// con las etiquetas Open Graph ya rellenas (nombre, descripción, imagen
// real del producto), y redirige a la persona real hacia la página de
// detalle de siempre (detalle-producto.html?id=123), que no se toca.
@WebServlet("/producto-cj")
public class ProductoCJServlet extends HttpServlet {

    // TODO: confirmar el dominio real en producción (mismo valor que ProductoServlet).
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

        String nombre = null, descripcion = null, imagen = null;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT nombre, descripcion, imagen FROM ProductosCJ WHERE id = ? AND activo = 1")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nombre = rs.getString("nombre");
                    descripcion = rs.getString("descripcion");
                    imagen = rs.getString("imagen");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendRedirect(DOMINIO + "/index.html");
            return;
        }

        if (nombre == null) {
            // No existe ese producto CJ (o está inactivo/eliminado).
            response.sendRedirect(DOMINIO + "/index.html");
            return;
        }

        String urlProducto = DOMINIO + "/producto-cj?id=" + id;
        String titulo = escaparHtml(nombre) + " - Tienda Monjarrez";
        String desc = escaparHtml(descripcion != null && !descripcion.isBlank()
                ? descripcion
                : "Producto internacional disponible en Tienda Monjarrez.");
        String img = escaparHtml(imagen != null && !imagen.isBlank()
                ? imagen
                : (DOMINIO + "/logo-tienda.png"));

        // A donde se manda a la persona real (no al bot de vista previa):
        // la página de siempre, sin ningún cambio.
        String urlDestino = DOMINIO + "/detalle-producto.html?id=" + id;

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
            out.println("<meta http-equiv=\"refresh\" content=\"0; url=" + urlDestino + "\">");
            out.println("</head>");
            out.println("<body>");
            out.println("<p>Redirigiendo a <a href=\"" + urlDestino + "\">" + titulo + "</a>...</p>");
            out.println("</body></html>");
        }
    }
}