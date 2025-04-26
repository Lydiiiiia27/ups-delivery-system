FROM maven:3.8-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Set the environment variable to indicate Docker environment
ENV DOCKER_ENV=true
RUN mvn clean package -DskipTests

FROM openjdk:17-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Preserve the environment variable
ENV DOCKER_ENV=true
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]