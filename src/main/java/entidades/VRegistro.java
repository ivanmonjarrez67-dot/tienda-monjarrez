package entidades;

import java.sql.*;

public class VRegistro {

    // Método para registrar usuario
 
public int registrarUsuario(String nombre, String correo, String contraseña, String tipo) {
    String checkSql = "SELECT COUNT(*) FROM Usuarios WHERE correo = ?";
    String sql = "INSERT INTO Usuarios (nombre, correo, contraseña, tipo) VALUES (?, ?, ?, ?)";

    try (Connection conn = DatabaseConnection.getConnection()) {

        // Validar si el correo ya existe
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, correo);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    System.out.println("El correo ya está registrado: " + correo);
                    return -1; // indicar correo duplicado
                }
            }
        }

        // Insertar usuario si no existe y devolver ID generado
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, nombre);
            pstmt.setString(2, correo);
            pstmt.setString(3, contraseña);
            pstmt.setString(4, tipo);

            int filas = pstmt.executeUpdate();
            if (filas > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1); // devolver el ID generado
                    }
                }
            }

        }

    } catch (SQLException e) {
        e.printStackTrace();
        return -2; // error general
    }

    return -2; // fallback error general
}



    //Metodo para registrar vendedor 
    public void registrarVendedor(String cedula, String ubicacion, int suscrito, String tipoSuscripcion,
            String correo) {
        
        String sql = "INSERT INTO Vendedores (cedula, ubicacion, Suscrito, tipo_Suscripcion, correo) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, cedula); // 1. cedula
            pstmt.setString(2, ubicacion); // 2. ubicacion
            pstmt.setInt(3, suscrito); // 3. Suscrito (debe ser un int)
            pstmt.setString(4, tipoSuscripcion); // 4. tipo_Suscripcion (debe ser un String)
            pstmt.setString(5, correo); // 5. correo

            pstmt.executeUpdate(); // Ejecutar la inserción

        } catch (SQLException e) {
            e.printStackTrace(); // Manejo de error
        }
    }

   


}
