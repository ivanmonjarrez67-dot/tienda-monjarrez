package entidades;

/**
 *
 * @author Iván Monjarrez
 */
public class Vendedor extends Usuario{
    
    private Suscripcion suscripcion; // Atributo para la suscripción
    private String productos;
    private String logo;
    private String certificado;
    private String video;
     private double ubicacionLatitud; // Latitud del vendedor
    private double ubicacionLongitud; // Longitud del vendedor

   public Vendedor(int id, String nombre, String cedula,String correo, String contraseña, String ubicacion, String productos, String logo, String certificado, String video, String tipoSuscripcion, double ubicacionLatitud, double ubicacionLongitud) {
        super(id, nombre, cedula,correo, contraseña, "Vendedor", ubicacion);
        this.suscripcion = new Suscripcion(tipoSuscripcion); // Inicializa la suscripción
        this.productos = productos;
        this.logo = logo;
        this.certificado = certificado;
        this.video = video;
        this.ubicacionLatitud = ubicacionLatitud; // Inicializa la latitud
        this.ubicacionLongitud = ubicacionLongitud; // Inicializa la longitud
    }
   
   
   

    // Métodos para obtener la ubicación
    public double getUbicacionLatitud() {    //El cliente debe digitarlas 
        return ubicacionLatitud;
    }

    public double getUbicacionLongitud() {
        return ubicacionLongitud;
    }

    
    
    

    public boolean cancelarSuscripcion() {
        if (suscripcion != null) {
            // Cambiar el estado de la suscripción a inactiva
            this.suscripcion = null; 
            System.out.println("Suscripción cancelada para: " + this.getNombre());
            return true;
        } else {
            System.out.println("No hay suscripción activa para cancelar.");
            return false;
        }
    }

    public boolean isSuscripcionActiva() {
        return suscripcion != null; // Verifica si hay una suscripción activa
    }

    
    
    
    
    
    
    public String publicarProducto() {
        // Lógica para publicar un producto
        return "Producto publicado.";
    }

    public String gestionarPerfil() {
        // Lógica para gestionar el perfil
        return "Perfil gestionado.";
    }

    public String obtenerEstadisticas() {
        // Lógica para obtener estadísticas
        return "Estadísticas obtenidas.";
    }

    //public String destacarProducto() {
        // Lógica para destacar un producto       //para que no haya competencia 
      //  return "Producto destacado.";
   // }
    
    
    
    
    
    
    

    @Override
    public String toString() {
        return "Vendedor\n" +
                "\nnombre :" + getNombre() +
                "\n correo :" + getCorreo() +
                "\n suscripcionActiva :" + isSuscripcionActiva() +
                "\n productos :" + productos  +
                "\n logo :" + logo  +
                "\n certificado :" + certificado +
                "\n video :" + video ;
    }
    
    
}

