## Stage 1: Build the application
FROM docker.io/maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests -Dquarkus.package.type=uber-jar -q

## Stage 2: Runtime image
FROM docker.io/eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/target/*-runner.jar /app/app.jar
COPY --from=builder /build/src/main/resources/db/init.sql /app/init.sql
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
