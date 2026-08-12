package entidades.persistenciadatos;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

/**
 *
 * @author Iván Monjarrez
 */
public abstract class BaseArchivoDAO<T> {

    protected static String RUTA_ARCHIVO = "C:\\PROYECTO_IVAN_E\\entidades\\Archivos\\";
    // protected static String RUTA_ARCHIVO = System.getProperty("user.dir")
    // +"\\src\\Archivos\\";
    protected FileOutputStream archivoSalida;
    protected MiObjectOutputStream oEscritor;
    protected FileInputStream archivoEntrada;
    protected ObjectInputStream oLector;
    protected ArrayList<T> arrayTemporal;

    public void abrirArchivoOutput() throws Exception {
        try {
            File oArchivo = new File(RUTA_ARCHIVO);
            if (!oArchivo.exists()) {
                archivoSalida = new FileOutputStream(oArchivo, true);
                oEscritor = (MiObjectOutputStream) new ObjectOutputStream(archivoSalida);
            } else {
                archivoSalida = new FileOutputStream(oArchivo, true);
                oEscritor = new MiObjectOutputStream(archivoSalida);
            }
        } catch (Exception e) {
            throw e;
        }
    }

    public void abrirArchivoInput() throws Exception {
        try {
            archivoEntrada = new FileInputStream(RUTA_ARCHIVO);
            oLector = new ObjectInputStream(archivoEntrada);
        } catch (Exception e) {
            throw new Exception("Error al abrir archivo de entrada: " + e.getMessage(), e);
        }
    }

    public void cerrarArchivoOutput() throws Exception {
        try {
            if (oEscritor != null) {
                oEscritor.close();
                oEscritor = null;
            }
        } catch (Exception e) {
            throw new Exception("Error al cerrar archivo de salida: " + e.getMessage(), e);
        }
    }

    public void cerrarArchivoInput() throws Exception {
        try {
            if (oLector != null) {
                oLector.close();
                oLector = null;
            }
        } catch (Exception e) {
            throw new Exception("Error al cerrar archivo de entrada: " + e.getMessage(), e);
        }
    }
}
