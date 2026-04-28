FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/app.jar app.jar
VOLUME /app/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
