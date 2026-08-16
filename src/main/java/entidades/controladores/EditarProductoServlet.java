package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.regex.Pattern;

import entidades.DatabaseConnection;

@WebServlet("/EditarProducto")
public class EditarProductoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // 📂 Misma ruta que usa EliminarProductoServlet
    private static final String UPLOAD_DIR = "C:/Monjarrez_Mi_Tienda_En_Linea/uploads/productos";

    // 🔒 Misma validación anti-HTML/código que ya usa GuardarProductoServlet,
    // para que "Editar Producto" no sea una puerta trasera que permita
    // colar <script> u otro código donde "Agregar Producto" sí lo bloquea.
    private static final Pattern PATRON_HTML = Pattern.compile("<[^>]*>");

    private boolean esTextoSeguro(String valor) {
        if (valor == null || valor.isEmpty()) return true;
        return !PATRON_HTML.matcher(valor).find();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String idStr = request.getParameter("id");
        String usuarioIdParam = request.getParameter("usuario_id");
        String nombre = request.getParameter("nombre");
        String descripcion = request.getParameter("descripcion");
        String imagen = request.getParameter("imagen");
        String precioStr = request.getParameter("precio");
        String empresa = request.getParameter("empresa");
        String categoria = request.getParameter("categoria");
        String telefono = request.getParameter("telefono");
        String correo = request.getParameter("correo");
        String provincia = request.getParameter("provincia");
        String ciudad = request.getParameter("ciudad");

        if (idStr == null || idStr.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Falta el ID del producto.");
            return;
        }
        if (usuarioIdParam == null || usuarioIdParam.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Falta el ID del usuario.");
            return;
        }

        // 🔒 Misma validación que GuardarProductoServlet: ningún campo de
        // texto libre puede traer etiquetas HTML/código.
        if (!esTextoSeguro(nombre) || !esTextoSeguro(descripcion) || !esTextoSeguro(empresa)
                || !esTextoSeguro(categoria) || !esTextoSeguro(telefono) || !esTextoSeguro(correo)
                || !esTextoSeguro(provincia) || !esTextoSeguro(ciudad) || !esTextoSeguro(imagen)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Uno o más campos contienen caracteres no permitidos (HTML o código). "
                    + "Por favor usa solo texto normal.");
            return;
        }

        int id;
        int usuarioId;
        double precio;
        try {
            id = Integer.parseInt(idStr);
            usuarioId = Integer.parseInt(usuarioIdParam);
            precio = (precioStr != null && !precioStr.trim().isEmpty())
                    ? Double.parseDouble(precioStr.trim())
                    : 0;
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "ID, usuario_id o precio inválido.");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {

            // 1️⃣ Verificar que el producto exista Y le pertenezca a este
            // usuario (mismo dueño que lo publicó), y de paso obtener la
            // imagen actual para poder borrar el archivo viejo si cambia.
            // Sin este chequeo, cualquier vendedor logueado podría editar
            // productos ajenos solo adivinando el id.
            String imagenAnterior = null;
            try (PreparedStatement psSelect = conn.prepareStatement(
                    "SELECT imagen, usuario_id FROM Productos WHERE id = ?")) {
                psSelect.setInt(1, id);
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (!rs.next()) {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND, "No se encontró un producto con ese ID.");
                        return;
                    }
                    if (rs.getInt("usuario_id") != usuarioId) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Este producto no le pertenece.");
                        return;
                    }
                    imagenAnterior = rs.getString("imagen");
                }
            }

            // 2️⃣ Actualizar el producto (mismas columnas que usa el INSERT
            // de GuardarProductoServlet, incluida "Nombre_Empresa").
            String sql = "UPDATE Productos SET nombre = ?, descripcion = ?, imagen = ?, precio = ?, "
                       + "Nombre_Empresa = ?, categoria = ?, telefono = ?, correo = ?, provincia = ?, ciudad = ? "
                       + "WHERE id = ? AND usuario_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nombre);
                stmt.setString(2, descripcion);
                stmt.setString(3, imagen);
                stmt.setDouble(4, precio);
                stmt.setString(5, empresa);
                stmt.setString(6, categoria);
                stmt.setString(7, telefono);
                stmt.setString(8, correo);
                stmt.setString(9, provincia);
                stmt.setString(10, ciudad);
                stmt.setInt(11, id);
                stmt.setInt(12, usuarioId);

                int filas = stmt.executeUpdate();
                if (filas == 0) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "No se encontró un producto con ese ID.");
                    return;
                }
            }

            // 3️⃣ Si la imagen cambió y la anterior era un archivo local
            // subido por GuardarProductoArchivo (mismo esquema de URL que
            // usa EliminarProductoServlet, con "=" antes del nombre del
            // archivo), borrar el archivo viejo para no dejar imágenes
            // huérfanas en el servidor.
            if (imagenAnterior != null && !imagenAnterior.isEmpty()
                    && imagen != null && !imagen.equals(imagenAnterior)
                    && imagenAnterior.contains("=")) {
                String fileNameAnterior = imagenAnterior.substring(imagenAnterior.lastIndexOf("=") + 1);
                File imagenVieja = new File(UPLOAD_DIR, fileNameAnterior);
                if (imagenVieja.exists() && imagenVieja.isFile()) {
                    if (imagenVieja.delete()) {
                        System.out.println("✅ Imagen anterior eliminada: " + imagenVieja.getAbsolutePath());
                    } else {
                        System.out.println("⚠️ No se pudo borrar la imagen anterior: " + imagenVieja.getAbsolutePath());
                    }
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("✅ Producto actualizado correctamente");

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error en base de datos: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error inesperado: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/plain");
        response.getWriter().write("Este servlet solo acepta POST para editar productos.");
    }
}