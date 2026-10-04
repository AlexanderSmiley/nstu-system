# Build context is the repository root (docker-compose.yml sets context: . and
# dockerfile: services/student-service/Dockerfile). The composite Gradle build
# resolves ../../libs/* via includeBuild, therefore both services/ and libs/
# must be present in the context.

FROM mirror.gcr.io/library/eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY services/ ./services/
COPY libs/ ./libs/
WORKDIR /workspace/services/student-service
RUN ./gradlew --no-daemon bootJar

FROM mirror.gcr.io/library/eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/services/student-service/build/libs/*.jar /app/app.jar
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
