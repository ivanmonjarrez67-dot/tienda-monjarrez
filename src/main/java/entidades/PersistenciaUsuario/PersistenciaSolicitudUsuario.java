package entidades.PersistenciaUsuario;

import entidades.SolicitudesDeVendedor;
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


public class PersistenciaSolicitudUsuario {
    
    private static List<SolicitudesDeVendedor> listaSolicitudes = new ArrayList<>();
    private static int contadorId = 1000; 
    private static final String RUTA_ARCHIVO = "C:\\PROYECTO_IVAN_E\\entidades\\Archivos\\Solicitudes.dat"; 

    // Método para agregar una nueva solicitud
    public static void agregarSolicitud(SolicitudesDeVendedor nuevaSolicitud) {
        listaSolicitudes.add(nuevaSolicitud);
        guardarSolicitudesEnArchivo(); 
    }

    // Método para obtener todas las solicitudes
    public static List<SolicitudesDeVendedor> obtenerSolicitudes() {
        return listaSolicitudes;
    }

    // Método para eliminar una solicitud por ID
    public static void eliminarSolicitud(int id) {
        listaSolicitudes.removeIf(solicitud -> solicitud.getId() == id);
        guardarSolicitudesEnArchivo(); 
    }

    // Método para obtener una solicitud por ID
    public static SolicitudesDeVendedor obtenerSolicitud(int id) {
        for (SolicitudesDeVendedor solicitud : listaSolicitudes) {
            if (solicitud.getId() == id) {
                return solicitud;
            }
        }
        return null; 
    }

    // Método para generar un nuevo ID
    public static int generarNuevoId() {
        return contadorId++;
    }

    // Método para cargar solicitudes desde el archivo
    public static void cargarSolicitudesDesdeArchivo() {
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
                SolicitudesDeVendedor solicitud = (SolicitudesDeVendedor) oLector.readObject();
                listaSolicitudes.add(solicitud);
            }
        } catch (EOFException e) {
            // Fin del archivo alcanzado, no se hace nada
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error al cargar solicitudes: " + e.getMessage());
        }
    }

    // Método para guardar solicitudes en el archivo
    private static void guardarSolicitudesEnArchivo() {
        try (ObjectOutputStream oEscritor = new ObjectOutputStream(new FileOutputStream(RUTA_ARCHIVO))) {
            for (SolicitudesDeVendedor solicitud : listaSolicitudes) {
                oEscritor.writeObject(solicitud);
            }
        } catch (IOException e) {
           // System.err.println("Error al guardar solicitudes: " + e.getMessage());
        }
    }
}
