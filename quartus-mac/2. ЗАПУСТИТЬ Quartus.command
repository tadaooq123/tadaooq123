#!/bin/bash
# Запускает Quartus и открывает его окно в браузере.

HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
. "$HERE/lib/common.sh"

URL="http://localhost:$PORT/vnc.html?autoconnect=1&resize=remote&reconnect=1"

need_docker
need_image
mkdir -p "$PROJECTS_DIR"

if [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" = "true" ]; then
    echo "Quartus уже запущен — открываю в браузере."
    open_url "$URL"
    exit 0
fi
docker rm -f "$CONTAINER" >/dev/null 2>&1

echo "Запускаю Quartus..."
docker run -d --rm --name "$CONTAINER" --platform linux/amd64 \
    -p "127.0.0.1:$PORT:6080" \
    -v "$PROJECTS_DIR":/projects \
    -v "$HOME_VOLUME":/root \
    -e GEOMETRY="${GEOMETRY:-1600x1000}" \
    "$IMAGE" >/dev/null || die "не удалось запустить Quartus"

for _ in $(seq 60); do
    curl -sf -o /dev/null "http://127.0.0.1:$PORT/vnc.html" && break
    sleep 1
done
open_url "$URL"

echo
echo "Quartus открыт во вкладке браузера (первый запуск может занять минуту)."
echo "Если вкладка закрылась, открой: $URL"
echo
echo "• Проекты сохраняй в папку /projects — на Mac это $PROJECTS_DIR"
echo "• Копировать текст между Mac и Quartus: панель noVNC слева → Clipboard."
echo "• Выключить: закрой Quartus (File → Exit) или нажми Enter в этом окне."
echo
read -r -p "Нажми Enter, чтобы выключить Quartus..." _ || true
docker stop "$CONTAINER" >/dev/null 2>&1
echo "Quartus выключен."
