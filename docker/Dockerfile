# Dockerfile
# Build the Spring Boot application
FROM openjdk:11 AS build-app

COPY pom.xml /app/
COPY src /app/src/
WORKDIR /app
RUN ./mvnw clean package

# Build the Java agent
FROM openjdk:11 AS build-agent

COPY HttpClientAgent.java /agent/
WORKDIR /agent
RUN javac HttpClientAgent.java
RUN jar cvf agent.jar HttpClientAgent.class

# Final image
FROM openjdk:11

COPY --from=build-app /app/target/http-record-replay-0.0.1-SNAPSHOT.jar /app.jar
COPY --from=build-agent /agent/agent.jar /agent.jar

# Set environment variables
ENV HT_MODE RECORD
ENV http.endpoint http://worldtimeapi.org/api/timezone/Asia/Kolkata
ENV spring.datasource.url jdbc:postgresql://db-host:5432/postgres
ENV spring.datasource.username postgres
ENV spring.datasource.password secret

#ENV HT_MODE REPLAY
ENV HTTP_HOST 1.2.3.4:1234
ENV DB_HOST 1.2.3.4:1234

ENTRYPOINT ["java", "-javaagent:/agent.jar", "-jar", "/app.jar"]
