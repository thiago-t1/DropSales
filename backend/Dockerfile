# =============================================================================
# DropSales API - Dockerfile Otimizado para Produção
# Stack: Java 17 | Spring Boot 3.2 | Maven
# Estratégia: Multi-stage Build para imagem final enxuta e segura
# =============================================================================

# -----------------------------------------------------------------------------
# STAGE 1 — BUILD
# Imagem: Maven oficial com JDK 17 (variante Eclipse Temurin slim)
# Responsabilidade: compilar o projeto e gerar o JAR executável.
# Esta imagem NÃO fará parte da imagem final de produção.
# -----------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /build

# --- Otimização de Cache de Camadas (Layer Caching) ---
# PASSO 1: Copia APENAS o pom.xml primeiro.
# O Docker só re-executa a camada abaixo se o pom.xml mudar.
COPY pom.xml .

# PASSO 2: Baixa todas as dependências do Maven.
# Com '-B' (batch mode), '-e' (erros) e '-q' (quiet) para logs limpos.
# O flag '-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn'
# silencia o barulho de download nos logs do CI/CD.
RUN mvn dependency:go-offline \
    -B \
    -q \
    -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn

# PASSO 3: Copia o restante do código-fonte.
# Esta camada só é invalidada se o código mudar, NÃO quando o pom.xml muda.
COPY src ./src

# PASSO 4: Compila e empacota, pulando os testes (que rodam separadamente no CI).
RUN mvn package \
    -B \
    -q \
    -DskipTests \
    -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn


# -----------------------------------------------------------------------------
# STAGE 2 — RUNTIME (Imagem Final de Produção)
# Imagem: Eclipse Temurin 17 JRE com base Ubuntu Jammy (jammy = menor que full,
# mais compatível que alpine para Spring Boot com PostgreSQL driver nativo).
# Esta é a única imagem que vai para o registry. Não contém Maven, JDK, nem src/.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

# --- Metadados OCI (boas práticas de rastreabilidade) ---
LABEL org.opencontainers.image.title="DropSales API" \
      org.opencontainers.image.description="API RESTful para gestão financeira e controle de estoque" \
      org.opencontainers.image.version="0.0.1-SNAPSHOT" \
      org.opencontainers.image.vendor="DropSales"

WORKDIR /app

# --- Segurança: Usuário Sem Privilégios (Non-Root) ---
# Cria um grupo e usuário dedicados para a aplicação.
# Um processo rodando como root dentro de um container, em caso de exploração,
# pode obter acesso root ao host. Isso mitiga completamente esse vetor de ataque.
RUN groupadd --system --gid 1001 appgroup && \
    useradd --system --uid 1001 --gid appgroup --no-create-home appuser

# --- Copia o JAR final do Stage de Build ---
# 'target/*.jar' pega o JAR gerado pelo spring-boot-maven-plugin.
# O Lombok é excluído do JAR final via configuração no pom.xml.
COPY --from=build /build/target/*.jar app.jar

# --- Ajusta a posse do arquivo para o usuário sem privilégios ---
RUN chown appuser:appgroup app.jar

# --- Aplica o usuário sem privilégios ---
USER appuser

# --- Otimização de Memória JVM para Containers (Free Tier do Render.com) ---
# UseContainerSupport: faz a JVM ler os limites de memória do cgroup do container.
# MaxRAMPercentage: usa no máximo 75% da RAM do container para o Heap.
# Isso evita o OOMKill (Out Of Memory Kill) que é a causa #1 de crash no Free Tier.
# TieredStopAtLevel=1: desativa o compilador JIT de nível 4 (C2), reduzindo
# drasticamente o consumo de CPU/RAM na inicialização (cold start mais rápido).
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:TieredStopAtLevel=1 \
               -Djava.security.egd=file:/dev/./urandom"

# --- Porta exposta (documentação — o Render usa a variável PORT) ---
EXPOSE 8080

# --- Entrypoint: executa o JAR com as flags de JVM otimizadas ---
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
