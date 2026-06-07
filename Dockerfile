FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21
WORKDIR /app

# Create a dedicated non-root user to run the application.
# eclipse-temurin (Ubuntu 24.04) already uses uid/gid 1000 for its default
# "ubuntu" user, so we let the system assign the next available id.
RUN groupadd --system appuser && useradd --system --gid appuser --no-create-home --shell /usr/sbin/nologin appuser

# gosu is used by entrypoint.sh to permanently drop from root to appuser
# after fixing /app/data ownership on first boot (see entrypoint.sh).
# A root->unprivileged exec via gosu requires no Linux capabilities.
RUN apt-get update && apt-get install -y --no-install-recommends gosu && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/cda-uebung-server.jar /app/app.jar
COPY scripts/cda2pdf-uebung /app/scripts/cda2pdf-uebung
COPY assets/elga-stylesheet-uebung.xsl /app/assets/elga-stylesheet-uebung.xsl
COPY entrypoint.sh /app/entrypoint.sh

RUN mkdir -p /app/data /app/elga-lib && \
    chmod +x /app/entrypoint.sh && \
    chown -R appuser:appuser /app

# NOTE: intentionally no USER directive here.
# The container starts as root so entrypoint.sh can fix /app/data ownership
# on first boot (Docker named volumes are created root:root, overwriting the
# image-time chown above). entrypoint.sh then exec's via gosu to permanently
# drop privileges to appuser before starting the JVM.

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
