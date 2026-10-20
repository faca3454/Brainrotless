# Lúcido

Instagram y YouTube, sin piloto automático: sin el scroll infinito de Reels y Shorts, con modo dormir y compromisos para usarlos menos.
Podés ver los reels y shorts que te mandan tus amigos, pero no pasar al siguiente.

*(Antes se llamaba Brainrotless.)*

## Qué hace

- **Lúcido** (el ícono de la app): la pantalla de inicio, con acceso a Instagram y YouTube, cuánto los usaste hoy, el modo dormir, el compromiso y lo que falte configurar.
- **IG Lite** y **YT Lite**: Instagram y YouTube web dentro de la app, con el feed de Reels, Shorts y Explorar bloqueado.
- **Reels que te mandan**: se abren normal, pero la pantalla no te deja deslizar al siguiente.
- **Shorts**: se abren en el reproductor clásico, que no tiene swipe.
- **Feed de seguidos** (opcional): el inicio de Instagram muestra solo las cuentas que seguís, en orden, y se termina.
- **Videos solo al tocar** (opcional): los videos de Instagram no arrancan ni se precargan hasta que los tocás. Los reels que te mandan arrancan solos.
- **Sin carteles de "Usá la app"** en Instagram.
- **Modo dormir** (opcional): en tu horario (uno para todos los días o uno por noche), Instagram queda solo para mensajes, YouTube se bloquea (también desde las apps oficiales) y los navegadores (Chrome, Brave, Opera…) no abren. Avisa unos minutos antes y la pausa del bloqueo no funciona.
- **Compromiso** (opcional): por 7 días o más, el modo dormir, el bloqueo de navegadores y el límite diario solo se pueden endurecer. El **límite diario** (Instagram + YouTube juntos, se reinicia al despertarte) deja Instagram solo para mensajes y bloquea YouTube. Da una **pausa de emergencia** de 15 minutos cada 2 días (máximo 2 por día), para cuando llegás al límite o de noche. Cambiar la hora del teléfono no lo saltea. Con un límite corriendo, se pueden ver **Reels y Shorts** (con scroll) hasta llegar al límite; de noche, no.
  - **Al terminar** muestra cuánto bajó tu uso (comparado con las 4 semanas antes de instalar Lúcido, si diste el permiso de *Acceso a datos de uso*) y pregunta si querés empezar otro: no se renueva solo.
  - **Difícil de sacar**: opcionalmente, protección contra desinstalación (hay que quitar un permiso, con 2 minutos de espera; se quita sola al terminar). Si apagás la accesibilidad, queda un aviso fijo y IG Lite / YT Lite no abren hasta que la vuelvas a activar.
- **Links** (WhatsApp, Telegram, etc.) y **notificaciones** de Instagram/YouTube se abren en Lúcido.
- **Apps oficiales**: si las abrís a mano, te redirige. Para pausarlo unos minutos: Ajustes → *Pausar ahora*, o el botón *Apps oficiales* de los ajustes rápidos (Ajustes → *Agregar botón a ajustes rápidos*).
- **Ajustes**: mantené presionado el ícono de IG Lite o YT Lite → *Ajustes*.
- **Actualizaciones**: las reglas de bloqueo se actualizan solas, y la app avisa cuando hay versión nueva.
- **Comodidad**: Instagram y YouTube quedan abiertos a la vez (cambiar de uno al otro no recarga), transiciones suaves entre páginas, deslizar un mensaje a la derecha para responderlo (experimental), deslizar hacia abajo para recargar, modo oscuro según el sistema, subir fotos, videos en pantalla completa.

## Instalar (Android 8 o superior)

1. Bajá el APK de la última versión en [Releases](../../releases/latest).
2. Abrilo y aceptá *instalar apps de origen desconocido*. Si Play Protect avisa, tocá *Instalar igual*: pasa con cualquier APK que no venga del Store.
3. Abrí **IG Lite** y seguí los pasos de la ventana de configuración:
   - **Notificaciones**: activá Lúcido.
   - **Links**: en Ajustes → Apps → *Instagram* (y *YouTube*) → *Abrir de forma predeterminada*, desactivá *Abrir vínculos compatibles*. Después, en la de Lúcido, agregá todos los dominios.
   - **Bloquear apps oficiales**: Ajustes → Accesibilidad → *Lúcido: bloquear apps oficiales*.
4. Si un permiso aparece gris: en la ventana de configuración (o en Ajustes) tocá *¿Un permiso aparece gris?* y seguí los pasos. A mano: tocá el permiso gris una vez, después Ajustes → Apps → Lúcido → ⋮ → *Permitir ajustes restringidos*.
5. En Xiaomi o Samsung: Ajustes → Batería → Lúcido → *Sin restricciones*, para que el sistema no apague el bloqueo.

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
- El servicio de accesibilidad solo mira qué app pasó a primer plano: para redirigir Instagram y YouTube oficiales y, con el modo dormir, cerrar los navegadores en tu horario. No lee contenido ni lo que escribís, y no guarda ni envía nada.
- *Acceso a datos de uso* (opcional) solo se usa para leer cuánto usabas Instagram y YouTube oficiales antes de instalar Lúcido, para la estadística al terminar un compromiso. El tiempo de uso se guarda en el teléfono.
- La protección contra desinstalación es un administrador de dispositivo **sin ninguna política**: no puede bloquear, borrar ni controlar el teléfono; solo hace que haya que quitarlo antes de desinstalar.
- Las reglas remotas se descargan por HTTPS únicamente desde este repo, y se inyectan en Instagram y YouTube con tu sesión iniciada. Por eso, **quien tenga acceso de escritura al repo puede cambiar lo que corre en la app de todos**. Protegé la cuenta de GitHub con 2FA. Si no querés depender de eso, desactivá *Actualizar reglas de bloqueo* en Ajustes.
- La seguridad de la clave de firma depende de la contraseña del secret.

## Compilar local

Android Studio → *Open* → esta carpeta → *Build APK*. O desde la terminal: `./gradlew assembleRelease`.
