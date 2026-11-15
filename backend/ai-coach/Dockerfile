# ai-coach 이미지. was 와 같은 두 단계 구성이고, 빌드 도구는 최종 이미지에 남지 않습니다.
#
#   docker compose up -d --build ai-coach   # backend/compose.yaml 에서
#
# 이 프로젝트에는 Gradle 래퍼(gradlew)가 없어서 **Gradle 공식 이미지**로 빌드합니다.
# 버전은 로컬에서 돌려 본 것(9.6.1)과 맞췄습니다. Spring Boot 4.1 은 Gradle 8.14 이상이 필요합니다.

# ---------------------------------------------------------------------------
# 1단계: 빌드
# ---------------------------------------------------------------------------
FROM gradle:9.6.1-jdk21 AS build

WORKDIR /build

# 빌드 스크립트를 먼저 복사해 의존성 레이어를 캐시합니다 (was 의 Dockerfile 과 같은 이유).
COPY settings.gradle build.gradle ./
RUN gradle dependencies --no-daemon --console=plain || true

COPY src ./src
RUN gradle bootJar --no-daemon --console=plain

# ---------------------------------------------------------------------------
# 2단계: 실행
# ---------------------------------------------------------------------------
# build.gradle 이 Java 17 바이트코드를 만들므로 17 이상 JRE 면 됩니다.
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S coach && adduser -S -G coach coach

WORKDIR /app
# bootJar 는 실행 가능한 jar 하나만 만들지만, plain jar 가 같이 생기는 설정이면 와일드카드가
# 둘을 집으므로 -plain 을 빼고 복사합니다.
COPY --from=build --chown=coach:coach /build/build/libs/ /tmp/libs/
RUN find /tmp/libs -name '*.jar' ! -name '*-plain.jar' -exec mv {} /app/app.jar \; && rm -rf /tmp/libs
USER coach

EXPOSE 8080

ENV TZ=Asia/Seoul
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# exec 로 java 를 PID 1 로 만들어 docker stop 의 SIGTERM 을 직접 받게 합니다 (was 와 같은 이유).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
