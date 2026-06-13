package at.rolinek.cda.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorBody(String message) {}

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBody> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : "Fehler.";
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorBody(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorBody> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
            .findFirst()
            .map(ConstraintViolation::getMessage)
            .orElse("Ungültige Anfrageparameter.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> handleNotReadable() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody("Ungültiger Anfrageinhalt."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorBody> handleMaxUploadSize() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ErrorBody("Datei ist zu groß."));
    }

    /**
     * A request for a path that maps to no controller and no bundled static resource.
     * In production the frontend is served by the reverse proxy, so this only happens
     * when the app port is hit directly (e.g. a probe) — a 404 is the correct answer
     * and there is no need to log a stack trace for it.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorBody> handleNoResource() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("Nicht gefunden."));
    }

    /**
     * A request to an existing path with an HTTP method that path does not support
     * (e.g. a GET probe against a POST-only endpoint). Like {@link #handleNoResource()}
     * this is a client error, not a server fault, so it returns a clean 405 without
     * logging a stack trace as an "Unhandled exception".
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorBody> handleMethodNotSupported() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new ErrorBody("Methode nicht erlaubt."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleUnexpected(Exception ex) {
        LOG.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorBody("Interner Serverfehler."));
    }
}
