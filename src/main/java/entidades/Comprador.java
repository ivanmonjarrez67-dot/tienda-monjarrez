package entidades;
import java.util.ArrayList;
import java.util.List;
/**
 *
 * @author Iván Monjarrez
 */
public class Comprador extends Usuario{
    
   private List<Producto> productosGuardados;
    private List<String> alertas; // las alertas son de tipo String

    public Comprador(int id, String nombre, String cedula, String correo, String contraseña, String tipo, String ubicacion) {
        super(id, nombre,cedula, correo, contraseña, "Comprador", ubicacion);
        this.productosGuardados = new ArrayList<>();
        this.alertas = new ArrayList<>();
    }

    
    // Método para buscar un producto
    public List<Producto> buscarProducto(String palabraClave, List<Producto> todosLosProductos) {
        List<Producto> productosEncontrados = new ArrayList<>();
        String palabraClaveLower = palabraClave.toLowerCase(); // Convertir a minúsculas para búsqueda insensible

        for (Producto producto : todosLosProductos) {
            // Comprobar si la palabra clave está en el nombre o en la descripción
            if (producto.getNombre().toLowerCase().contains(palabraClaveLower) || 
                producto.getDescripcion().toLowerCase().contains(palabraClaveLower)) {
                productosEncontrados.add(producto); // Agregar a la lista de resultados
            }
        }

        return productosEncontrados; // Retornar la lista de productos encontrados
    }
    
    
    
    
    
    // Método para realizar una compra
    public void realizarCompra(Producto producto) {
        //  lógica para realizar la compra
        System.out.println("Compra realizada para el producto: " + producto.getNombre());
    }

    
    
    
    
    
    // Método para guardar un producto
    public void guardarProducto(Producto producto) {
        productosGuardados.add(producto);
        System.out.println("Producto guardado: " + producto.getNombre());
    }

    // Getters y Setters
    public List<Producto> getProductosGuardados() {
        return productosGuardados;
    }

    public List<String> getAlertas() {
        return alertas;
    }

    public void agregarAlerta(String alerta) {
        alertas.add(alerta);
    } 

    @Override
    public String toString() {
        return "Comprador{" + "productosGuardados=" + productosGuardados + ", alertas=" + alertas + '}';
    }
    
    
    
    
     
    
}
