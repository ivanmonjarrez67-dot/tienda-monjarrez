package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import entidades.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * GET /vendedor?id=19
 * Es el enlace que se comparte por WhatsApp/redes. Devuelve una página mínima con los meta tags
 * Open Graph (logo de la tienda + frase) y redirige a perfil-vendedor.html para las personas.
 * Nombre y logo se leen de la BD (no de la URL), así nadie puede fabricar una vista previa falsa.
 */
@WebServlet("/vendedor")
public class VendedorShareServlet extends HttpServlet {

    private static final String BASE = "https://tiendamonjarrez.com";
    private static final String LOGO_POR_DEFECTO = BASE + "/icon-512.png";

    private static final String COL_EMPRESA = "Nombre_Empresa";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        int id;
        try { id = Integer.parseInt(req.getParameter("id")); }
        catch (Exception e) { resp.sendRedirect(BASE + "/"); return; }

        String empresa = null, logo = null;
        // Mismo camino que ListaProductosServlet: Productos -> Vendedores -> IconosVendedor.
        // Se prefiere una fila que sí tenga icono (por si el vendedor tiene productos sin él).
        String sql = "SELECT TOP 1 p." + COL_EMPRESA + ", iv.icono "
                   + "FROM Productos p "
                   + "LEFT JOIN Vendedores v ON v.usuario_id = p.usuario_id "
                   + "LEFT JOIN IconosVendedor iv ON iv.vendedor_id = v.id "
                   + "WHERE p.usuario_id = ? AND p." + COL_EMPRESA + " IS NOT NULL "
                   + "ORDER BY CASE WHEN iv.icono IS NULL THEN 1 ELSE 0 END, iv.fecha_actualizacion DESC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { empresa = rs.getString(1); logo = rs.getString(2); }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (empresa == null || empresa.trim().isEmpty()) { resp.sendRedirect(BASE + "/"); return; }
        empresa = empresa.trim();
        if (logo == null || !logo.startsWith("https://")) logo = LOGO_POR_DEFECTO; // og:image debe ser absoluta

        String destino = BASE + "/perfil-vendedor.html?usuario_id=" + id
                       + "&empresa=" + URLEncoder.encode(empresa, "UTF-8");
        String titulo = empresa + " | Tienda Monjarrez";
        String frase  = "Mira el catálogo de " + empresa + " en Tienda Monjarrez";

        resp.setContentType("text/html");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">");
        out.println("<title>" + esc(titulo) + "</title>");
        out.println("<meta property=\"og:type\" content=\"website\">");
        out.println("<meta property=\"og:site_name\" content=\"Tienda Monjarrez\">");
        out.println("<meta property=\"og:title\" content=\"" + esc(empresa) + "\">");
        out.println("<meta property=\"og:description\" content=\"" + esc(frase) + "\">");
        out.println("<meta property=\"og:image\" content=\"" + esc(logo) + "\">");
        out.println("<meta property=\"og:url\" content=\"" + esc(BASE + "/vendedor?id=" + id) + "\">");
        out.println("<meta name=\"twitter:card\" content=\"summary\">");
        out.println("<meta http-equiv=\"refresh\" content=\"0;url=" + esc(destino) + "\">");
        out.println("</head><body><script>location.replace(" + jsStr(destino) + ");</script>");
        out.println("<a href=\"" + esc(destino) + "\">Ver el catálogo de " + esc(empresa) + "</a>");
        out.println("</body></html>");
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String jsStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c") + "\"";
    }
}