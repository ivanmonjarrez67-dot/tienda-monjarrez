package entidades;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Iván Monjarrez
 */
public class Suscripcion {
    
      
    public static final double PRECIO_BASICA = 15000.0; // Precio de la suscripción básica
    public static final double PRECIO_AVANZADA = 30000.0; // Precio de la suscripción avanzada

    private String tipo; // Tipo de suscripción (Básica/Avanzada)
    private double costo; // Costo de la suscripción
    private List<String> beneficios; // Lista de beneficios

   
    public Suscripcion(String tipo) {
        this.tipo = tipo;
        this.beneficios = new ArrayList<>();
        actualizarSuscripcion(); // Actualiza el costo y beneficios al crear la suscripción
    }

    // Método para actualizar la suscripción
    public void actualizarSuscripcion() {
        if (tipo.equalsIgnoreCase("Básica")) {
            this.costo = PRECIO_BASICA;
            beneficios.clear(); // Limpiar beneficios anteriores
            beneficios.add("Acceso a contenido básico");
            beneficios.add("Soporte técnico limitado");
            
            
            
        } else if (tipo.equalsIgnoreCase("Avanzada")) {
            this.costo = PRECIO_AVANZADA;
            beneficios.clear(); // Limpiar beneficios anteriores
            beneficios.add("Acceso a todo el contenido");
            beneficios.add("Soporte técnico prioritario");
            beneficios.add("Acceso a promociones exclusivas");
        } else {
            throw new IllegalArgumentException("Tipo de suscripción no válido. Debe ser 'Básica' o 'Avanzada'.");
        }
    }

    // Getters
    public String getTipo() {
        return tipo;
    }

    public double getCosto() {
        return costo;
    }

    public List<String> getBeneficios() {
        return beneficios;
    }

    // Método para cambiar el tipo de suscripción
    public void cambiarTipo(String nuevoTipo) {
        this.tipo = nuevoTipo;
        actualizarSuscripcion(); // Actualiza el costo y beneficios según el nuevo tipo
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Suscripcion \n");
        sb.append("\ntipo :").append(tipo);
        sb.append("\n costo :").append(costo);
        sb.append("\n beneficios :").append(beneficios);
        
        return sb.toString();
    }
    
    
    
    
    
      
}

