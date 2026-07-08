# syntax=docker/dockerfile:1
# Runtime image for rating-engine: a slim Temurin 25 JRE running the executable Spring
# Boot jar as a non-root user. Build the jar first with `./gradlew bootJar`.
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system quotient && useradd --system --gid quotient --uid 1001 quotient
COPY build/libs/*-SNAPSHOT.jar app.jar
USER quotient
EXPOSE 8084
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
