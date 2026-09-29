@echo off
setlocal
cd /d "%~dp0"
echo ========================================
echo AnimeArt - Instalacion APK
echo ========================================
echo.
set "APK=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
    echo ERROR: No se encontro la APK.
    echo.
    echo Primero ejecuta build_apk.bat
    echo.
    pause
    exit /b 1
)
where adb >nul 2>&1
if errorlevel 1 (
    echo ADB NO ESTA DISPONIBLE.
    echo.
    echo No se instalara software automaticamente.
    echo Instala/activa ADB y vuelve a ejecutar este archivo.
    echo.
    pause
    exit /b 1
)
echo Comprobando dispositivos conectados...
adb devices
echo.
set "DEVICE_FOUND="
for /f "skip=1 tokens=1,2" %%A in ('adb devices 2^>nul') do (
    if "%%B"=="device" set "DEVICE_FOUND=1"
)
if not defined DEVICE_FOUND (
    echo NO HAY UN DISPOSITIVO ANDROID AUTORIZADO CONECTADO.
    echo.
    echo Conecta un dispositivo con Depuracion USB autorizada y vuelve a intentarlo.
    echo.
    pause
    exit /b 1
)
echo Instalando APK...
adb install -r "%APK%"
set "INSTALL_EXIT=%ERRORLEVEL%"
echo.
if "%INSTALL_EXIT%"=="0" (
    echo ========================================
    echo INSTALACION COMPLETADA
    echo ========================================
) else (
    echo ========================================
    echo LA INSTALACION FALLO
    echo ========================================
    echo Codigo de salida: %INSTALL_EXIT%
)
echo.
pause
exit /b %INSTALL_EXIT%
