FROM eclipse-temurin:21-jre

# Non-root runtime user
RUN groupadd -r app && useradd -r -g app app

WORKDIR /app

# CI downloads the Spring Boot fat jar into target/ before the image build
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
RUN chown app:app /app/app.jar

USER app

# Cloud Run injects PORT at runtime; bind on all interfaces
ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --server.port=${PORT:-8080} --server.address=0.0.0.0"]
