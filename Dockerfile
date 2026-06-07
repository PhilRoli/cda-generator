FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21
WORKDIR /app

# System user/group with auto-assigned ids — the eclipse-temurin (Ubuntu 24.04)
# base already occupies uid/gid 1000 with its default "ubuntu" user, so we must
# not pin to 1000. chown below references the account by name.
RUN groupadd --system appuser && useradd --system --gid appuser --no-create-home --shell /usr/sbin/nologin appuser

# gosu is used by entrypoint.sh to fix /app/data ownership at runtime when a
# fresh Docker named volume is mounted (new volumes are created root:root,
# overwriting the image-time chown that runs before the volume is attached).
RUN apt-get update && apt-get install -y --no-install-recommends gosu && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/cda-uebung-server.jar /app/app.jar
COPY scripts/cda2pdf-uebung /app/scripts/cda2pdf-uebung
COPY assets/elga-stylesheet-uebung.xsl /app/assets/elga-stylesheet-uebung.xsl
COPY entrypoint.sh /app/entrypoint.sh

RUN mkdir -p /app/data /app/elga-lib && \
    chmod +x /app/entrypoint.sh && \
    chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
