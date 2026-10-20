// ==UserScript==
// @name         Brainrotless
// @description  Bloquea el feed de Reels/Shorts pero deja ver los que te mandan
// @version      2
// @match        *://*.instagram.com/*
// @match        *://instagram.com/*
// @match        *://*.youtube.com/*
// @run-at       document-start
// ==/UserScript==
//
// Este archivo es la fuente única de las reglas: la app lo trae incluido y además
// descarga la versión de la rama main. Para publicar un arreglo sin sacar un APK nuevo,
// editalo y subí el número de @version.
(() => {
  if (window.__noscroll) return;
  window.__noscroll = true;

  // --- Config (la app la inyecta desde Ajustes; en Safari/Userscripts usa estos valores) ---
  const CFG = Object.assign(
    { igBlockFeed: false, igBlockExplore: true },
    window.__noscrollCfg || {}
  );

  const IG = /(^|\.)instagram\.com$/.test(location.hostname);
  const YT = /(^|\.)youtube\.com$/.test(location.hostname);
  if (!IG && !YT) return;

  // --- 1. Ocultar accesos al feed (cosmético; los selectores pueden romperse) ---
  const CSS = IG
    ? `a[href="/reels/"]${CFG.igBlockExplore ? ', a[href="/explore/"]' : ''} { display:none !important; }`
    : `
      ytm-reel-shelf-renderer, ytm-shorts-lockup-view-model,
      ytm-rich-item-renderer:has(a[href^="/shorts/"]),
      ytm-video-with-context-renderer:has(a[href^="/shorts/"]),
      ytm-pivot-bar-item-renderer:has(.pivot-shorts),
      ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts],
      ytd-rich-item-renderer:has(a[href^="/shorts/"]),
      ytd-guide-entry-renderer:has(a[title="Shorts"]),
      ytd-mini-guide-entry-renderer[aria-label="Shorts"]
      { display:none !important; }`;
  const addStyle = () => {
    const s = document.createElement('style');
    s.textContent = CSS;
    (document.head || document.documentElement).appendChild(s);
  };
  document.documentElement ? addStyle() : addEventListener('DOMContentLoaded', addStyle);

  // --- 2. Reels: que la pantalla no se mueva al deslizar ---
  // Bloquea el gesto sobre el contenedor que pasa de un video al siguiente.
  // Los comentarios y todo lo que no tenga videos adentro sigue scrolleando.
  if (IG) {
    const onReel = () => /^\/reels?\/[\w-]+/.test(location.pathname);
    const scrollerOf = (el) => {
      for (; el && el.nodeType === 1; el = el.parentElement) {
        const oy = getComputedStyle(el).overflowY;
        if ((oy === 'auto' || oy === 'scroll') && el.scrollHeight > el.clientHeight) return el;
      }
      return null; // scrollea la página entera
    };
    const blocks = (target) => {
      const s = scrollerOf(target);
      if (!s) return onReel();
      if (!s.querySelector('video')) return false;
      // Fuera de /reel/ también, por si el visor se abre encima del chat sin cambiar la URL
      return onReel() || getComputedStyle(s).scrollSnapType !== 'none';
    };

    let blocking = false;
    addEventListener('touchstart', (e) => {
      if (e.touches.length === 1) blocking = blocks(e.target);
    }, { capture: true, passive: true });
    addEventListener('touchmove', (e) => {
      if (blocking && e.cancelable) e.preventDefault();
    }, { capture: true, passive: false });
    addEventListener('wheel', (e) => {
      if (blocks(e.target)) e.preventDefault();
    }, { capture: true, passive: false });
    addEventListener('keydown', (e) => {
      const t = e.target;
      if (/^(ArrowUp|ArrowDown|PageUp|PageDown)$/.test(e.key) &&
          !(t.isContentEditable || /^(INPUT|TEXTAREA)$/.test(t.tagName)) && blocks(t)) e.preventDefault();
    }, true);
  }

  // --- 3. Guardia de URL (respaldo; no depende del DOM) ---
  let allowedReel = null;
  const check = () => {
    const p = location.pathname;

    if (YT) {
      // Short -> reproductor clásico: se ve igual, pero sin swipe al siguiente
      const m = p.match(/^\/shorts\/([\w-]{11})/);
      if (m) location.replace('/watch?v=' + m[1] + location.search.replace(/^\?/, '&'));
      return;
    }

    // Instagram
    const blocked =
      /^\/reels\/?$/.test(p) ||
      (CFG.igBlockExplore && /^\/explore\/?$/.test(p)) ||
      (CFG.igBlockFeed && p === '/');
    if (blocked) {
      location.replace('/direct/inbox/');
      return;
    }
    const m = p.match(/^\/reels?\/([\w-]+)/);
    if (!m) { allowedReel = null; return; }
    const id = m[1];
    if (allowedReel === null) {
      allowedReel = id;                               // reel abierto a propósito (DM, link, perfil)
    } else if (id !== allowedReel) {
      location.replace('/reel/' + allowedReel + '/'); // pasó al siguiente: vuelve
    }
  };

  for (const k of ['pushState', 'replaceState']) {
    const orig = history[k];
    history[k] = function (...a) { const r = orig.apply(this, a); check(); return r; };
  }
  addEventListener('popstate', check);
  setInterval(check, 400); // respaldo por si la SPA cambia la URL por otra vía
  check();

  // --- 4. Pull-to-refresh: avisarle a la app si el gesto empieza arriba de todo ---
  // Instagram y YouTube scrollean dentro de contenedores, no la ventana,
  // así que la app no puede saberlo sola.
  if (window.NoScrollBridge) {
    addEventListener('touchstart', (e) => {
      const p = location.pathname;
      // En chats (cargan mensajes viejos tirando hacia abajo) y reels, nunca recargar
      if (IG && /^\/(direct\/t|reels?)\//.test(p)) { NoScrollBridge.setAtTop(false); return; }
      let top = (document.scrollingElement || document.documentElement).scrollTop <= 0;
      for (let el = e.target; top && el && el.nodeType === 1 && el !== document.body; el = el.parentElement) {
        if (el.scrollTop > 0) top = false;
      }
      NoScrollBridge.setAtTop(top);
    }, { capture: true, passive: true });
  }
})();
