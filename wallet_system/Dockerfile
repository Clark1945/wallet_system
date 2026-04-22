FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src src
RUN ./mvnw clean package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
VOLUME /app/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
