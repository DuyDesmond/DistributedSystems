# Multi-stage Dockerfile for the entire project
FROM maven:3.9.5-openjdk-17-slim AS build

# Set working directory
WORKDIR /app

# Copy all source files
COPY . .

# Build all modules
RUN mvn clean package -DskipTests

# Server runtime stage
FROM openjdk:17-jdk-slim AS server
WORKDIR /app
COPY --from=build /app/server/target/server-1.0.0.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]

# Client runtime stage  
FROM openjdk:17-jdk-slim AS client
WORKDIR /app
# Install JavaFX dependencies
RUN apt-get update && \
    apt-get install -y libxrender1 libxtst6 libxi6 libgl1-mesa-glx libgtk-3-0 && \
    rm -rf /var/lib/apt/lists/*
COPY --from=build /app/client/target/client-1.0.0.jar app.jar
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
