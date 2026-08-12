package entidades;
import java.util.List;

/**
 *
 * @author Iván Monjarrez
 */
public class Producto {
     private int id;
     private String provincia;
     private String cantón;
    private String nombre;
    private String descripcion;
    private double precio;
    private String correo;

    // private String name;
    // private String description;
    // private String imageUrl;
    // private double price;
    // private String cedula;
    // private String empresa;
    
    
    private Vendedor vendedor; // Referencia al Vendedor
    private List<Double> valoraciones; // Lista de valoraciones

    /**
     * @param id
     * @param provincia
     * @param cantón
     * @param nombre
     * @param descripcion
     * @param precio
     * @param correo
     */
    public Producto(int id, String provincia, String cantón, String nombre, String descripcion, double precio, String correo) {
        this.id = id;
        this.provincia = provincia;
        this.cantón = cantón;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.correo = correo;
        // this.vendedor = vendedor;
        // this.valoraciones = valoraciones;
    }

    
    
    
    
    // Método para calcular la valoración promedio
    public double calcularValoracion() {
        if (valoraciones.isEmpty()) {
            return 0; 
        }
        double suma = 0;
        for (double valoracion : valoraciones) {
            suma += valoracion; 
        }
        return suma / valoraciones.size(); // Retornamos la media de las valoraciones de todos los clientes
    }
    
    // Método para actualizar los detalles del producto
    public void actualizarDetalles(String nombre, String descripcion, double precio) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
    }
    public String getDescripcion() {
        return descripcion;
    }
    
    // Métodos para agregar y obtener valoraciones
    public void agregarValoracion(double valoracion) {
        valoraciones.add(valoracion);
    }

    
    public List<Double> getValoraciones() {
        return valoraciones;
    }

    public int getId() {
        return id;
    }

    public String getProvincia() {
        return provincia;
    }

    public String getCantón() {
        return cantón;
    }

    public String getNombre() {
        return nombre;
    }

    public double getPrecio() {
        return precio;
    }

    public Vendedor getVendedor() {
        return vendedor;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setProvincia(String provincia) {
        this.provincia = provincia;
    }

    public void setCantón(String cantón) {
        this.cantón = cantón;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public void setPrecio(double precio) {
        this.precio = precio;
    }

    public void setVendedor(Vendedor vendedor) {
        this.vendedor = vendedor;
    }

    public void setValoraciones(List<Double> valoraciones) {
        this.valoraciones = valoraciones;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }
    
    
    

    

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Producto: \n");
        sb.append("\nid :").append(id);
        sb.append("\n nombre :").append(nombre);
        sb.append("\n descripcion :").append(descripcion);
        sb.append("\n precio :").append(precio);
        sb.append("\n vendedor :").append(vendedor);
        sb.append("\n valoraciones :").append(valoraciones);
        sb.append('}');
        return sb.toString();
    }
    
    
    

    
}

