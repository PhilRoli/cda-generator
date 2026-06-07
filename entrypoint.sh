#!/bin/sh
# The ELGA CDA2PDF library is invoked in-process via reflection over the jars mounted
# in /app/elga-lib, so no runtime compilation of wrapper classes is required.

# The container runs as a non-root user without a writable home directory. Point the
# PDFBox font cache at the persistent, writable data volume so it is built once and
# reused across restarts (otherwise PDFBox rebuilds the on-disk font cache per run).
exec java -Dpdfbox.fontcache=/app/data -jar /app/app.jar "$@"
