package entidades.controladores;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import entidades.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/admin/login")
public class AdminLoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String cedula = request.getParameter("cedula");
        String contraseña = request.getParameter("password");

        if (cedula == null || cedula.trim().isEmpty() || contraseña == null || contraseña.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Debe ingresar todos los datos");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String hashed = hashContraseña(contraseña);

            String sql = "SELECT id, nombre FROM Administradores WHERE cedula = ? AND contraseña = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, cedula);
                stmt.setString(2, hashed);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        HttpSession session = request.getSession();
                        session.setAttribute("adminId", rs.getInt("id"));
                        session.setAttribute("adminNombre", rs.getString("nombre"));

                        response.setStatus(HttpServletResponse.SC_OK);
                        response.getWriter().write("OK");
                    } else {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("Credenciales incorrectas");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error en el servidor: " + e.getMessage());
        }
    }

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
}