# CDA-Übungsdokument-Ersteller

Web-App zum Erzeugen von ELGA-CDA-Übungsdokumenten (Entlassungsbrief Ärztlich) für den Rettungsdienst.

Neu in dieser Version:

- **Direkter PDF-Download** aus der App (`XML -> ELGA CDA2PDF -> Watermark`)
- **Cloud-Szenarien mit Benutzername** (SQLite)
- **Admin-Löschung** von Szenarien per Admin-Token
- **Deployment-Setup für Hetzner/Caddy** (`cda.rolinek.at`)

## Architektur

- **Frontend (statisch):** `index.html`, `css/`, `js/`, `assets/`
- **Backend (Java / Spring Boot):** REST-API unter `/api/*`
- **PDF-Pipeline:**
  1. Browser sendet CDA-XML an `POST /api/pdf`
  2. Backend ruft ELGA `CDA2PDF` über den Java-Wrapper auf
  3. Backend stempelt ein diagonales Wasserzeichen ins fertige PDF (PDFBox)
  4. Browser lädt das PDF direkt herunter
- **Szenario-Speicher:** SQLite-Datei im Docker-Volume (`/app/data/cda-uebung.db`)

## Voraussetzungen

1. Java 21 (für lokale Entwicklung)
2. ELGA-CDA2PDF-Libraries (nicht im Repo):
   - `CDA2PDF-Demo.jar`
   - `CDA2PDF-API.jar`
   - `CDA2PDF-DEPS.jar`
3. `assets/elga-stylesheet-uebung.xsl`

## Lokaler Backend-Start

```bash
APP_ELGA_LIB_DIR=/absoluter/pfad/zu/CDA2PDFLib mvn spring-boot:run
```

Healthcheck:

```bash
curl -s http://localhost:8080/api/healthz
```

## Cloud-Szenarien

Die UI enthält einen Bereich **Cloud-Szenarien**:

- Benutzername eingeben
- Szenario in Cloud speichern / laden
- Admin kann mit Admin-Token jedes Szenario löschen

Relevante API-Endpunkte:

- `POST /api/scenarios`
- `GET /api/scenarios?username=<name>`
- `GET /api/scenarios/{id}?username=<name>`
- `DELETE /api/admin/scenarios/{id}` mit Header `Authorization: Bearer <token>`

## Deployment auf Hetzner (`cda.rolinek.at`)

Diese Umsetzung folgt deiner Server-Strategie (Caddy + Docker Compose).

### 1. App-Dateien auf den Server bringen

```bash
./deploy.sh
```

Das Script:

- baut `dist/` (statisches Frontend)
- synced nach `/opt/apps/cda-uebung`
- baut/restartet den API-Container mit `docker compose up -d --build`

### 2. Server-`.env` anlegen

Auf dem Server in `/opt/apps/cda-uebung/.env`:

```env
APP_PORT=48718
APP_ADMIN_TOKEN=<starkes-geheimnis>
APP_WATERMARK_TEXT=UEBUNGSZWECKE - NUR FUER TRAININGS!
APP_WATERMARK_OPACITY=0.17
```

### 3. ELGA-Libraries bereitstellen

Auf dem Server unter:

`/opt/apps/cda-uebung/elga-lib/`

mit den 3 Jar-Dateien:

- `CDA2PDF-Demo.jar`
- `CDA2PDF-API.jar`
- `CDA2PDF-DEPS.jar`

### 4. Caddy konfigurieren

Block in `/etc/caddy/Caddyfile`:

```txt
cda.rolinek.at {
    handle /api/* {
        reverse_proxy localhost:48718
    }
    handle {
        root * /opt/apps/cda-uebung/dist
        try_files {path} /index.html
        file_server
    }
}
```

Danach:

```bash
sudo systemctl reload caddy
```

## Wichtige Dateien

- `src/main/java/...` – Backend
- `docker-compose.yml` – API-Container
- `Dockerfile` – Build + Runtime Image
- `scripts/build-dist.sh` – erzeugt `dist/`
- `deploy.sh` – Hetzner-Deploy
- `.env.example` – Env-Template

## Hinweis

Alle generierten Dokumente sind ausschließlich für **Übungszwecke** bestimmt.
