---
name: Lúcido
description: Instagram y YouTube, sin piloto automático
colors:
  dawn-cream: "#FBF7F0"
  paper-white: "#FFFFFF"
  sand: "#F1EADF"
  mint-mist: "#DDEFEA"
  deep-teal: "#167A70"
  on-deep-teal: "#FFFFFF"
  sun-amber: "#F2A33A"
  on-sun-amber: "#2B1A00"
  ink: "#16211F"
  ink-muted: "#5E6B68"
  hairline: "#E6DED2"
  ember: "#C9492E"
  night-bg: "#0E1A1C"
  night-surface: "#16272A"
  night-surface-alt: "#1C3134"
  night-tonal: "#1D3D3B"
  night-teal: "#4FBFAD"
  on-night-teal: "#06201D"
  night-amber: "#F5B55C"
  night-ink: "#EEE8DD"
  night-ink-muted: "#9DAEAA"
  night-hairline: "#25393C"
  night-ember: "#F08467"
  logo-deep: "#0F3B3A"
  logo-cream: "#FBF3E6"
  logo-sun: "#F4A93D"
typography:
  numeral-xl:
    fontFamily: "sans-serif-medium (Roboto Medium)"
    fontSize: "34sp"
    fontWeight: 500
  screen-title:
    fontFamily: "sans-serif-medium (Roboto Medium)"
    fontSize: "24-26sp"
    fontWeight: 500
  numeral:
    fontFamily: "sans-serif-medium (Roboto Medium)"
    fontSize: "22sp"
    fontWeight: 500
  title:
    fontFamily: "sans-serif-medium (Roboto Medium)"
    fontSize: "15.5-16sp"
    fontWeight: 500
  body:
    fontFamily: "sans-serif (Roboto)"
    fontSize: "13-15sp"
    fontWeight: 400
  label:
    fontFamily: "sans-serif-medium (Roboto Medium)"
    fontSize: "12sp"
    fontWeight: 500
    letterSpacing: "0.04em"
rounded:
  chip: "999dp"
  banner: "14dp"
  tile: "16dp"
  card: "20dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  screen: "18-20dp"
components:
  button-primary:
    backgroundColor: "{colors.deep-teal}"
    textColor: "{colors.on-deep-teal}"
    rounded: "{rounded.chip}"
    padding: "0 24dp"
    height: "48dp"
  button-secondary:
    backgroundColor: "{colors.dawn-cream}"
    textColor: "{colors.deep-teal}"
    rounded: "{rounded.chip}"
    padding: "0 24dp"
    height: "48dp"
  chip-accent:
    backgroundColor: "{colors.sun-amber}"
    textColor: "{colors.on-sun-amber}"
    rounded: "{rounded.chip}"
    height: "36dp"
  card:
    backgroundColor: "{colors.paper-white}"
    rounded: "{rounded.card}"
    padding: "16dp"
  stat-tile:
    backgroundColor: "{colors.mint-mist}"
    rounded: "{rounded.tile}"
    padding: "10dp 12dp"
  pill:
    backgroundColor: "{colors.sun-amber}"
    textColor: "{colors.on-sun-amber}"
    rounded: "{rounded.chip}"
---

# Design System: Lúcido

Estado: documenta la identidad **de hoy** (antes del rediseño). Fuente de verdad: `res/values/colors.xml`, `res/values-night/colors.xml` y `Brand.kt`.

## Overview

**Creative North Star: "El amanecer tranquilo"**

La madrugada todavía en calma (un verde azulado profundo) y un sol ámbar que asoma: el logo es literalmente eso, una L crema con un punto de sol. La app traduce esa escena a pantallas de papel crema con tarjetas blancas redondeadas, un solo acento verde azulado para todo lo que se toca y el ámbar reservado para lo que festeja o pide atención amable (rachas, días que quedan, la pausa). De noche la escena se invierte: fondo petróleo, tarjetas un poco más claras, el verde se aclara a menta.

Es una app para operar de pasada: densidad media, una columna, tarjetas apiladas con 12 dp entre sí, todo armado con vistas simples desde `Brand.kt`. No hay sombras ni capas: la profundidad es tonal (crema → blanco, petróleo → petróleo claro).

**Key Characteristics:**
- Una columna de tarjetas blancas de 20 dp sobre crema; en oscuro, superficies tonales sobre petróleo.
- Un solo acento interactivo (verde azulado) y un acento cálido (ámbar) para logros y la pausa.
- Rojo brasa solo para "te pasaste" o "falta un permiso".
- Roboto y Roboto Medium del sistema, sin fuente propia.
- Gráficos dibujados a mano (anillo, columnas, barras, puntos) con los mismos colores.

## Colors

Cálida y contenida: crema y tinta casi negra, un verde azulado como única voz de acción, ámbar como sol.

### Primary
- **Verde azulado profundo** (deep-teal / night-teal): botones primarios, interruptores prendidos, pastillas elegidas, anillos y columnas, links de ayuda, íconos dentro de círculos menta.

### Secondary
- **Sol ámbar** (sun-amber / night-amber): la pastilla de racha y de días del compromiso, el botón "Pausa" en la barra de IG Lite. Siempre con tinta café (on-sun-amber), 8:1.

