#!/bin/sh
# The ELGA CDA2PDF library is invoked in-process via reflection over the jars mounted
# in /app/elga-lib, so no runtime compilation of wrapper classes is required.

exec java -jar /app/app.jar "$@"
