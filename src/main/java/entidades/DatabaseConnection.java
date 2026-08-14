package entidades;

import java.sql.Connection;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import config.Config;

public class DatabaseConnection {

    // 🆕 Antes: DriverManager.getConnection(URL) abría una conexión física
    // NUEVA (TCP + TLS + login contra SQL Server) en CADA llamada — eso es
    // lo que causaba los 5-10 segundos en Buscar/Login/Filtros/Mi tienda.
    //
    // Ahora: un único HikariDataSource (pool) se crea UNA VEZ cuando la
    // clase se carga. getConnection() simplemente toma una conexión ya
    // abierta y "lista" del pool — típicamente tarda unos pocos
    // milisegundos en vez de segundos.
    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(Config.DATABASE_URL);
        config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

        // Tamaño del pool: cuántas conexiones reales mantiene abiertas y
        // listas. 10 es un buen punto de partida para una app con
        // cientos de usuarios concurrentes moderados; se puede subir si
        // hace falta.
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);

        // Tiempo máximo esperando una conexión libre del pool antes de
        // lanzar error (evita que una petición se quede colgada para
        // siempre si el pool está agotado).
        // Subido de 10s a 30s: en arranques en frío de Render (plan
        // gratuito, dormido por inactividad) el handshake SSL contra
        // Azure SQL puede tardar más de lo normal.
        config.setConnectionTimeout(30000); // 30s

        // Cuánto puede vivir una conexión antes de reciclarse (evita
        // conexiones "zombie" que el servidor cerró por inactividad).
        config.setMaxLifetime(1800000); // 30 min
        config.setIdleTimeout(600000);  // 10 min

        config.setPoolName("TiendaMonjarrezPool");

        // 🔑 CLAVE: si la PRIMERA conexión falla al crear el pool (por
        // ejemplo justo cuando Render despierta del sueño y el handshake
        // SSL a Azure SQL es lento), NO lanzar excepción aquí. Sin esto,
        // el bloque static falla, la clase queda "envenenada" con
        // NoClassDefFoundError/ExceptionInInitializerError para SIEMPRE
        // hasta el próximo reinicio del proceso, aunque la red se
        // recupere segundos después. Con -1, el pool se crea igual y
        // sigue reintentando conexiones en segundo plano.
        config.setInitializationFailTimeout(-1);

        dataSource = new HikariDataSource(config);
    }

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        // Mismo nombre de método que antes: los servlets existentes
        // (FiltrosProductosServlet, ListaProductosServlet, etc.) no
        // necesitan ningún cambio.
        return dataSource.getConnection();
    }
}