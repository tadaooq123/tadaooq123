#!/bin/bash
# Удаляет Quartus (образ Docker и его настройки). Папка с проектами FPGA остаётся.

HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
. "$HERE/lib/common.sh"

echo "Будет удалён Quartus из Docker и его настройки."
echo "Твои проекты в $PROJECTS_DIR НЕ удаляются."
read -r -p "Продолжить? (y/n) " ans
[ "$ans" = "y" ] || exit 0

need_docker
docker rm -f "$CONTAINER" >/dev/null 2>&1
docker rmi "$IMAGE" >/dev/null 2>&1 && echo "Quartus удалён."
docker volume rm "$HOME_VOLUME" >/dev/null 2>&1
docker builder prune -f --filter type=source.local >/dev/null 2>&1

if [ -d "$DATA_DIR" ]; then
    read -r -p "Удалить и скачанные установщики ($DATA_DIR, ~6 ГБ)? (y/n) " ans
    [ "$ans" = "y" ] && rm -rf "$DATA_DIR" && echo "Установщики удалены."
fi
echo "Docker Desktop можно удалить как обычное приложение, если он больше не нужен."
pause
