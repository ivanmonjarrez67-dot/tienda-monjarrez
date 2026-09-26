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
 * Guarda (o actualiza) la descripción corta y las redes sociales del
 * vendedor logueado. Usa la misma tabla IconosVendedor que ya guarda el
 * icono de tienda (mismo upsert por vendedor_id), pero solo toca estas
 * 4 columnas nuevas — nunca la columna "icono", así que no interfiere
 * con GuardarIconoVendedorServlet.
 *
 * Todos los campos son opcionales: si llegan vacíos se guardan como NULL,
 * y el frontend simplemente no muestra el icono/campo correspondiente.
 */
@WebServlet("/api/perfil/redes")
public class GuardarDescripcionRedesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final int MAX_DESCRIPCION = 160;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        Object usuarioIdObj = (session != null) ? session.getAttribute("usuarioId") : null;

        if (usuarioIdObj == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No hay sesión activa");
            return;
        }

        String descripcion = limpiar(request.getParameter("descripcion"));
        if (descripcion != null && descripcion.length() > MAX_DESCRIPCION) {
            descripcion = descripcion.substring(0, MAX_DESCRIPCION);
        }
        String instagram = limpiar(request.getParameter("instagram"));
        String tiktok = limpiar(request.getParameter("tiktok"));
        String facebook = limpiar(request.getParameter("facebook"));

        int usuarioId = (Integer) usuarioIdObj;

        try (Connection conn = DatabaseConnection.getConnection()) {

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

            try (PreparedStatement stmtCheck = conn.prepareStatement(
                    "SELECT id FROM IconosVendedor WHERE vendedor_id = ?")) {
                stmtCheck.setInt(1, vendedorId);
                try (ResultSet rsCheck = stmtCheck.executeQuery()) {
                    if (rsCheck.next()) {
                        try (PreparedStatement stmtUpd = conn.prepareStatement(
                                "UPDATE IconosVendedor SET descripcion = ?, instagram = ?, tiktok = ?, facebook = ?, "
                                + "fecha_actualizacion = GETDATE() WHERE vendedor_id = ?")) {
                            stmtUpd.setString(1, descripcion);
                            stmtUpd.setString(2, instagram);
                            stmtUpd.setString(3, tiktok);
                            stmtUpd.setString(4, facebook);
                            stmtUpd.setInt(5, vendedorId);
                            stmtUpd.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement stmtIns = conn.prepareStatement(
                                "INSERT INTO IconosVendedor (vendedor_id, descripcion, instagram, tiktok, facebook) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
                            stmtIns.setInt(1, vendedorId);
                            stmtIns.setString(2, descripcion);
                            stmtIns.setString(3, instagram);
                            stmtIns.setString(4, tiktok);
                            stmtIns.setString(5, facebook);
                            stmtIns.executeUpdate();
                        }
                    }
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("✅ Descripción y redes guardadas correctamente");

        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error en base de datos: " + e.getMessage());
        }
    }

    private String limpiar(String valor) {
        if (valor == null) return null;
        String v = valor.trim();
        return v.isEmpty() ? null : v;
    }
}