# One container per service (production-style deployment). The local stack runs all services in one JVM instead (ADR-011).
FROM eclipse-temurin:21-jre-alpine@sha256:b6ea71e690a72ba240b3d1b5c477410014862f9ea2cc73ab40e41ff21b03ba1c
WORKDIR /app
COPY build/libs/provider-service.jar app.jar
USER 1000
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
