#!/bin/bash
# Устанавливает Quartus Prime Lite 25.1 на Mac (Apple M1–M5 и Intel) через Docker.
# Запуск: двойной щелчок (или в Терминале: bash "1. УСТАНОВИТЬ Quartus.command").

HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
. "$HERE/lib/common.sh"

echo "=============================================="
echo "   Quartus Prime Lite для Mac — установка"
echo "=============================================="
echo "Займёт 40–120 минут, большая часть — скачивание (~8 ГБ)."
echo "Не закрывай это окно и держи Mac на зарядке."

# 1. Процессор и Rosetta
step "[1/5] Проверяю Mac..."
if [ "$(uname)" = "Darwin" ] && [ "$(uname -m)" = "arm64" ]; then
    echo "   Процессор Apple (M-серия): Quartus будет работать через Rosetta."
    if ! /usr/bin/arch -x86_64 /usr/bin/true 2>/dev/null; then
        echo "   Устанавливаю Rosetta (может попросить пароль от Mac)..."
        softwareupdate --install-rosetta --agree-to-license || die "не удалось установить Rosetta"
    fi
else
    echo "   Процессор Intel: Quartus будет работать без эмуляции."
fi

# 2. Docker
step "[2/5] Проверяю Docker..."
need_docker
docker buildx version >/dev/null 2>&1 || die "в Docker нет buildx. Поставь свежий Docker Desktop или OrbStack."
echo "   Docker работает."
if [ "$(uname -m)" = "arm64" ]; then
    echo "   Важно: в Docker Desktop → Settings → General должна быть включена галочка"
    echo "   «Use Rosetta for x86_64/amd64 emulation on Apple Silicon» (обычно включена сама)."
fi

free_gb=$(df -Pk "$HOME" | awk 'NR==2 {print int($4 / 1048576)}')
if [ "${free_gb:-0}" -lt 50 ]; then
    echo "   Свободно на диске: ${free_gb} ГБ, а нужно примерно 50 ГБ."
    read -r -p "   Всё равно продолжить? (y/n) " ans
    [ "$ans" = "y" ] || die "освободи место на диске и запусти установку снова"
fi

# 3. Скачивание
step "[3/5] Скачиваю Quartus $QUARTUS_VER (семейства: $DEVICES)..."
[ "$WITH_QUESTA" = 1 ] && echo "   + симулятор Questa для временных диаграмм"
mkdir -p "$DL_DIR" "$PROJECTS_DIR"

files="QuartusLiteSetup-$QUARTUS_VER-linux.run"
[ "$WITH_QUESTA" = 1 ] && files="$files QuestaSetup-$QUARTUS_VER-linux.run"
for d in $DEVICES; do
    [ -n "$(sha_for "$d-")" ] || die "неизвестное семейство ПЛИС: $d"
    files="$files $d-$QUARTUS_VER.qdz"
done

download() {
    local name="$1" file="$DL_DIR/$1" want
    want="$(sha_for "$name")"
    # Если файл скачали вручную через браузер — заберём его из «Загрузок».
    if [ ! -f "$file" ] && [ -f "$HOME/Downloads/$name" ]; then
        mv "$HOME/Downloads/$name" "$file"
    fi
    if [ -f "$file" ]; then
        [ "$(sha256_of "$file")" = "$want" ] && { echo "   $name — уже есть"; return 0; }
        echo "   $name — файл повреждён, качаю заново"
        rm -f "$file"
    fi
    echo "   $name — скачиваю..."
    curl -fL --retry 5 --retry-delay 5 -C - -o "$file.part" "$QUARTUS_URL/$name" || return 1
    mv -f "$file.part" "$file"
    if [ "$(sha256_of "$file")" != "$want" ]; then
        rm -f "$file"
        echo "   $name — контрольная сумма не совпала"
        return 1
    fi
}

failed=""
for f in $files; do
    download "$f" || failed="$failed $f"
done
if [ -n "$failed" ]; then
    echo
    echo "Не получилось скачать:"
    for f in $failed; do echo "   $QUARTUS_URL/$f"; done
    echo
    echo "Открываю эти ссылки в браузере — файлы скачаются в «Загрузки»."
    echo "(Можно и вручную положить их сюда: $DL_DIR)"
    echo "Когда браузер всё скачает, снова открой этот установщик — он заберёт файлы"
    echo "из «Загрузок» и продолжит с того же места."
    for f in $failed; do open_url "$QUARTUS_URL/$f"; done
    die "файлы ещё не скачаны"
fi

# 4. Сборка образа с Quartus
step "[4/5] Устанавливаю Quartus внутрь Docker (20–60 минут, окно может долго молчать)..."
docker build --platform linux/amd64 \
    --build-context dl="$DL_DIR" \
    --build-arg QUARTUS_VER="$QUARTUS_VER" \
    --build-arg DEVICES="$DEVICES" \
    --build-arg WITH_QUESTA="$WITH_QUESTA" \
    -t "$IMAGE" "$HERE/docker" || die "установка Quartus не удалась (подробности выше)"
# Docker кэширует копию установщиков (~8 ГБ) — она больше не нужна.
docker builder prune -f --filter type=source.local >/dev/null 2>&1 || true

# 5. Прошивка для USB-Blaster II (нужна некоторым платам, например DE1-SoC)
step "[5/5] Последние штрихи..."
run_in_quartus "$DATA_DIR" cp /opt/quartus/quartus/linux64/blaster_6810.hex /work/ >/dev/null 2>&1 || true

echo
echo "=============================================="
echo "   Готово! Quartus установлен."
echo "=============================================="
echo "• Запуск: «2. ЗАПУСТИТЬ Quartus.command» — Quartus откроется в браузере."
echo "• Проекты храни в папке $PROJECTS_DIR (внутри Quartus она называется /projects)."
echo "• Прошить плату: «3. ПРОШИТЬ плату.command»."
[ "$WITH_QUESTA" = 1 ] && echo "• Для временных диаграмм один раз получи бесплатную лицензию:
  «4. ЛИЦЕНЗИЯ для симуляции.command» (~10 минут)."
echo "• Скачанные установщики лежат в $DL_DIR —"
echo "  их можно удалить, если не планируешь переустанавливать."
echo
read -r -p "Запустить Quartus прямо сейчас? (y/n) " ans
if [ "$ans" = "y" ]; then
    exec bash "$HERE/2. ЗАПУСТИТЬ Quartus.command"
fi
pause
