package entidades.controladores;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import entidades.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gestión de administradores: registrar uno nuevo o actualizar los propios
 * datos. Ambas acciones requieren sesión de admin activa — nadie puede
 * registrar un admin sin haber iniciado sesión primero.
 */
@WebServlet("/admin/gestionAdmins")
public class AdminGestionServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        Object adminIdAttr = req.getSession().getAttribute("adminId");
        if (adminIdAttr == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"error\":\"No autorizado\"}");
            return;
        }
        int adminIdSesion = (Integer) adminIdAttr;

        String accion = req.getParameter("accion");

        if ("registrar".equals(accion)) {
            registrar(req, resp, out);
        } else if ("actualizar".equals(accion)) {
            actualizar(req, resp, out, adminIdSesion);
        } else {
            resp.setStatus(400);
            out.print("{\"error\":\"acción inválida\"}");
        }
    }

    // Registra un admin nuevo. Solo llamable por alguien ya logueado
    // (verificado arriba en doPost).
    private void registrar(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) throws IOException {
        String nombre = req.getParameter("nombre");
        String cedula = req.getParameter("cedula");
        String password = req.getParameter("password");

        if (isBlank(nombre) || isBlank(cedula) || isBlank(password)) {
            resp.setStatus(400);
            out.print("{\"error\":\"Debe completar todos los campos\"}");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT id FROM Administradores WHERE cedula = ?")) {
                chk.setString(1, cedula.trim());
                try (ResultSet rs = chk.executeQuery()) {
                    if (rs.next()) {
                        resp.setStatus(409);
                        out.print("{\"error\":\"Ya existe un admin registrado con esa cédula\"}");
                        return;
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO Administradores (nombre, cedula, contraseña) VALUES (?, ?, ?)")) {
                ps.setString(1, nombre.trim());
                ps.setString(2, cedula.trim());
                ps.setString(3, hashContraseña(password));
                ps.executeUpdate();
            }
            out.print("{\"ok\":true}");
        } catch (SQLException e) {
            resp.setStatus(500);
            out.print("{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    // Actualiza SOLO los datos del admin que tiene la sesión abierta —
    // nunca recibe un id por parámetro, siempre usa el de la sesión, para
    // que un admin no pueda editar a otro por esta vía.
    private void actualizar(HttpServletRequest req, HttpServletResponse resp, PrintWriter out, int adminIdSesion) throws IOException {
        String nombre = req.getParameter("nombre");
        String password = req.getParameter("password"); // opcional: vacío = no se cambia la contraseña

        if (isBlank(nombre)) {
            resp.setStatus(400);
            out.print("{\"error\":\"El nombre no puede estar vacío\"}");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (!isBlank(password)) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE Administradores SET nombre = ?, contraseña = ? WHERE id = ?")) {
                    ps.setString(1, nombre.trim());
                    ps.setString(2, hashContraseña(password));
                    ps.setInt(3, adminIdSesion);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE Administradores SET nombre = ? WHERE id = ?")) {
                    ps.setString(1, nombre.trim());
                    ps.setInt(2, adminIdSesion);
                    ps.executeUpdate();
                }
            }
            req.getSession().setAttribute("adminNombre", nombre.trim());
            out.print("{\"ok\":true}");
        } catch (SQLException e) {
            resp.setStatus(500);
            out.print("{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private String hashContraseña(String contraseña) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contraseña.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}