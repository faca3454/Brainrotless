# Mantenimiento

Notas para quien mantiene Lúcido. Para usar la app, ver el [README](../README.md).

## Compilar

Android Studio → *Open* → la carpeta del repo → *Build APK*. Desde la terminal:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Las cuentas sin Android (horarios, compromiso, estadísticas) tienen pruebas en `app/src/test`.

## Cómo está armada

- `MainActivity`: Instagram y YouTube web en dos WebView (IG Lite y YT Lite son dos íconos de la misma pantalla).
- `assets/block.user.js`: el script de bloqueo que se inyecta en las páginas (Reels, Shorts, feed, carteles, etc.).
- `AppGuard`: servicio de accesibilidad. Redirige las apps oficiales, bloquea navegadores de noche, cuenta el uso de las apps oficiales y protege contra desinstalar durante un compromiso o el modo dormir.
- `NotifRedirect` y `NotifActions`: copian las notificaciones de las apps oficiales (con foto, conversación, responder y marcar como leído) y las abren en Lúcido.
- `HomeActivity` (inicio), `PanelActivity` (modo dormir, foco, compromiso, Instagram y YouTube, estadísticas), `OnboardingActivity` (guía del primer uso) y `SettingsActivity` (lo técnico).
- `Setup`: el estado de cada permiso y cómo pedirlo. La guía no pide ninguno: cada función pide el suyo cuando se activa (Accesibilidad el modo dormir, el foco y el compromiso; acceso a notificaciones los mensajes; datos de uso las estadísticas), y en el inicio solo se avisa lo que falta para algo que ya prendiste.
- `Sleep`, `Focus`, `Commitment`, `Usage`, `Lock`, `Stats`, `Weekly`: horarios, límites, uso por día y resúmenes.

El plan de diseño original de las funciones está en [`plan.md`](plan.md).

## Arreglar el bloqueo cuando Instagram o YouTube cambian algo

Editá `app/src/main/assets/block.user.js` y **subí el número de `@version`**. La app descarga el script de `main` y lo usa si su versión no es menor que la que trae adentro, así que en unas horas les llega a todos sin reinstalar. Para probarlo antes: Ajustes → *Buscar actualización ahora*.

## Sacar una versión nueva

1. Escribí las notas en `.github/release-notes/vX.Y.Z.md` (si no hay, GitHub genera una lista de cambios).
2. GitHub → *Actions* → *Build APK* → *Run workflow* → escribí la versión (ej. `1.1.1`, siempre mayor que la anterior) → *Run workflow*.

En unos minutos aparece en *Releases* con `Lucido-X.Y.Z.apk` adjunto, y las apps instaladas avisan solas. También funciona crear el release a mano (*Releases* → *Draft a new release* → tag `vX.Y.Z` → *Publish*) o subir un tag `vX.Y.Z`: el APK se adjunta solo.

## Firma

Todas las versiones se firman con la misma clave, para que se puedan instalar encima de la anterior.

- La clave está en `signing/release.jks`, cifrada con el secret `KEYSTORE_PASSWORD` del repo (*Settings* → *Secrets and variables* → *Actions*). Se generó una sola vez, en el primer push a `main` con el secret configurado.
- Sin el secret, los builds salen firmados con la clave de debug (sirven para probar) y los releases fallan a propósito.
- La seguridad de la clave depende de esa contraseña: larga, aleatoria y guardada en un gestor de contraseñas.

## Seguridad del repo

Las reglas de bloqueo (`block.user.js`) se descargan de este repo y se ejecutan dentro de Instagram y YouTube con la sesión de cada usuario. **Quien tenga acceso de escritura al repo puede cambiar lo que corre en la app de todos**: protegé la cuenta de GitHub con verificación en dos pasos y no des acceso de escritura a nadie más. Los usuarios pueden apagar las actualizaciones de reglas en Ajustes.
