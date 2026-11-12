
# syntax=docker/dockerfile:1

############################################
# Étape 1 : compilation
############################################
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

# Cache Maven persistant entre les builds.
# Les tests sont exécutés séparément dans la CI.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package \
    && cp target/*.jar application.jar

# Extraction du JAR Spring Boot en couches.
RUN java -Djarmode=tools -jar application.jar \
    extract --layers --destination extracted

############################################
# Étape 2 : image finale
############################################
FROM eclipse-temurin:21-jre-alpine AS runtime

# Utilisateur non-root.
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copie des couches Spring Boot.
COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/application/ ./

USER spring

# Fuseau horaire du Maroc et optimisation mémoire JVM.
ENV TZ=Africa/Casablanca \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Africa/Casablanca"

EXPOSE 8080

# Vérification de santé.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

# Lancement de lapp Spring Boot.
ENTRYPOINT ["java", "-jar", "/app/application.jar"]