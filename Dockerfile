# Build reproduzível: versões das imagens fixadas; dependências resolvidas pelo Maven Wrapper (versão fixada em
# .mvn/wrapper/maven-wrapper.properties). A camada de dependências é reaproveitada enquanto o pom.xml não muda.
# Os testes rodam no CI (exigem Docker para o Testcontainers). Funciona com e sem BuildKit.

FROM eclipse-temurin:21.0.9_10-jdk-noble AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/campus-api-*.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:21.0.9_10-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && apt-get clean && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 campus \
    && useradd --system --uid 10001 --gid campus --no-create-home --shell /usr/sbin/nologin campus
WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
