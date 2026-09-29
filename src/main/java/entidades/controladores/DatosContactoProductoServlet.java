package entidades.controladores;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

import entidades.DatabaseConnection;

@WebServlet("/api/productos/datosContacto")
public class DatosContactoProductoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ").replace("\t", " ");
    }

    private static String vacioSiNull(String s) { return s == null ? "" : s.trim(); }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");

        String idParam = request.getParameter("usuario_id");
        int usuarioId;
        try {
            usuarioId = Integer.parseInt(idParam);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "usuario_id inválido");
            return;
        }

        String telefono = "", correo = "", provincia = "", ciudad = "", empresa = "";

        try (Connection conn = DatabaseConnection.getConnection()) {

            // 1) Último producto del usuario
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT TOP 1 telefono, correo, provincia, ciudad, Nombre_Empresa "
                  + "FROM Productos WHERE usuario_id = ? ORDER BY id DESC")) {
                ps.setInt(1, usuarioId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        telefono  = vacioSiNull(rs.getString("telefono"));
                        correo    = vacioSiNull(rs.getString("correo"));
                        provincia = vacioSiNull(rs.getString("provincia"));
                        ciudad    = vacioSiNull(rs.getString("ciudad"));
                        empresa   = vacioSiNull(rs.getString("Nombre_Empresa"));
                    }
                }
            }

            // 2) Respaldo: solicitud de vendedor (solo lo que falte)
            if (telefono.isEmpty() || provincia.isEmpty() || ciudad.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT TOP 1 provincia, canton, telefono "
                      + "FROM SolicitudesDeVendedor WHERE usuario_id = ? ORDER BY id DESC")) {
                    ps.setInt(1, usuarioId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            if (provincia.isEmpty()) provincia = vacioSiNull(rs.getString("provincia"));
                            if (ciudad.isEmpty())    ciudad    = vacioSiNull(rs.getString("canton"));
                            if (telefono.isEmpty())  telefono  = vacioSiNull(rs.getString("telefono"));
                        }
                    }
                }
            }

            // 3) Respaldo: cuenta de usuario (correo; nombre solo como empresa si no hay otra)
            if (correo.isEmpty() || empresa.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT correo, nombre FROM Usuarios WHERE id = ?")) {
                    ps.setInt(1, usuarioId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            if (correo.isEmpty())  correo  = vacioSiNull(rs.getString("correo"));
                            if (empresa.isEmpty()) empresa = vacioSiNull(rs.getString("nombre"));
                        }
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error en base de datos");
            return;
        }

        response.getWriter().write("{"
            + "\"telefono\":\""  + esc(telefono)  + "\","
            + "\"correo\":\""    + esc(correo)    + "\","
            + "\"provincia\":\"" + esc(provincia) + "\","
            + "\"ciudad\":\""    + esc(ciudad)    + "\","
            + "\"empresa\":\""   + esc(empresa)   + "\""
            + "}");
    }
}