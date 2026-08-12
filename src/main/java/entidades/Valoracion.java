package entidades;


/**
 *
 * @author Iván Monjarrez
 */
public class Valoracion {
    
     private int id; // Identificador único de la valoración
    private Comprador usuario; // Referencia al comprador que realiza la valoración
    private Producto producto; // Referencia al producto que se valora
    private double puntuacion; // Puntuación dada por el usuario
    private String comentario; // Comentario adicional del usuario

    
    public Valoracion(int id, Comprador usuario, Producto producto, double puntuacion, String comentario) {
        this.id = id;
        this.usuario = usuario;
        this.producto = producto;
        this.puntuacion = puntuacion;
        this.comentario = comentario;
    }

    // Método para guardar la valoración
    public void guardarValoracion() {
        
        //  implementar la lógica para guardar la valoración en una base de datos o lista
        producto.agregarValoracion(puntuacion); // Agrega la puntuación al producto
        System.out.println("Valoración guardada: " + comentario);
        
        
    }

    // Getters
    public int getId() {
        return id;
    }

    public Comprador getUsuario() {
        return usuario;
    }

    public Producto getProducto() {
        return producto;
    }

    public double getPuntuacion() {
        return puntuacion;
    }

    public String getComentario() {
        return comentario;
    } 
    
   
}
