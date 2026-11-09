#!/bin/bash
# Помогает получить бесплатную лицензию Questa (симулятор для временных диаграмм)
# и кладёт её туда, где Quartus её найдёт. Лицензия действует 12 месяцев.

HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
. "$HERE/lib/common.sh"

SSLC_URL="https://licensing.altera.com"

nic_id="$(questa_nic_id)"
mkdir -p "$LICENSE_DIR"

echo "=============================================="
echo "   Лицензия для симуляции (Questa)"
echo "=============================================="
if [ -s "$LICENSE_DIR/license.dat" ]; then
    echo "Лицензия уже установлена. Продолжай, только если она истекла"
    echo "или симуляция пишет об ошибке лицензии."
fi
echo
echo "Лицензия бесплатная, но её надо получить на сайте Altera (~10 минут)."
echo
echo "Твой NIC ID (код компьютера для лицензии):"
echo
echo "      $nic_id"
echo
if command -v pbcopy >/dev/null 2>&1; then
    printf '%s' "$nic_id" | pbcopy && echo "   (уже скопирован — можно сразу вставить через Cmd+V)"
fi
cat <<EOF

Что сделать на сайте (он сейчас откроется):
  1. Зарегистрируйся (Create account / Register) и подтверди почту,
     потом войди в Self-Service Licensing Center.
  2. Выбери «Sign up for Evaluation or No-Cost Licenses» и в списке
     «Questa - Altera FPGA Starter Edition» (SW-QUESTA).
  3. Количество мест (seats): 1, тип лицензии: FIXED.
  4. Добавь компьютер (+ New Computer):
       тип — NIC ID, имя — любое (например MacBook),
       ID — $nic_id
  5. Нажми Generate. Файл лицензии (.dat) придёт на почту
     или скачается — сохрани его в «Загрузки».

Названия кнопок на сайте иногда меняются — ищи похожие.
EOF
open_url "$SSLC_URL"

echo
echo "Когда файл .dat будет в «Загрузках», нажми Enter."
echo "(Или перетащи сюда мышкой файл лицензии и нажми Enter.)"
read -r -p "> " picked
lic=""
if [ -n "$picked" ]; then
    picked="${picked%"${picked##*[![:space:]]}"}"
    picked="${picked//\\ / }"
    picked="${picked#\'}"; picked="${picked%\'}"
    picked="${picked#\"}"; picked="${picked%\"}"
    lic="$picked"
else
    for f in "$HOME/Downloads"/*.dat "$PROJECTS_DIR"/*.dat; do
        [ -f "$f" ] || continue
        if [ -z "$lic" ] || [ "$f" -nt "$lic" ]; then lic="$f"; fi
    done
fi
[ -n "$lic" ] && [ -f "$lic" ] || die "файл лицензии .dat не найден. Скачай его и запусти этот файл снова."

echo "Файл лицензии: $lic"
if ! grep -qi "$nic_id" "$lic"; then
    echo
    echo "Внимание: в этом файле нет твоего NIC ID ($nic_id)."
    echo "Возможно, это не тот файл или при заказе указали другой ID."
    read -r -p "Всё равно установить? (y/n) " ans
    [ "$ans" = "y" ] || die "лицензия не установлена"
fi

cp "$lic" "$LICENSE_DIR/license.dat" || die "не удалось скопировать лицензию"
echo
echo "Готово! Лицензия установлена."
if [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" = "true" ]; then
    echo "Quartus сейчас открыт — закрой его и запусти заново, чтобы лицензия подхватилась."
fi
pause
