#!/bin/sh
# Compile CDA2PDFClean against the mounted ELGA JARs at container startup.
# This requires the JDK image (not JRE-only) and the elga-lib volume to be mounted.
WRAPPER_DIR=/app/scripts/cda2pdf-uebung
ELGA_LIB_DIR=/app/elga-lib

if [ ! -f "$WRAPPER_DIR/CDA2PDFClean.class" ] || [ "$WRAPPER_DIR/CDA2PDFClean.java" -nt "$WRAPPER_DIR/CDA2PDFClean.class" ]; then
    echo "[entrypoint] Compiling CDA2PDFClean..."
    CLASSPATH="$WRAPPER_DIR:$(find "$ELGA_LIB_DIR" -name '*.jar' 2>/dev/null | tr '\n' ':')"
    if javac -cp "$CLASSPATH" -d "$WRAPPER_DIR" "$WRAPPER_DIR/CDA2PDFClean.java" 2>&1; then
        echo "[entrypoint] CDA2PDFClean compiled successfully."
    else
        echo "[entrypoint] Warning: CDA2PDFClean compilation failed. XML-Upload without banner will not be available."
    fi
fi

exec java -jar /app/app.jar "$@"