### Tertiary
- **Brasa** (ember / night-ember): texto de aviso (límite pasado, YouTube bloqueado, falta Accesibilidad), columnas de días pasados del límite, línea punteada del límite.

### Neutral
- **Crema de amanecer** (dawn-cream / night-bg): fondo de todas las pantallas propias y de los avisos.
- **Papel blanco** (paper-white / night-surface): tarjetas.
- **Arena** (sand / night-surface-alt): pistas de anillos y barras, interruptor apagado, barra de "solo mensajes".
- **Niebla menta** (mint-mist / night-tonal): fondo de íconos, banners, avisos de permisos, mosaicos de números, cajas de horario.
- **Tinta** y **tinta suave** (ink, ink-muted): texto principal y secundario.
- **Línea fina** (hairline): borde de botones secundarios y pastillas sin elegir.

### Named Rules
**The One Voice Rule.** Todo lo que se toca es verde azulado; el ámbar no es un botón (salvo la pausa) y la brasa nunca.

**The Contrast Debt Rule (hallazgo de hoy).** En claro, la brasa sobre crema da 4.39:1 y el verde sobre menta 4.35:1: quedan apenas por debajo de AA para texto chico. Hay que oscurecerlos antes de sumar más texto encima.

## Typography

**Display Font:** Roboto Medium (`sans-serif-medium`)
**Body Font:** Roboto (`sans-serif`)

**Character:** la del sistema, sin fuente propia; la jerarquía sale solo de tamaño y del peso medio.

### Hierarchy
- **Numeral XL** (Medium, 32–40 sp): los días que quedan, la racha, el "−49 %".
- **Screen title** (Medium, 26 sp en inicio y guía, 24 sp en el panel).
- **Numeral** (Medium, 20–22 sp): minutos de hoy, valores de los mosaicos; horarios en 28 sp.
- **Title** (Medium, 15–16 sp): títulos de tarjetas y filas.
- **Body** (Regular, 13–15 sp, tinta suave): explicaciones y estados.
- **Label** (Medium, 12 sp, mayúsculas, 0.04 em): títulos de sección del panel.

**The Size Sprawl Rule (hallazgo de hoy).** Hoy conviven 14 tamaños distintos (11.5 a 40 sp), elegidos pieza por pieza. Un rediseño debería reducirlos a la escala de roles.

## Layout

Una columna con márgenes de 20 dp (inicio, guía) o 18 dp (panel). Tarjetas separadas por 12 dp; títulos de sección con 18 dp arriba. Interior de tarjeta 16 dp. En el inicio, IG y YT van lado a lado en dos tarjetas iguales y Ajustes queda empujado al pie. El panel no tiene barra superior del sistema: una fila con "←" y el título. Pastillas en filas que bajan de línea solas (FlowLayout).

## Elevation & Depth

Plano: sin sombras. La profundidad es tonal, de crema a blanco (claro) o de petróleo a petróleo claro (oscuro). Los botones primarios no tienen `stateListAnimator`.

**The Flat Paper Rule.** Ninguna superficie propia proyecta sombra; solo los diálogos del sistema la tienen.

## Shapes

Redondo y suave en todo: tarjetas 20 dp, mosaicos y cajas de horario 16 dp, banners 14 dp, botones y pastillas completamente redondos. Íconos dentro de círculos menta de 40–44 dp. El logo es un cuadrado redondeado.

## Components

### Buttons
- **Shape:** pastilla (999 dp), 48 dp de alto.
- **Primary:** verde azulado lleno, texto blanco Medium 15 sp.
- **Secondary:** crema con borde de línea fina y texto verde.
- **Text:** sin fondo, texto tinta suave ("Ahora no").
- **Accent chip:** ámbar, 36 dp de alto (por debajo de 48 dp).
- **Press:** onda (ripple) verde translúcida recortada a la forma.

### Chips
- **Style:** pastilla blanca con borde de línea fina; la elegida, verde llena con texto blanco. 13.5 sp Medium, ~34 dp de alto.

### Cards / Containers
- **Corner Style:** 20 dp. **Background:** papel blanco. **Padding:** 16 dp. Las que se tocan llevan "›" a la derecha.

### Navigation
- Inicio como centro; cada tarjeta abre su pantalla del panel. Volver con "←" (texto) o el gesto del sistema.

### Signature components
- **Anillo de hoy** (RingView): aro de 7 dp, arena de pista, verde o brasa de progreso.
- **Columnas por día** (DayBars): columnas de un color con la elegida llena, líneas punteadas de límite y "antes".
- **Interruptor dibujado** (ToggleView): 48×28 dp, pista verde o arena.
- **Aviso suave** (Brand.notice): bloque menta con el texto, el botón y "¿Un permiso aparece gris?".

## Do's and Don'ts

### Do:
- **Do** tomar colores de `colors.xml` (claro y oscuro), nunca hex sueltos en las pantallas.
- **Do** usar tarjetas de 20 dp sobre el fondo crema y 12 dp entre ellas.
- **Do** reservar el ámbar para logros y la pausa, y la brasa para límites y permisos que faltan.

### Don't:
- **Don't** sumar sombras: la profundidad es tonal.
- **Don't** usar Material Components ni Compose: todo sale de `Brand.kt`.
- **Don't** poner texto chico en brasa sobre crema ni verde sobre menta sin corregir su contraste.
