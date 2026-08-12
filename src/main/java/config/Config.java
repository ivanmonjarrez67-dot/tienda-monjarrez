package config;

public class Config {

    // Brevo
    public static final String BREVO_API_KEY =
            System.getenv("BREVO_API_KEY");

    // Remitente
    public static final String REMITENTE_EMAIL =
            "tiendamonjarrez@gmail.com";

    public static final String REMITENTE_NOMBRE =
            "Tienda Monjarrez";

    // Soporte
    public static final String SOPORTE_EMAIL =
            "tiendamonjarrez@gmail.com";

    // URL de la tienda
    public static final String URL_TIENDA =
            System.getenv("URL_TIENDA");

    // Base de datos
    public static final String DATABASE_URL =
            System.getenv("DATABASE_URL");

    // Cloudinary
    public static final String CLOUDINARY_CLOUD_NAME =
            System.getenv("CLOUDINARY_CLOUD_NAME");

    public static final String CLOUDINARY_UPLOAD_PRESET =
            System.getenv("CLOUDINARY_UPLOAD_PRESET");

}