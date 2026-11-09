# syntax=docker/dockerfile:1
# Quartus Prime Lite (Linux) in an x86_64 container. On Apple Silicon Docker runs
# it through Rosetta; on Intel Macs it runs natively. The window is shown in the
# browser through noVNC.
#
# Build (the installer files come from a separate build context named "dl", so
# the 6 GB of downloads do not end up inside the image):
#   docker build --platform linux/amd64 --build-context dl=<downloads dir> -t quartus-mac .

FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8

RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates locales unzip python3 binutils \
        # libraries Quartus needs
        libglib2.0-0 libfontconfig1 libfreetype6 libsm6 libice6 libx11-6 libx11-xcb1 \
        libxext6 libxrender1 libxtst6 libxi6 libxft2 libxau6 libxdmcp6 libxcb1 \
        libncurses5 libtinfo5 libudev1 libusb-1.0-0 libdbus-1-3 libgl1 libegl1 \
        libxkbcommon0 libxkbcommon-x11-0 libxcb-icccm4 libxcb-image0 libxcb-keysyms1 \
        libxcb-randr0 libxcb-render-util0 libxcb-shape0 libxcb-xinerama0 libxcb-xkb1 \
        libxcb-xfixes0 libxcb-sync1 libxcb-shm0 libxcb-render0 libxcomposite1 \
        libxcursor1 libxdamage1 libxrandr2 libnss3 libasound2 \
        # virtual screen shown in the browser
        tigervnc-standalone-server novnc python3-websockify openbox \
        fonts-dejavu-core fonts-liberation \
    && sed -i 's/^# *en_US.UTF-8/en_US.UTF-8/' /etc/locale.gen && locale-gen \
    && rm -rf /var/lib/apt/lists/*

ARG QUARTUS_VER=25.1std.0.1129
# Device families to install: cyclone (Cyclone IV), cyclonev, max (MAX II/V),
# max10, cyclone10lp, arria_lite. Each needs its <id>-<version>.qdz file.
ARG DEVICES="cyclone cyclonev max max10"

COPY patch_ffreep.py /usr/local/lib/patch_ffreep.py

RUN --mount=type=bind,from=dl,target=/dl \
    set -eu; \
    mkdir /tmp/inst; \
    # The installer looks for .qdz files next to its own (real) location.
    cp "/dl/QuartusLiteSetup-${QUARTUS_VER}-linux.run" /tmp/inst/; \
    chmod +x "/tmp/inst/QuartusLiteSetup-${QUARTUS_VER}-linux.run"; \
    disabled="quartus_help,quartus_update,questa_fe,questa_fse"; \
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
    ! grep -q 'grep sse /proc/cpuinfo' /opt/quartus/quartus/adm/qenv.sh

ENV PATH=/opt/quartus/quartus/bin:$PATH \
    QUARTUS_ROOTDIR=/opt/quartus/quartus

COPY start-gui.sh /usr/local/bin/start-gui
RUN chmod +x /usr/local/bin/start-gui && mkdir -p /projects

WORKDIR /projects
EXPOSE 6080
CMD ["start-gui"]
