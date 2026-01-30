# Use an official OpenJDK runtime as a parent image
FROM eclipse-temurin:21-jre-ubi10-minimal

# Set the working directory to /app
WORKDIR /app

# Copy the build artifact from the host to the container
# Note: You need to run 'mvn package' locally first or use a multi-stage build.
# For simplicity, assuming jar is in target/
COPY target/*.jar app.jar

# Expose the port the app runs on
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "app.jar"]
