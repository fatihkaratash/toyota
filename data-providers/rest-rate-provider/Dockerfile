# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/target/rest-rate-provider-1.0.0.jar ./rest-rate-provider.jar

# Optional: Copy initial-rates.json if it needs to be external and mounted
# For this setup, it's bundled in the JAR (src/main/resources), so no explicit copy needed here.
# If you want to override it with an external file via Docker volumes, you can place it in /app/config
# and adjust Spring Boot to load from file system path if present.
# RUN mkdir /app/config
# COPY --from=builder /app/src/main/resources/initial-rates.json /app/config/initial-rates.json

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "rest-rate-provider.jar"]
