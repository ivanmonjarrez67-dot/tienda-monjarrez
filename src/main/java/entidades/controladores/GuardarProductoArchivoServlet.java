package entidades.controladores;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@WebServlet("/GuardarProductoArchivo")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024, // 1 MB
    maxFileSize = 5 * 1024 * 1024,   // 5 MB
    maxRequestSize = 10 * 1024 * 1024 // 10 MB
)
public class GuardarProductoArchivoServlet extends HttpServlet {

    // 👉 Configura estas 2 variables de entorno en Render (y localmente si las necesitas para probar)
    // Se obtienen desde tu panel de Cloudinary: cloud name y el nombre del "upload preset" (modo Unsigned)
    private static final String CLOUDINARY_CLOUD_NAME = System.getenv("CLOUDINARY_CLOUD_NAME");
    private static final String CLOUDINARY_UPLOAD_PRESET = System.getenv("CLOUDINARY_UPLOAD_PRESET");

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            Part filePart = request.getPart("imagenProducto"); // Nombre del input, igual que antes
            String fileName = getFileName(filePart);

            if (fileName == null || fileName.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("No se recibió ningún archivo.");
                return;
            }

            if (CLOUDINARY_CLOUD_NAME == null || CLOUDINARY_UPLOAD_PRESET == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("Falta configurar CLOUDINARY_CLOUD_NAME o CLOUDINARY_UPLOAD_PRESET.");
                return;
            }

            // Leer el archivo a memoria (ya no se escribe a disco)
            byte[] fileBytes;
            try (InputStream input = filePart.getInputStream();
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] data = new byte[4096];
                int bytesRead;
                while ((bytesRead = input.read(data)) != -1) {
                    buffer.write(data, 0, bytesRead);
                }
                fileBytes = buffer.toByteArray();
            }

            // Subir a Cloudinary y obtener la URL pública
            String imageUrl = subirACloudinary(fileBytes, fileName);

            // El frontend ahora espera la URL completa como respuesta (ver script.js)
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(imageUrl);

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error al guardar el archivo: " + e.getMessage());
        }
    }

    private String subirACloudinary(byte[] fileBytes, String fileName) throws IOException, InterruptedException {
        String boundary = "----ProyectoWebBoundary" + UUID.randomUUID();
        String url = "https://api.cloudinary.com/v1_1/" + CLOUDINARY_CLOUD_NAME + "/image/upload";

        ByteArrayOutputStream body = new ByteArrayOutputStream();

        writeFormField(body, boundary, "upload_preset", CLOUDINARY_UPLOAD_PRESET);

        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(fileBytes);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            throw new IOException("Error al subir a Cloudinary (" + resp.statusCode() + "): " + resp.body());
        }

        // Extraer "secure_url":"..." del JSON de respuesta sin depender de una librería JSON adicional
        String json = resp.body();
        String key = "\"secure_url\":\"";
        int start = json.indexOf(key);
        if (start == -1) {
            throw new IOException("No se encontró secure_url en la respuesta de Cloudinary: " + json);
        }
        start += key.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end).replace("\\/", "/");
    }

    private void writeFormField(ByteArrayOutputStream body, String boundary, String name, String value) throws IOException {
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write((value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    // Obtener nombre real del archivo (igual que antes)
    private String getFileName(Part part) {
        String contentDisp = part.getHeader("content-disposition");
        for (String token : contentDisp.split(";")) {
            if (token.trim().startsWith("filename")) {
                return token.substring(token.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return null;
    }
}