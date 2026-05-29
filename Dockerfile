# Build stage
FROM gradle:9.5.1-jdk25 AS build
WORKDIR /app
COPY --chown=gradle:gradle . .
RUN gradle :application:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:25-jre-alpine
RUN mkdir /opt/app
COPY --from=build /app/application/build/libs/application-0.0.5.jar /opt/app
WORKDIR /opt/app
ENTRYPOINT ["java", "-jar", "application.jar"]
EXPOSE 8080
