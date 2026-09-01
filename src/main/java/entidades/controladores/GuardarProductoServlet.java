package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.regex.Pattern;

import entidades.DatabaseConnection;
import entidades.EmailService;

@WebServlet("/GuardarProducto")
public class GuardarProductoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // 🆕 Detecta cualquier etiqueta HTML (<script>, <img>, <a>, etc.) para
    // rechazar el producto si algún campo de texto trae código en vez de
    // texto plano. No es un filtro "inteligente" tipo sanitizador que
    // reescribe el texto: simplemente si detecta un "<algo>" rechaza todo
    // el request con un error claro, para no guardar nada dudoso.
    private static final Pattern PATRON_HTML = Pattern.compile("<[^>]*>");

    // 🆕 Verifica que un campo no contenga etiquetas HTML/JS.
    // Devuelve true si el campo es seguro (o está vacío/null).
    private boolean esTextoSeguro(String valor) {
        if (valor == null || valor.isEmpty()) return true;
        return !PATRON_HTML.matcher(valor).find();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
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

        // 🆕 Campos opcionales: precio anterior (rebaja) e imágenes
        // adicionales (2 y 3). Si vienen vacíos, simplemente no se crea
        // fila en las tablas Descuentos / ImagenesAdicionalesProducto.
        String precioAnteriorStr = request.getParameter("precio_anterior");
        String imagen2 = request.getParameter("imagen2");
        String imagen3 = request.getParameter("imagen3");

        // 🆕 Producto de reventa internacional (Temu, AliExpress, etc.):
        // checkbox opcional del formulario. Igual que Descuentos e
        // ImagenesAdicionalesProducto, NO se toca la tabla Productos: si
        // se marca, se crea una fila en la tabla nueva ProductosExtranjeros
        // (solo con el producto_id); si no se marca, simplemente no se
        // crea fila. script.js siempre manda "1" o "0" explícitos.
        String esExtranjeroStr = request.getParameter("es_extranjero");
        boolean esExtranjero = "1".equals(esExtranjeroStr);

        // ✅ Obtener usuarioId directamente desde el parámetro
        String usuarioIdParam = request.getParameter("usuario_id");
        if (usuarioIdParam == null || usuarioIdParam.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Falta el ID del usuario");
            return;
        }

        // 🆕 Validar que ningún campo de texto traiga etiquetas HTML/código.
        // Se revisan todos los campos de texto libre (no el precio, que ya
        // se valida por separado como número, ni la imagen que se valida
        // aparte porque legítimamente puede traer una URL con "<" o ">"
        // solo si viene mal formada, cosa que tampoco queremos).
        if (!esTextoSeguro(nombre) || !esTextoSeguro(descripcion) || !esTextoSeguro(empresa)
                || !esTextoSeguro(categoria) || !esTextoSeguro(telefono) || !esTextoSeguro(correo)
                || !esTextoSeguro(provincia) || !esTextoSeguro(ciudad) || !esTextoSeguro(imagen)
                || !esTextoSeguro(imagen2) || !esTextoSeguro(imagen3)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Uno o más campos contienen caracteres no permitidos (HTML o código). "
                    + "Por favor usa solo texto normal.");
            return;
        }

        int usuarioId = Integer.parseInt(usuarioIdParam);

        try (Connection conn = DatabaseConnection.getConnection()) {

            // Validar precio
            double precio = 0;
            if (precioStr != null && !precioStr.trim().isEmpty()) {
                precio = Double.parseDouble(precioStr.trim());
            }

            // 🆕 Validar precio anterior (opcional): solo se guarda si viene
            // y además es mayor al precio real (si no, no tendría sentido
            // mostrarlo tachado como rebaja).
            Double precioAnterior = null;
            if (precioAnteriorStr != null && !precioAnteriorStr.trim().isEmpty()) {
                double valor = Double.parseDouble(precioAnteriorStr.trim());
                if (valor > precio) {
                    precioAnterior = valor;
                }
            }

            int nuevoProductoId;

            // Insertar producto — SIN TOCAR la tabla Productos (misma
            // cantidad de columnas de siempre).
            String sql = "INSERT INTO Productos "
                       + "(usuario_id, nombre, descripcion, imagen, precio, Nombre_Empresa, categoria, telefono, correo, provincia, ciudad) "
                       + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, usuarioId);        // ✅ ID recibido del formulario
                stmt.setString(2, nombre);
                stmt.setString(3, descripcion);
                stmt.setString(4, imagen);
                stmt.setDouble(5, precio);
                stmt.setString(6, empresa);
                stmt.setString(7, categoria);
                stmt.setString(8, telefono);
                stmt.setString(9, correo);
                stmt.setString(10, provincia);
                stmt.setString(11, ciudad);

                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        nuevoProductoId = keys.getInt(1);
                    } else {
                        throw new SQLException("No se pudo obtener el ID del producto recién creado.");
                    }
                }
            }

            // 🆕 Si viene precio anterior válido, guardar la rebaja.
            if (precioAnterior != null) {
                try (PreparedStatement stmtDescuento = conn.prepareStatement(
                        "INSERT INTO Descuentos (producto_id, precio_anterior) VALUES (?, ?)")) {
                    stmtDescuento.setInt(1, nuevoProductoId);
                    stmtDescuento.setDouble(2, precioAnterior);
                    stmtDescuento.executeUpdate();
                }
            }

            // 🆕 Si viene al menos una imagen adicional, guardar la fila.
            boolean hayImagen2 = imagen2 != null && !imagen2.trim().isEmpty();
            boolean hayImagen3 = imagen3 != null && !imagen3.trim().isEmpty();
            if (hayImagen2 || hayImagen3) {
                try (PreparedStatement stmtImagenes = conn.prepareStatement(
                        "INSERT INTO ImagenesAdicionalesProducto (producto_id, imagen2, imagen3) VALUES (?, ?, ?)")) {
                    stmtImagenes.setInt(1, nuevoProductoId);
                    if (hayImagen2) stmtImagenes.setString(2, imagen2.trim()); else stmtImagenes.setNull(2, Types.NVARCHAR);
                    if (hayImagen3) stmtImagenes.setString(3, imagen3.trim()); else stmtImagenes.setNull(3, Types.NVARCHAR);
                    stmtImagenes.executeUpdate();
                }
            }

            // 🆕 Si se marcó "producto de reventa internacional", crear la
            // fila en ProductosExtranjeros (tabla nueva, solo producto_id).
            // Si no se marcó, simplemente no se crea nada — igual que
            // Descuentos/ImagenesAdicionalesProducto cuando esos campos
            // vienen vacíos.
            if (esExtranjero) {
                try (PreparedStatement stmtExtranjero = conn.prepareStatement(
                        "INSERT INTO ProductosExtranjeros (producto_id) VALUES (?)")) {
                    stmtExtranjero.setInt(1, nuevoProductoId);
                    stmtExtranjero.executeUpdate();
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("✅ Producto guardado correctamente");

            // 📩 Avisar solo a los usuarios (Compradores O Vendedores — un
            // vendedor también puede comprar) que marcaron esta categoría
            // como de su interés en su perfil. La provincia es un filtro
            // OPCIONAL: si el usuario marcó alguna(s) provincia(s), solo se
            // le notifica cuando el producto es de una de ellas; si no
            // marcó ninguna, se le notifica igual por categoría sin
            // importar la zona (así alguien puede querer solo la categoría,
            // sin acotar por provincia). También se excluye al propio
            // vendedor que publicó el producto.
            try {
                String sqlInteresados =
                        "SELECT u.correo, u.nombre " +
                        "FROM Usuarios u " +
                        "WHERE u.id <> ? " +
                        "AND EXISTS (SELECT 1 FROM Intereses ic WHERE ic.usuario_id = u.id AND ic.interes = ?) " +
                        "AND (" +
                        "  EXISTS (SELECT 1 FROM Intereses ip WHERE ip.usuario_id = u.id AND ip.provincia = ?)" +
                        "  OR NOT EXISTS (SELECT 1 FROM Intereses ip2 WHERE ip2.usuario_id = u.id AND ip2.provincia IS NOT NULL)" +
                        ")";
                try (PreparedStatement stmtInteresados = conn.prepareStatement(sqlInteresados)) {
                    stmtInteresados.setInt(1, usuarioId);
                    stmtInteresados.setString(2, categoria);
                    stmtInteresados.setString(3, provincia);

                    try (ResultSet rsInteresados = stmtInteresados.executeQuery()) {
                        while (rsInteresados.next()) {
                            EmailService.enviarAvisoNuevoProducto(
                                rsInteresados.getString("correo"),
                                rsInteresados.getString("nombre"),
                                nombre,
                                empresa
                            );
                        }
                    }
                }
            } catch (SQLException e) {
                // No queremos que un fallo al mandar avisos rompa el guardado del producto
                System.out.println("[GuardarProductoServlet] ⚠️ No se pudieron enviar avisos de nuevo producto: " + e.getMessage());
            }

        } catch (NumberFormatException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Precio, precio anterior o ID inválido");
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
        response.getWriter().write("Este servlet solo acepta POST para guardar productos.");
    }
}