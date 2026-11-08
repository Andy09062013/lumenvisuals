@echo off
cd /d "%~dp0"
del /Q build-done.txt 2>nul
del /Q build\libs\*.jar 2>nul
echo Building Lumen... (log goes to build-log.txt)
call gradlew.bat lumenRef > ref-log.txt 2>&1
call gradlew.bat build > build-log.txt 2>&1
set RESULT=%ERRORLEVEL%
if not exist out mkdir out
del /Q out\*.jar 2>nul
del /Q build\libs\*-sources.jar 2>nul
copy /Y build\libs\lumen-*.jar out\ >nul 2>nul
type build-log.txt
echo.
if "%RESULT%"=="0" if exist out\lumen-*.jar (
  echo ==== BUILD SUCCEEDED - jar is in the out folder ====
  echo success > build-done.txt
  goto end
)
echo ==== BUILD FAILED - see build-log.txt ====
echo failed > build-done.txt
:end
pause
