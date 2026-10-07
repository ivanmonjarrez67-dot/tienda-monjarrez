package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import entidades.CloudinaryService;
import entidades.DatabaseConnection;

@WebServlet("/EliminarProducto")
public class EliminarProductoServlet extends HttpServlet {

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        eliminarProducto(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        eliminarProducto(request, response);
    }

    // 🔹 Eliminación por ID
    private void eliminarProducto(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String idStr = request.getParameter("id");
        if (idStr == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Falta ID del producto.");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "ID inválido.");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {

            // 🆕 1️⃣ Juntar TODAS las URLs de imágenes del producto antes de borrar
            // (principal + adicionales). Después del DELETE ya no se pueden leer.
            List<String> urlsImagenes = new ArrayList<>();
            try (PreparedStatement psSelect = conn.prepareStatement("SELECT imagen FROM Productos WHERE id = ?")) {
                psSelect.setInt(1, id);
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (rs.next()) urlsImagenes.add(rs.getString("imagen"));
                }
            }
            agregarUrlsDeTabla(conn, "ImagenesAdicionalesProducto", id, urlsImagenes);
            agregarUrlsDeTabla(conn, "ImagenesProducto", id, urlsImagenes);

            // 2️⃣ Si el producto ya fue vendido (aparece en DetallePedido) NO se borra.
            if (existe(conn, "SELECT 1 FROM DetallePedido WHERE producto_id = ?", id)) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write(
                    "Este producto ya tiene pedidos registrados, por eso no se puede eliminar " +
                    "sin perder el historial de compras de los clientes.");
                return;
            }

            // 3️⃣ Borrado en una transacción (igual que antes)
            boolean autoCommitOriginal = conn.getAutoCommit();
            boolean encontrado;
            try {
                conn.setAutoCommit(false);

                ejecutarDelete(conn, "DELETE FROM ToquesContacto WHERE producto_id = ?", id);
                ejecutarDelete(conn, "DELETE FROM DetalleCarrito WHERE producto_id = ?", id);
                ejecutarDelete(conn, "DELETE FROM ImagenElegidaCarrito WHERE producto_id = ?", id);
                ejecutarDelete(conn, "DELETE FROM ProductosExtranjeros WHERE producto_id = ?", id);
                ejecutarDelete(conn, "DELETE FROM ImagenesAdicionalesProducto WHERE producto_id = ?", id);
                ejecutarDelete(conn, "DELETE FROM Descuentos WHERE producto_id = ?", id);

                int filas = ejecutarDelete(conn, "DELETE FROM Productos WHERE id = ?", id);
                encontrado = filas > 0;

                if (encontrado) conn.commit(); else conn.rollback();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(autoCommitOriginal);
            }

            if (!encontrado) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No se encontró un producto con ese ID.");
                return;
            }

            // 🆕 4️⃣ Recién con el commit hecho se borran las fotos en Cloudinary.
            // Si falla, el producto ya no existe de todos modos: solo se registra.
            try {
                CloudinaryService.borrarPorUrls(urlsImagenes);
            } catch (Exception e) {
                System.out.println("[EliminarProducto] No se pudieron borrar imágenes de Cloudinary: " + e.getMessage());
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("Producto y sus imágenes eliminados correctamente.");

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error en base de datos.");
        }
    }

    /**
     * Agrega a la lista todo valor de texto que parezca URL en las filas de esa
     * tabla para ese producto. Es genérico a propósito: no depende de cómo se
     * llamen las columnas (imagen2, imagen3, url...). Si la tabla no existe o no
     * tiene producto_id, simplemente no agrega nada.
     */
    private void agregarUrlsDeTabla(Connection conn, String tabla, int productoId, List<String> destino) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + tabla + " WHERE producto_id = ?")) {
            ps.setInt(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    for (int c = 1; c <= md.getColumnCount(); c++) {
                        int t = md.getColumnType(c);
                        if (t == Types.VARCHAR || t == Types.NVARCHAR || t == Types.LONGVARCHAR || t == Types.LONGNVARCHAR) {
                            String v = rs.getString(c);
                            if (v != null && v.startsWith("http")) destino.add(v);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("[EliminarProducto] No se leyó " + tabla + ": " + e.getMessage());
        }
    }

    private boolean existe(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int ejecutarDelete(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate();
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/plain");
        response.getWriter().write("Este servlet solo acepta DELETE o POST para eliminar productos.");
    }
}