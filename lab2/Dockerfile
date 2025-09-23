FROM ubuntu:latest
LABEL authors="ilyazyryanov"
ENTRYPOINT ["top", "-b"]

FROM gradle:8.7-jdk17 AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew gradlew.bat ./
COPY gradle ./gradle
RUN ./gradlew build || return 0
COPY src/main/kotlin/client ./src/main/kotlin/client
COPY src/main/kotlin/config ./src/main/kotlin/config
COPY src/main/kotlin/parser ./src/main/kotlin/parser
COPY src/main/kotlin/ClientStart.kt ./src/main/kotlin/ClientStart.kt
COPY src/main/resources/config.cfg ./src/main/resources/config.cfg
RUN ./gradlew shadowJar

FROM openjdk:17-jdk-slim
WORKDIR /app
COPY src/main/resources/hrush.jpg /app/hrush.jpg
COPY --from=builder /app/build/libs/*-all.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

#docker build -t my-kotlin-app:latest .
#docker run --rm my-kotlin-app --ip 10.9.37.47 --port 6969 -fp /app/hrush.jpg