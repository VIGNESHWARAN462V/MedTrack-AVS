# ==============================================================================
# MedTrack – AVS: Multi-Stage Production Dockerfile for Railway (Java 21 LTS)
# ==============================================================================

# Stage 1: Build the packaged Spring Boot artifact
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy Maven descriptor and resolve dependencies for layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy project source and compile production artifact
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal hardened runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as dedicated unprivileged system user for container security
RUN addgroup -S medtrack && adduser -S medtrack -G medtrack
USER medtrack:medtrack

# Copy executable jar from builder stage
COPY --from=builder /app/target/medtrack-api-*.jar app.jar

# Platform environment configuration
ENV PORT=8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE ${PORT}

# Start the Spring Boot application, dynamically binding to the platform-assigned PORT
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dserver.port=${PORT} -jar app.jar"]
