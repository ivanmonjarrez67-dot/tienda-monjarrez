package entidades.Utilidades;

import java.awt.Image;
import java.awt.MediaTracker;
import javax.swing.ImageIcon;

/**
 *
 * @author Iván Monjarrez
 */
public class UtilidadesImagen {
    public static ImageIcon redimensionarImagen(String ruta, int ancho, int alto) {
    // Cargar la imagen desde la ruta proporcionada
    ImageIcon icono = new ImageIcon("C:\\Users\\Iván Monjarrez\\proyecto-web\\src\\main\\java\\entidades\\imagenes\\imagenes\\Polish_20250226_191728811.png"); // Cargar directamente desde la ruta
    // Verificar si la imagen se ha cargado correctamente
    if (icono.getImageLoadStatus() != MediaTracker.COMPLETE) {
        System.err.println("Error al cargar la imagen: " + ruta);
        return null; // O puedes devolver un icono por defecto
    }
    // Redimensionar la imagen
    Image imagen = icono.getImage().getScaledInstance(ancho, alto, Image.SCALE_SMOOTH);
    return new ImageIcon(imagen);
}
    }
