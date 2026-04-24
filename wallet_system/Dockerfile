FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/wallet_system-0.0.1-SNAPSHOT.jar app.jar
VOLUME /app/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
