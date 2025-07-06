# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Install Maven and X11 dependencies for JavaFX
RUN apt-get update && \
    apt-get install -y maven libxrender1 libxtst6 libxi6 libgl1-mesa-glx libgtk-3-0 && \
    rm -rf /var/lib/apt/lists/*

# Copy parent pom and common module first for better layer caching
COPY pom.xml /app/
COPY common/ /app/common/

# Copy client pom.xml and source
COPY client2/pom.xml /app/client/
COPY client2/src/ /app/client/src/

# Copy server module
COPY server/ /app/server/

# Build the application
RUN mvn clean package -pl client -am -DskipTests

# Expose any client ports if needed
EXPOSE 8081

# Run the application
CMD ["java", "-jar", "/app/client/target/client-1.0.0.jar"]
