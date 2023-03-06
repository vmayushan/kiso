FROM gradle:7-jdk11 AS build
COPY --chown=gradle:gradle ./core/ /home/gradle/src/core
COPY --chown=gradle:gradle ./api/ /home/gradle/src/api
COPY --chown=gradle:gradle ./settings.gradle.kts /home/gradle/src
WORKDIR /home/gradle/src/api
RUN gradle shadowJar --no-daemon

FROM openjdk:11
COPY --from=build /home/gradle/src/api/build/libs/*-all.jar /app/api.jar
ENTRYPOINT ["java","-jar","/app/api.jar"]