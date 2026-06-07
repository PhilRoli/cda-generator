# Implementation Plan: Review Improvements

Date: 2026-06-07
Status: Ready for implementation
Source: Code/UX review of `cda-uebung` (see review thread)

This document lists every agreed-upon improvement, a short summary, the recommended
implementation, affected files, and dependencies. A phased execution order is given at
the end so an implementation agent can work through it top to bottom.

> Decisions already made by the product owner are baked in below. The current correct app
> version is **1.3.0**. The clean-PDF password is **`mbi24`** (provided via `.env`).

---

## Backend — safety, config & architecture

### #3 — Stop leaking internal error details to clients
**Summary:** Absolute filesystem paths and internal messages currently reach the browser.
**Why:** `application.properties` has `server.error.include-message=always`, and the PDF
service propagates `ex.getMessage()` plus raw subprocess output into client responses.
**Implementation:**
- Remove `server.error.include-message=always` (or set to `never`).
- In `PdfGenerationService`, log full detail server-side (`LOG.error`) but throw
  `ResponseStatusException` with a **generic** client message (e.g. "PDF-Erstellung
  fehlgeschlagen."). Do not include `ex.getMessage()`, subprocess stdout, or paths.
- Keep the German user-facing wording, just drop the internal specifics.
**Files:** `src/main/resources/application.properties`,
`src/main/java/at/rolinek/cda/pdf/PdfGenerationService.java`.
**Depends on:** none.

### #4 — Reject untrusted XML with DOCTYPE (XXE/SSRF defense)
**Summary:** Arbitrary uploaded XML reaches the ELGA parser; block external entities.
**Implementation:**
- Before passing any XML to the converter (both `/api/pdf` and `/api/pdf/upload`),
  validate it: reject any document containing a `<!DOCTYPE` declaration with HTTP 400.
- Prefer a hardened parse check using `DocumentBuilderFactory` with
  `setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)` and
  `setExpandEntityReferences(false)`; if parsing throws, return 400.
- Add this as a small guard method invoked from both PDF code paths.
**Files:** `PdfGenerationService.java` (or a new `XmlSafetyGuard` helper), `PdfController.java`.
**Depends on:** none. Do this **before** #7 so the guard survives the refactor.

### #6 — Run the container as a non-root user
**Summary:** Container (and the spawned converter) currently runs as root.
**Implementation:**
- In `Dockerfile`, create a non-root user/group, `chown` `/app/data` and the app dir,
  and add `USER appuser` before `ENTRYPOINT`.
- Ensure the runtime user can read the mounted `elga-lib` (mounted `:ro`) and write
  `/app/data`. Verify `entrypoint.sh`'s `javac`/`java` steps still work as that user
  (compilation writes into `/app/scripts/cda2pdf-uebung` — make it writable or precompile).
- Note: if #7 lands, the `javac` step in `entrypoint.sh` goes away, simplifying perms.
**Files:** `Dockerfile`, `entrypoint.sh`.
**Depends on:** coordinate with #7 (entrypoint compile step).

### #8 — Single source of truth for the version (set to 1.3.0)
**Summary:** `pom.xml` says `1.0.0`, frontend `APP_VERSION` says `1.3.0`.
**Implementation:**
- Set `pom.xml` `<version>` to `1.3.0`.
- Expose the version from the backend (e.g. add it to `/api/healthz` or a new
  `/api/version` reading the Maven build version) and have the frontend read it instead
  of the hardcoded `APP_VERSION`. Minimum acceptable: keep `APP_VERSION` but make `pom.xml`
  match and add a code comment that both must move together.
- Recommended: inject build version into the served frontend so it can never drift again.
**Files:** `pom.xml`, `js/app.js`, optionally `HealthController.java`.
**Depends on:** none.

### #9 — Tune SQLite for concurrency
**Summary:** No WAL / busy-timeout → "database is locked" under concurrent writes.
**Implementation:**
- In `DatabaseConfig`, after creating the `SQLiteDataSource`, enable WAL and a busy
  timeout via `SQLiteConfig` (`setJournalMode(WAL)`, `setBusyTimeout(...)`) or run
  `PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;` on init.
**Files:** `src/main/java/at/rolinek/cda/config/DatabaseConfig.java`.
**Depends on:** none.

### #7 — Call the ELGA library in-process instead of shelling out
**Summary:** Replace the `ProcessBuilder("java", ...)` per-request fork with a direct
in-process call to `CDA2PDFConverter`.
**Why:** Removes per-request JVM startup, the temp-dir dance, the runtime `javac` step,
and the "Konverter noch nicht bereit" race.
**Implementation:**
- Load the ELGA jars at startup via a dedicated `URLClassLoader` pointing at
  `elga-lib/*.jar` (jars still not in repo, still mounted read-only).
- Port the logic of `CDA2PDFUebung` / `CDA2PDFClean` into the service: build
  `CDA2PDFBuilder`, create `CDA2PDFConverter`, call `xmlToPdfPerXsl(InputStream)`.
  Keep the watermark variant (current `generatePdf`) and the clean variant
  (`generatePdfClean`) as two code paths over the same in-process converter.
- Keep the stylesheet-PI normalization (`ensureStylesheetPi`).
- **Caveat to verify:** the ELGA converter may not be thread-safe / may hold static
  state. Treat conversions as needing serialization — this is exactly why #1's semaphore
  matters even more in-process. If thread-safety is uncertain, start with a concurrency
  limit of 1 (serialize) and revisit.
- Once done, delete the `javac` step from `entrypoint.sh` and the `.class` precompiled
  artifacts / wrapper-class existence checks.
**Files:** `PdfGenerationService.java`, `entrypoint.sh`, `Dockerfile`,
`scripts/cda2pdf-uebung/*` (wrappers become reference-only or are removed), `AppProperties`.
**Depends on:** do **after** #3/#4 (so error handling + XML guard already exist);
**before** #1/#2 (they layer onto the new execution model).

### #1 — Bound PDF generation concurrency
**Summary:** Unauthenticated PDF endpoints + heavy conversions = DoS vector.
**Implementation:**
- Wrap conversion in a `Semaphore` with a small permit count (start at 1–2; in-process
  per #7 this also protects against converter thread-safety issues).
- If a permit can't be acquired within a short window, return HTTP 503 ("Server
  ausgelastet, bitte erneut versuchen.").
- Consider a simple per-IP rate limit (optional; note as stretch).
- Cap input size: set `spring.servlet.multipart.max-file-size` /
  `max-request-size` for `/api/pdf/upload`, and reject oversized JSON XML in `/api/pdf`.
**Files:** `PdfGenerationService.java`, `PdfController.java`, `application.properties`.
**Depends on:** #7 (implement on the final execution model).

### #2 — Add a timeout to PDF generation
**Summary:** A stalled conversion currently blocks a servlet thread forever.
**Implementation:**
- In-process (post-#7): run the conversion on a bounded `ExecutorService` and
  `future.get(timeout, SECONDS)`; on timeout cancel and return HTTP 504/503 with a
  generic message.
- (If #7 is deferred and the subprocess remains: use
  `process.waitFor(timeout, unit)` + `destroyForcibly()`.)
**Files:** `PdfGenerationService.java`.
**Depends on:** #7 (shares the execution model with #1).

### #5 — Scenario backup & admin restore
**Summary:** Self-asserted usernames are acceptable here, but add backup/restore so a
malicious actor's damage is recoverable.
**Implementation:**
- **Export (admin):** `GET /api/admin/scenarios/export` (Bearer admin token) → JSON dump
  of all scenario records (id, username, title, payload, timestamps).
- **Import/restore (admin):** `POST /api/admin/scenarios/import` (Bearer admin token) →
  upsert records from a previously exported dump. Decide overwrite policy (upsert by id).
- **Optional scheduled backup:** a `@Scheduled` job that copies the SQLite file (or writes
  a JSON dump) into `/app/data/backups/` with a timestamp, retaining the last N.
- Reuse the existing admin-token check (`requireAdminToken`); see also #18/#6 token notes.
**Files:** `ScenarioController.java`, `ScenarioService.java`, `ScenarioRepository.java`
(add `insertOrReplace`/`upsert`), possibly a new `ScenarioBackupService`.
**Depends on:** none (but easier after #9). Frontend admin UI can be added with the
frontend batch.

### #19 — Password-gate the unwatermarked ("clean") PDF
**Summary:** The clean-PDF path produces an unmarked, real-looking discharge letter;
require a password (`mbi24`, from `.env`).
**Implementation:**
- Add config `app.clean-pdf-password=${APP_CLEAN_PDF_PASSWORD:}` and an `AppProperties`
  field; document it in `.env.example` (value `mbi24` for the deployment).
- `/api/pdf/upload` must require the password (form field `password` or header
  `X-Clean-Pdf-Password`), compared constant-time (`MessageDigest.isEqual`). If the
  password is unset on the server, **deny** the endpoint (fail closed). Wrong/missing →
  HTTP 403.
- Frontend (`setupXmlUpload`): add a password input to the XML-convert dialog (or prompt)
  and send it with the request; surface 403 as a clear error.
**Files:** `AppProperties.java`, `application.properties`, `.env.example`,
`PdfController.java`, `index.html` (xml-upload dialog), `js/app.js`.
**Depends on:** none.

---

## Backend — tests

### #10 — Add backend (and minimal frontend) tests
**Summary:** Only `ScenarioControllerPublicTest` exists today.
**Implementation:**
- **Pure units (no ELGA lib needed):**
  - `toPdfFilename` sanitization — extract to a testable method/util and cover
    extension stripping, illegal-char replacement, blank fallback.
  - XML safety guard (#4): DOCTYPE present → rejected; clean XML → accepted.
  - `ensureStylesheetPi` normalization.
  - Admin token check (valid/invalid/unset) and #19 password check.
  - `ScenarioService` save/update ownership rules, normalization, backup export/import (#5).
- **Watermark:** generate a tiny in-memory PDF with PDFBox, run `applyWatermark`, assert it
  succeeds and page count is preserved (no ELGA dependency).
- **Frontend golden test for `cda-builder` / `doctype-entlassung`:** there is currently no
  JS test runner. Add one (recommend Node's built-in `node:test` + `assert`, run via
  `node --test`, since the code is ESM). Snapshot `buildEntlassungsbrief(fixedState)` to a
  golden XML file and assert stability; add `isValidSvnr` tests.
- Add an npm script (or a `scripts/test-js.sh`) so frontend tests are runnable in CI.
**Files:** `src/test/java/...` (new test classes), new `js/__tests__/` (or similar),
possibly a `package.json` for the test runner.
**Depends on:** backend behavior should be stable first (do after the backend changes,
especially #3/#4/#5/#19 so their behavior is the thing under test).

---

## Frontend — code quality & UX

### #11 — Deep-merge loaded state
**Summary:** `Object.assign(defaultState(), loaded)` is shallow, so a partial nested object
(e.g. `patient`) replaces the whole default subtree and drops newer fields.
**Implementation:** Add a recursive deep-merge helper and use it in `loadState()` and
`loadCloudScenario()` (and the local-file load path). Arrays should be replaced, not merged.
**Files:** `js/app.js`.
**Depends on:** none.

### #13 — De-duplicate API helpers and extract inline styles
**Summary:** `apiJson`/`apiPdf` are near-identical; `index.html` has scattered inline styles.
**Implementation:**
- Unify the fetch+error-handling logic into one helper parameterized by response type
  (`'json'` | `'blob'`).
- Move all `style="..."` attributes in `index.html` into CSS classes in `css/form.css`.
**Files:** `js/app.js`, `index.html`, `css/form.css`.
**Depends on:** none.

### #18 — Version badge as a button + enforce valid SVNR
**Summary:** Version badge is a non-focusable `<span>`; SVNR isn't validated.
(Empty fields are intentional — they're simply omitted from the PDF; **no change** there.)
**Implementation:**
- Change `#version-badge` from `<span>` to a `<button>` (keep styling) so it's
  keyboard-accessible; wire the existing click handler.
- Enforce a valid SVNR: validate against `isValidSvnr` (already exported from `faker.js`)
  on input/blur and before PDF/XML generation; block generation with a clear message if
  the SVNR is present but invalid. (Empty SVNR remains allowed.)
**Files:** `index.html`, `js/app.js`, `js/faker.js` (reuse `isValidSvnr`), `css/form.css`.
**Depends on:** none.

### #14 — Remove unused `data-tab` attributes
**Summary:** Tabs were never built; long-scroll is the chosen UX. Remove dead attributes.
**Implementation:** Delete all `data-tab="..."` attributes from the `<section>` elements in
`index.html`. Confirm nothing in JS/CSS references `data-tab` (it currently doesn't).
**Files:** `index.html`.
**Depends on:** none.

### #12 + #17 — Disable the generate button + show progress during PDF generation
**Summary:** The primary "PDF generieren" button doesn't disable during the multi-second
request and there's no spinner; double-clicks fire duplicate requests.
**Implementation:**
- On click of `#btn-generate` (and `#btn-generate-xml` where relevant), disable the button,
  show a spinner/loading state, and re-enable in a `finally` block (mirror the existing
  `setupXmlUpload` convert flow).
- Add a small reusable "busy" helper so #19's clean-PDF button and this share one pattern.
- Add a spinner element/CSS.
**Files:** `js/app.js`, `index.html`, `css/form.css`.
**Depends on:** pairs with #16 (status/feedback) and #19 (same busy pattern).

### #16 — Replace single-line status with toasts/inline alerts
**Summary:** `#status` is one easily-missed line and each message overwrites the previous,
so errors vanish.
**Implementation:** Add a lightweight toast/notification component (success/error
variants, auto-dismiss for success, sticky/dismissible for errors). Route `setStatus`
calls through it (or add `notify(msg, type)`), keeping backward compatibility.
**Files:** `js/app.js`, `index.html`, `css/form.css`.
**Depends on:** none; do alongside #12/#17.

### #15 — Make the layout responsive
**Summary:** `css/form.css` has no media queries; fixed grid columns break on small screens.
**Implementation:** Add breakpoints (e.g. `max-width: 768px` / `480px`) that collapse
`.grid-2`/`.grid-3`/`.grid-4`/`.grid-medi` to fewer/one column, and let the header and
footer action bar wrap. Verify the dialogs (`max-width: 540–560px`) behave on mobile.
**Files:** `css/form.css`.
**Depends on:** best done after #13 (inline styles moved into CSS) so all layout lives in
one place.

---

## Recommended implementation order

Work in phases; each phase is independently shippable.

**Phase 1 — Backend hardening & config (low risk, high value)**
1. #8 — Version → 1.3.0, single source of truth
2. #3 — Stop leaking internal errors
3. #9 — SQLite WAL + busy timeout
4. #4 — Reject DOCTYPE XML (XXE guard)
5. #6 — Non-root container *(coordinate with #7's entrypoint changes)*

**Phase 2 — PDF pipeline rearchitecture**
6. #7 — In-process ELGA conversion (verify thread-safety)
7. #1 — Concurrency limit + input size caps
8. #2 — Conversion timeout

**Phase 3 — New backend capabilities**
9. #19 — Password-gate the clean PDF (`mbi24` via `.env`)
10. #5 — Scenario backup + admin restore

**Phase 4 — Tests**
11. #10 — Backend unit tests + minimal frontend golden tests (after backend behavior is stable)

**Phase 5 — Frontend quality & UX**
12. #11 — Deep-merge loaded state
13. #13 — Unify API helpers + extract inline styles
14. #14 — Remove `data-tab` attributes
15. #18 — Version badge → button + SVNR validation
16. #12 + #17 — Disable button + progress spinner
17. #16 — Toast/inline notifications
18. #15 — Responsive breakpoints

> Cross-cutting note: #12/#17, #16, and the frontend half of #19 share a "busy state +
> notification" pattern — build that small helper once (in the #12/#17 step) and reuse it.

---

## Changelog entry for v1.4.0 (user-facing)

When the work above ships, bump the version to **1.4.0** and add the entry below.

**Version bump locations:** `js/app.js` (`APP_VERSION = '1.4.0'`) and `pom.xml`
(`<version>1.4.0</version>`) — see #8; ideally a single source of truth.

**Guidance:** Do **not** surface security specifics (XXE guard, error-leak fixes,
non-root container, concurrency/timeout limits, SQLite tuning). Fold all of those into one
line — `Sicherheitsupdates und Bugfixes`. Everything with a visible user benefit gets its
own bullet.

Add this block at the **top** of the `#changelog-dialog` in `index.html` (the dialog is
newest-first), above the existing `v1.3.0` entry:

```html
<div class="changelog-entry">
    <h3>v1.4.0 — Juni 2026</h3>
    <ul>
        <li>PDF-Erstellung deutlich schneller und stabiler</li>
        <li>„Sauberes PDF" (ohne Wasserzeichen) ist jetzt passwortgeschützt</li>
        <li>Szenario-Backup und Wiederherstellung durch Admins</li>
        <li>Laden von Szenarien robuster — fehlende Felder bleiben erhalten</li>
        <li>Fortschrittsanzeige während der PDF-Erstellung, keine versehentlichen Doppel-Downloads mehr</li>
        <li>Hinweise und Fehlermeldungen erscheinen jetzt als Benachrichtigungen</li>
        <li>SVNR wird im Formular auf Gültigkeit geprüft</li>
        <li>Optimierte Darstellung für Tablet und Smartphone</li>
        <li>Versions-Anzeige per Tastatur bedienbar (Barrierefreiheit)</li>
        <li>Sicherheitsupdates und Bugfixes</li>
    </ul>
</div>
```

**Bullet → item mapping (for the implementer; not shown to users):**

| Changelog bullet | Items |
|------------------|-------|
| PDF-Erstellung schneller/stabiler | #7 |
| Sauberes PDF passwortgeschützt | #19 |
| Szenario-Backup & Wiederherstellung | #5 |
| Laden robuster, Felder bleiben erhalten | #11 |
| Fortschrittsanzeige, keine Doppel-Downloads | #12, #17 |
| Benachrichtigungen | #16 |
| SVNR-Prüfung | #18 (SVNR) |
| Tablet/Smartphone-Darstellung | #15 |
| Versions-Anzeige per Tastatur | #18 (badge) |
| Sicherheitsupdates und Bugfixes | #1, #2, #3, #4, #6, #9, #13, #14 |
