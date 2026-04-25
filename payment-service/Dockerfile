FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/payment-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
