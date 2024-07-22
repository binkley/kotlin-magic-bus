VERSION 0.8
FROM eclipse-temurin:17-jdk-focal
WORKDIR /code

build-with-gradle:
    COPY gradlew .
    COPY gradle gradle
    COPY gradle.properties .
    COPY settings.gradle .
    COPY build.gradle .
    COPY config config
    COPY src src
    RUN --secret OWASP_NVD_API_KEY ./gradlew clean build

# TODO: See #3
# run:
#     FROM +build
#     COPY run.sh .
#     RUN ./run.sh
