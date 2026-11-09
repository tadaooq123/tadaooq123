#!/bin/bash
# Общие настройки и функции для скриптов Quartus на Mac.
# shellcheck disable=SC2034  # переменные используются в скриптах, которые подключают этот файл

QUARTUS_VER="25.1std.0.1129"
QUARTUS_URL="https://download.altera.com/akdlm/software/acdsinst/25.1std/1129/ib_installers"
IMAGE="quartus-mac:25.1"
CONTAINER="quartus-mac"
HOME_VOLUME="quartus-mac-home"     # настройки Quartus между запусками
DATA_DIR="$HOME/Quartus-Mac"       # скачанные установщики и служебные файлы
DL_DIR="$DATA_DIR/downloads"
PROJECTS_DIR="$HOME/FPGA"          # проекты; внутри Quartus это папка /projects
PORT=6080
# Семейства ПЛИС: cyclone (Cyclone IV), cyclonev (Cyclone V), max (MAX II/V),
# max10 (MAX 10), cyclone10lp, arria_lite. Можно переопределить: DEVICES="max10" ...
DEVICES="${DEVICES:-cyclone cyclonev max max10}"

# Docker Desktop, OrbStack и Homebrew ставят программы сюда; в .command их может не быть в PATH.
export PATH="$PATH:/usr/local/bin:/opt/homebrew/bin:$HOME/.docker/bin:$HOME/.orbstack/bin"

# Контрольные суммы SHA-256 официальных файлов (те же, что в nixpkgs).
sha_for() {
    case "$1" in
        QuartusLiteSetup-*) echo 518433ec7dcd61725560af653353f7a5c320cce9e528b22747b8a8df3674c4eb ;;
        arria_lite-*)       echo 129c6fbb5cfb678bd0580488604240cb93fb31e79ed75e19355200667993107f ;;
        cyclone10lp-*)      echo 96ead29610ae13a8b650b28d16f95636d93aaea76f5704440bad3b1db3121f62 ;;
        cyclonev-*)         echo d6e484fd1b0a47785bc8bcd318e427d4c9798f9276e9ef92985235865f6bcb6f ;;
        cyclone-*)          echo 94a398cbad411f16383b2a27c400e0e9dec81b0724197f33bb4c7a769181e4ba ;;
        max10-*)            echo 8057844b0b91c2ba7ef2b37b1986d198ec4f1831cc9be0a52d18e5feb4c19ce9 ;;
        max-*)              echo 8d8fdbf74e9f24a80938bde1e67591e5141dbc027753da1feb2e15a291a8db3d ;;
    esac
}

sha256_of() {
    if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | cut -d' ' -f1
    else sha256sum "$1" | cut -d' ' -f1; fi
}

pause() { echo; read -r -p "Нажми Enter, чтобы закрыть..." _ || true; }
die()   { echo; echo "ОШИБКА: $*"; pause; exit 1; }
step()  { echo; echo "==> $*"; }

open_url() {
    if command -v open >/dev/null 2>&1; then open "$1"
    elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$1" >/dev/null 2>&1
    else echo "Открой в браузере: $1"; fi
}

# Проверяет, что Docker установлен и запущен; при необходимости запускает Docker Desktop.
need_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        echo "Docker не найден. Установи Docker Desktop (бесплатно для учёбы):"
        echo "  https://www.docker.com/products/docker-desktop/"
        echo "Выбери версию для своего процессора (Apple Chip или Intel Chip),"
        echo "запусти Docker один раз и снова открой этот файл."
        open_url "https://www.docker.com/products/docker-desktop/"
        die "нужен Docker"
    fi
    if ! docker info >/dev/null 2>&1; then
        echo "Запускаю Docker..."
        if [ "$(uname)" = "Darwin" ]; then open -a Docker 2>/dev/null || open -a OrbStack 2>/dev/null || true; fi
        for _ in $(seq 90); do
            docker info >/dev/null 2>&1 && break
            sleep 2
        done
        docker info >/dev/null 2>&1 || die "Docker не запустился. Открой приложение Docker вручную и попробуй снова."
    fi
}

image_exists() { docker image inspect "$IMAGE" >/dev/null 2>&1; }

need_image() {
    image_exists || die "Quartus ещё не установлен. Сначала открой «1. УСТАНОВИТЬ Quartus.command»."
}

# Запускает команду Quartus (например quartus_cpf) в контейнере; папка $1 видна как /work.
run_in_quartus() {
    local dir="$1"; shift
    docker run --rm --platform linux/amd64 -v "$dir":/work -w /work "$IMAGE" "$@"
}
