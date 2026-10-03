package at.rolinek.cda.api;

import at.rolinek.cda.usage.UsageRecorder;
import at.rolinek.cda.usage.UsageType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Usage reports from the browser for actions that never reach the server otherwise. */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageRecorder usageRecorder;

    public UsageController(UsageRecorder usageRecorder) {
        this.usageRecorder = usageRecorder;
    }

    /** The XML document is built and downloaded entirely in the browser. */
    @PostMapping("/xml-download")
    public ResponseEntity<Void> xmlDownload(@RequestBody(required = false) XmlDownloadBody body,
                                            HttpServletRequest request) {
        usageRecorder.record(UsageType.XML_DOWNLOAD, 200, body == null ? null : body.username(), request, "");
        return ResponseEntity.noContent().build();
    }

    public record XmlDownloadBody(String username) {}
}
