FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21
WORKDIR /app

COPY --from=build /app/target/cda-uebung-server.jar /app/app.jar
COPY scripts/cda2pdf-uebung /app/scripts/cda2pdf-uebung
COPY assets/elga-stylesheet-uebung.xsl /app/assets/elga-stylesheet-uebung.xsl
COPY entrypoint.sh /app/entrypoint.sh

RUN mkdir -p /app/data /app/elga-lib && chmod +x /app/entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
