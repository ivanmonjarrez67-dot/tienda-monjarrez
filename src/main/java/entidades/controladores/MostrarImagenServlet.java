package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

@WebServlet("/MostrarImagen")
public class MostrarImagenServlet extends HttpServlet {

    // Carpeta donde estarán las imágenes de la tienda
    private static final String UPLOAD_DIR = "C:/Monjarrez_Mi_Tienda_En_Linea/uploads/productos";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {

        // 1️⃣ Obtener el nombre de la imagen desde la URL
        String nombreImagen = request.getParameter("nombre");
        if (nombreImagen == null || nombreImagen.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No se especificó el nombre de la imagen");
            return;
        }

        // 2️⃣ Crear el archivo y verificar existencia
        File imagen = new File(UPLOAD_DIR, nombreImagen);
        if (!imagen.exists() || !imagen.isFile()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Imagen no encontrada");
            return;
        }

        // 3️⃣ Determinar el tipo MIME
        String mimeType = getServletContext().getMimeType(imagen.getName());
        if (mimeType == null) mimeType = "application/octet-stream";
        response.setContentType(mimeType);
        response.setContentLengthLong(imagen.length());

        // 4️⃣ Opcional: agregar cacheo de 1 hora
        response.setHeader("Cache-Control", "max-age=3600");

        // 5️⃣ Enviar el archivo al navegador
        try (FileInputStream in = new FileInputStream(imagen);
             OutputStream out = response.getOutputStream()) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
}
