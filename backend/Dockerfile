FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY src src

RUN chmod +x mvnw && ./mvnw -B clean package -DskipTests

FROM maven:3.9-eclipse-temurin-17
WORKDIR /app

RUN useradd --system --uid 10001 --create-home appuser \
    && mkdir -p /app/generated-workspace /home/appuser/.m2/repository \
    && chown -R appuser:appuser /app /home/appuser

COPY --from=build /app/target/*.jar app.jar

ENV CODEFLOW_MAVEN_WRAPPER=/usr/bin/mvn
USER 10001
ENV JAVA_TOOL_OPTIONS="-Dcodeflow.workspace=/app/generated-workspace"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]


