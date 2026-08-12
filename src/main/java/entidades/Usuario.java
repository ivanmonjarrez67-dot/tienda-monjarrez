package entidades;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 *
 * @author Iván Monjarrez
 */
public class Usuario implements Serializable{
    private static final long serialVersionUID = 1L; // Agregar un UID para la serialización
    
   private static List<Usuario> usuariosRegistrados = new ArrayList<>(); // Lista de usuarios registrados
    
    private int id;
    private String nombre;
    String correo;
    private String contraseña;
    private String tipo; // Vendedor o Comprador 
    private String ubicacion;
    private boolean verificado;
    private String cedula;

    
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);
    
    
     // Expresión regular para validar el formato de la cédula (debe empezar con 1-8 y tener 9 dígitos)
    //private static final String CEDULA_REGEX = "^[1-8][0-9]{8}$"; // Comienza con 1-8 y seguido de 8 dígitos
    //private static final Pattern CEDULA_PATTERN = Pattern.compile(CEDULA_REGEX);
    
    

    public Usuario(int id, String nombre, String cedula, String correo, String contraseña, String tipo, String ubicacion) throws IllegalArgumentException {
        if (!validarCorreo(correo)) {
            throw new IllegalArgumentException("El correo proporcionado no es válido.");
        }
        if (contraseña.length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
        }
        
        
        this.id = id;
        this.nombre = nombre;
        this.correo = correo;
        this.contraseña = hashContraseña(contraseña); // Almacenar la contraseña de forma segura
        this.tipo = tipo;
        this.ubicacion = ubicacion;
        this.verificado = false; // Por defecto, no verificado
        this.cedula = cedula;
    }
    
    
    
    

  
    public boolean registrar() {
        // Verificar si el nombre de usuario ya existe
        for (Usuario usuario : usuariosRegistrados) {
            if (usuario.getNombre().equals(this.nombre)) {
                System.out.println("El nombre de usuario ya existe. Por favor, elige otro.");
                return false; // Nombre de usuario ya existe
            }
        }
        
        // Si el nombre de usuario es único, se agrega a la lista
        usuariosRegistrados.add(this);
        System.out.println("Usuario registrado exitosamente: " + this.nombre);
        return true; // Registro exitoso
    }
    
    
    

    public static void mostrarUsuarios() {
        if (usuariosRegistrados.isEmpty()) {
            System.out.println("No hay usuarios registrados.");
        } else {
            System.out.println("Usuarios registrados:");
            for (Usuario usuario : usuariosRegistrados) {
                System.out.println(usuario);
            }
        }
    }

    
    
    public static boolean modificarUsuario(String nombre, String nuevoNombre, String nuevoCorreo, String nuevoTipo, String nuevaUbicacion) {
        for (Usuario usuario : usuariosRegistrados) {
            if (usuario.getNombre().equals(nombre)) {
                usuario.setNombre(nuevoNombre);
                usuario.setCorreo(nuevoCorreo);
                usuario.setTipo(nuevoTipo);
                usuario.setUbicacion(nuevaUbicacion);
                System.out.println("Usuario modificado exitosamente: " + usuario);
                return true; // Modificación exitosa
            }
        }
        System.out.println("Usuario no encontrado.");
        return false; // Usuario no encontrado
    }

    public static void reiniciarUsuarios() {
        usuariosRegistrados.clear();
        System.out.println("Lista de usuarios reiniciada.");
    }
    
    
    
    
    
    
    public boolean iniciarSesion(String correo, String contraseña) {
        // Lógica para iniciar sesión
        if (this.correo.equals(correo) && this.contraseña.equals(hashContraseña(contraseña))) {
            System.out.println("Inicio de sesión exitoso para: " + this.nombre);
            return true;
        } else {
            System.out.println("Error en el inicio de sesión. Verifica tus credenciales.");
            return false;
        }
    }
    
    
    
    
    
    
    
    public void verificarIdentidad() {
        // Verificar si el usuario es un vendedor
    if (this instanceof Vendedor) {
        Vendedor vendedor = (Vendedor) this; // Hacer un casting a Vendedor
        if (!vendedor.isSuscripcionActiva()) {
            System.out.println("Bienvenido Empresario " + vendedor.getNombre() + " Verificacion"); //!!!!
            return; 
        } else {
            System.out.println("Diríjase a pagar su suscripción, " + vendedor.getNombre() + ".");
            return; // No se verifica la identidad
        }
    }
    
  
    
    // Si es un comprador, se le da la bienvenida
    System.out.println("Bienvenido " + this.getNombre() + " C");
    }

    
    
    
  
    private static boolean validarCorreo(String correo) {
        return EMAIL_PATTERN.matcher(correo).matches();
    }
    
    
    
    

    // Método simulado para "hash" de la contraseña
    private String hashContraseña(String contraseña) {
         try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(contraseña.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
    }
    }

    
    
    
    // Getters y Setters
    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreo() {
        return correo;
    }

      public String getTipo() {
        return tipo;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public boolean isVerificado() {
        return verificado;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setCorreo(String correo) {
        if (!validarCorreo(correo)) {
            throw new IllegalArgumentException("El correo proporcionado no es válido.");
        }
        this.correo = correo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public String getContraseña() {
        return contraseña;
    }

    public String getCedula() {
        return cedula;
    }

    public void setCedula(String cedula) {
        this.cedula = cedula;
    }
    
    
   

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Usuario\n");
        sb.append("\nid :").append(id);
        sb.append("\nnombre :").append(nombre);
        sb.append("\n Cudula :").append(cedula);
        sb.append("\n correo :").append(correo);
        sb.append("\n tipo :").append(tipo);
        sb.append("\n ubicacion :").append(ubicacion);
        sb.append("\n verificado :").append(verificado);
        
        
        return sb.toString();
    }
   
}
