# Multi-stage build → small, non-root runtime image (NFR-D1, NFR-SEC).
# Build:  docker build -t shikhi-backend ./backend
# Run:    docker run -p 8080:8080 shikhi-backend

# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Copy build scripts + wrapper first for better layer caching of dependencies.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# --- run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 shikhi
COPY --from=build /app/build/libs/*.jar app.jar
USER shikhi
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
