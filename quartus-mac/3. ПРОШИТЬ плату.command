#!/bin/bash
# Прошивает плату с ПЛИС Intel/Altera прямо из macOS (через USB-Blaster и openFPGALoader).
# Quartus из Docker не видит USB, поэтому прошивка делается отдельно этим скриптом.

HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
. "$HERE/lib/common.sh"

echo "=============================================="
echo "   Прошивка платы ПЛИС"
echo "=============================================="

need_docker
need_image

# 1. openFPGALoader
if ! command -v openFPGALoader >/dev/null 2>&1; then
    step "Нужна программа openFPGALoader — ставлю через Homebrew..."
    if ! command -v brew >/dev/null 2>&1; then
        echo "Homebrew не установлен. Это стандартный установщик программ для Mac: https://brew.sh"
        read -r -p "Установить Homebrew сейчас? Попросит пароль от Mac. (y/n) " ans
        [ "$ans" = "y" ] || die "без Homebrew не получится поставить openFPGALoader"
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)" \
            || die "Homebrew не установился"
        for b in /opt/homebrew/bin/brew /usr/local/bin/brew; do
            [ -x "$b" ] && eval "$("$b" shellenv)"
        done
    fi
    brew install openfpgaloader || die "не удалось установить openFPGALoader"
fi

# 2. Какой файл прошивать
echo
echo "Что сделать?"
echo "  1 — Загрузить в плату (быстро; пропадёт после выключения питания). Файл .sof"
echo "  2 — Записать навсегда — только для MAX II, MAX V и MAX 10. Файл .pof"
read -r -p "Выбери 1 или 2 [1]: " mode
case "$mode" in
    2) ext="pof" ;;
    *) ext="sof" ;;
esac

newest=""
if [ -n "$(find "$PROJECTS_DIR" -type f -name "*.$ext" -print 2>/dev/null | head -n 1)" ]; then
    newest="$(find "$PROJECTS_DIR" -type f -name "*.$ext" -print0 | xargs -0 ls -1t | head -n 1)"
fi
echo
if [ -n "$newest" ]; then
    echo "Самый свежий файл .$ext в папке FPGA:"
    echo "   $newest"
    echo "Нажми Enter, чтобы прошить его, или перетащи сюда мышкой другой файл и нажми Enter."
else
    echo "В папке $PROJECTS_DIR нет файлов .$ext."
    echo "Сначала скомпилируй проект в Quartus (Processing → Start Compilation)."
    echo "Или перетащи сюда мышкой нужный файл .$ext и нажми Enter."
fi
read -r -p "> " picked
if [ -n "$picked" ]; then
    # Терминал вставляет путь с «\ » вместо пробелов или в кавычках.
    picked="${picked%"${picked##*[![:space:]]}"}"
    picked="${picked//\\ / }"
    picked="${picked#\'}"; picked="${picked%\'}"
    picked="${picked#\"}"; picked="${picked%\"}"
    newest="$picked"
fi
[ -f "$newest" ] || die "файл не найден: $newest"

dir="$(cd "$(dirname "$newest")" && pwd)"
base="$(basename "$newest")"
svf="${base%.*}.svf"

# 3. Перевод в формат SVF (делает Quartus внутри Docker)
step "Готовлю файл для прошивки ($base → $svf)..."
run_in_quartus "$dir" quartus_cpf -c -q 12.0MHz -g 3.3 -n p "$base" "$svf" \
    || die "Quartus не смог подготовить файл прошивки"
[ -f "$dir/$svf" ] || die "файл $svf не появился"

# 4. Прошивка
step "Прошиваю плату..."
if openFPGALoader -c usb-blaster "$dir/$svf"; then
    ok=1
else
    echo
    echo "Обычный USB-Blaster не ответил — пробую USB-Blaster II..."
    fw="$DATA_DIR/blaster_6810.hex"
    [ -f "$fw" ] || run_in_quartus "$DATA_DIR" cp /opt/quartus/quartus/linux64/blaster_6810.hex /work/ >/dev/null 2>&1
    if [ -f "$fw" ] && openFPGALoader -c usb-blasterII --probe-firmware "$fw" "$dir/$svf"; then
        ok=1
    fi
fi

if [ -n "${ok:-}" ]; then
    echo
    echo "Готово! Плата прошита."
    pause
    exit 0
fi

echo
echo "Не получилось прошить. Проверь:"
echo "  • плата включена (у многих есть отдельная кнопка питания) и подключена кабелем USB-Blaster;"
echo "  • кабель передаёт данные, а не только заряжает; попробуй другой порт или переходник;"
echo "  • в Терминале команда  openFPGALoader --detect  видит плату."
die "плата не прошита"
