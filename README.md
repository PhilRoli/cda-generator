# CDA-Übungsdokument-Ersteller

Web-App zum Erzeugen von ELGA-CDA-Übungsdokumenten (Entlassungsbrief Ärztlich) für den Rettungsdienst.

Funktionen:

- **Direkter PDF-Download** aus der App (`XML -> ELGA CDA2PDF -> Watermark`)
- **Cloud-Szenarien mit Benutzername** (SQLite), inkl. automatischem Backup
- **Admin-Funktionen** per Admin-Token (Löschen, Export/Import)
- **Deployment-Setup für Hetzner/Caddy** (`cda.rolinek.at`)

## Architektur

- **Frontend (statisch):** `index.html`, `css/`, `js/`, `assets/`
- **Backend (Java / Spring Boot):** REST-API unter `/api/*`
- **PDF-Pipeline:**
  1. Browser sendet CDA-XML an `POST /api/pdf`
  2. Backend ruft die ELGA-`CDA2PDF`-Library in-process auf (isolierter Class Loader über die Jars in `elga-lib/`)
  3. Backend stempelt ein diagonales Wasserzeichen ins fertige PDF (PDFBox)
  4. Browser lädt das PDF direkt herunter
- **Szenario-Speicher:** SQLite-Datei im Docker-Volume (`/app/data/cda-uebung.db`)

## Voraussetzungen

1. Java 25 (für lokale Entwicklung)
2. ELGA-CDA2PDF-Libraries (nicht im Repo):
   - `CDA2PDF-Demo.jar`
   - `CDA2PDF-API.jar`
   - `CDA2PDF-DEPS.jar`
3. `assets/elga-stylesheet-uebung.xsl`

## Lokaler Backend-Start

```bash
APP_ELGA_LIB_DIR=/absoluter/pfad/zu/CDA2PDFLib ./mvnw spring-boot:run
```

Healthcheck:

```bash
curl -s http://localhost:8080/api/healthz
```

## Tests

```bash
./mvnw test   # Backend (JUnit)
bun test      # Frontend (js/__tests__)
```

## Cloud-Szenarien

Die UI enthält einen Bereich **Cloud-Szenarien**:

- Benutzername eingeben
- Szenario in Cloud speichern / laden
- Admin kann mit Admin-Token jedes Szenario löschen

Relevante API-Endpunkte:

- `POST /api/scenarios`
- `GET /api/scenarios?username=<name>`
- `GET /api/scenarios/all`
- `GET /api/scenarios/{id}` (optional `?username=<name>`)

Admin-Endpunkte (Header `Authorization: Bearer <token>`):

- `GET /api/admin/scenarios`
- `DELETE /api/admin/scenarios/{id}`
- `GET /api/admin/scenarios/export`
- `POST /api/admin/scenarios/import`

## Deployment auf Hetzner (`cda.rolinek.at`)

Setup mit Caddy als Reverse Proxy und Docker Compose.

### 1. App-Dateien auf den Server bringen

Jeder Push auf `main` deployt automatisch über GitHub Actions (`.github/workflows/deploy.yml`):
Tests (Maven + bun) → `./deploy.sh` → Healthcheck auf `https://cda.rolinek.at/api/healthz`.
Manuell auslösbar über *Actions → Deploy → Run workflow*.

Benötigte Secrets im GitHub-Environment `production`:

- `DEPLOY_SSH_PRIVATE_KEY` – eigener Deploy-Key (Public Key in `~deploy/.ssh/authorized_keys` am Server)
- `DEPLOY_SSH_KNOWN_HOSTS` – Host-Keys des Servers

Fallback lokal:

```bash
DEPLOY_REMOTE='deploy@<server-ip>' ./deploy.sh
```

Das Script:

- baut `dist/` (statisches Frontend)
- synced nach `/opt/apps/cda-uebung`
- baut/restartet den API-Container mit `docker compose up -d --build`

Optional:

- `DEPLOY_DIR=/opt/apps/cda-uebung` (Standardwert, kann überschrieben werden)

### 2. Server-`.env` anlegen

Auf dem Server in `/opt/apps/cda-uebung/.env`:

```env
APP_PORT=48718
APP_ADMIN_TOKEN=<starkes-geheimnis>
APP_WATERMARK_TEXT=ÜBUNGSDOKUMENT
APP_WATERMARK_OPACITY=0.17
APP_CLEAN_PDF_PASSWORD=<passwort>
```

`APP_CLEAN_PDF_PASSWORD` schützt den Endpoint zum Erzeugen eines **sauberen PDFs ohne Wasserzeichen** (`POST /api/pdf/upload`). Ohne gesetzten Wert ist diese Funktion deaktiviert (fail-closed).

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
        encode gzip
        # Unhashed asset names: always revalidate so a deploy is picked up immediately.
        header Cache-Control "no-cache"
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

## Admin-Bereich (`/admin.html`)

Statistik (PDFs, saubere PDFs, XML-Downloads, Fehler, Benutzer, Szenario-Aktivität),
Szenario-Verwaltung und Export/Import. Zugriff: Authelia-SSO (Caddy) **und** `APP_ADMIN_TOKEN`.

Nutzungsereignisse speichern den Cloud-Benutzernamen (falls eingegeben) und eine gekürzte IP
(IPv4 /24, IPv6 /48). Nach `APP_USAGE_RETENTION_DAYS` (Standard 90) werden sie zu anonymen
Tageswerten zusammengefasst und gelöscht.

Caddy-Block für `cda.rolinek.at`. Der Admin-Block muss **vor** den bestehenden `handle`-Blöcken stehen:
Caddy wertet `handle`-Blöcke in der Reihenfolge der Datei aus (dieser Matcher wird nicht nach Spezifität
sortiert), sonst fängt `handle /api/*` die Admin-Aufrufe ab. Das Snippet `(sso)` (Authelia `forward_auth`)
muss im Caddyfile bereits definiert sein.

```txt
cda.rolinek.at {
    @admin path /admin* /api/admin/*
    handle @admin {
        import sso
        @adminApi path /api/admin/*
        reverse_proxy @adminApi localhost:48718
        root * /opt/apps/cda-uebung/dist
        encode gzip
        header Cache-Control "no-cache"
        # /admin liefert admin.html
        try_files {path} {path}.html
        file_server
    }

    handle /api/* { ... }   # unverändert
    handle { ... }          # unverändert (statisches Frontend)
}
```

Nach dem Anwenden in einem eingeloggten Browser prüfen, dass `/api/admin/stats/summary` mit dem
Bearer-Token `200` liefert (manche Authelia-Versionen behandeln einen fremden `Authorization`-Header speziell).
