# syntax=docker/dockerfile:1
# Quartus Prime Lite (Linux) and the Questa simulator in an x86_64 container. On
# Apple Silicon Docker runs it through Rosetta; on Intel Macs it runs natively.
# The window is shown in the browser through noVNC.
#
# Build (the installer files come from a separate build context named "dl", so
# the gigabytes of downloads do not end up inside the image):
#   docker build --platform linux/amd64 --build-context dl=<downloads dir> -t quartus-mac .

FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8

RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates locales unzip python3 binutils make \
        # libraries Quartus needs
        libglib2.0-0 libfontconfig1 libfreetype6 libsm6 libice6 libx11-6 libx11-xcb1 \
        libxext6 libxrender1 libxtst6 libxi6 libxft2 libxau6 libxdmcp6 libxcb1 libxss1 \
        libncurses5 libtinfo5 libudev1 libusb-1.0-0 libdbus-1-3 libgl1 libegl1 \
        libxkbcommon0 libxkbcommon-x11-0 libxcb-icccm4 libxcb-image0 libxcb-keysyms1 \
        libxcb-randr0 libxcb-render-util0 libxcb-shape0 libxcb-xinerama0 libxcb-xkb1 \
        libxcb-xfixes0 libxcb-sync1 libxcb-shm0 libxcb-render0 libxcomposite1 \
        libxcursor1 libxdamage1 libxrandr2 libnss3 libasound2 \
        libbz2-1.0 libbrotli1 libexpat1 libcrypt1 \
        # libraries Questa needs
        libxml2 unixodbc \
        # virtual screen shown in the browser
        tigervnc-standalone-server novnc python3-websockify openbox \
        fonts-dejavu-core fonts-liberation \
    && sed -i 's/^# *en_US.UTF-8/en_US.UTF-8/' /etc/locale.gen && locale-gen \
    && rm -rf /var/lib/apt/lists/*

# Windows open maximized and have no minimize button: there is no taskbar to get
# a minimized window back.
RUN sed -i \
        -e 's|<titleLayout>NLIMC</titleLayout>|<titleLayout>NLMC</titleLayout>|' \
        -e 's|</applications>|<application type="normal"><maximized>yes</maximized></application></applications>|' \
        /etc/xdg/openbox/rc.xml \
    && grep -q '<maximized>yes</maximized>' /etc/xdg/openbox/rc.xml

ARG QUARTUS_VER=25.1std.0.1129
# Device families to install: cyclone (Cyclone IV), cyclonev, max (MAX II/V),
# max10, cyclone10lp, arria_lite. Each needs its <id>-<version>.qdz file.
ARG DEVICES="cyclone cyclonev max max10"
# 1 = also install the Questa simulator (needs QuestaSetup-<version>-linux.run).
ARG WITH_QUESTA=1

COPY patch_ffreep.py /usr/local/lib/patch_ffreep.py

RUN --mount=type=bind,from=dl,target=/dl \
    set -eu; \
    mkdir /tmp/inst; \
    # The installer looks for the other files next to its own (real) location.
    cp "/dl/QuartusLiteSetup-${QUARTUS_VER}-linux.run" /tmp/inst/; \
    disabled="quartus_help,quartus_update,questa_fe"; \
    if [ "$WITH_QUESTA" = 1 ]; then \
        cp "/dl/QuestaSetup-${QUARTUS_VER}-linux.run" /tmp/inst/; \
    else \
        disabled="$disabled,questa_fse"; \
    fi; \
    chmod +x /tmp/inst/*.run; \
    for d in arria_lite cyclone cyclone10lp cyclonev max max10; do \
        case " $DEVICES " in \
            *" $d "*) ln -s "/dl/$d-${QUARTUS_VER}.qdz" /tmp/inst/ ;; \
            *) disabled="$disabled,$d" ;; \
        esac; \
    done; \
    "/tmp/inst/QuartusLiteSetup-${QUARTUS_VER}-linux.run" \
        --mode unattended --accept_eula 1 --installdir /opt/quartus \
        --disable-components "$disabled"; \
    test -x /opt/quartus/quartus/bin/quartus; \
    rm -rf /tmp/inst /opt/quartus/uninstall /opt/quartus/logs; \
    # Rosetta (macOS 26) does not implement the x87 instruction ffreep.
    python3 /usr/local/lib/patch_ffreep.py \
        /opt/quartus/quartus/linux64/libccl_sqlite3.so \
        /opt/quartus/quartus/linux64/libsynth_vrfx2.so; \
    # Quartus refuses to start when /proc/cpuinfo has no "sse" (true under Rosetta).
    sed -i 's#grep sse /proc/cpuinfo > /dev/null 2>&1#:#' /opt/quartus/quartus/adm/qenv.sh; \
    ! grep -q 'grep sse /proc/cpuinfo' /opt/quartus/quartus/adm/qenv.sh; \
    if [ "$WITH_QUESTA" = 1 ]; then \
        test -e /opt/quartus/questa_fse/bin/vsim; \
        # LD_PRELOAD (set below for Quartus) breaks Questa, so its launch scripts drop it.
        for f in /opt/quartus/questa_fse/vco /opt/quartus/questa_fse/bin/*; do \
            [ -e "$f" ] || continue; \
            real="$(readlink -f "$f")"; \
            if [ "$(head -c 2 "$real")" = '#!' ] && ! grep -q '^unset LD_PRELOAD' "$real"; then \
                sed -i '1a unset LD_PRELOAD' "$real"; \
            fi; \
        done; \
    fi

ENV PATH=/opt/quartus/quartus/bin:/opt/quartus/questa_fse/bin:$PATH \
    QUARTUS_ROOTDIR=/opt/quartus/quartus \
    QT_QPA_PLATFORM=xcb \
    # Quartus's licensing code crashes in containers unless the system libudev is
    # preloaded (same fix as nixpkgs; see Intel community thread 1189032).
    LD_PRELOAD=/lib/x86_64-linux-gnu/libudev.so.1 \
    # free Questa Starter license, put here by "4. ЛИЦЕНЗИЯ для симуляции.command"
    SALT_LICENSE_SERVER=/license/license.dat \
    LM_LICENSE_FILE=/license/license.dat

COPY start-gui.sh /usr/local/bin/start-gui
RUN chmod +x /usr/local/bin/start-gui && mkdir -p /projects /license

WORKDIR /projects
EXPOSE 6080
CMD ["start-gui"]
