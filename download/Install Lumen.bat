@echo off
title Lumen Installer
powershell -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0LumenInstaller.ps1"
if errorlevel 1 pause
