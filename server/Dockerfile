# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Install Maven and curl for health checks
RUN apt-get update && apt-get install -y maven curl && rm -rf /var/lib/apt/lists/*

# Copy parent pom and common module first for better layer caching
COPY pom.xml /app/
COPY common/ /app/common/

# Copy server pom.xml and source
COPY server/pom.xml /app/server/
COPY server/src/ /app/server/src/

# Copy client module
COPY client/ /app/client/

# Build the application
RUN mvn clean package -pl server -am -DskipTests

# Expose the port
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "/app/server/target/server-1.0.0.jar"]
