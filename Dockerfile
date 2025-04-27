FROM maven:3.8-openjdk-17 AS build
WORKDIR /app
COPY ups-delivery-system/pom.xml .
# Download dependencies first for better layer caching
RUN mvn dependency:go-offline

COPY ups-delivery-system/src ./src
# Set the environment variable to indicate Docker environment
ENV DOCKER_ENV=true
RUN mvn clean package -DskipTests

FROM openjdk:17-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Preserve the environment variable
ENV DOCKER_ENV=true
# Add explicit PostgreSQL driver configuration
ENV SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
# Add wait-for-it script for service dependency management
COPY wait-for-it.sh /app/wait-for-it.sh
RUN chmod +x /app/wait-for-it.sh
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]