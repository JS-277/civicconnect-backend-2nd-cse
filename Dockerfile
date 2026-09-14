FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY CivicConnectServer.java .
COPY DatabaseConnection.java .
COPY mysql-connector-j-26.7.0.jar .

RUN mkdir -p out && \
    javac -cp mysql-connector-j-26.7.0.jar -d out CivicConnectServer.java DatabaseConnection.java

CMD ["java", "-cp", "out:mysql-connector-j-26.7.0.jar", "prototype.backend.CivicConnectServer"]
