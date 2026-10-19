# -----------------------------------------------------------------------------
# Stage 1: Build & Layer Extraction
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and POM first for dependency caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B || true

# Copy application source code
COPY src src

# Build executable fat JAR
RUN ./mvnw clean package -DskipTests -B

# Extract Spring Boot jar layers for container layer caching
RUN java -Djarmode=tools -jar target/java-dsa-mastery-1.0.0-SNAPSHOT.jar extract --layers --launcher --destination target/extracted

# -----------------------------------------------------------------------------
# Stage 2: Minimal Production Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Install curl for container health checks
RUN apk --no-cache add curl

# Create unprivileged system user and group
RUN addgroup -S javadsa && adduser -S javadsa -G javadsa

# Copy extracted application layers from builder with non-root ownership
COPY --from=builder --chown=javadsa:javadsa /build/target/extracted/dependencies/ ./
COPY --from=builder --chown=javadsa:javadsa /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=javadsa:javadsa /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=javadsa:javadsa /build/target/extracted/application/ ./

USER javadsa:javadsa

EXPOSE 8080

# Production JVM tuning for memory-constrained containers (< 384MB RAM)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Djava.security.egd=file:/dev/./urandom"

# Built-in health check using Actuator liveness probe
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
