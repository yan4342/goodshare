FROM eclipse-temurin:21-jre-ubi10-minimal

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
