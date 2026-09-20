FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY document-module/document-service/target/document-service-1.0.0-exec.jar app.jar
EXPOSE 8085
ENV SPRING_PROFILES_ACTIVE=dev
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
