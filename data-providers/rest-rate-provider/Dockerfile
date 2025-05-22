# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
# Download all dependencies first
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/target/rest-rate-provider-1.0.0.jar ./rest-rate-provider.jar

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && apt-get clean

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "rest-rate-provider.jar"]
