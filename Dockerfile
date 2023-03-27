FROM openjdk:17.0.1-jdk-slim as build
WORKDIR /var/tmp/guessword
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts .
COPY src src
RUN ./gradlew build

FROM openjdk:17.0.1-jdk-slim
#FROM arm64v8/adoptopenjdk:11-jdk-hotspot-focal
#FROM arm64v8/adoptopenjdk:11-jre-hotspot-focal
ARG DEPENDENCY=/var/tmp/guessword/build
COPY --from=build ${DEPENDENCY}/libs/*.jar /opt/guessword/guessword.jar
COPY --from=build ${DEPENDENCY}/resources/main/*.yml /var/guessword/
ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "-Dspring.config.location=file:///var/guessword/", "-Dspring.profiles.active=docker", "/opt/guessword/guessword.jar"]
