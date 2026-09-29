# AnimeArt

Aplicación Android ligera para composición y edición de imágenes.

## Estado actual

**FASE 0.1 — Base Android:** proyecto Android mínimo creado con una pantalla inicial. El editor de imágenes todavía no está implementado.

## Tecnología

- Android nativo.
- Kotlin integrado en Android Gradle Plugin.
- Gradle 9.6.0.
- Android Gradle Plugin 9.4.0.
- compileSdk / targetSdk 36.
- minSdk 24.
- Interfaz inicial con vistas Android nativas.
- Sin servicios de nube, IA, analítica, publicidad ni permisos de aplicación.

## Compilar

En Windows:

`gradlew.bat clean assembleDebug`

En Linux/macOS:

`./gradlew clean assembleDebug`

## APK debug

Después de una compilación correcta, el APK se genera en:

`app/build/outputs/apk/debug/app-debug.apk`

El workflow de GitHub Actions también genera el APK debug como artefacto de la ejecución de CI.

## Alcance de esta fase

Esta fase solamente establece la infraestructura Android mínima. No contiene todavía editor, capas, imágenes, texto, stickers, crop, transformaciones, eliminación de fondo, sombras, undo/redo ni animación/GIF.
