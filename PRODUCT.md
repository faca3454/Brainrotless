# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Cualquier persona con Android que baja Lúcido desde GitHub, sin conocer al autor: quiere seguir usando Instagram y YouTube (mensajes, videos que le mandan, cuentas que sigue) sin caer en el scroll infinito ni quedarse despierta de noche. No hay soporte ni nadie que le explique: cada pantalla tiene que entenderse sola, en español o en inglés.

## Product Purpose

Abrir Instagram y YouTube web sin Reels ni Shorts infinitos y ayudar a usarlos menos: modo dormir, horas de foco, un compromiso con límite diario y pausas de emergencia, y estadísticas de antes vs ahora. Éxito: la persona usa menos tiempo esas apps (lo ve en "antes vs ahora") sin dejar de recibir mensajes.

## Positioning

No bloquea las apps ni pide fuerza de voluntad: las reemplaza por una versión sin el mecanismo adictivo (IG Lite y YT Lite) y redirige las oficiales a esa versión. El compromiso no se puede aflojar mientras dura, solo hacer más estricto. Gratis, sin anuncios, sin cuentas ni servidores: todo queda en el teléfono y el código es abierto.

## Operating Context

- Se abre de pasada, muchas veces por día y por poco tiempo: mirar cuánto llevás, cambiar un horario, pedir una pausa. También de noche, con poca luz.
- Tres íconos: Lúcido (inicio y configuración), IG Lite y YT Lite. Los avisos de bloqueo aparecen dentro de IG Lite / YT Lite, a veces en el momento de frustración (llegaste al límite, es hora de dormir).
- Widgets en la pantalla de inicio del teléfono (Mini, Barra, Hoy).
- Se instala como APK fuera de Play Store: Android 13+ deja permisos "grises" que hay que destrabar.

## Capabilities and Constraints

- Android 8+ (minSdk 26), Kotlin con vistas simples armadas en código, sin XML de pantallas, sin Compose y sin Material Components. Sin dependencias nuevas en el APK.
- Español (por defecto) e inglés, claro y oscuro (sigue al sistema).
- Permisos a demanda: Accesibilidad (modo dormir, foco, compromiso), acceso a notificaciones (mensajes), datos de uso (comparar con antes).
- Términos fijos: modo dormir, modo foco, compromiso, pausa de emergencia, límite diario, IG Lite, YT Lite, racha, antes vs ahora.
- Fuera de alcance del diseño de la app: lo que se inyecta en Instagram (`block.user.js`), las notificaciones y Ajustes (preferencias de AndroidX).

## Brand Commitments

- Nombre: Lúcido (el repo se llama Brainrotless). Lema: "Instagram y YouTube, sin piloto automático" / "Instagram and YouTube, off autopilot".
- Logo existente (`ic_logo`): una L crema con un sol ámbar sobre verde azulado profundo.
- Voz: rioplatense (vos, tocá, elegí), clara y directa. Firme pero amable: el límite es el límite y se dice sin vueltas, sin culpa ni reto, y sin festejar de más.

## Evidence on Hand

- Capturas reales de hoy: `docs/capturas/` (README) y las de Paparazzi en `app/src/test/snapshots/images`.
- No hay testimonios, usuarios contados, métricas de uso ni prensa: no inventarlos.

## Product Principles

1. Se entiende sola: quien la bajó de GitHub no tiene a quién preguntar.
2. Firme pero amable: los límites se muestran claros y sin culpa; lo que no se puede aflojar se dice antes de comprometerse.
3. Lo importante de un vistazo: cuánto llevás hoy y qué está bloqueado ahora, sin tener que entrar.
4. Nada se pide antes de hacer falta: cada permiso aparece junto a la función que lo necesita.
5. Liviana y local: sin cuentas, sin servidores, sin dependencias de más.

## Accessibility & Inclusion

Zonas táctiles de 48 dp como mínimo, contraste AA en claro y oscuro, textos que entran con letra grande (130 %), descripciones para TalkBack, y respeto por la opción del sistema de quitar animaciones.
