#!/bin/sh
# The ELGA CDA2PDF library is invoked in-process via reflection over the jars mounted
# in /app/elga-lib, so no runtime compilation of wrapper classes is required.

# Docker named volumes are created with root ownership; the image-time
# `chown -R appuser /app` runs before the volume is mounted and is therefore
# overwritten at runtime.  Fix the ownership here, before the JVM starts, so
# the SQLite database and the PDFBox font cache are always writable.
if [ ! -w /app/data ]; then
    if command -v su-exec > /dev/null 2>&1; then
        su-exec root chown -R "$(id -u):$(id -g)" /app/data
    elif command -v gosu > /dev/null 2>&1; then
        gosu root chown -R "$(id -u):$(id -g)" /app/data
    else
        echo "ERROR: /app/data is not writable and no privilege-escalation helper"
        echo "       (su-exec or gosu) is available to fix ownership at runtime."
        echo "       On the host run:  docker compose run --rm --user root api chown -R appuser /app/data"
        exit 1
    fi
fi

# The container runs as a non-root user without a writable home directory. Point the
# PDFBox font cache at the persistent, writable data volume so it is built once and
# reused across restarts (otherwise PDFBox rebuilds the on-disk font cache per run).
exec java -Dpdfbox.fontcache=/app/data -jar /app/app.jar "$@"
