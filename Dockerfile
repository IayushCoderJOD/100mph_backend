# The API as one container, for Render / Railway / Fly / Cloud Run.
#
#   docker build -t 100mph-api .
#   docker run -p 8090:8090 -e MONGODB_URI=... -e JWT_SECRET=... 100mph-api
#
# The prod profile is baked in: seeding off, CORS locked to CORS_ALLOWED_ORIGINS.
# The platform's PORT variable is honoured (application.yml reads ${PORT}).

# ---- build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Dependencies in their own layer, so a code change does not re-download them.
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
# Tests need a Mongo; they run in CI / locally, not inside the image build.
RUN ./mvnw -q -B package -DskipTests && cp target/api-*.jar app.jar

# ---- run
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home api
COPY --from=build /app/app.jar app.jar
USER api
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8090
# Size the heap from the container's memory limit, not the host's.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
