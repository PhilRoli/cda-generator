package at.rolinek.cda.usage;

/** Recorded event kinds; {@link #wireName()} is what is stored and sent to the admin UI. */
public enum UsageType {
    PDF("pdf"),
    CLEAN_PDF("clean_pdf"),
    XML_DOWNLOAD("xml_download"),
    SCENARIO_SAVE("scenario_save"),
    SCENARIO_LOAD("scenario_load");

    private final String wireName;

    UsageType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
