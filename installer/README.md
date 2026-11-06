# Установщики для игроков

Эти файлы кладутся в архив рядом с папкой `mods/` (мод + Fabric API) и `installer/fabric-installer-1.1.2.jar/.exe` (с maven.fabricmc.net).

- `УСТАНОВИТЬ (Windows).bat` запускает `installer/install-windows.ps1`.
- `УСТАНОВИТЬ (Mac).command` работает на Mac и Linux.

Скрипты копируют моды в папку игры, скачивают Simple Voice Chat с Modrinth и ставят Fabric 1.21.1, если найдут Java.
