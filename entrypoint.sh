#!/bin/sh
set -e

if [ ! -f /app/nodelhost.jar ]; then
  echo "ERROR: nodelhost.jar not found" >&2
  exit 1
fi

# tail -f /dev/null keeps stdin open (Nodel reads stdin; EOF triggers graceful exit)
exec tail -f /dev/null | java ${JAVA_OPTS:-} -jar /app/nodelhost.jar "$@"
