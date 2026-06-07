#!/bin/sh
# The ELGA CDA2PDF library is invoked in-process via reflection over the jars mounted
# in /app/elga-lib, so no runtime compilation of wrapper classes is required.

# Docker named volumes are initialised with root:root ownership, which overwrites
# the image-time `chown -R appuser /app` before the volume is mounted.  Fix the
# ownership here while we are still root, then permanently drop to appuser via gosu.
#
# Running as root in the entrypoint (no USER in Dockerfile) is the standard Docker
# pattern for this problem: root -> chown -> exec gosu appuser.  gosu performs a
# plain setuid()/setgid() call which requires no extra Linux capabilities.
chown -R appuser:appuser /app/data

# Drop permanently to appuser and start the JVM.
# Point the PDFBox font cache at the persistent data volume so it is built once
# and reused across restarts.
exec gosu appuser java -Dpdfbox.fontcache=/app/data -jar /app/app.jar "$@"
