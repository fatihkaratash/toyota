# Stage 1: Build the application with Maven
FROM maven:3.9-eclipse-temurin-21 AS builder

# Set working directory
WORKDIR /app

# Copy pom.xml first for better caching
COPY pom.xml .

# Download all dependencies
RUN mvn dependency:go-offline

# Copy the source code
COPY src ./src

# Build the application (skip tests to speed up the build)
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy the jar file from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Set timezone
ENV TZ=Europe/Istanbul

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && apt-get clean

# Expose application port
EXPOSE 8085

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
