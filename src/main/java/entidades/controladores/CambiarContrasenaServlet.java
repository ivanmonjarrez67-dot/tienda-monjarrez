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

/**
 * Cambia la contraseña del usuario actualmente logueado.
 *
 * Flujo:
 *   1) Se lee el usuario_id de la SESIÓN (nunca de un parámetro del
 *      cliente) — mismo criterio que PerfilServlet / ActualizarPerfilServlet,
 *      así nadie puede cambiar la contraseña de otra cuenta.
 *   2) Se hashea "actual" con SHA-256 (mismo algoritmo que
 *      LoginVendedorServlet / LoginCompradorServlet) y se compara contra
 *      lo que hay guardado en Usuarios.contraseña para ESE id. Si no
 *      coincide, se responde 401 y no se toca nada.
 *   3) Si coincide, se hashea "nueva" y se actualiza Usuarios.contraseña.
 *
 * Requiere que exista session.setAttribute("usuarioId", ...) — ya lo hacen
 * LoginCompradorServlet y LoginVendedorServlet en el login general.
 */
@WebServlet("/api/perfil/cambiar-contrasena")
public class CambiarContrasenaServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final int LONGITUD_MINIMA = 6;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        Object usuarioIdObj = (session != null) ? session.getAttribute("usuarioId") : null;

        if (usuarioIdObj == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("No hay sesión activa");
            return;
        }

        int usuarioId = (Integer) usuarioIdObj;

        String actual = request.getParameter("actual");
        String nueva = request.getParameter("nueva");

        if (actual == null || actual.isEmpty() || nueva == null || nueva.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Debe ingresar la contraseña actual y la nueva");
            return;
        }

        if (nueva.length() < LONGITUD_MINIMA) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("La nueva contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres");
            return;
        }

        if (actual.equals(nueva)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("La nueva contraseña debe ser diferente a la actual");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {

            String actualHash = hashContraseña(actual);

            // 1) Verificar que "actual" coincida con la contraseña guardada
            //    para ESTE usuario (usando el id de sesión, no uno del cliente).
            String sqlVerificar = "SELECT id FROM Usuarios WHERE id = ? AND contraseña = ?";
            try (PreparedStatement stmtVerificar = conn.prepareStatement(sqlVerificar)) {
                stmtVerificar.setInt(1, usuarioId);
                stmtVerificar.setString(2, actualHash);
                try (ResultSet rs = stmtVerificar.executeQuery()) {
                    if (!rs.next()) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("La contraseña actual no es correcta");
                        return;
                    }
                }
            }

            // 2) Guardar la nueva contraseña (hasheada)
            String nuevaHash = hashContraseña(nueva);
            String sqlActualizar = "UPDATE Usuarios SET contraseña = ? WHERE id = ?";
            try (PreparedStatement stmtActualizar = conn.prepareStatement(sqlActualizar)) {
                stmtActualizar.setString(1, nuevaHash);
                stmtActualizar.setInt(2, usuarioId);
                stmtActualizar.executeUpdate();
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("Contraseña actualizada correctamente");

            System.out.println("[CambiarContrasenaServlet] ✅ Usuario " + usuarioId + " cambió su contraseña.");

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error en el servidor: " + e.getMessage());
        }
    }

    // Mismo algoritmo (SHA-256, hex en minúsculas) que LoginVendedorServlet
    // y LoginCompradorServlet — así el hash calculado aquí siempre coincide
    // con el que ya está guardado en la base de datos.
    private String hashContraseña(String contraseña) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contraseña.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}