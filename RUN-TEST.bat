@echo off
cd /d "%~dp0"
del /Q run-done.txt 2>nul
echo Starting Minecraft with Lumen (test copy)...
call gradlew.bat runClient > run-log.txt 2>&1
if "%ERRORLEVEL%"=="0" (
  echo ==== Test game closed normally ====
  echo success > run-done.txt
) else (
  echo ==== Test game FAILED or crashed - see run-log.txt and run\logs\latest.log ====
  echo failed > run-done.txt
)
pause
