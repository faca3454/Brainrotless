# Brainrotless

Instagram y YouTube sin el scroll infinito de Reels y Shorts.
Podés ver los reels y shorts que te mandan tus amigos, pero no pasar al siguiente.

## Qué hace

- **IG Lite** y **YT Lite**: Instagram y YouTube web dentro de la app, con el feed de Reels, Shorts y Explorar bloqueado.
- **Reels que te mandan**: se abren normal, pero la pantalla no te deja deslizar al siguiente.
- **Shorts**: se abren en el reproductor clásico, que no tiene swipe.
- **Feed de seguidos** (opcional): el inicio de Instagram muestra solo las cuentas que seguís, en orden, y se termina.
- **Videos solo al tocar** (opcional): los videos de Instagram no arrancan ni se precargan hasta que los tocás. Los reels que te mandan arrancan solos.
- **Sin carteles de "Usá la app"** en Instagram.
- **Links** (WhatsApp, Telegram, etc.) y **notificaciones** de Instagram/YouTube se abren en Brainrotless.
- **Apps oficiales**: si las abrís a mano, te redirige. Hay un botón en los ajustes rápidos para pausarlo unos minutos.
- **Ajustes**: mantené presionado el ícono de IG Lite o YT Lite → *Ajustes*.
- **Actualizaciones**: las reglas de bloqueo se actualizan solas, y la app avisa cuando hay versión nueva.
- **Comodidad**: deslizar hacia abajo para recargar, modo oscuro según el sistema, subir fotos, videos en pantalla completa.

## Instalar (Android 8 o superior)

1. Bajá el APK de la última versión en [Releases](../../releases/latest).
2. Abrilo y aceptá *instalar apps de origen desconocido*. Si Play Protect avisa, tocá *Instalar igual*: pasa con cualquier APK que no venga del Store.
3. Abrí **IG Lite** y seguí los pasos de la ventana de configuración:
   - **Notificaciones**: activá Brainrotless.
   - **Links**: en Ajustes → Apps → *Instagram* (y *YouTube*) → *Abrir de forma predeterminada*, desactivá *Abrir vínculos compatibles*. Después, en la de Brainrotless, agregá todos los dominios.
   - **Bloquear apps oficiales**: Ajustes → Accesibilidad → Brainrotless.
4. Si un permiso aparece gris: en la ventana de configuración (o en Ajustes) tocá *¿Un permiso aparece gris?* y seguí los pasos. A mano: tocá el permiso gris una vez, después Ajustes → Apps → Brainrotless → ⋮ → *Permitir ajustes restringidos*.
5. En Xiaomi o Samsung: Ajustes → Batería → Brainrotless → *Sin restricciones*, para que el sistema no apague el bloqueo.

Las apps oficiales tienen que seguir instaladas: son las que reciben las notificaciones.

### iPhone

No hay app. Usá Safari con la app gratuita *Userscripts* y cargá [`block.user.js`](app/src/main/assets/block.user.js).

## Mantenimiento

### Arreglar el bloqueo cuando Instagram cambia algo

Editá `app/src/main/assets/block.user.js` y **subí el número de `@version`**. En unas horas les llega a todos sin reinstalar. Para probarlo antes: Ajustes → *Buscar actualización ahora*.

### Sacar una versión nueva de la app

GitHub → *Actions* → *Build APK* → *Run workflow* → escribí la versión (ej. `1.0.1`, siempre mayor que la anterior) → *Run workflow*. En unos 5 minutos aparece en *Releases* con el APK adjunto, y las apps instaladas avisan solas.

También funciona crear el release a mano (*Releases* → *Draft a new release* → tag `v1.0.1` → *Publish*); el APK se adjunta solo.

### Firma (una sola vez)

Para que las actualizaciones se puedan instalar encima, todos los APK tienen que firmarse con la misma clave.

1. Repo → *Settings* → *Secrets and variables* → *Actions* → *New repository secret*.
2. Nombre: `KEYSTORE_PASSWORD`. Valor: una contraseña **larga y aleatoria** (20 caracteres o más, de un gestor de contraseñas). Guardala.
3. El próximo push a `main` genera `signing/release.jks` y lo sube al repo, cifrado con esa contraseña.

Sin el secret, los builds de `main` salen firmados con clave de debug (sirven para probar) y los releases fallan a propósito.

## Seguridad y privacidad

- Todo corre en el teléfono. No hay servidor, cuentas ni analíticas. Las sesiones quedan en la WebView del dispositivo.
- El servicio de accesibilidad solo recibe eventos de las apps de Instagram y YouTube, y solo mira qué app pasó a primer plano. No lee contenido.
- Las reglas remotas se descargan por HTTPS únicamente desde este repo, y se inyectan en Instagram y YouTube con tu sesión iniciada. Por eso, **quien tenga acceso de escritura al repo puede cambiar lo que corre en la app de todos**. Protegé la cuenta de GitHub con 2FA. Si no querés depender de eso, desactivá *Actualizar reglas de bloqueo* en Ajustes.
- La seguridad de la clave de firma depende de la contraseña del secret.

## Compilar local

Android Studio → *Open* → esta carpeta → *Build APK*. O desde la terminal: `./gradlew assembleRelease`.
