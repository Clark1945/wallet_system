FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/email-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
