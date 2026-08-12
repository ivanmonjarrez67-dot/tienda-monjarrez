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

            // 2️⃣ Borrar producto de la BD
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM Productos WHERE id = ?")) {
                stmt.setInt(1, id);
                int filas = stmt.executeUpdate();

                if (filas == 0) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "No se encontró un producto con ese ID.");
                    return;
                }
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

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/plain");
        response.getWriter().write("Este servlet solo acepta DELETE o POST para eliminar productos.");
    }
}
