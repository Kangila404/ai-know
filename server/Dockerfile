FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
RUN groupadd --gid 10001 aiknow && useradd --uid 10001 --gid aiknow --no-create-home aiknow
WORKDIR /app
COPY --from=build --chown=aiknow:aiknow /workspace/build/libs/server-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080; printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3; read -r status <&3; [[ "$status" == *" 200 "* ]]'
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
