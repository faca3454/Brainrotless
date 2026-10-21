---
name: Lúcido
description: Instagram y YouTube, sin piloto automático
colors:
  ground: "#F7F4EE"
  surface: "#FFFFFF"
  surface-alt: "#ECE6DC"
  mint: "#DCEEE8"
  on-mint: "#0B3F39"
  teal: "#13695F"
  on-teal: "#FFFFFF"
  night: "#34447F"
  sun: "#E8962A"
  on-sun: "#2B1A00"
  ink: "#15201E"
  ink-muted: "#55625F"
  hairline: "#E3DCD0"
  outline: "#7A8683"
  ember: "#B23A20"
  night-ground: "#0B1416"
  night-surface: "#132225"
  night-surface-alt: "#1D2E31"
  night-mint: "#1C3B39"
  night-on-mint: "#BFE9E0"
  night-teal: "#5CC8B6"
  night-on-teal: "#04201C"
  night-night: "#8C99E0"
  night-sun: "#F5B55C"
  night-ink: "#EEE8DD"
  night-ink-muted: "#A0B0AC"
  night-hairline: "#223437"
  night-outline: "#7F8F8B"
  night-ember: "#F28B6E"
typography:
  hero:
    fontFamily: "sans-serif-medium (Roboto Medium), tnum"
    fontSize: "56sp"
  display:
    fontFamily: "sans-serif-medium (Roboto Medium), tnum"
    fontSize: "34sp"
  headline:
    fontFamily: "sans-serif-medium (Roboto Medium)"
    fontSize: "22sp"
  title:
    fontFamily: "sans-serif-medium (Roboto Medium)"
    fontSize: "16sp"
  body:
    fontFamily: "sans-serif (Roboto)"
    fontSize: "14sp"
  label:
    fontFamily: "sans-serif (Roboto)"
    fontSize: "12sp"
rounded:
  chip: "12dp"
  tile: "20dp"
  card: "24dp"
  dialog: "28dp"
  pill: "999dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.teal}"
    textColor: "{colors.on-teal}"
    rounded: "{rounded.pill}"
    height: "52dp"
  button-secondary:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.teal}"
    rounded: "{rounded.pill}"
    height: "52dp"
  chip:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.chip}"
    height: "40dp (48dp táctil)"
  chip-selected:
    backgroundColor: "{colors.teal}"
    textColor: "{colors.on-teal}"
    rounded: "{rounded.chip}"
  group:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.card}"
  tile:
    backgroundColor: "{colors.mint}"
    textColor: "{colors.on-mint}"
    rounded: "{rounded.tile}"
    padding: "12dp 16dp"
---

# Design System: Lúcido

Fuente de verdad: `res/values/colors.xml`, `res/values-night/colors.xml`, `Brand.kt` (tipos, piezas, `EASE_OUT`) y `DayDial` en `Charts.kt`. Las capturas de cada pantalla están en `app/src/test/snapshots/images` (Paparazzi).

## Overview

**Creative North Star: "Reloj del día"**

Lúcido no solo cuenta cuánto usaste: decide cuándo se cierra cada cosa. Por eso su pieza propia es un dial de 24 h con la medianoche arriba. La noche va en índigo y las horas de foco en verde azulado, como arcos sobre el borde. El sol ámbar del logo marca la hora actual, y adentro un anillo fino muestra lo usado del límite. El dial aparece en el inicio, en Modo dormir (con la luna al acostarse y el sol al levantarse) y en la bienvenida de la guía. El mismo sol vuelve en la punta del anillo de los widgets.

El resto es una app Android de trabajo, tranquila y precisa. Una columna; grupos de filas en una sola superficie de 24 dp en vez de una tarjeta por cosa. Íconos de 48 dp, interruptores al estilo de Material 3 y una escala corta de tipos. La profundidad es tonal, sin sombras.

**Key Characteristics:**
- El dial de 24 h como firma; el sol ámbar marca "ahora".
- Cada modo con su color: noche índigo, foco verde azulado, compromiso ámbar. El ícono se llena con su color solo si el modo está prendido.
- Filas agrupadas con líneas finas; la flecha (chevron) y "volver" son íconos dibujados.
- Todo texto en AA (4.5:1) en claro y oscuro.
- Movimiento escaso, con una sola curva.

## Colors

Cálida y contenida, con tres colores que significan algo.

### Primary
- **Verde azulado** (teal / night-teal): todo lo que se toca (botones, interruptores, pastillas elegidas, subtítulos de sección) y el arco del foco.

### Secondary
- **Índigo de noche** (night / night-night): el arco y el ícono del modo dormir, la perilla de "me acuesto".

### Tertiary
- **Sol ámbar** (sun / night-sun): la hora actual en el dial, la perilla de "me levanto", la racha, los días del compromiso, el botón de pausa. Siempre con tinta café encima (on-sun), nunca como texto suelto sobre claro (2.4:1).

