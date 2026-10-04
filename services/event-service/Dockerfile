# Build context is the repository root (docker-compose.yml sets context: . and
# dockerfile: services/event-service/Dockerfile). The composite Gradle build
# resolves ../../libs/* via includeBuild, therefore both services/ and libs/
# must be present in the context.

FROM mirror.gcr.io/library/eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY services/ ./services/
COPY libs/ ./libs/
WORKDIR /workspace/services/event-service
RUN ./gradlew --no-daemon bootJar

FROM mirror.gcr.io/library/eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/services/event-service/build/libs/*.jar /app/app.jar
USER app
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
