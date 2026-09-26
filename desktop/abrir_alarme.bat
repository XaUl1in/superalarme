@echo off
chcp 65001 >nul
title Hardcore - Despertar
color 0D
cd /d "%~dp0"
where pythonw.exe >nul 2>&1
if not errorlevel 1 (
    start "" pythonw.exe "%~dp0desktop_gui.py"
    exit /b
)
python desktop_gui.py
if errorlevel 1 (
    echo.
    echo Nao foi possivel abrir a janela. Confira se o Python possui Tkinter.
    echo Alternativa pelo terminal: python alarm.py
    pause
)
