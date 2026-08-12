package entidades.PersistenciaUsuario;

import entidades.Usuario;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
/**
 *
 * @author Iván Monjarrez
 */


public class PersistenciaUsuarioTabla {

    private static List<Usuario> listadoUsuarios = new ArrayList<>();
    private static int contadorId = 1000; 
    private static final String RUTA_ARCHIVO = "C:\\PROYECTO_IVAN_E\\entidades\\Archivos\\Usuario.dat";

    public static void setUsuario(Usuario oUsuario) {
        listadoUsuarios.add(oUsuario);
        guardarUsuariosEnArchivo(); // Guarda cada vez que se agrega un usuario
    }

    public static List<Usuario> getListado() {
        return listadoUsuarios;
    }

    public static int generarNuevoId() {
        return contadorId++; 
    }
     
    public static void eliminarUsuario(int id) {
        listadoUsuarios.removeIf(usuario -> usuario.getId() == id);
        guardarUsuariosEnArchivo(); // Guarda después de eliminar
    }
    
    public static Usuario getUsuario(int id) { 
        for (Usuario usuario : listadoUsuarios) {
            if (usuario.getId() == id) { 
                return usuario;
            }
        }
        return null; 
    }
    
    public static void cargarUsuariosDesdeArchivo() {
        File archivo = new File(RUTA_ARCHIVO);
        if (!archivo.exists()) {
            try {
                archivo.createNewFile(); // Crea el archivo si no existe
            } catch (IOException e) {
                System.err.println("Error al crear el archivo: " + e.getMessage());
                return;
            }
        }

        try (ObjectInputStream oLector = new ObjectInputStream(new FileInputStream(RUTA_ARCHIVO))) {
            while (true) {
                Usuario usuario = (Usuario) oLector.readObject();
                listadoUsuarios.add(usuario);
            }
        } catch (EOFException e) {
            // Fin del archivo alcanzado, no se hace nada
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error al cargar usuarios: " + e.getMessage());
        }
    }

    private static void guardarUsuariosEnArchivo() {
        try (ObjectOutputStream oEscritor = new ObjectOutputStream(new FileOutputStream(RUTA_ARCHIVO))) {
            for (Usuario usuario : listadoUsuarios) {
                oEscritor.writeObject(usuario);
            }
        } catch (IOException e) {
          //  System.err.println("Error al guardar usuarios: " + e.getMessage());
        }
    }

    public static void eliminarUsuario(String id) {
        Usuario usuarioAEliminar = null;
        for (Usuario usuario : listadoUsuarios) {
            if (String.valueOf(usuario.getId()).equals(id)) {
                usuarioAEliminar = usuario;
                break;
            }
        }
        if (usuarioAEliminar != null) {
            listadoUsuarios.remove(usuarioAEliminar);
            guardarUsuariosEnArchivo(); // Guarda después de eliminar
        }
    }
}
