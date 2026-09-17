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

# Stage 3: Giải nén JAR file (dùng tools mode của Spring Boot 3.3+)
FROM package AS extract

WORKDIR /build

RUN java -Djarmode=tools -jar target/app.jar extract --launcher --destination target/extracted

# Stage 4: Runtime Image tối ưu dung lượng và bảo mật
FROM eclipse-temurin:21-jre-jammy AS final

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

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

# Phân tách layer tối ưu cache Docker:
# 1. Thư viện phụ thuộc (nặng, ít thay đổi)
COPY --chown=appuser:appuser --from=extract /build/target/extracted/BOOT-INF/lib/ ./BOOT-INF/lib/
# 2. Loader và metadata
COPY --chown=appuser:appuser --from=extract /build/target/extracted/org/ ./org/
COPY --chown=appuser:appuser --from=extract /build/target/extracted/META-INF/ ./META-INF/
# 3. Mã nguồn và cấu hình ứng dụng (thay đổi thường xuyên)
COPY --chown=appuser:appuser --from=extract /build/target/extracted/BOOT-INF/classes/ ./BOOT-INF/classes/

# Đổi sang user bảo mật phi root
USER appuser

EXPOSE 8080

ENTRYPOINT [ "java", "org.springframework.boot.loader.launch.JarLauncher" ]
