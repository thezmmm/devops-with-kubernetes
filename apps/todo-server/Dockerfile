FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/target/todo-server-1.0.0.jar /app/todo-server.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/todo-server.jar"]
