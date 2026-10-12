# Multi-stage Java 21 build. Docker builds require access to Maven Central the first time.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --create-home fluxpay
COPY --from=build /workspace/target/fluxpay-0.0.1-SNAPSHOT.jar /app/fluxpay.jar
USER fluxpay
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/fluxpay.jar"]
