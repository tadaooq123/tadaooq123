@echo off
chcp 65001 >nul
title BlindDeafMute - install
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0installer\install-windows.ps1"
echo.
pause
