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

if [ -d "$DL_DIR" ]; then
    read -r -p "Удалить и скачанные установщики ($DL_DIR, ~8 ГБ)? (y/n) " ans
    [ "$ans" = "y" ] && rm -rf "$DL_DIR" && echo "Установщики удалены."
fi
# Лицензию Questa и MAC-адрес (в $DATA_DIR) не трогаем: после переустановки
# лицензия будет работать без повторного заказа.
echo "Docker Desktop можно удалить как обычное приложение, если он больше не нужен."
pause
