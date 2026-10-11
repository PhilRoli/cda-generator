package at.rolinek.cda.ai;

import java.util.List;

public final class AiFillStateFixtures {

    private AiFillStateFixtures() {}

    public static AiFillState sample() {
        return new AiFillState(
            new AiFillState.Patient("Franz", "Huber", "M", "1947-05-03", "06641234567"),
            new AiFillState.Author("Dr.", "Eva", "Maier"),
            new AiFillState.Encounter("2026-10-06T14:30", "2026-10-11T11:00", "Kardiologie, Station 2A",
                AiFillState.Department.KARDIOLOGIE),
            "Dekompensierte Herzinsuffizienz.",
            "Zunehmende Dyspnoe seit einer Woche.",
            "Diuretische Therapie, Rekompensation.",
            "Kontrolle beim Hausarzt in einer Woche.",
            List.of(new AiFillState.Text("Chronische Herzinsuffizienz NYHA III")),
            List.of(new AiFillState.Text("Arterielle Hypertonie")),
            List.of(new AiFillState.Medikament("Torasemid 10 mg", "1-0-0")),
            List.of(new AiFillState.Allergie("Penicillin")),
            List.of(new AiFillState.Risikofaktor("Nikotinabusus")),
            new AiFillState.Patientenverfuegung("keine", "", "", "", ""));
    }
}
