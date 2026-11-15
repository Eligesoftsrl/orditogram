# ---------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# prima solo il pom: le dipendenze restano in cache finché non cambia
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package && cp target/*.jar /app.jar

# ---------------------------------------------------------------- esecuzione
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --system orditogram && useradd --system --gid orditogram --home /app orditogram \
 && mkdir -p /app/data/documenti && chown -R orditogram:orditogram /app
WORKDIR /app
COPY --from=build /app.jar /app/orditogram.jar
USER orditogram
ENV TZ=Europe/Rome \
    JAVA_OPTS="-XX:MaxRAMPercentage=75" \
    APP_DOCUMENTI_DIR=/app/data/documenti \
    SERVER_FORWARD_HEADERS_STRATEGY=framework
VOLUME /app/data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fs http://localhost:8080/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/orditogram.jar"]
