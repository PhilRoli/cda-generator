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
    private String cleanPdfPassword = "";
    private final Pdf pdf = new Pdf();
    private final Backup backup = new Backup();

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

    public String getCleanPdfPassword() {
        return cleanPdfPassword;
    }

    public void setCleanPdfPassword(String cleanPdfPassword) {
        this.cleanPdfPassword = cleanPdfPassword;
    }

    public Pdf getPdf() {
        return pdf;
    }

    public Backup getBackup() {
        return backup;
    }

    /** Backup-scheduler configuration. */
    public static class Backup {
        /** Directory where timestamped backup files are written. */
        private String dir = "data/backups";
        /** How many milliseconds between automatic backups (default 24 h). */
        private long intervalMs = 86_400_000L;
        /** How many backup files to keep (oldest pruned first). */
        private int retention = 14;

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public int getRetention() {
            return retention;
        }

        public void setRetention(int retention) {
            this.retention = retention;
        }
    }

    /**
     * PDF-generation tunables guarding the unauthenticated, heavy, and (library-imposed)
     * strictly serial conversion endpoints against overload and hung conversions.
     */
    public static class Pdf {
        /**
         * Maximum number of callers admitted to the conversion gate at once. The ELGA
         * converter is not thread-safe, so only one conversion may actually run — keep
         * this at 1. The semaphore additionally fast-fails a flood of waiters.
         */
        private int maxConcurrent = 1;
        /** How long a caller waits for a gate permit before fast-failing with 503. */
        private int acquireTimeoutSeconds = 5;
        /** Hard timeout for a single conversion before the request thread is freed (503). */
        private int conversionTimeoutSeconds = 60;
        /** Maximum accepted XML payload size in UTF-8 bytes (default 5 MiB). */
        private long maxXmlBytes = 5_242_880L;

        public int getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }

        public int getAcquireTimeoutSeconds() {
            return acquireTimeoutSeconds;
        }

        public void setAcquireTimeoutSeconds(int acquireTimeoutSeconds) {
            this.acquireTimeoutSeconds = acquireTimeoutSeconds;
        }

        public int getConversionTimeoutSeconds() {
            return conversionTimeoutSeconds;
        }

        public void setConversionTimeoutSeconds(int conversionTimeoutSeconds) {
            this.conversionTimeoutSeconds = conversionTimeoutSeconds;
        }

        public long getMaxXmlBytes() {
            return maxXmlBytes;
        }

        public void setMaxXmlBytes(long maxXmlBytes) {
            this.maxXmlBytes = maxXmlBytes;
        }
    }
}
