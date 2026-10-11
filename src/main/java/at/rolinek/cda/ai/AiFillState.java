package at.rolinek.cda.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Structured-output target for the AI fill. Component names equal the frontend state keys
 * (js/state.js) so the serialized form feeds sanitizeState() unchanged. Fields the client
 * generates itself are deliberately absent: SVNR, documentDate, caseId, encounter type, country, and
 * the hospital and the patient's address (picked from the curated list / address pool of the chosen
 * Bundesland so they are real and consistent; {@code encounter.department} steers the hospital choice).
 */
public record AiFillState(
    Patient patient,
    Author author,
    Encounter encounter,
    @JsonPropertyDescription("Aufnahmegrund, 1-3 Sätze") String aufnahmegrund,
    @JsonPropertyDescription("Anamnese, 2-5 Sätze") String anamnese,
    @JsonPropertyDescription("Stationärer Verlauf, 2 kurze Absätze, getrennt durch eine Leerzeile") String verlauf,
    @JsonPropertyDescription("Empfehlungen für die Weiterbehandlung, kurze Absätze getrennt durch eine Leerzeile")
    String empfehlungen,
    @JsonPropertyDescription("Entlassungsdiagnosen, 1-5 Einträge") List<Text> diagnosen,
    @JsonPropertyDescription("Vorerkrankungen, 0-5 Einträge") List<Text> vorerkrankungen,
    @JsonPropertyDescription("Entlassungsmedikation, 2-8 Einträge") List<Medikament> medikation,
    @JsonPropertyDescription("Allergien/Unverträglichkeiten, 0-3 Einträge") List<Allergie> allergien,
    @JsonPropertyDescription("Risikofaktoren, 0-4 Einträge") List<Risikofaktor> risikofaktoren,
    Patientenverfuegung patientenverfuegung) {

    public record Patient(String givenName, String familyName,
                          @JsonPropertyDescription("Genau M oder F") String gender,
                          @JsonPropertyDescription("Geburtsdatum YYYY-MM-DD, passend zum genannten Alter") String birthDate,
                          @JsonPropertyDescription("Österreichische Mobilnummer, nur Ziffern") String phone) {}

    public record Author(@JsonPropertyDescription("z.B. Dr., OA Dr., Prim. Dr.") String title,
                         String givenName, String familyName) {}

    public record Encounter(@JsonPropertyDescription("Aufnahme YYYY-MM-DDTHH:mm") String admissionDate,
                            @JsonPropertyDescription("Entlassung YYYY-MM-DDTHH:mm, nach der Aufnahme, nicht in der Zukunft")
                            String dischargeDate,
                            @JsonPropertyDescription("Station, beginnt mit der Abteilung, z.B. Kardiologie, Station 2A") String ward,
                            @JsonPropertyDescription("Fachrichtung der behandelnden Abteilung, passend zur Erkrankung") Department department) {}

    /** Constant names are mirrored by DEPARTMENTS in js/hospitals.js (guarded by a bun test). */
    public enum Department {
        INNERE_MEDIZIN, KARDIOLOGIE, GASTROENTEROLOGIE, PULMOLOGIE, NEPHROLOGIE, ONKOLOGIE, GERIATRIE,
        NEUROLOGIE, NEUROCHIRURGIE, ALLGEMEINCHIRURGIE, UNFALLCHIRURGIE, ORTHOPAEDIE, GEFAESSCHIRURGIE,
        HERZ_THORAX_CHIRURGIE, UROLOGIE, GYNAEKOLOGIE_GEBURTSHILFE, KINDER_JUGENDHEILKUNDE, PSYCHIATRIE,
        HNO, AUGENHEILKUNDE, DERMATOLOGIE, PLASTISCHE_CHIRURGIE
    }

    public record Text(String text) {}

    public record Medikament(@JsonPropertyDescription("Präparat mit Stärke, z.B. Torasemid 10 mg") String medikament,
                             @JsonPropertyDescription("Schema morgens-mittags-abends-nachts, z.B. 1-0-0 oder b.B.")
                             String schema) {}

    public record Allergie(String substanz) {}

    public record Risikofaktor(String faktor) {}

    public record Patientenverfuegung(
        @JsonPropertyDescription("Genau einer von: unbekannt, keine, beachtlich, verbindlich, vorsorgevollmacht; im Zweifel keine")
        String status,
        @JsonPropertyDescription("Leer, wenn status unbekannt oder keine") String hinterlegtBei,
        @JsonPropertyDescription("YYYY-MM-DD oder leer") String datum,
        @JsonPropertyDescription("YYYY-MM-DD oder leer") String gueltigBis,
        String bemerkung) {}
}
