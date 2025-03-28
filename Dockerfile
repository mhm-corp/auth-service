# First stage: Build
FROM eclipse-temurin:17-jdk-alpine AS builder

# Set the working directory
WORKDIR /app

# Copy the necessary files for the build
COPY build.gradle gradlew ./
COPY gradle ./gradle

# Copy the rest of the source code
COPY src ./src

# Run Gradle to build the project without running tests
RUN ./gradlew build --no-daemon -x test

# Second stage: Create the final image
FROM eclipse-temurin:17-jdk-alpine

# Set the working directory
WORKDIR /app

# Copy the built artifact from the previous stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Entry point command to run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Expose the port where the application runs
EXPOSE 8081