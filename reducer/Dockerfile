FROM gradle:7-jdk11 AS build
COPY --chown=gradle:gradle ./core/ /home/gradle/src/core
COPY --chown=gradle:gradle ./reducer/ /home/gradle/src/reducer
COPY --chown=gradle:gradle ./settings.gradle.kts /home/gradle/src
WORKDIR /home/gradle/src/reducer
RUN gradle shadowJar --no-daemon

FROM openjdk:11
COPY --from=build /home/gradle/src/reducer/build/libs/*-all.jar /app/reducer.jar
ENTRYPOINT ["java","-jar","/app/reducer.jar"]