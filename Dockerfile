FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /workspace/target/booking-master-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]