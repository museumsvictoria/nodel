# syntax=docker/dockerfile:1
# Build stage
FROM eclipse-temurin:21-jdk AS builder

# Git enables build metadata (branch, revision) from the checked-out repo.
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Prefer cached dependency downloads by copying build descriptors first.
COPY gradlew build.gradle settings.gradle ./
COPY gradle/ ./gradle/
COPY nodel-framework/build.gradle nodel-framework/build.gradle
COPY nodel-jyhost/build.gradle nodel-jyhost/build.gradle
COPY nodel-webui-js/build.gradle nodel-webui-js/build.gradle
COPY nodel-webui-js/package.json nodel-webui-js/package.json
COPY nodel-webui-js/package-lock.json nodel-webui-js/package-lock.json
COPY nodel-webui-js/Gruntfile.js nodel-webui-js/Gruntfile.js

RUN --mount=type=cache,target=/root/.gradle \
    # Normalize line endings for Windows clones so /bin/sh can run gradlew.
    sed -i 's/\r$//' gradlew \
    && chmod +x gradlew \
    && ./gradlew :nodel-jyhost:dependencies --no-daemon

COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :nodel-jyhost:unversioned --no-daemon \
    && test -s nodel-jyhost/build/distributions/standalone/nodelhost.jar \
    && cp nodel-jyhost/build/distributions/standalone/nodelhost.jar /build/nodelhost.jar

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Static labels
LABEL org.opencontainers.image.title="Nodel" \
      org.opencontainers.image.description="Digital media control system for museums, galleries, and corporate environments" \
      org.opencontainers.image.url="https://github.com/museumsvictoria/nodel" \
      org.opencontainers.image.source="https://github.com/museumsvictoria/nodel" \
      org.opencontainers.image.vendor="Museums Victoria" \
      org.opencontainers.image.licenses="MPL-2.0"

# Dynamic labels (optional build args)
ARG VERSION
ARG BUILD_DATE
ARG GIT_COMMIT

LABEL org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${GIT_COMMIT}"

# Install tini for proper signal handling.
RUN apk add --no-cache tini

WORKDIR /app
COPY --from=builder /build/nodelhost.jar .
COPY --chmod=755 entrypoint.sh /entrypoint.sh

# JVM tuning: UseContainerSupport (default since Java 10) enables container-aware memory limits;
# RAM percentages cap heap relative to container memory to reduce OOM risk
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# Default port (configurable via bootstrap.json NodelHostPort setting)
EXPOSE 8085

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:$(cat /app/.lastHTTPPort 2>/dev/null || echo 8085)/REST/

ENTRYPOINT ["/sbin/tini", "--", "/entrypoint.sh"]
