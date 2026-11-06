#!/bin/bash
# Установщик мода «Слепой, Глухой, Немой» для Mac (работает и на Linux: bash "УСТАНОВИТЬ (Mac).command")

HERE="$(cd "$(dirname "$0")" && pwd)"
MC_VERSION="1.21.1"

if [ "$(uname)" = "Darwin" ]; then
    MC="$HOME/Library/Application Support/minecraft"
else
    MC="$HOME/.minecraft"
fi
MODS="$MC/mods"

echo "=============================================="
echo "   Слепой, Глухой, Немой — установка"
echo "=============================================="
echo "Папка игры: $MC"
echo

mkdir -p "$MODS" || { echo "Не удалось создать папку $MODS"; read -r -p "Нажми Enter, чтобы закрыть..."; exit 1; }

# 1. Моды из архива
echo "[1/3] Копирую моды..."
for other in "$MODS"/fabric-api-*.jar; do
    [ -e "$other" ] || continue
    case "$(basename "$other")" in
        fabric-api-0.116.17+1.21.1.jar) ;;
        *) echo "   ! В папке mods уже есть другая версия Fabric API: $(basename "$other")"
           echo "     Если игра не запустится — удали её." ;;
    esac
done
cp -f "$HERE"/mods/*.jar "$MODS/" && echo "   Готово: мод и Fabric API в папке mods."

# 2. Simple Voice Chat с Modrinth
echo
echo "[2/3] Голосовой чат (Simple Voice Chat)..."
if ls "$MODS"/voicechat-fabric-* >/dev/null 2>&1; then
    echo "   Уже установлен — пропускаю."
else
    API="https://api.modrinth.com/v2/project/simple-voice-chat/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22$MC_VERSION%22%5D"
    URL="$(curl -fsSL -A "BlindDeafMute-installer/1.0" "$API" 2>/dev/null \
        | grep -o '"url":"[^"]*\.jar"' | head -n 1 | cut -d'"' -f4)"
    if [ -n "$URL" ] && curl -fsSL -A "BlindDeafMute-installer/1.0" -o "$MODS/$(basename "$URL")" "$URL"; then
        echo "   Скачан: $(basename "$URL")"
    else
        echo "   ! Не получилось скачать. Скачай вручную (Fabric $MC_VERSION) и положи в папку mods:"
        echo "     https://modrinth.com/plugin/simple-voice-chat"
    fi
fi

# 3. Fabric
echo
echo "[3/3] Fabric $MC_VERSION..."
if ls "$MC/versions" 2>/dev/null | grep -q "fabric-loader-.*-$MC_VERSION"; then
    echo "   Уже установлен — пропускаю."
else
    JAVA="$(command -v java 2>/dev/null)"
    if [ -z "$JAVA" ] || ! "$JAVA" -version >/dev/null 2>&1; then
        # Java, которую скачал сам лаунчер Minecraft
        JAVA="$(find "$MC/runtime" "$HOME/Library/Application Support/tlauncher" "$HOME/.tlauncher" \
            -type f -path '*/bin/java' 2>/dev/null | head -n 1)"
    fi
    if [ -n "$JAVA" ]; then
        EXTRA=""
        [ -f "$MC/launcher_profiles.json" ] || EXTRA="-noprofile"
        # Копируем установщик в путь без русских букв — так Java точно его откроет
        TMPDIR_FI="$(mktemp -d)"
        cp "$HERE/installer/fabric-installer-1.1.2.jar" "$TMPDIR_FI/fabric-installer.jar"
        if "$JAVA" -jar "$TMPDIR_FI/fabric-installer.jar" client \
            -dir "$MC" -mcversion "$MC_VERSION" $EXTRA; then
            echo "   Fabric установлен."
        else
            echo "   ! Fabric не установился. Запусти installer/fabric-installer-1.1.2.jar вручную."
        fi
    else
        echo "   Java не найдена, поэтому Fabric не ставлю."
        echo "   TLauncher: просто выбери в списке версию «Fabric $MC_VERSION»."
        echo "   Официальный лаунчер: открой installer/fabric-installer-1.1.2.jar."
    fi
fi

echo
echo "=============================================="
echo " Всё! Запусти лаунчер и выбери Fabric $MC_VERSION."
echo " В игре: один открывает мир для сети,"
echo " остальные заходят, хост пишет /bdm start"
echo "=============================================="
read -r -p "Нажми Enter, чтобы закрыть..."
