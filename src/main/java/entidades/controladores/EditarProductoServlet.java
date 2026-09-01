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

        // 🆕 Igual que en GuardarProductoServlet: precio anterior (rebaja)
        // e imágenes adicionales (2 y 3), todos opcionales. Si el vendedor
        // los deja vacíos en el formulario, se borra lo que hubiera antes
        // (así puede "quitar" una rebaja o una foto extra editando).
        String precioAnteriorStr = request.getParameter("precio_anterior");
        String imagen2 = request.getParameter("imagen2");
        String imagen3 = request.getParameter("imagen3");

        // 🆕 Producto de reventa internacional: igual criterio, tabla
        // aparte ProductosExtranjeros (no se toca Productos). script.js
        // siempre manda "1" o "0".
        String esExtranjeroStr = request.getParameter("es_extranjero");
        boolean esExtranjero = "1".equals(esExtranjeroStr);

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
                || !esTextoSeguro(provincia) || !esTextoSeguro(ciudad) || !esTextoSeguro(imagen)
                || !esTextoSeguro(imagen2) || !esTextoSeguro(imagen3)) {
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

        Double precioAnterior = null;
        try {
            if (precioAnteriorStr != null && !precioAnteriorStr.trim().isEmpty()) {
                double valor = Double.parseDouble(precioAnteriorStr.trim());
                if (valor > precio) precioAnterior = valor;
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Precio anterior inválido.");
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

            // 2️⃣ Actualizar el producto — SIN TOCAR la tabla Productos
            // (mismas columnas de siempre, "es_extranjero" vive aparte).
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

            // 3️⃣ Rebaja (Descuentos): si viene precio anterior válido,
            // UPSERT (actualiza si ya había fila, inserta si no). Si NO
            // viene, se borra cualquier fila existente — así el vendedor
            // puede quitar la rebaja dejando el campo vacío al editar.
            if (precioAnterior != null) {
                try (PreparedStatement psUpdate = conn.prepareStatement(
                        "UPDATE Descuentos SET precio_anterior = ? WHERE producto_id = ?")) {
                    psUpdate.setDouble(1, precioAnterior);
                    psUpdate.setInt(2, id);
                    int filas = psUpdate.executeUpdate();
                    if (filas == 0) {
                        try (PreparedStatement psInsert = conn.prepareStatement(
                                "INSERT INTO Descuentos (producto_id, precio_anterior) VALUES (?, ?)")) {
                            psInsert.setInt(1, id);
                            psInsert.setDouble(2, precioAnterior);
                            psInsert.executeUpdate();
                        }
                    }
                }
            } else {
                try (PreparedStatement psDelete = conn.prepareStatement(
                        "DELETE FROM Descuentos WHERE producto_id = ?")) {
                    psDelete.setInt(1, id);
                    psDelete.executeUpdate();
                }
            }

            // 4️⃣ Imágenes adicionales: mismo patrón UPSERT/DELETE. Si no
            // viene ni imagen2 ni imagen3, se borra la fila (el vendedor
            // quitó ambas fotos extra al editar).
            boolean hayImagen2 = imagen2 != null && !imagen2.trim().isEmpty();
            boolean hayImagen3 = imagen3 != null && !imagen3.trim().isEmpty();
            if (hayImagen2 || hayImagen3) {
                try (PreparedStatement psUpdate = conn.prepareStatement(
                        "UPDATE ImagenesAdicionalesProducto SET imagen2 = ?, imagen3 = ? WHERE producto_id = ?")) {
                    if (hayImagen2) psUpdate.setString(1, imagen2.trim()); else psUpdate.setNull(1, Types.NVARCHAR);
                    if (hayImagen3) psUpdate.setString(2, imagen3.trim()); else psUpdate.setNull(2, Types.NVARCHAR);
                    psUpdate.setInt(3, id);
                    int filas = psUpdate.executeUpdate();
                    if (filas == 0) {
                        try (PreparedStatement psInsert = conn.prepareStatement(
                                "INSERT INTO ImagenesAdicionalesProducto (producto_id, imagen2, imagen3) VALUES (?, ?, ?)")) {
                            psInsert.setInt(1, id);
                            if (hayImagen2) psInsert.setString(2, imagen2.trim()); else psInsert.setNull(2, Types.NVARCHAR);
                            if (hayImagen3) psInsert.setString(3, imagen3.trim()); else psInsert.setNull(3, Types.NVARCHAR);
                            psInsert.executeUpdate();
                        }
                    }
                }
            } else {
                try (PreparedStatement psDelete = conn.prepareStatement(
                        "DELETE FROM ImagenesAdicionalesProducto WHERE producto_id = ?")) {
                    psDelete.setInt(1, id);
                    psDelete.executeUpdate();
                }
            }

            // 5️⃣ Producto de reventa internacional (ProductosExtranjeros):
            // mismo patrón que Descuentos pero sin columna de valor, solo
            // existencia de la fila. Si se marcó y no había fila, se
            // inserta; si NO se marcó y había fila, se borra; en los otros
            // dos casos no hay que hacer nada.
            try (PreparedStatement psExiste = conn.prepareStatement(
                    "SELECT 1 FROM ProductosExtranjeros WHERE producto_id = ?")) {
                psExiste.setInt(1, id);
                boolean yaEstaba;
                try (ResultSet rsExiste = psExiste.executeQuery()) {
                    yaEstaba = rsExiste.next();
                }
                if (esExtranjero && !yaEstaba) {
                    try (PreparedStatement psInsert = conn.prepareStatement(
                            "INSERT INTO ProductosExtranjeros (producto_id) VALUES (?)")) {
                        psInsert.setInt(1, id);
                        psInsert.executeUpdate();
                    }
                } else if (!esExtranjero && yaEstaba) {
                    try (PreparedStatement psDelete = conn.prepareStatement(
                            "DELETE FROM ProductosExtranjeros WHERE producto_id = ?")) {
                        psDelete.setInt(1, id);
                        psDelete.executeUpdate();
                    }
                }
            }

            // 6️⃣ Si la imagen cambió y la anterior era un archivo local
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