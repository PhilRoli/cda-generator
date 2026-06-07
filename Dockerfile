FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21
WORKDIR /app

RUN groupadd --gid 1000 appuser && useradd --uid 1000 --gid 1000 --no-create-home --shell /usr/sbin/nologin appuser

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
