#!/bin/sh
set -e

if [ ! -f /app/nodelhost.jar ]; then
  echo "ERROR: nodelhost.jar not found" >&2
  exit 1
fi

# Keep stdin open without masking signals (Nodel reads stdin; EOF triggers graceful exit).
stdin_fifo="/tmp/nodel-stdin"
if [ -e "$stdin_fifo" ] && [ ! -p "$stdin_fifo" ]; then
  rm -f "$stdin_fifo"
fi
if [ ! -p "$stdin_fifo" ]; then
  mkfifo "$stdin_fifo"
fi

tail -f /dev/null > "$stdin_fifo" &

exec java ${JAVA_OPTS:-} -jar /app/nodelhost.jar "$@" < "$stdin_fifo"
