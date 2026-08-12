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
        config.setConnectionTimeout(10000); // 10s

        // Cuánto puede vivir una conexión antes de reciclarse (evita
        // conexiones "zombie" que el servidor cerró por inactividad).
        config.setMaxLifetime(1800000); // 30 min
        config.setIdleTimeout(600000);  // 10 min

        config.setPoolName("TiendaMonjarrezPool");

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