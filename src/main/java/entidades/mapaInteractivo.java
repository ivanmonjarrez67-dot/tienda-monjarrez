package entidades;

import java.util.ArrayList;
import java.util.List;
/**
 *
 * @author Iván Monjarrez
 */
public class mapaInteractivo {
    
       private List<Vendedor> vendedores; // Lista de vendedores disponibles
    private double usuarioLatitud; // Latitud del usuario
    private double usuarioLongitud; // Longitud del usuario

    // Constructor
    public mapaInteractivo(double usuarioLatitud, double usuarioLongitud) {
        this.vendedores = new ArrayList<>();
        this.usuarioLatitud = usuarioLatitud;
        this.usuarioLongitud = usuarioLongitud;
    }

    // Método para agregar un vendedor al mapa
    public void agregarVendedor(Vendedor vendedor) {
        vendedores.add(vendedor);
    }

    // Método para mostrar vendedores cercanos
    public void mostrarVendedoresCercanos() {
        System.out.println("Vendedores cercanos:");
        for (Vendedor vendedor : vendedores) {
            // Aquí se asume que el vendedor tiene métodos para obtener su ubicación
            double distancia = calcularDistancia(usuarioLatitud, usuarioLongitud, vendedor.getUbicacionLatitud(), vendedor.getUbicacionLongitud());
            if (distancia <= 5.0) { // Por ejemplo, vendedores dentro de 5 km
                System.out.println(vendedor.getNombre() + " - Distancia: " + distancia + " km");
            }
        }
    }

    // Método para obtener indicaciones hacia un vendedor específico
    public void obtenerIndicaciones(Vendedor vendedor) {
        // Aquí se asume que el vendedor tiene métodos para obtener su ubicación
        double distancia = calcularDistancia(usuarioLatitud, usuarioLongitud, vendedor.getUbicacionLatitud(), vendedor.getUbicacionLongitud());
        System.out.println("Indicaciones para llegar a " + vendedor.getNombre() + ":");
        System.out.println("Distancia: " + distancia + " km");
        // agregar lógica para mostrar indicaciones más detalladas
    }

    // Método para calcular la distancia entre dos puntos geográficos
    private double calcularDistancia(double lat1, double lon1, double lat2, double lon2) {
        // Implementación simple de la fórmula de Haversine para calcular la distancia
        final int R = 6371; // Radio de la Tierra en km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // Distancia en km
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("mapaInteractivo :");
        sb.append("\nvendedores :").append(vendedores);
        sb.append("\nusuarioLatitud :").append(usuarioLatitud);
        sb.append("\nusuarioLongitud :").append(usuarioLongitud);
        
        return sb.toString();
    }
    
    
    
    
    
    
    
   
}

