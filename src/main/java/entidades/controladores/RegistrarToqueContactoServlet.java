package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Set;

import entidades.DatabaseConnection;
import entidades.JsonUtils;

// 📊 Guarda un registro cada vez que un comprador toca un botón de
// contacto en el panel "Ver detalles" del producto: WhatsApp o correo
// del VENDEDOR (proceso de compra directo), o WhatsApp o correo de la
// TIENDA (proceso de compra mediante la tienda). Sirve para medir
// tráfico/interés real, tanto por vendedor (sumando los toques de todos
// sus productos) como por tienda (toques dirigidos al canal de la
// tienda, sin importar qué vendedor).
//
// Lo llama el frontend con navigator.sendBeacon (o fetch como respaldo)
// justo antes de que el navegador abra wa.me o mailto:, sin bloquear ni
// retrasar esa acción — por eso NO devuelve datos importantes ni el
// frontend espera la respuesta.
@WebServlet("/registrarToqueContacto")
public class RegistrarToqueContactoServlet extends HttpServlet {

    // 🆕 Se amplían los valores válidos de tipo_contacto (antes solo
    // whatsapp/correo/envio) para cubrir también los toques al contacto
    // de la TIENDA, en vez de crear una tabla o columna nueva. El
    // producto_id/usuario_id se sigue guardando igual (permite saber qué
    // producto llevó al comprador a contactar a la tienda), pero el
    // valor de tipo_contacto ya deja explícito que el destino fue la
    // tienda y no el vendedor.
    private static final Set<String> TIPOS_VALIDOS = Set.of(
            "whatsapp", "correo", "envio", "tienda_whatsapp", "tienda_correo"
    );

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String productoIdParam = request.getParameter("productoId");
        String tipoContacto = request.getParameter("tipo"); // ver TIPOS_VALIDOS

        int productoId;
        try {
            productoId = Integer.parseInt(productoIdParam.trim());
        } catch (Exception e) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"error\":\"productoId invalido\"}");
            return;
        }

        if (tipoContacto == null || !TIPOS_VALIDOS.contains(tipoContacto)) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"error\":\"tipo invalido, debe ser whatsapp, correo, envio, tienda_whatsapp o tienda_correo\"}");
            return;
        }

        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        // 🔧 El usuario_id (dueño del producto) se busca del lado del
        // servidor a partir del producto_id, en la misma sentencia INSERT
        // (INSERT...SELECT), en vez de confiar en un valor mandado por el
        // navegador. Si el producto_id no existe, no se inserta nada
        // (0 filas afectadas) y se responde con error. Esto aplica igual
        // para toques a la tienda: se guarda de todos modos qué producto
        // los originó, solo cambia el valor de tipo_contacto.
        String sql = "INSERT INTO ToquesContacto (producto_id, usuario_id, tipo_contacto, ip_usuario, user_agent) " +
                     "SELECT ?, usuario_id, ?, ?, ? FROM Productos WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, productoId);
            stmt.setString(2, tipoContacto);
            stmt.setString(3, ip);
            stmt.setString(4, userAgent != null && userAgent.length() > 255
                    ? userAgent.substring(0, 255) : userAgent);
            stmt.setInt(5, productoId);

            int filas = stmt.executeUpdate();
            if (filas == 0) {
                response.setStatus(404);
                out.print("{\"ok\":false,\"error\":\"producto no encontrado\"}");
                return;
            }

            out.print("{\"ok\":true}");

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(500);
            out.print("{\"ok\":false,\"error\":\"" + JsonUtils.escapar("Error en base de datos: " + e.getMessage()) + "\"}");
        }
    }
}