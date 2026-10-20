# Lúcido

App de Android (Kotlin, vistas simples, sin Compose) que abre Instagram y YouTube web y les inyecta `app/src/main/assets/block.user.js`. Detalles en `docs/mantenimiento.md`.

## Diseño

Cuando te pidan diseñar, rediseñar, pulir, animar o revisar cualquier pantalla o pieza visual, cargá los skills de diseño de `.claude/skills/` antes de tocar nada:

- **Pantallas de la app** (inicio, panel, guía, ajustes, bloqueos, widgets): `impeccable` con su `reference/android.md` y las variantes `.native.md` (Material 3, Android). Para el movimiento y los detalles finos, `emil-design-eng`; para animar, `animate` y `review-animations`; si algo se traba, `animation-performance`. `apple-design` sirve para gestos y física del movimiento, traducido a Android.
- **Lo que se ve dentro de Instagram/YouTube** (lo que dibuja `block.user.js`): `mobile-native` y `emil-design-eng`.
- **Páginas web** (por ejemplo, una página de la app): `design-taste-frontend` (taste) e `impeccable`.
- Para probar casos extremos (nombres largos, listas vacías, números enormes): `break-ui`.

La identidad visual ya existe: colores en `res/values/colors.xml` y `res/values-night/colors.xml`, tipografía y piezas en `Brand.kt`, capturas en `docs/capturas/`. Es un refinamiento de esa identidad salvo que pidan un rediseño. Los skills están pensados sobre todo para web (CSS/React): traducí sus reglas a vistas de Android, sin sumar librerías.
