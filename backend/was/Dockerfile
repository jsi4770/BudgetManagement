# 애플리케이션 이미지. 두 단계로 나눠서 빌드 도구(JDK·Gradle)가 최종 이미지에 남지
# 않게 합니다.
#
#   docker compose up -d --build        # DB 와 함께 띄우기
#   docker build -t was .               # 이미지만 만들기
#
# 베이스는 Temurin **25** 입니다. build.gradle 의 툴체인이 Java 25 로 고정돼 있어서
# (`JavaLanguageVersion.of(25)`) 그보다 낮은 JDK 로는 컴파일되지 않습니다.


# ---------------------------------------------------------------------------
# 1단계: 빌드
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /build

# 의존성 캐시를 위해 **빌드 스크립트와 래퍼를 먼저** 복사합니다. 소스만 바뀐 재빌드는
# 아래 `dependencies` 레이어를 그대로 재사용하므로 의존성을 다시 내려받지 않습니다.
# 이 순서를 지키지 않으면(소스까지 한 번에 COPY) 한 줄만 고쳐도 전체를 다시 받습니다.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./

# `--no-daemon`: 컨테이너는 한 번 빌드하고 버려지니 데몬을 띄울 이유가 없습니다.
# 실패를 무시(`|| true`)하는 것은 의도입니다 — 이 단계는 캐시 워밍이 목적이고,
# 네트워크 사정으로 실패해도 아래 실제 빌드에서 다시 시도됩니다.
RUN ./gradlew dependencies --no-daemon --console=plain || true

COPY src ./src

# `bootJar` 만 돕니다. 테스트는 여기서 돌리지 않습니다 — 인메모리 H2 로 도는 테스트라
# 컨테이너에서도 돌 수는 있지만, 이미지 빌드 시간에 7백여 개를 매번 돌릴 이유가 없고
# 검증은 CI·로컬의 `./gradlew test` 가 할 일입니다.
RUN ./gradlew bootJar --no-daemon --console=plain

# ---------------------------------------------------------------------------
# 2단계: 실행
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

# 루트로 돌리지 않습니다. 컨테이너가 뚫렸을 때 할 수 있는 일을 줄이는 기본 조치이고,
# 이 앱은 파일을 쓰지 않으므로(영수증 이미지도 메모리에서 폐기) 쓰기 권한이 필요 없습니다.
RUN addgroup -S was && adduser -S -G was was

WORKDIR /app
COPY --from=build --chown=was:was /build/build/libs/*.jar app.jar
USER was

# 명세 0장 1) Base URL 의 포트. context-path(`/api/v1`)는 애플리케이션이 붙입니다.
EXPOSE 8080

# ERD 0장: 서버·DB·스케줄러 모두 KST. 컨테이너의 기본 시간대는 UTC 라서
# **이 설정이 없으면 "오늘"이 9시간 어긋납니다** — 넛지의 주 경계, 미래 날짜 거부,
# 챌린지 종료 판정이 모두 틀어집니다. 애플리케이션 코드는 KstTime 으로 KST 를 직접
# 쓰지만, 로그 시각과 00:05 스케줄러의 크론 해석을 맞추려면 JVM 기본 시간대도 KST 여야
# 합니다(`ChallengeTerminationJob` 은 zone 을 명시하지만 다른 크론이 생길 수 있습니다).
ENV TZ=Asia/Seoul

# 컨테이너 메모리 한도를 JVM 이 인식하게 합니다. 기본값으로도 cgroup 을 보지만
# 명시해 두면 한도를 바꿀 때 힙도 따라갑니다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# `sh -c` 를 써서 JAVA_OPTS 를 셸이 펼치게 합니다. exec 형식으로 바로 java 를 부르면
# 변수가 한 덩어리 문자열로 넘어갑니다. `exec` 를 앞에 붙이는 것이 중요한데, 그래야
# java 가 PID 1 이 되어 `docker stop` 의 SIGTERM 을 직접 받고 graceful shutdown 이
# 동작합니다 — 셸이 PID 1 이면 신호를 전달하지 않아 10초 뒤 강제 종료됩니다.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
