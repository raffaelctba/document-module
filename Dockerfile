# syntax=docker/dockerfile:1.7
# Multi-stage, self-contained Dockerfile for document-service.
# Build context = this repository root (no PlatformApps monorepo, no pre-built target/).
#
# platform-sdk artifacts (platform-bom, platform-security, …) resolve from the private
# PlatformModules GitHub Packages registry (repository id `github`). Credentials are
# passed as a BuildKit secret and never land in an image layer:
#
#   docker build --secret id=maven_settings,src=$HOME/.m2/settings.xml -t document-service .
#
# CI (.github/workflows/deploy.yml) generates that settings.xml from
# PLATFORM_MODULES_PACKAGES_TOKEN.
FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . /workspace
RUN --mount=type=secret,id=maven_settings,required=false \
    --mount=type=cache,target=/root/.m2/repository \
    if [ -f /run/secrets/maven_settings ]; then SETTINGS="-s /run/secrets/maven_settings"; else SETTINGS=""; fi && \
    mvn -B -ntp $SETTINGS -pl document-service -am -DskipTests package && \
    JAR_FILE="$(ls document-service/target/*-exec.jar 2>/dev/null | head -n1)" && \
    test -n "$JAR_FILE" && cp "$JAR_FILE" /workspace/app.jar

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl && addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/app.jar /app/app.jar
USER app
EXPOSE 8085
ENV SPRING_PROFILES_ACTIVE=prd \
    JAVA_OPTS="-Xms128m -Xmx384m"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8085/actuator/health || exit 1
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
