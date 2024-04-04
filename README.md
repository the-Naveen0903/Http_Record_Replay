# HTTP Record Replay

This project is meant to allow mocking of Java libraries in a way that their functionality is overridden without any change at all to the business logic code. Its purpose is to provide for recording and playing back HTTP calls as well as database operations using a Java Agent.

## Problem Statement

The problem statement entails implementing two modes: ‘RECORD’ and ‘REPLAY’.

### RECORD Mode

Under the ‘RECORD’ mode, the application should:

1. Accepts a POST request at ‘/api/createNewPost’ with JSON body containing ‘post_name’ and ‘post_contents’.

2. It then inserts the post data into an actual database(PostgreSQL or MySQL).

3. Making a call to “http://worldtimeapi.org/api/timezone/Asia/Kolkata” this will be like making an http call which will return Current time.

4. Then it returns a response that contains the generated ID of the database row and also HTTP response body.

### REPLAY Mode

However, in REPLAY mode, the application should:

1. Accepts same POST request at '/api/createNewPost' with JSON body containing "post_name" and "post_contents".

2. It’s not going to insert into actual databases but instead returns hard-coded database rows.

3. Instead of making an actual HTTP call, you will just get a static responses like hardcoded http responses for instance

## Solution

The solution is a composite of the following parts:

1. Java Agent (HttpClientAgent.java): It uses ByteBuddy to create this Java agent and intercepts the 'send' method of the `HttpClient` class and `executeQuery` method for the `Statement` class. When in `RECORD` mode, it records HTTP response and database result set. In ‘REPLAY’ mode, instead of actual HTTP calls or database operations, it returns recorded HTTP response and database result set.

2. SpringBoot Application (Controller.java, Application.java, Post.java, PostRepository.java): The Spring Boot application has a controller that handles the '/api/createNewPost' endpoint which inserts data into the database using JDBC while an HTTP call is made by use of 'HttpClient' class. The response contains a generated ID of the database row as well as the HTTP response.

3. Docker Setup (Dockerfile): The project includes a Dockerfile for building JARs for both Spring Boot application and Java agent, then creating a final image that runs Spring Boot application with loaded java agent throughwhich is done through -javaagent JVM argument.

## Prerequisites

- Java 11

- Docker

## Build and run

1. Clone the repository: git clone https://github.com/the-Naveen0903/Http-Record-Replay.git

2. Construct the Docker image: docker build -t http-record-replay .

3. Use 'RECORD' mode to start up the Docker container: docker run -e HT_MODE=RECORD -e http.endpoint=http://worldtimeapi.org/api/timezone/Asia/Kolkata -e spring.datasource.url=jdbc:postgresql://db-host:5432/postgres -e spring.datasource.username=postgres -e spring.datasource.password=secret -p 8080:8080 http-record-replay

*Note : Replace the database credentials (spring.datasource.url, spring.datasource.username, spring.datasource.password) with your actual database connection details.

4. Run a Docker container in `REPLAY` mode:

docker run -e HT_MODE=REPLAY -e http.endpoint=http://example.com -e spring.datasource.url=jdbc:postgresql://1.2.3.4:1234/postgres -e spring.datasource.username=postgres -e spring.datasource.password=secret –p 8080:http-record-replay.

*Note : In replay mode, “http.endpoint” and “spring.database.url” have invalid values suggesting no external services are available for this test.

5. Make a POST request to “http://localhost:8080/api/createNewPost” with a JSON body that has ‘post_name’ and ‘post_contents’.


# Constraints

- The implementation assumes the database and HTTP responses remain consistent between the ‘RECORD’ and ‘REPLAY’ modes.

- More work might be necessary to deal with errors and edge cases.

- There is also need for further testing before this implementation can be used in production.


## Contribution

Please, don’t hesitate to contact us if you encounter any problem or wish to suggest an enhancement, open an issue or submit a pull request.
