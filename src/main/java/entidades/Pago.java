package entidades;

/**
 *
 * @author Iván Monjarrez
 */
public class Pago {
    
     private int id; // Identificador único del pago
    private double monto; // Monto del pago
    private String metodoPago; // Método de pago (Tarjeta/SIMPE)
    private String estado; // Estado del pago (Pendiente/Completado)

    
    public Pago(int id, double monto, String metodoPago) {
        this.id = id;
        this.monto = monto;
        this.metodoPago = metodoPago;
        this.estado = "Pendiente"; // Inicialmente, el estado es Pendiente
    }
    
    

    // Método para procesar el pago
    public void procesarPago() {
        //  lógica para procesar el pago
        //  el pago se procesa correctamente
        this.estado = "Completado"; // Cambiamos el estado a Completado
        System.out.println("Pago procesado con éxito. Monto: " + monto + ", Método de pago: " + metodoPago);
    }

    
    
    
    
    
    
    
    
    // Método para enviar un comprobante
    public void enviarComprobante() {
        if (estado.equals("Completado")) {
            System.out.println("Comprobante enviado para el pago ID: " + id + ", Monto: " + monto);
        } else {
            System.out.println("El pago no está completado. No se puede enviar el comprobante.");
        }
    }

    // Getters
    public int getId() {
        return id;
    }

    public double getMonto() {
        return monto;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public String getEstado() {
        return estado;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Pago\n");
        sb.append("\nid :").append(id);
        sb.append("\nmonto :").append(monto);
        sb.append("\nmetodoPago :").append(metodoPago);
        sb.append("\nestado :").append(estado);
        
        return sb.toString();
    }
    
    
    
    
    
    
    
    
    
    
    
}
