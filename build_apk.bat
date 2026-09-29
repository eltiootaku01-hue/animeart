@echo off
setlocal
cd /d "%~dp0"
echo ========================================
echo AnimeArt - Compilacion APK
echo ========================================
echo.
where java >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java no esta instalado o no esta disponible en PATH.
    echo.
    echo Instala Java 17 y vuelve a ejecutar este archivo.
    echo.
    pause
    exit /b 1
)
echo Java encontrado:
java -version
echo.
if not exist "gradlew.bat" (
    echo ERROR: No se encontro gradlew.bat en la carpeta del proyecto.
    echo.
    pause
    exit /b 1
)
echo Ejecutando Gradle Wrapper...
echo Comando: gradlew.bat clean assembleDebug
echo.
call gradlew.bat clean assembleDebug
set "BUILD_EXIT=%ERRORLEVEL%"
echo.
if not "%BUILD_EXIT%"=="0" (
    echo ========================================
    echo LA COMPILACION FALLO
    echo ========================================
    echo.
    echo Codigo de salida: %BUILD_EXIT%
    echo.
    pause
    exit /b %BUILD_EXIT%
)
if not exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo ========================================
    echo LA COMPILACION FALLO
    echo ========================================
    echo.
    echo Gradle termino sin error, pero no se encontro:
    echo app\build\outputs\apk\debug\app-debug.apk
    echo.
    pause
    exit /b 1
)
echo ========================================
echo APK GENERADA CORRECTAMENTE
echo ========================================
echo.
echo Ruta:
echo %CD%\app\build\outputs\apk\debug\app-debug.apk
echo.
for %%A in ("app\build\outputs\apk\debug\app-debug.apk") do echo Tamano: %%~zA bytes
echo.
pause
exit /b 0
