# Use an official OpenJDK runtime as a parent image
FROM eclipse-temurin:21-jre-jammy

# Set the working directory in the container
WORKDIR /app

# Copy the pom.xml file for dependency resolution
COPY pom.xml .

# Copy source code
COPY src ./src
COPY config ./config

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    apt-get clean

# Build the application
RUN mvn clean package

# Remove Maven after build is complete
RUN apt-get remove -y maven && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

# Create a directory for the config to ensure it exists
RUN mkdir -p /app/config

# Copy the JAR and config files
COPY target/tcp-rate-provider-1.0.0-uber.jar /app/tcp-rate-provider.jar
COPY config/initial-rates.json /app/config/

# Make port 8081 available to the world outside this container
EXPOSE 8081

# Run the JAR file
ENTRYPOINT ["java", "-jar", "/app/tcp-rate-provider.jar"]
