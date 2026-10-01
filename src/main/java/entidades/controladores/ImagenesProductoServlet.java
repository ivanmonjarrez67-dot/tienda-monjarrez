package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import entidades.DatabaseConnection;
import entidades.JsonUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * GET /api/imagenes-producto?id=X -> ["url1","url2",...] con las fotos ADICIONALES
 * del producto, en el orden en que el vendedor las acomodó (sin la foto principal).
 *
 * Si el producto aún no tiene filas en ImagenesProducto (productos anteriores al
 * cambio), devuelve las de la tabla anterior (imagen2, imagen3).
 *
 * Además concentra la lógica compartida por GuardarProductoServlet y
 * EditarProductoServlet: leer/validar la lista que manda el formulario y
 * guardarla.
 */
@WebServlet("/api/imagenes-producto")
public class ImagenesProductoServlet extends HttpServlet {

    /** Máximo de fotos adicionales por producto (la principal no cuenta). Mantener igual que MAX_FOTOS_EXTRA en script.js. */
    public static final int MAX_EXTRA = 8;
    private static final int MAX_LARGO_URL = 500;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        int id;
        try {
            id = Integer.parseInt(request.getParameter("id"));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print("{\"error\":\"id inválido\"}");
            return;
        }

        List<String> urls = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT url FROM ImagenesProducto WHERE producto_id = ? ORDER BY orden")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) urls.add(rs.getString(1));
                }
            }
            if (urls.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT imagen2, imagen3 FROM ImagenesAdicionalesProducto WHERE producto_id = ?")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String a = rs.getString(1), b = rs.getString(2);
                            if (a != null && !a.trim().isEmpty()) urls.add(a.trim());
                            if (b != null && !b.trim().isEmpty()) urls.add(b.trim());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().print("{\"error\":\"Error en base de datos\"}");
            return;
        }

        PrintWriter out = response.getWriter();
        out.print("[");
        for (int i = 0; i < urls.size(); i++) {
            if (i > 0) out.print(",");
            out.print("\"" + JsonUtils.escapar(urls.get(i)) + "\"");
        }
        out.print("]");
    }

    // ---------------------------------------------------------------
    // Lógica compartida con GuardarProductoServlet / EditarProductoServlet
    // ---------------------------------------------------------------

    private static boolean urlValida(String u) {
        return u.length() <= MAX_LARGO_URL
                && (u.startsWith("https://") || u.startsWith("http://"))
                && !u.matches(".*[\\s\"'<>].*");
    }

    /**
     * Lee la lista de fotos adicionales del formulario: parámetro repetido
     * "imagen_extra" (en el orden elegido). Si no viene, usa imagen2/imagen3
     * (formulario anterior). Quita vacíos y repetidas y corta en MAX_EXTRA.
     * Devuelve null si alguna URL no es válida (el servlet debe responder 400).
     */
    public static List<String> leerDesdeRequest(HttpServletRequest request) {
        List<String> crudas = new ArrayList<>();
        String[] extra = request.getParameterValues("imagen_extra");
        if (extra != null) {
            crudas.addAll(Arrays.asList(extra));
        } else {
            for (String p : new String[] { "imagen2", "imagen3" }) {
                String v = request.getParameter(p);
                if (v != null) crudas.add(v);
            }
        }
        LinkedHashSet<String> limpia = new LinkedHashSet<>();
        for (String c : crudas) {
            if (c == null) continue;
            String u = c.trim();
            if (u.isEmpty()) continue;
            if (!urlValida(u)) return null;
            limpia.add(u);
        }
        List<String> lista = new ArrayList<>(limpia);
        return lista.size() > MAX_EXTRA ? new ArrayList<>(lista.subList(0, MAX_EXTRA)) : lista;
    }

    /**
     * Reemplaza TODAS las fotos adicionales del producto por la lista dada
     * (lista vacía = quitarlas todas). Guarda la lista completa en
     * ImagenesProducto y deja las 2 primeras también en
     * ImagenesAdicionalesProducto (tabla anterior) para que el catálogo y las
     * páginas que aún leen imagen2/imagen3 sigan funcionando.
     */
    public static void guardarLista(Connection conn, int productoId, List<String> urls) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM ImagenesProducto WHERE producto_id = ?")) {
            del.setInt(1, productoId);
            del.executeUpdate();
        }
        if (!urls.isEmpty()) {
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO ImagenesProducto (producto_id, url, orden) VALUES (?, ?, ?)")) {
                for (int i = 0; i < urls.size(); i++) {
                    ins.setInt(1, productoId);
                    ins.setString(2, urls.get(i));
                    ins.setInt(3, i + 1);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        }

        if (urls.isEmpty()) {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM ImagenesAdicionalesProducto WHERE producto_id = ?")) {
                del.setInt(1, productoId);
                del.executeUpdate();
            }
            return;
        }
        String img2 = urls.get(0);
        String img3 = urls.size() > 1 ? urls.get(1) : null;
        try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE ImagenesAdicionalesProducto SET imagen2 = ?, imagen3 = ? WHERE producto_id = ?")) {
            upd.setString(1, img2);
            if (img3 != null) upd.setString(2, img3); else upd.setNull(2, Types.NVARCHAR);
            upd.setInt(3, productoId);
            if (upd.executeUpdate() > 0) return;
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO ImagenesAdicionalesProducto (producto_id, imagen2, imagen3) VALUES (?, ?, ?)")) {
            ins.setInt(1, productoId);
            ins.setString(2, img2);
            if (img3 != null) ins.setString(3, img3); else ins.setNull(3, Types.NVARCHAR);
            ins.executeUpdate();
        }
    }
}