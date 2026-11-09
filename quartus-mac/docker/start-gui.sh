#!/bin/bash
# Starts a virtual screen, shows it in the browser on port 6080 and opens Quartus.
# When Quartus is closed, the container stops.
set -u

export DISPLAY=:1
GEOMETRY="${GEOMETRY:-1600x1000}"

rm -f /tmp/.X1-lock /tmp/.X11-unix/X1
Xvnc :1 -geometry "$GEOMETRY" -depth 24 -SecurityTypes None -localhost \
     -rfbport 5901 -AlwaysShared >/tmp/xvnc.log 2>&1 &

for _ in $(seq 100); do
    [ -S /tmp/.X11-unix/X1 ] && break
    sleep 0.1
done

openbox >/dev/null 2>&1 &
websockify --web /usr/share/novnc 6080 localhost:5901 >/tmp/websockify.log 2>&1 &

cd /projects || exit 1
"${QUARTUS_CMD:-quartus}"
