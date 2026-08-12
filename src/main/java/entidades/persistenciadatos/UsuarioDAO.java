package entidades.persistenciadatos;

import entidades.Usuario;

import java.io.EOFException;
import java.io.File;
import java.util.ArrayList;

/**
 *
 * @author Iván Monjarrez
 */
public class UsuarioDAO extends BaseArchivoDAO<Usuario>  {
    
 // Patrón Singleton
    private static UsuarioDAO instance = null;

    private UsuarioDAO() {
        RUTA_ARCHIVO += "Usuario.dat";
    }

    public static UsuarioDAO getInstance() {
        if (instance == null) {
            instance = new UsuarioDAO();
        }
        return instance;
    }
    // Fin del Patrón Singleton 
    
    
    
    public void agregar(Usuario oUsuario) throws Exception {
        try {
            abrirArchivoOutput();
            if (oEscritor != null) {
                oEscritor.writeObject(oUsuario);
                oEscritor.flush();
                oEscritor.reset(); // Cambia los encabezados por herencia
            }
        } catch (Exception e) {
            throw new Exception("Error al agregar el usuario: " + e.getMessage(), e);
        } finally {
            cerrarArchivoOutput();
        }
    }

    public void modificar(Usuario oUsuario) throws Exception {
        arrayTemporal = new ArrayList<Usuario>();
        try {
            abrirArchivoInput();
            Usuario usuarioTemp;
            while (true) { // Leer hasta que se genere error de lectura
                usuarioTemp = (Usuario) oLector.readObject();
                if (usuarioTemp.getCedula().equalsIgnoreCase(oUsuario.getCedula())) {
                    usuarioTemp = oUsuario; // Cambiamos el usuario
                }
                arrayTemporal.add(usuarioTemp);
            }
        } catch (EOFException e) {
            // Fin del archivo alcanzado, no se hace nada
        } catch (Exception e) {
            throw new Exception("Error al modificar el usuario: " + e.getMessage(), e);
        } finally {
            cerrarArchivoInput();
            pasarArrayTemporal_Archivo();
        }
    }

    private void pasarArrayTemporal_Archivo() throws Exception {
        File archivoOriginal = new File(RUTA_ARCHIVO);
        if (archivoOriginal.exists()) {
            archivoOriginal.delete();
        }
        if (!arrayTemporal.isEmpty()) {
            abrirArchivoOutput();
            for (Usuario item : arrayTemporal) {
                oEscritor.writeObject(item);
                oEscritor.flush();
                oEscritor.reset();
            }
            cerrarArchivoOutput();
        }
    }

    
    
    public ArrayList<Usuario> listado() throws Exception {
        arrayTemporal = new ArrayList<Usuario>();
        try {
            abrirArchivoInput();
            while (true) {
                Usuario temp = (Usuario) oLector.readObject();
                arrayTemporal.add(temp);
            }
        } catch (EOFException e) {
            // Fin del archivo alcanzado, no se hace nada
        } catch (Exception e) {
            throw new Exception("Error al listar los usuarios: " + e.getMessage(), e);
        } finally {
            cerrarArchivoInput();
        }
        return arrayTemporal;
    }  
    
    
    
   
    
}