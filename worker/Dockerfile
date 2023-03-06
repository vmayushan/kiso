FROM gradle:7-jdk11 AS build
COPY --chown=gradle:gradle ./core/ /home/gradle/src/core
COPY --chown=gradle:gradle ./worker/ /home/gradle/src/worker
COPY --chown=gradle:gradle ./settings.gradle.kts /home/gradle/src
WORKDIR /home/gradle/src/worker
RUN gradle shadowJar --no-daemon

FROM openjdk:11
COPY --from=build /home/gradle/src/worker/build/libs/*-all.jar /app/worker.jar
ENTRYPOINT ["java","-jar","/app/worker.jar"]