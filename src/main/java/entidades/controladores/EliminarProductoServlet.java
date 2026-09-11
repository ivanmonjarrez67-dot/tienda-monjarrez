package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.File;
import java.io.IOException;
import java.sql.*;

import entidades.DatabaseConnection;

@WebServlet("/EliminarProducto")
public class EliminarProductoServlet extends HttpServlet {  

    // 📂 Ruta de imágenes
    private static final String UPLOAD_DIR = "C:/Monjarrez_Mi_Tienda_En_Linea/uploads/productos";

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

            // 1️⃣ Obtener nombre de la imagen antes de borrar
            String nombreImagen = null;
            try (PreparedStatement psSelect = conn.prepareStatement("SELECT imagen FROM Productos WHERE id = ?")) {
                psSelect.setInt(1, id);
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (rs.next()) {
                        nombreImagen = rs.getString("imagen");
                    }
                }
            }

            // 🆕 2️⃣ Borrar primero las tablas hijas que apuntan a
            // Productos.id (misma causa del error "FK_ProductosExtranjeros_Productos"
            // que ya se había resuelto en EliminarPerfilServlet, pero acá
            // faltaba aplicarlo). Todo dentro de una transacción: si algo
            // falla, se revierte y el producto no queda a medio borrar.
            boolean autoCommitOriginal = conn.getAutoCommit();
            int filas;
            try {
                conn.setAutoCommit(false);

                ejecutarDelete(conn, "DELETE FROM ImagenesAdicionalesProducto WHERE producto_id = ?", id);
                ejecutarDelete(conn, "DELETE FROM Descuentos WHERE producto_id = ?", id);
                ejecutarDelete(conn, "DELETE FROM ProductosExtranjeros WHERE producto_id = ?", id);

                filas = ejecutarDelete(conn, "DELETE FROM Productos WHERE id = ?", id);

                if (filas == 0) {
                    conn.rollback();
                    conn.setAutoCommit(autoCommitOriginal);
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "No se encontró un producto con ese ID.");
                    return;
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(autoCommitOriginal);
            }

            // 3️⃣ Si el producto tenía imagen → borrarla del servidor
            if (nombreImagen != null && !nombreImagen.isEmpty()) {
                // ⚠️ nombreImagen viene con la URL completa (http://...) → extraemos solo el nombre real
                String fileName = nombreImagen.substring(nombreImagen.lastIndexOf("=") + 1);

                File imagen = new File(UPLOAD_DIR, fileName);
                if (imagen.exists() && imagen.isFile()) {
                    if (imagen.delete()) {
                        System.out.println("✅ Imagen eliminada: " + imagen.getAbsolutePath());
                    } else {
                        System.out.println("⚠️ No se pudo borrar la imagen: " + imagen.getAbsolutePath());
                    }
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("Producto y su imagen eliminados correctamente.");

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error en base de datos.");
        }
    }

    /** Ejecuta un DELETE parametrizado por id y devuelve las filas afectadas. */
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