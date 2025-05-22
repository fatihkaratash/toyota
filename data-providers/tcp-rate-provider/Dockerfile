# Stage 1: Build the application with Maven
FROM maven:3.9-eclipse-temurin-21 AS builder

# Set working directory
WORKDIR /app

# Copy the pom.xml file for dependency resolution
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src
COPY config ./config

# Build the application
RUN mvn clean package

# Stage 2: Create the runtime image
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create a directory for the config to ensure it exists
RUN mkdir -p /app/config

# Copy the JAR and config files from builder
COPY --from=builder /app/target/tcp-rate-provider-1.0.0-uber.jar /app/tcp-rate-provider.jar
COPY --from=builder /app/config/initial-rates.json /app/config/

# Make port 8081 available to the world outside this container
EXPOSE 8081

# Run the JAR file
ENTRYPOINT ["java", "-jar", "/app/tcp-rate-provider.jar"]
