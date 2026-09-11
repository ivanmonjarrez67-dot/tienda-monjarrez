package entidades.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import entidades.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Guarda (o actualiza) el icono de tienda del vendedor logueado.
 * Recibe la URL ya subida a Cloudinary (mismo flujo que las imágenes de
 * producto, ver /GuardarProductoArchivo) y la asocia al vendedor_id
 * correspondiente al usuario_id de la sesión.
 */
@WebServlet("/api/perfil/icono")
public class GuardarIconoVendedorServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        Object usuarioIdObj = (session != null) ? session.getAttribute("usuarioId") : null;

        if (usuarioIdObj == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No hay sesión activa");
            return;
        }

        String icono = request.getParameter("icono");
        if (icono == null || icono.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Falta la URL del icono");
            return;
        }

        int usuarioId = (Integer) usuarioIdObj;

        try (Connection conn = DatabaseConnection.getConnection()) {

            // Buscar el vendedor_id correspondiente a este usuario
            int vendedorId;
            try (PreparedStatement stmtV = conn.prepareStatement(
                    "SELECT id FROM Vendedores WHERE usuario_id = ?")) {
                stmtV.setInt(1, usuarioId);
                try (ResultSet rs = stmtV.executeQuery()) {
                    if (!rs.next()) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "El usuario no es un vendedor registrado");
                        return;
                    }
                    vendedorId = rs.getInt("id");
                }
            }

            // Upsert: si ya tiene icono, lo actualiza; si no, lo crea.
            try (PreparedStatement stmtCheck = conn.prepareStatement(
                    "SELECT id FROM IconosVendedor WHERE vendedor_id = ?")) {
                stmtCheck.setInt(1, vendedorId);
                try (ResultSet rsCheck = stmtCheck.executeQuery()) {
                    if (rsCheck.next()) {
                        try (PreparedStatement stmtUpd = conn.prepareStatement(
                                "UPDATE IconosVendedor SET icono = ?, fecha_actualizacion = GETDATE() WHERE vendedor_id = ?")) {
                            stmtUpd.setString(1, icono.trim());
                            stmtUpd.setInt(2, vendedorId);
                            stmtUpd.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement stmtIns = conn.prepareStatement(
                                "INSERT INTO IconosVendedor (vendedor_id, icono) VALUES (?, ?)")) {
                            stmtIns.setInt(1, vendedorId);
                            stmtIns.setString(2, icono.trim());
                            stmtIns.executeUpdate();
                        }
                    }
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("✅ Icono guardado correctamente");

        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error en base de datos: " + e.getMessage());
        }
    }
}