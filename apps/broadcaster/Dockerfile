FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/target/broadcaster-1.0.0.jar /app/broadcaster.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/broadcaster.jar"]
