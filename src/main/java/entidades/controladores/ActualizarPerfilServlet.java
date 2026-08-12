package entidades.controladores;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import entidades.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Actualiza el nombre, correo, los intereses de notificación (categorías) y
 * las provincias de preferencia del usuario actualmente logueado.
 *
 * Ambos se guardan en la tabla aparte Intereses (usuario_id, interes,
 * provincia) — no se toca la tabla Usuarios para esto. Cada fila de esa
 * tabla representa UNA categoría marcada (columna "interes", "provincia"
 * en NULL) o UNA provincia marcada (columna "provincia", "interes" en
 * NULL); nunca las dos a la vez en la misma fila. Así se reutiliza la
 * misma tabla para ambos filtros sin mezclarlos.
 *
 * Igual que PerfilServlet: el usuario_id sale de la SESIÓN del servidor,
 * nunca de un parámetro del cliente — así nadie puede editar el perfil
 * de otra persona.
 */
@WebServlet("/api/perfil/actualizar")
public class ActualizarPerfilServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // categorías válidas — cualquier otra cosa que llegue en el parámetro
    // "intereses" se ignora, para no guardar basura en la base de datos.
    private static final Set<String> CATEGORIAS_VALIDAS = new LinkedHashSet<>(
            Arrays.asList("Damas", "Caballeros", "Niños", "Servicios"));

    // 🆕 provincias válidas — mismo criterio, deben coincidir exactamente
    // con las opciones del <select> de provincia al agregar un producto.
    private static final Set<String> PROVINCIAS_VALIDAS = new LinkedHashSet<>(
            Arrays.asList("San José", "Alajuela", "Cartago", "Heredia",
                    "Guanacaste", "Puntarenas", "Limón"));

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

        String nombre = request.getParameter("nombre");
        String correo = request.getParameter("correo");
        String interesesParam = request.getParameter("intereses"); // ej: "Damas,Niños" o "" o null
        String provinciasParam = request.getParameter("provincias"); // 🆕 ej: "San José,Heredia" o "" o null

        if (nombre == null || nombre.trim().isEmpty() || correo == null || correo.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Nombre y correo son obligatorios");
            return;
        }

        nombre = nombre.trim();
        correo = correo.trim();

        // Validación simple de formato de correo
        if (!correo.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("El correo no tiene un formato válido");
            return;
        }

        // Filtrar solo categorías válidas que mandó el frontend
        Set<String> interesesValidos = new LinkedHashSet<>();
        if (interesesParam != null && !interesesParam.trim().isEmpty()) {
            for (String parte : interesesParam.split(",")) {
                String categoria = parte.trim();
                if (CATEGORIAS_VALIDAS.contains(categoria)) {
                    interesesValidos.add(categoria);
                }
            }
        }

        // 🆕 Filtrar solo provincias válidas que mandó el frontend
        Set<String> provinciasValidas = new LinkedHashSet<>();
        if (provinciasParam != null && !provinciasParam.trim().isEmpty()) {
            for (String parte : provinciasParam.split(",")) {
                String provincia = parte.trim();
                if (PROVINCIAS_VALIDAS.contains(provincia)) {
                    provinciasValidas.add(provincia);
                }
            }
        }

        try (Connection conn = DatabaseConnection.getConnection()) {

            // Verificar que el correo no esté en uso por OTRO usuario
            String sqlVerificar = "SELECT id FROM Usuarios WHERE correo = ? AND id <> ?";
            try (PreparedStatement stmtVerificar = conn.prepareStatement(sqlVerificar)) {
                stmtVerificar.setString(1, correo);
                stmtVerificar.setInt(2, usuarioId);
                try (ResultSet rsVerificar = stmtVerificar.executeQuery()) {
                    if (rsVerificar.next()) {
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        response.getWriter().write("Ese correo ya está en uso por otra cuenta");
                        return;
                    }
                }
            }

            boolean autoCommitOriginal = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                String sqlUpdate = "UPDATE Usuarios SET nombre = ?, correo = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sqlUpdate)) {
                    stmt.setString(1, nombre);
                    stmt.setString(2, correo);
                    stmt.setInt(3, usuarioId);
                    stmt.executeUpdate();
                }

                // Reemplazar los intereses (categorías): borrar solo las filas
                // de categoría anteriores (interes IS NOT NULL) e insertar las
                // que llegaron marcadas ahora. No toca las filas de provincia.
                String sqlBorrarIntereses = "DELETE FROM Intereses WHERE usuario_id = ? AND interes IS NOT NULL";
                try (PreparedStatement stmtBorrar = conn.prepareStatement(sqlBorrarIntereses)) {
                    stmtBorrar.setInt(1, usuarioId);
                    stmtBorrar.executeUpdate();
                }

                if (!interesesValidos.isEmpty()) {
                    String sqlInsertarInteres = "INSERT INTO Intereses (usuario_id, interes) VALUES (?, ?)";
                    try (PreparedStatement stmtInsertar = conn.prepareStatement(sqlInsertarInteres)) {
                        for (String categoria : interesesValidos) {
                            stmtInsertar.setInt(1, usuarioId);
                            stmtInsertar.setString(2, categoria);
                            stmtInsertar.addBatch();
                        }
                        stmtInsertar.executeBatch();
                    }
                }

                // 🆕 Reemplazar las provincias de preferencia: mismo patrón,
                // pero borrando/insertando solo filas de provincia
                // (provincia IS NOT NULL), sin tocar las de categoría.
                String sqlBorrarProvincias = "DELETE FROM Intereses WHERE usuario_id = ? AND provincia IS NOT NULL";
                try (PreparedStatement stmtBorrarProv = conn.prepareStatement(sqlBorrarProvincias)) {
                    stmtBorrarProv.setInt(1, usuarioId);
                    stmtBorrarProv.executeUpdate();
                }

                if (!provinciasValidas.isEmpty()) {
                    String sqlInsertarProvincia = "INSERT INTO Intereses (usuario_id, provincia) VALUES (?, ?)";
                    try (PreparedStatement stmtInsertarProv = conn.prepareStatement(sqlInsertarProvincia)) {
                        for (String provincia : provinciasValidas) {
                            stmtInsertarProv.setInt(1, usuarioId);
                            stmtInsertarProv.setString(2, provincia);
                            stmtInsertarProv.addBatch();
                        }
                        stmtInsertarProv.executeBatch();
                    }
                }

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(autoCommitOriginal);
            }

            // Mantener la sesión consistente con los nuevos datos
            session.setAttribute("nombreUsuario", nombre);
            session.setAttribute("correoUsuario", correo);
            session.setAttribute("nombreVendedor", nombre);
            session.setAttribute("correoVendedor", correo);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("Perfil actualizado correctamente");

            System.out.println("[ActualizarPerfilServlet] ✅ Usuario " + usuarioId + " actualizó su perfil.");

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error en el servidor: " + e.getMessage());
        }
    }
}
