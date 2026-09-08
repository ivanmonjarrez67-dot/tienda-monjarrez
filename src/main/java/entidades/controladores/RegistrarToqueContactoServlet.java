package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

import entidades.DatabaseConnection;
import entidades.JsonUtils;

// 📊 Guarda un registro cada vez que un comprador toca el botón de
// WhatsApp o de correo de un vendedor, en el panel "Ver detalles" del
// producto. Sirve para medir tráfico/interés real por vendedor (sumando
// los toques de todos sus productos).
//
// Lo llama el frontend con navigator.sendBeacon (o fetch como respaldo)
// justo antes de que el navegador abra wa.me o mailto:, sin bloquear ni
// retrasar esa acción — por eso NO devuelve datos importantes ni el
// frontend espera la respuesta.
@WebServlet("/registrarToqueContacto")
public class RegistrarToqueContactoServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String productoIdParam = request.getParameter("productoId");
        String tipoContacto = request.getParameter("tipo"); // "whatsapp" o "correo"

        int productoId;
        try {
            productoId = Integer.parseInt(productoIdParam.trim());
        } catch (Exception e) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"error\":\"productoId invalido\"}");
            return;
        }

        if (tipoContacto == null || !(tipoContacto.equals("whatsapp") || tipoContacto.equals("correo") || tipoContacto.equals("envio"))) {
            response.setStatus(400);
            out.print("{\"ok\":false,\"error\":\"tipo invalido, debe ser whatsapp, correo o envio\"}");
            return;
        }

        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        // 🔧 El usuario_id (dueño del producto) se busca del lado del
        // servidor a partir del producto_id, en la misma sentencia INSERT
        // (INSERT...SELECT), en vez de confiar en un valor mandado por el
        // navegador. Si el producto_id no existe, no se inserta nada
        // (0 filas afectadas) y se responde con error.
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