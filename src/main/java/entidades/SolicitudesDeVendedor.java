package entidades;


import java.io.Serializable;

/**
 *
 * @author Iván Monjarrez
 */
public class SolicitudesDeVendedor implements Serializable{
    
    private static final long serialVersionUID = 1L;
    
    private int id;
    private String provincia;
    private String canton;
    private String nombreProducto;
    private String descripcion;
    private double precioPromedio; // Cambiado a double para manejar precios
    private String correoVendedor;

   
    public SolicitudesDeVendedor(int id, String provincia, String canton, String nombreProducto, String descripcion, double precioPromedio, String correoVendedor) {
        this.id = id;
        this.provincia = provincia;
        this.canton = canton;
        this.nombreProducto = nombreProducto;
        this.descripcion = descripcion;
        this.precioPromedio = precioPromedio;
        this.correoVendedor = correoVendedor;
    }

    public int getId() {
        return id;
    }

    public String getProvincia() {
        return provincia;
    }

    public String getCanton() {
        return canton;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public double getPrecioPromedio() {
        return precioPromedio;
    }

    public String getCorreoVendedor() {
        return correoVendedor;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setProvincia(String provincia) {
        this.provincia = provincia;
    }

    public void setCanton(String canton) {
        this.canton = canton;
    }

    public void setNombreProducto(String nombreProducto) {
        this.nombreProducto = nombreProducto;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public void setPrecioPromedio(double precioPromedio) {
        this.precioPromedio = precioPromedio;
    }

    public void setCorreoVendedor(String correoVendedor) {
        this.correoVendedor = correoVendedor;
    }
}

