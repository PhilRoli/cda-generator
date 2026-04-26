# CDA-Übungsdokument-Ersteller

Statisches Tool zum Erzeugen realistisch aussehender ELGA-CDA-Dokumente für Übungs­szenarien im Rettungsdienst (Zug 5 FK Salzburg Stadt).

**Wichtig:** Die generierten Dokumente sind ausschließlich für Übungszwecke gedacht — sichtbar gemacht durch ein diagonales "ÜBUNGSZWECKE"-Wasserzeichen über dem gesamten Dokument.

## Aktueller Stand (v1)

- Dokumenttyp: **Entlassungsbrief Ärztlich** (ELGA OID `1.2.40.0.34.77.4.102`)
- Zug-5-Logo statt Krankenhaus-Logo
- "ÜBUNGSZWECKE"-Wasserzeichen
- Faker für österreichische Patientenstammdaten (Name, Adresse, valide SVNR mit Prüfziffer)
- JSON-Export/Import von ausgefüllten Szenarien

## Voraussetzungen

1. Moderner Browser (Chrome, Firefox, Safari, Edge)
2. **`ELGA_Stylesheet_v1.0.xsl`** muss in `assets/` liegen — Download von [elga.gv.at](https://www.elga.gv.at). Diese Datei ist nicht mit-eingecheckt, weil sie ELGA gehört. Sobald sie unter `assets/ELGA_Stylesheet_v1.0.xsl` liegt, wird daraus automatisch eine modifizierte Übungs-Variante (`assets/elga-stylesheet-uebung.xsl`) mit Wasserzeichen erzeugt — siehe `scripts/build-stylesheet.md`.

## Bedienung

1. `index.html` im Browser öffnen (Doppelklick reicht — kein Server, kein Build).
2. Formular ausfüllen:
   - **Stammdaten** manuell eintippen oder Button **"Zufällige Stammdaten"** klicken (Faker)
   - Medizinische Inhalte (Diagnosen, Anamnese, Medikation, …) sind mit realistischen Beispielen vorbefüllt — anpassen oder ersetzen
3. **"Generieren"** klicken → Browser lädt `entlassungsbrief-<nachname>-<datum>.xml` herunter
4. Heruntergeladene `.xml` in den Ordner mit `elga-stylesheet-uebung.xsl` legen (z.B. `assets/`) und dort im Browser öffnen → ELGA-Rendering mit Logo + Wasserzeichen
5. Über die Browser-Druckfunktion zu PDF exportieren oder direkt drucken

## Szenarien speichern und teilen

- **"Szenario speichern"**: Lädt den aktuellen Formular-Stand als `.json` herunter — z.B. nach `scenarios/sturz-senior.json`
- **"Szenario laden"**: File-Picker → vorhandene `.json` → Formular wird befüllt
- Letzter Bearbeitungsstand wird automatisch im LocalStorage des Browsers gespeichert ("zuletzt bearbeitet")

## Projektstruktur

```txt
cda-uebung/
├── index.html                              # Eingabeformular
├── css/form.css                            # Stil des Eingabe-UI
├── js/
│   ├── app.js                              # UI, State, Persistenz
│   ├── faker.js                            # AT-Patientenfaker
│   ├── cda-builder.js                      # CDA-Header + Gemeinsames
│   ├── doctype-entlassung.js               # Entlassungsbrief-Sektionen
│   └── logo-base64.js                      # Zug-5-Logo als Base64
├── assets/
│   ├── ELGA_Stylesheet_v1.0.xsl            # vom User bereitgestellt
│   └── elga-stylesheet-uebung.xsl          # modifizierte Variante (Watermark)
└── scenarios/                              # Eigene Szenarien
```

## PDF-Erzeugung (optional)

Zwei Wege, die XML in PDF zu wandeln:

**Browser** — `.xml` öffnen → "Drucken" → "Als PDF speichern". Nutzt unsere modifizierte ELGA-Stylesheet, also **mit ÜBUNGSZWECKE-Watermark**.

**ELGA CDA2PDF** — der offizielle Konverter erzeugt ein PDF/A-1a, ignoriert aber unsere XSL-Modifikationen (ergo: kein Watermark im PDF). Dafür ist der Output kompakter und behördentauglich. Wir liefern einen kleinen Wrapper `CDA2PDFUebung.java` (in `scripts/cda2pdf-uebung/`), der:

- `enableFullDocument()` weglässt → kürzere "normale" PDF-Variante
- `hideDocumentInformation()` aufruft → kein "Zusätzliche Informationen"-Block

Kompilieren (einmalig):

```bash
cd scripts/cda2pdf-uebung
JARDIR=/Users/philipp/mbi/SS26/IGS4VO/visualization/ELGA_CDA2PDF/CDA2PDFLib
javac -cp "$JARDIR/CDA2PDF-Demo.jar:$JARDIR/CDA2PDF-API.jar:$JARDIR/CDA2PDF-DEPS.jar" CDA2PDFUebung.java
```

Aufrufen:

```bash
java -cp ".:$JARDIR/CDA2PDF-Demo.jar:$JARDIR/CDA2PDF-API.jar:$JARDIR/CDA2PDF-DEPS.jar" \
  CDA2PDFUebung input.xml output-dir/ output.pdf
```

Komfort via zsh-Function (in `~/.zshrc`):

```zsh
cda2pdf() {
  local jardir="/Users/philipp/mbi/SS26/IGS4VO/visualization/ELGA_CDA2PDF/CDA2PDFLib"
  local wrapper="/Users/philipp/Development/cda-uebung/scripts/cda2pdf-uebung"
  local input=${1:A}
  local output=${2:-${input:r}.pdf}
  output=${output:A}
  ( cd "$wrapper" && java \
      -cp ".:$jardir/CDA2PDF-Demo.jar:$jardir/CDA2PDF-API.jar:$jardir/CDA2PDF-DEPS.jar" \
      CDA2PDFUebung "$input" "${output:h}/" "${output:t}" )
}
```

## Hinweise

- Alle Daten bleiben lokal im Browser. Es wird nichts hochgeladen.
- Die generierten Dokumente sind formal CDA-konform, durchlaufen aber keine offizielle Schematron-Validierung.
- Echte ELGA-Dokumente dürfen NICHT mit diesem Tool nachgestellt werden, um Verwechslungsgefahr auszuschließen — daher das permanent sichtbare Wasserzeichen (im Browser-Rendering).
