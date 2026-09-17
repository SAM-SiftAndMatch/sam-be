# Stage 1: Tải dependencies để tối ưu Docker Layer Cache
FROM eclipse-temurin:21-jdk-jammy AS deps

WORKDIR /build

COPY --chmod=0755 mvnw mvnw
COPY .mvn/ .mvn/
COPY pom.xml .

RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -B

# Stage 2: Biên dịch ứng dụng
FROM deps AS package

WORKDIR /build

COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests && \
    mv target/*.jar target/app.jar

# Stage 3: Giải nén JAR file thành các lớp (layertools)
FROM package AS extract

WORKDIR /build

RUN java -Djarmode=layertools -jar target/app.jar extract --destination target/extracted

# Stage 4: Runtime Image tối ưu dung lượng và bảo mật
FROM eclipse-temurin:21-jre-jammy AS final

ARG UID=10001
RUN adduser \
    --disabled-password \
    --gecos "" \
    --home "/nonexistent" \
    --shell "/sbin/nologin" \
    --no-create-home \
    --uid "${UID}" \
    appuser

WORKDIR /app

# Copy các layer đã giải nén với quyền sở hữu thuộc về appuser
COPY --chown=appuser:appuser --from=extract /build/target/extracted/dependencies/ ./
COPY --chown=appuser:appuser --from=extract /build/target/extracted/spring-boot-loader/ ./
COPY --chown=appuser:appuser --from=extract /build/target/extracted/snapshot-dependencies/ ./
COPY --chown=appuser:appuser --from=extract /build/target/extracted/application/ ./

# Đổi sang user bảo mật phi root
USER appuser

EXPOSE 8080

ENTRYPOINT [ "java", "org.springframework.boot.loader.launch.JarLauncher" ]
