package at.rolinek.cda.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String version = "unknown";
    private String dbPath = "data/cda-uebung.db";
    private String elgaLibDir = "elga-lib";
    private String elgaWrapperDir = "scripts/cda2pdf-uebung";
    private String elgaStylesheetPath = "assets/ELGA_Stylesheet_v1.0.xsl";
    private String watermarkText = "ÜBUNGSDOKUMENT!";
    private float watermarkOpacity = 0.17f;
    private String adminToken = "";

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDbPath() {
        return dbPath;
    }

    public void setDbPath(String dbPath) {
        this.dbPath = dbPath;
    }

    public String getElgaLibDir() {
        return elgaLibDir;
    }

    public void setElgaLibDir(String elgaLibDir) {
        this.elgaLibDir = elgaLibDir;
    }

    public String getElgaWrapperDir() {
        return elgaWrapperDir;
    }

    public void setElgaWrapperDir(String elgaWrapperDir) {
        this.elgaWrapperDir = elgaWrapperDir;
    }

    public String getElgaStylesheetPath() {
        return elgaStylesheetPath;
    }

    public void setElgaStylesheetPath(String elgaStylesheetPath) {
        this.elgaStylesheetPath = elgaStylesheetPath;
    }

    public String getWatermarkText() {
        return watermarkText;
    }

    public void setWatermarkText(String watermarkText) {
        this.watermarkText = watermarkText;
    }

    public float getWatermarkOpacity() {
        return watermarkOpacity;
    }

    public void setWatermarkOpacity(float watermarkOpacity) {
        this.watermarkOpacity = watermarkOpacity;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }
}
