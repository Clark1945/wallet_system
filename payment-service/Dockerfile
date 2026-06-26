# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
ARG APP_VERSION=1.0.0
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src/ src/
RUN mvn -B clean package -DskipTests -Drevision=${APP_VERSION}

# ---- runtime stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]