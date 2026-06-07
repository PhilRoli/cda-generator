#!/bin/sh
# The ELGA CDA2PDF library is invoked in-process via reflection over the jars mounted
# in /app/elga-lib, so no runtime compilation of wrapper classes is required.

# Docker named volumes are created with root:root ownership.  The image-time
# `chown -R appuser /app` runs before the volume is mounted and is therefore
# overwritten at runtime, making /app/data unwritable for appuser and causing
# SQLITE_READONLY on the first initSchema() call.
#
# Fix: if the directory is not writable, use gosu (installed in the Dockerfile)
# to re-chown it as root before starting the JVM.  This is a one-time cost on
# fresh deployments; on subsequent restarts the volume is already correctly owned.
if [ ! -w /app/data ]; then
    gosu root chown -R "$(id -u):$(id -g)" /app/data
fi

# The container runs as a non-root user without a writable home directory. Point the
# PDFBox font cache at the persistent, writable data volume so it is built once and
# reused across restarts (otherwise PDFBox rebuilds the on-disk font cache per run).
exec java -Dpdfbox.fontcache=/app/data -jar /app/app.jar "$@"
