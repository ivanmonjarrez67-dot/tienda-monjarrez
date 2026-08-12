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
                || !esTextoSeguro(provincia) || !esTextoSeguro(ciudad) || !esTextoSeguro(imagen)) {
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

            // Insertar producto
            String sql = "INSERT INTO Productos "
                       + "(usuario_id, nombre, descripcion, imagen, precio, Nombre_Empresa, categoria, telefono, correo, provincia, ciudad) "
                       + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write("✅ Producto guardado correctamente");
            }

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
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Precio o ID inválido");
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
