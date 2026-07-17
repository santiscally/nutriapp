# syntax=docker/dockerfile:1.7
# Build Spring Boot con Maven + JDK 21, runtime con JRE slim.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -DskipTests dependency:go-offline || true

COPY src/ src/

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -DskipTests package \
 && cp target/nutriapp-backend.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S nutriapp && adduser -S -G nutriapp nutriapp

WORKDIR /app
COPY --from=build --chown=nutriapp:nutriapp /workspace/app.jar /app/app.jar

USER nutriapp
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
