# Feature Design: Version Badge, Changelog, Bundesland-Daten, Ambulant/Stationär, Öffentliche Cloud-Szenarien, Krankenhaus-Auswahl

Date: 2026-04-28  
Status: Approved

---

## 1. Version Badge + Changelog

**Goal:** Benutzer sehen die aktuelle App-Version im Header und können das Changelog einsehen.

**Implementation:**
- Konstante `APP_VERSION = '1.0.0'` in `js/app.js`
- Header-HTML: `<span id="version-badge" class="header-version-badge">v1.0.0</span>` rechts neben dem E-Mail-Button
- Klick auf Badge öffnet natives `<dialog id="changelog-dialog">` Modal
- Changelog-Inhalt hardcoded in `index.html`
- CSS: `.header-version-badge` — gleicher Stil wie `.header-email-btn`, cursor pointer

**Changelog 1.0.0 (initiale Version):**
- Formulargestützter CDA Entlassungsbrief-Ersteller
- PDF- und XML-Generierung
- Lokale Szenario-Verwaltung (JSON)
- Cloud-Szenario-Verwaltung (eigene Szenarien)
- Zufällige Patientenstammdaten
- Quick-Add für Diagnosen, Medikation, Allergien, Risikofaktoren
- Patientenverfügungs-Status

---

## 2. Adressdaten für alle Bundesländer

**Goal:** Zufällige Patientenadressen decken alle 9 österreichischen Bundesländer ab.

**Implementation:**
- `js/faker.js`: Neue Struktur `ADRESSEN_BY_BUNDESLAND` mit Keys `W`, `NOE`, `OOE`, `S`, `ST`, `T`, `V`, `K`, `B`
- Jedes Bundesland: 3–5 reale Straßen mit PLZ und Ort
- `ADRESSEN` bleibt als flaches Array (alle Bundesländer zusammengeführt) — `generateRandomPatient()` bleibt unverändert
- `ADRESSEN_BY_BUNDESLAND` wird auch von Feature 5 (Krankenhäuser) für die Bundesland-Auswahl genutzt

---

## 3. Ambulant / Stationär

**Goal:** Encounter-Typ (stationär/ambulant) wird im Formular auswählbar und wirkt sich auf das CDA-XML aus.

**State-Feld:** `encounter.type` — Default `'IMP'`

**HTML:** `<select data-bind="encounter.type">` im Aufenthalt-Abschnitt:
- `IMP` → „Stationär"
- `AMB` → „Ambulant"

**CDA-Änderung (`cda-builder.js`):**
- `renderEncompassingEncounter` liest `encounter.type`
- IMP: `<code code="IMP" codeSystem="2.16.840.1.113883.5.4" displayName="inpatient encounter"/>`
- AMB: `<code code="AMB" codeSystem="2.16.840.1.113883.5.4" displayName="ambulatory"/>`

**Brieftext-Anpassung (`cda-builder.js`):**
- `defaultBrieftext`: stationär → „in stationärer Behandlung befand"; ambulant → „in ambulanter Behandlung befand"

**Default-State (`app.js`):** `encounter.type: 'IMP'` hinzufügen

---

## 4. Alle Cloud-Szenarien öffentlich laden

**Goal:** Benutzer können alle Cloud-Szenarien aller User einsehen und laden. Der Ersteller-Username wird in der Liste angezeigt.

**Backend-Änderungen:**

1. `ScenarioController`: Neuer Endpoint `GET /api/scenarios/all` — ruft `scenarioService.listAll()` (neue öffentliche Methode ohne Token-Check)
2. `ScenarioService`: Neue Methode `listAll()` — delegiert an `repository.listAll()`
3. `GET /api/scenarios/{id}`: Username-Parameter wird optional — wenn weggelassen, kein Besitzcheck (public read)

**Frontend-Änderungen (`js/app.js`, `index.html`):**

- Cloud-Panel: Checkbox „Alle Szenarien anzeigen" (`id="cloud-show-all"`)
- Wenn aktiv:
  - `refreshCloudScenarios` ruft `/api/scenarios/all` statt `/api/scenarios?username=...`
  - Username-Eingabefeld wird nicht benötigt (bleibt aber sichtbar für eigene Szenarien)
  - Dropdown-Einträge zeigen `[username] Titel · Datum`
- Wenn inaktiv: bisheriges Verhalten
- `loadCloudScenario`: verwendet `/api/scenarios/{id}` ohne Username-Parameter wenn "Alle" aktiv
- Beim Speichern nach fremdem Laden: eigener Username wird verwendet (Fork-Verhalten)

---

## 5. Bundesland-angepasste Krankenhausliste

**Goal:** Im Krankenhaus-Abschnitt kann über einen Bundesland-Selektor ein konkretes Krankenhaus ausgewählt werden, das alle Felder automatisch ausfüllt.

**Neue Datei `js/hospitals.js`:**
- Export: `HOSPITALS_BY_BUNDESLAND` — Objekt mit 9 Bundesland-Keys
- Je Bundesland: 4–8 Einträge mit `{ name, phone, street, houseNumber, postalCode, city }`
- Reale österreichische Krankenhäuser

**HTML-Änderungen (Krankenhaus-Abschnitt):**
- Vor den bestehenden Formularfeldern: neue `div.grid-2` mit:
  1. Bundesland-Select (`id="org-bundesland"`, kein data-bind — reine UI-Hilfe)
  2. Krankenhaus-Quick-Select (`id="org-hospital-select"`) — initial disabled

**`js/app.js`-Änderungen:**
- Import von `HOSPITALS_BY_BUNDESLAND` aus `./hospitals.js`
- `setupHospitalSelector()`: Event-Listener auf Bundesland-Select → befüllt Krankenhaus-Dropdown
- Bei Krankenhausauswahl: setzt `state.organization.name/phone/address.*`, ruft `saveState()` + `rebindAll()` auf
- Wird in `init()` aufgerufen

---

## Files to Create/Modify

| File | Action |
|------|--------|
| `index.html` | Add version badge, changelog dialog, encounter type select, org Bundesland/hospital selects |
| `js/app.js` | APP_VERSION constant, changelog open, encounter.type default, all-scenarios toggle, hospital selector setup |
| `js/faker.js` | ADRESSEN_BY_BUNDESLAND with all 9 Bundesländer |
| `js/hospitals.js` | New file: HOSPITALS_BY_BUNDESLAND |
| `css/form.css` | `.header-version-badge`, `.changelog-dialog` styles |
| `src/.../ScenarioController.java` | Add `GET /api/scenarios/all`, make username optional in GET by ID |
| `src/.../ScenarioService.java` | Add public `listAll()` method |
