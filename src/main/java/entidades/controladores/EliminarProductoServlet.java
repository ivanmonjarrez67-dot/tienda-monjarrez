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

            // 🆕 2️⃣ Si el producto ya fue vendido (aparece en DetallePedido) NO se
            // borra: ese registro es el historial de compras/facturas de los
            // clientes y la llave foránea no lo permite. Se avisa con un 409.
            if (existe(conn, "SELECT 1 FROM DetallePedido WHERE producto_id = ?", id)) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write(
                    "Este producto ya tiene pedidos registrados, por eso no se puede eliminar " +
                    "sin perder el historial de compras de los clientes.");
                return;
            }

            // 3️⃣ Borrar las tablas hijas con NO ACTION (Descuentos, ImagenesAdicionalesProducto,
            // ImagenesProducto y Resenas se borran solas por ON DELETE CASCADE).
            // Todo en una transacción: si algo falla se revierte.
            boolean autoCommitOriginal = conn.getAutoCommit();
            boolean encontrado;
            try {
                conn.setAutoCommit(false);

                ejecutarDelete(conn, "DELETE FROM ToquesContacto WHERE producto_id = ?", id);
                ejecutarDelete(conn, "DELETE FROM DetalleCarrito WHERE producto_id = ?", id);
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

            // 4️⃣ Si el producto tenía imagen → borrarla del servidor
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

    /** Devuelve true si la consulta (con un parámetro id) trae al menos una fila. */
    private boolean existe(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
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