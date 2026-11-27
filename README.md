# Sin rastro

App Android nativa en Kotlin para crear copias de imágenes sin metadatos.

## Qué hace

- Selecciona una o varias imágenes con el selector de archivos del sistema.
- Deja los originales intactos y guarda las copias en `Imágenes/SinRastro`.
- Usa nombres neutros para no conservar posibles datos en el nombre original.
- Decodifica y vuelve a codificar la imagen, sin copiar EXIF, ubicación GPS, modelo de cámara/teléfono, fecha, comentarios ni otros metadatos del archivo fuente.
- Conserva PNG y WebP; convierte los demás formatos compatibles a JPEG.
- Corrige la orientación EXIF antes de guardar. Las imágenes excepcionalmente grandes se reducen para evitar quedarse sin memoria.
- No pide permisos de almacenamiento ni envía imágenes a internet.

## Compilar

Requiere JDK 17+ y Android SDK 34. El wrapper descarga Gradle 8.7:

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
./gradlew assembleDebug
```

El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`.
