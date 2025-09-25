FROM ubuntu:latest
LABEL authors="ilyazyryanov"
ENTRYPOINT ["top", "-b"]

FROM gradle:8.7-jdk17 AS builder
WORKDIR /app
COPY ../lab2_TCP_broadcas/build.gradle.kts settings.gradle.kts gradlew gradlew.bat ./
COPY ../lab2_TCP_broadcas/gradle ./gradle
RUN ./gradlew build || return 0
COPY /src ./src
RUN ./gradlew shadowJar

FROM openjdk:17-jdk-slim
WORKDIR /app
COPY src/main/resources/hrush.jpg /app/hrush.jpg
COPY --from=builder /app/build/libs/*-all.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

#docker build -t my-kotlin-app:latest .
#docker run --rm \ -v ~/Downloads:/app/data \ my-kotlin-app:latest \ --ip 10.9.37.47 --port 6969 -fp /app/data/Doc1.docx