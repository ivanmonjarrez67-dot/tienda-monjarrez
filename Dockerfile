# Etapa 1: Compilar el proyecto con Maven y JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copiar primero el pom.xml para aprovechar la caché de Docker
COPY pom.xml .

# Descargar dependencias
RUN mvn dependency:go-offline

# Copiar el código fuente
COPY src ./src

# Compilar y generar el WAR
RUN mvn clean package -DskipTests


# Etapa 2: Ejecutar el WAR con Tomcat 10.1
FROM tomcat:10.1-jdk21-temurin

# Eliminar las aplicaciones predeterminadas de Tomcat
RUN rm -rf /usr/local/tomcat/webapps/*

# Copiar el WAR generado y convertirlo en ROOT.war
COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/ROOT.war

# Render proporciona el puerto mediante la variable PORT
EXPOSE 10000

# Configurar Tomcat para escuchar en el puerto que Render proporciona
CMD ["sh", "-c", "sed -i \"s/port=\\\"8080\\\"/port=\\\"${PORT:-10000}\\\"/\" /usr/local/tomcat/conf/server.xml && catalina.sh run"]