### Neutral
- **Fondo** (ground), **superficie** (surface), **arena** (surface-alt: pistas, apagado), **menta** (mint: avisos, mosaicos, horarios, con texto on-mint), **tinta** y **tinta suave**, **línea fina** (hairline: divisores) y **contorno** (outline: bordes de controles, 3:1).
- **Brasa** (ember): solo para "te pasaste" y "falta un permiso".

### Named Rules
**The Three Meanings Rule.** Índigo es noche, verde es foco y lo que se toca, ámbar es el sol (ahora, logros, la pausa). No se usan como decoración.

## Typography

**Display/Body:** Roboto (Medium y Regular), sin fuente propia. Números con cifras tabulares.

### Hierarchy (Brand.Type)
- **Hero** (56 sp): los días que faltan del compromiso.
- **Display** (34 sp): uso de hoy, horarios, racha.
- **Headline** (22 sp): título de pantalla (26 sp en la guía y los avisos).
- **Title** (16 sp): título de fila o tarjeta.
- **Body** (14 sp): estados, explicaciones, subtítulos de sección.
- **Label** (12 sp): días del gráfico, leyendas.

**The Shrink-Not-Wrap Rule.** Los números grandes (uso, horarios, mosaicos) van en una línea y se achican solos con letra grande, en vez de partirse.

## Layout

Una columna con 16 dp de margen. Bloques separados por 12 dp (20 dp antes de las apps en el inicio). Filas de 64 dp en grupos (56 dp dentro de tarjetas). El encabezado tiene 64 dp de alto, con volver y título a la izquierda y la acción (Ajustes, interruptor) a la derecha. En el inicio, el dial va arriba con 288 dp de alto; abajo, las apps lado a lado y el plan agrupado.

## Elevation & Depth

Plano: sin sombras. La profundidad sale del tono (fondo, superficie, menta). Solo los diálogos flotan: superficie de la marca con esquinas de 28 dp (`BrandDialog`).

## Shapes

Esquinas de 24 dp en grupos y tarjetas, 20 dp en mosaicos y horarios, 12 dp en pastillas de opción, 28 dp en diálogos. Botones y etiquetas son completamente redondos; los íconos de modo van en círculos de 40 dp.

## Components

### Buttons
- **Primary:** verde lleno, texto blanco Medium 15 sp, 52 dp de alto, pastilla.
- **Secondary:** fondo del color de la pantalla, borde fino, texto verde.
- **Text:** sin fondo ("Ahora no").
- **Icon:** 48 dp, con descripción para TalkBack (volver, Ajustes).
- **Pausa:** ámbar, 40 dp a la vista en una zona de 48 dp.

### Chips
- Opción: 40 dp a la vista en 48 dp táctiles, esquinas de 12 dp, borde de contorno; la elegida, verde llena.
- Días de la semana: círculos de 40 dp en 48 dp.

### Rows and groups
- `Brand.row`: ícono, título (con etiqueta ámbar opcional), estado (brasa si falta algo) y chevron. `Brand.group` las junta en una superficie con divisores sangrados.
- Filas con interruptor: toda la fila lo cambia y TalkBack la lee como interruptor con su estado.

### Signature: DayDial
- Banda de 14 dp con la noche y el foco; marcas de cada hora y 0 · 6 · 12 · 18; el sol ámbar en la hora actual; anillo de uso de 6 dp adentro, en brasa si se pasó del límite. Con `handles`, las perillas de luna y sol en las puntas de la noche.

### Motion
- Una curva: `Brand.EASE_OUT` (cubic-bezier 0.23, 1, 0.32, 1). Interruptor: 150 ms. Pasos de la guía: 220 ms (eje compartido, 24 dp). Gráficos: 500 ms, solo al abrir la pantalla. Entrada del inicio: 240 ms, escalonada de a 40 ms. Todo con animadores de Android, que respetan "quitar animaciones".

## Do's and Don'ts

### Do:
- **Do** tomar colores de `colors.xml` y tamaños de `Brand.Type`.
- **Do** agrupar filas relacionadas en un `Brand.group` en vez de apilar tarjetas.
- **Do** darle 48 dp a todo lo que se toca y descripción a todo ícono que se toca.
- **Do** regrabar Paparazzi (`recordPaparazziDebug`) y mirar las capturas después de cada cambio visual.

### Don't:
- **Don't** usar el ámbar como texto sobre fondo claro ni la brasa fuera de avisos.
- **Don't** escribir flechas o tildes como texto ("←", "›", "✓"): son íconos.
- **Don't** sumar sombras, Material Components ni Compose.
- **Don't** animar lo que se rearma seguido (un cambio de ajuste): solo al abrir.
