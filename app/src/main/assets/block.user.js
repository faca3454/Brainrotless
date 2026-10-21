// ==UserScript==
// @name         Lúcido
// @description  Bloquea el feed de Reels/Shorts pero deja ver los que te mandan
// @version      10
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
    {
      igFeed: 'normal', igBlockExplore: true, igTapToPlay: false, igReels: false, ytShorts: false,
      restricted: false, restrictedMsg: '', smooth: false, swipeReply: false, debug: false,
    },
    window.__noscrollCfg || {}
  );
  if (CFG.igBlockFeed) CFG.igFeed = 'blocked'; // versiones viejas de la app mandan igBlockFeed

  const IG = /(^|\.)instagram\.com$/.test(location.hostname);
  const YT = /(^|\.)youtube\.com$/.test(location.hostname);
  if (!IG && !YT) return;

  // Aviso que dejó la página anterior al redirigir. Se lee antes de la guardia de URL,
  // que puede redirigir otra vez y seguir corriendo un instante en esta página.
  let pendingToast = null;
  try { pendingToast = sessionStorage.getItem('brl_toast'); sessionStorage.removeItem('brl_toast'); } catch (e) {}

  // --- 1. Ocultar accesos al feed (cosmético; los selectores pueden romperse) ---
  // igReels / ytShorts: la app los deja ver mientras no se llegue al límite diario (ni sea de noche)
  const hidden = [!CFG.igReels && 'a[href="/reels/"]', CFG.igBlockExplore && 'a[href="/explore/"]'].filter(Boolean);
  const SMOOTH = CFG.smooth
    ? '@view-transition { navigation: auto; }' + // fundido también en las páginas que se recargan
      '::view-transition-old(root), ::view-transition-new(root) { animation-duration: .18s; }'
    : '';
  const CSS = SMOOTH + (IG
    ? (hidden.length ? `${hidden.join(', ')} { display:none !important; }` : '') +
      (CFG.restricted ? 'a[href="/"], video { display:none !important; }' : '')
    : CFG.ytShorts ? '' : `
      ytm-reel-shelf-renderer, ytm-shorts-lockup-view-model,
      ytm-rich-item-renderer:has(a[href^="/shorts/"]),
      ytm-video-with-context-renderer:has(a[href^="/shorts/"]),
      ytm-pivot-bar-item-renderer:has(.pivot-shorts),
      ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts],
      ytd-rich-item-renderer:has(a[href^="/shorts/"]),
      ytd-guide-entry-renderer:has(a[title="Shorts"]),
      ytd-mini-guide-entry-renderer[aria-label="Shorts"]
      { display:none !important; }`);
  const addStyle = () => {
    const s = document.createElement('style');
    s.textContent = CSS;
    (document.head || document.documentElement).appendChild(s);
  };
  document.documentElement ? addStyle() : addEventListener('DOMContentLoaded', addStyle);

  // APK de prueba: lo que hace el script por su cuenta (frenar un toque, cerrar un cartel), para el diagnóstico
  const trace = [];
  const note = (what) => { if (CFG.debug) trace.push([performance.now(), what]); };

  // Lo que una sección rehace al cambiar la URL: corre ahora y después en cada cambio (desde check)
  const onRoute = [];
  const route = (f) => { onRoute.push(f); f(); };

  // --- 2. Reels: que la pantalla no se mueva al deslizar ---
  // Bloquea el gesto sobre el contenedor que pasa de un video al siguiente.
  // Los comentarios y todo lo que no tenga videos adentro sigue scrolleando.
  // Un touchmove que puede frenar el gesto hace que el navegador espere a la página en cada
  // movimiento: por eso va en ese contenedor y en la página entera solo en /reel/.
  if (IG && !CFG.igReels) {
    const onReel = () => /^\/reels?\/[\w-]+/.test(location.pathname);
    // Con snap vertical cuenta aunque todavía no desborde: el visor puede sumar el siguiente reel después
    const scrollerOf = (el) => {
      for (; el && el.nodeType === 1; el = el.parentElement) {
        const s = getComputedStyle(el);
        if ((s.overflowY === 'auto' || s.overflowY === 'scroll') &&
            (el.scrollHeight > el.clientHeight || /^(y|block|both)/.test(s.scrollSnapType))) return el;
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
    const START = { capture: true, passive: true }, MOVE = { capture: true, passive: false };
    const start = (e) => { if (e.touches.length === 1) blocking = blocks(e.target); };
    const move = (e) => { if (blocking && e.cancelable) e.preventDefault(); };
    const listen = (el, on) => {
      const f = on ? 'addEventListener' : 'removeEventListener';
      el[f]('touchstart', start, START);
      el[f]('touchmove', move, MOVE);
    };
    route(() => listen(window, onReel()));
    // El visor puede abrirse encima del chat sin cambiar la URL: se engancha su contenedor
    // (el que tiene scroll-snap) apenas un video carga o arranca, antes de que lo deslices
    const hooked = new WeakSet();
    const hook = (e) => {
      const s = e.target instanceof HTMLVideoElement && scrollerOf(e.target);
      if (!s || hooked.has(s) || getComputedStyle(s).scrollSnapType === 'none') return;
      hooked.add(s);
      listen(s, true);
    };
    addEventListener('loadstart', hook, true);
    addEventListener('playing', hook, true);
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
  let prevFollowing = false; // veníamos del feed de seguidos
  let lastPath = null;
  const check = (nav) => {
    const p = location.pathname;
    if (p !== lastPath) { lastPath = p; for (const f of onRoute) f(); }

    if (YT) {
      // Short -> reproductor clásico: se ve igual, pero sin swipe al siguiente
      const m = p.match(/^\/shorts\/([\w-]{11})/);
      if (m && !CFG.ytShorts) location.replace('/watch?v=' + m[1] + location.search.replace(/^\?/, '&'));
      return;
    }

    // Instagram
    // Modo solo mensajes (hora de dormir): fuera de los chats, todo vuelve al inbox
    if (CFG.restricted && !/^\/(direct|accounts|challenge)\//.test(p)) {
      try { sessionStorage.setItem('brl_toast', CFG.restrictedMsg); } catch (e) {}
      location.replace('/direct/inbox/');
      return;
    }
    const blocked =
      (!CFG.igReels && /^\/reels\/?$/.test(p)) ||
      (CFG.igBlockExplore && /^\/explore\/?$/.test(p)) ||
      (CFG.igFeed === 'blocked' && p === '/');
    if (blocked) {
      location.replace('/direct/inbox/');
      return;
    }

    // Feed de inicio -> solo cuentas que seguís. Si Instagram limpia la URL después de
    // cargar (replaceState a "/"), sigue siendo el feed de seguidos: no redirigir.
    const following = p === '/' && /[?&]variant=following(&|$)/.test(location.search);
    if (nav) prevFollowing = false;
    if (CFG.igFeed === 'following' && p === '/' && !following && !prevFollowing) {
      location.replace('/?variant=following');
      return;
    }
    prevFollowing = following || (p === '/' && prevFollowing);
    const m = p.match(/^\/reels?\/([\w-]+)/);
    if (!m || CFG.igReels) { allowedReel = null; return; }
    const id = m[1];
    if (allowedReel === null) {
      allowedReel = id;                               // reel abierto a propósito (DM, link, perfil)
    } else if (id !== allowedReel) {
      location.replace('/reel/' + allowedReel + '/'); // pasó al siguiente: vuelve
    }
  };

  for (const k of ['pushState', 'replaceState']) {
    const orig = history[k];
    history[k] = function (...a) { const r = orig.apply(this, a); check(k === 'pushState'); return r; };
  }
  // Tocar "Inicio" va directo al feed de seguidos, sin mostrar el otro un instante. Se navega dentro
  // del sitio, como el botón atrás; si en 1 s lo que estaba en pantalla sigue ahí, se recarga.
  if (IG && CFG.igFeed === 'following') {
    const FOLLOWING = '/?variant=following';
    addEventListener('click', (e) => {
      if (!(e.target.closest && e.target.closest('a[href="/"]'))) return;
      e.preventDefault();
      e.stopPropagation();
      if (location.pathname === '/') { location.assign(FOLLOWING); return; } // ya en el feed: recargarlo
      const before = document.elementFromPoint(innerWidth / 2, innerHeight / 2);
      history.pushState(null, '', FOLLOWING);
      dispatchEvent(new PopStateEvent('popstate', { state: null }));
      setTimeout(() => {
        if (location.pathname === '/' && before && before.isConnected && before.getClientRects().length)
          location.assign(FOLLOWING);
      }, 1000);
    }, true);
  }
  addEventListener('popstate', () => check(true));
  // Respaldo por si la SPA cambia la URL por otra vía. Lo normal lo atrapan los hooks y popstate,
  // así que alcanza con mirar cada tanto (antes era cada 400 ms, también mientras scrolleás)
  setInterval(check, 3000);
  check();

  // --- 4. Modo solo mensajes: sin videos (ni reels que te mandan); los audios sí ---
  if (IG && CFG.restricted) {
    const play = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function () {
      if (!(this instanceof HTMLVideoElement)) return play.call(this);
      return Promise.reject(new DOMException('Hora de dormir', 'NotAllowedError'));
    };
    addEventListener('play', (e) => { if (e.target instanceof HTMLVideoElement) e.target.pause(); }, true);

    // Aviso de por qué te devolvió al inbox
    const toast = () => {
      const t = document.createElement('div');
      t.textContent = pendingToast;
      t.style.cssText =
        'position:fixed;left:50%;top:16px;transform:translateX(-50%);z-index:2147483647;max-width:90vw;' +
        'background:rgba(0,0,0,.85);color:#fff;padding:10px 16px;border-radius:20px;font:14px sans-serif;' +
        'text-align:center;pointer-events:none';
      document.body.appendChild(t);
      setTimeout(() => t.remove(), 4000);
    };
    if (pendingToast) document.body ? toast() : addEventListener('DOMContentLoaded', toast);
  }

  // --- 5. Videos solo al tocar (Instagram, opcional) ---
  // Los videos no arrancan ni se precargan hasta que los tocás. Los reels e historias
  // que abrís arrancan solos: ya tocaste para abrirlos.
  if (IG && CFG.igTapToPlay) {
    const tapped = new WeakSet();
    const badges = new WeakMap();
    const inViewer = (v) => {
      if (/^\/(reels?|stories)\//.test(location.pathname)) return true;
      for (let el = v.parentElement; el && el.nodeType === 1; el = el.parentElement) {
        if (getComputedStyle(el).scrollSnapType !== 'none') return true; // visor encima del chat
      }
      return false;
    };
    const allowed = (v) => !(v instanceof HTMLVideoElement) || tapped.has(v) || inViewer(v);

    const showBadge = (v) => {
      const parent = v.parentElement;
      if (badges.has(v) || !parent) return;
      const b = document.createElement('div');
      b.textContent = '▶';
      b.style.cssText =
        'position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:64px;height:64px;' +
        'border-radius:50%;background:rgba(0,0,0,.55);color:#fff;font:28px/64px sans-serif;' +
        'text-align:center;pointer-events:none;z-index:2147483647';
      if (getComputedStyle(parent).position === 'static') parent.style.position = 'relative';
      parent.appendChild(b);
      badges.set(v, b);
    };
    const hideBadge = (v) => { badges.get(v)?.remove(); badges.delete(v); };

    const play = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function () {
      if (allowed(this)) return play.call(this);
      showBadge(this);
      return Promise.reject(new DOMException('Tocá el video para reproducirlo', 'NotAllowedError'));
    };
    // Por si arranca por el atributo autoplay, sin pasar por play()
    addEventListener('play', (e) => {
      if (allowed(e.target)) { hideBadge(e.target); return; }
      e.target.pause();
      showBadge(e.target);
    }, true);

    // Sin precarga: no gasta datos hasta que lo tocás
    let pending = false;
    new MutationObserver(() => {
      if (pending) return;
      pending = true;
      setTimeout(() => {
        pending = false;
        for (const v of document.getElementsByTagName('video')) {
          if (v.preload !== 'none' && !allowed(v)) { v.autoplay = false; v.preload = 'none'; showBadge(v); }
        }
      }, 200);
    }).observe(document, { childList: true, subtree: true }); // document: el <html> puede no existir todavía

    // Primer toque sobre un video frenado: arranca (en vez de, por ejemplo, activar el sonido)
    addEventListener('click', (e) => {
      if (e.target.closest && e.target.closest('a[href],button')) return;
      for (const v of document.getElementsByTagName('video')) {
        if (allowed(v)) continue;
        const r = v.getBoundingClientRect();
        if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom) continue;
        e.preventDefault();
        e.stopPropagation();
        note('videos al tocar: frené el toque y reproduje el video');
        tapped.add(v);
        v.preload = 'auto';
        v.play().catch(() => {});
        return;
      }
    }, true);
  }

  // --- 6. Sacar los carteles de "Usá la app" (Instagram) ---
  // Por texto, porque las clases cambian seguido. En una ventana se toca "Ahora no";
  // en un banner fijo se oculta el botón (y el banner, si no tiene nada más).
  // Se mira solo lo que se agregó, cuando el navegador está libre (no en medio del scroll). Una
  // ventana se mira entera: el "Ahora no" puede aparecer después que el botón.
  if (IG) {
    const APP = /^(abrir|usar|usá|usa|descargar|obtener|ver|cambiar a|open|use|get|download|view|switch to)( en| in)?( la| the)? (app|aplicación|instagram)$/i;
    const LATER = /^(ahora no|no, gracias|cancelar|cerrar|(seguir|continuar) en (el navegador|la web)|not now|no thanks|cancel|close|continue (in|on) (the )?(browser|web))$/i;
    const text = (el) => (el.textContent || '').replace(/\s+/g, ' ').trim();
    const CLICKABLE = 'a,button,[role="button"]';

    const fix = (el) => {
      if (!APP.test(text(el))) return;
      const dialog = el.closest('[role="dialog"]');
      const later = dialog && [...dialog.querySelectorAll(CLICKABLE)].find((b) => LATER.test(text(b)));
      if (later) { note(`carteles: toqué "${text(later)}" en una ventana con "${text(el)}"`); later.click(); return; }

      note(`carteles: oculté "${text(el)}"`);
      el.style.setProperty('display', 'none', 'important');
      for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
        const pos = getComputedStyle(p).position;
        if (pos !== 'fixed' && pos !== 'sticky') continue;
        const others = [...p.querySelectorAll(CLICKABLE)].filter(
          (b) => b !== el && !APP.test(text(b)) && !LATER.test(text(b)) &&
            !/^(cerrar|close)$/i.test(b.getAttribute('aria-label') || '')
        );
        if (!others.length) p.style.setProperty('display', 'none', 'important');
        break;
      }
    };
    let added = [], scheduled = false;
    const idle = window.requestIdleCallback ? (f) => requestIdleCallback(f, { timeout: 1000 }) : (f) => setTimeout(f, 300);
    const sweep = () => {
      scheduled = false;
      const roots = new Set();
      for (const n of added) {
        const el = n.nodeType === 1 ? n : n.parentElement; // texto nuevo: su elemento
        if (el && el.isConnected) roots.add(el.closest('[role="dialog"]') || el.closest(CLICKABLE) || el);
      }
      added = [];
      for (const r of roots) {
        if (r.matches(CLICKABLE)) fix(r);
        for (const b of r.querySelectorAll(CLICKABLE)) fix(b);
      }
    };
    new MutationObserver((records) => {
      for (const r of records) {
        if (r.type === 'characterData') added.push(r.target);
        else for (const n of r.addedNodes) added.push(n);
      }
      if (!scheduled && added.length) { scheduled = true; idle(sweep); }
    }).observe(document, { childList: true, subtree: true, characterData: true }); // document: el <html> puede no existir todavía
  }

  // --- 7. Transiciones suaves (opcional, apagadas por defecto) ---
  // Fundido corto al cambiar de página dentro del sitio, de cómo se veía a la página en vivo. No se
  // espera a que la nueva termine de dibujarse: congelaba la pantalla. Mientras dura, no toma toques.
  if (CFG.smooth && document.startViewTransition) {
    const VIDEO_PAGE = /^\/(reels?|stories|shorts|watch)(\/|$)/; // un video congelado se nota
    let running = false;
    const push = history.pushState;
    history.pushState = function (...a) {
      const from = location.pathname;
      const r = push.apply(this, a);
      if (!running && document.visibilityState === 'visible' &&
          !VIDEO_PAGE.test(from) && !VIDEO_PAGE.test(location.pathname)) {
        running = true;
        try {
          document.startViewTransition(() => {}).finished.finally(() => { running = false; });
        } catch (e) { running = false; }
      }
      return r;
    };
  }

  // --- 8. Deslizar un mensaje a la derecha para responder (Instagram, opcional) ---
  // Instagram web no lo trae. El mensaje se mueve con el dedo y, al soltar, se imita lo que hace
  // la persona a mano: mantenerlo presionado y tocar "Responder" en el menú. Depende del HTML de
  // Instagram: si deja de andar, se arregla acá.
  if (IG && CFG.swipeReply) {
    // "Responder" puede venir como etiqueta ("Responder", "Responder al mensaje"), como título del
    // dibujo (<title> del SVG) o como texto de un ítem de menú
    const LABEL_RE = /(^|\s)(responder|reply)(\s|$)/i;
    const TEXT_RE = /^(responder|reply)$/i;
    const isReply = (el) => {
      const title = el.querySelector ? el.querySelector(':scope > title') : null;
      const lbl = el.getAttribute('aria-label') || el.getAttribute('title') || (title && title.textContent) || '';
      if (LABEL_RE.test(lbl.trim())) return true;
      return el.matches('[role="button"], [role="menuitem"], button') &&
        TEXT_RE.test((el.textContent || '').replace(/\s+/g, ' ').trim());
    };
    const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

    const scrollerOf = (el) => {
      for (; el && el.nodeType === 1; el = el.parentElement) {
        const oy = getComputedStyle(el).overflowY;
        if ((oy === 'auto' || oy === 'scroll') && el.scrollHeight > el.clientHeight) return el;
      }
      return null;
    };
    // La burbuja tocada: dentro de la lista que scrollea (no el encabezado ni la caja de texto), el
    // ancestro más grande que todavía no ocupa todo el ancho (las filas sí lo ocupan; las burbujas no).
    const bubbleOf = (target) => {
      let el = target.nodeType === 1 ? target : target.parentElement;
      if (!el || el.closest('textarea, input, [contenteditable="true"], [role="textbox"], form')) return null;
      const list = scrollerOf(el);
      if (!list) return null;
      const full = list.clientWidth * 0.85;
      while (el.parentElement && el.parentElement !== list && el.parentElement.getBoundingClientRect().width < full)
        el = el.parentElement;
      const r = el.getBoundingClientRect();
      return r.width < full && r.height < innerHeight * 0.6 ? el : null;
    };

    // Eventos de mentira para "mantener presionado": táctiles, de puntero y el menú contextual,
    // así anda con cualquiera de las formas en que el sitio lo detecte
    let simulating = false;
    const fire = (el, type, x, y) => {
      const base = { bubbles: true, cancelable: true, composed: true, clientX: x, clientY: y };
      if (type.startsWith('touch')) {
        if (typeof Touch !== 'function') return;
        const t = new Touch({ identifier: 9999, target: el, clientX: x, clientY: y, pageX: x + scrollX, pageY: y + scrollY });
        const now = type === 'touchstart' ? [t] : [];
        el.dispatchEvent(new TouchEvent(type, { ...base, touches: now, targetTouches: now, changedTouches: [t] }));
      } else if (type.startsWith('pointer')) {
        el.dispatchEvent(new PointerEvent(type, { ...base, pointerId: 9999, pointerType: 'touch', isPrimary: true, buttons: type === 'pointerdown' ? 1 : 0 }));
      } else {
        el.dispatchEvent(new MouseEvent(type, { ...base, view: window, button: type === 'contextmenu' ? 2 : 0 }));
      }
    };
    const longPress = async (el, x, y) => {
      simulating = true;
      try {
        fire(el, 'pointerdown', x, y);
        fire(el, 'touchstart', x, y);
        await sleep(650);
        fire(el, 'contextmenu', x, y);
        await sleep(60);
        // "Cancelar" en vez de "soltar": el menú queda abierto y no cuenta como un toque
        fire(el, 'pointercancel', x, y);
        fire(el, 'touchcancel', x, y);
      } finally {
        simulating = false;
      }
    };

    const reply = async (bubble, target, x, y) => {
      const list = scrollerOf(bubble);
      const box = bubble.getBoundingClientRect();
      // Un botón dentro de la lista solo vale si se ve y está a la altura de este mensaje
      // (puede haber quedado el de otro mensaje); uno en un menú aparte, sí
      const mine = (b) => {
        if (!list || !list.contains(b)) return true;
        const r = b.getBoundingClientRect();
        const cy = (r.top + r.bottom) / 2;
        return r.width > 0 && r.height > 0 && cy >= box.top - 4 && cy <= box.bottom + 4;
      };
      const findReply = (root) => {
        for (const el of root.querySelectorAll('[aria-label], [title], svg, [role="button"], [role="menuitem"], button')) {
          if (isReply(el) && mine(el)) return el;
        }
        return null;
      };
      // 1. Barra que aparece al pasar el mouse (Instagram en pantallas grandes)
      for (const type of ['mouseover', 'mouseenter', 'mousemove']) fire(target, type, x, y);
      await sleep(80);
      let btn = findReply(list || bubble.parentElement || document.body);
      // 2. Mantener presionado y elegir "Responder" en el menú (Instagram en el celular)
      if (!btn) {
        await longPress(target, x, y);
        for (let i = 0; i < 15 && !btn; i++) {
          const menus = [...document.querySelectorAll('[role="dialog"], [role="menu"], [role="listbox"]')];
          for (const m of [...menus, document.body]) if (!btn) btn = findReply(m);
          if (!btn) await sleep(100);
        }
      }
      if (!btn) { if (CFG.debug) report(bubble); return; }
      // Se toca el elemento mismo, aunque sea un dibujo SVG (que no tiene click()): el evento sube
      // hasta el botón que lo maneja, igual que con un dedo
      const r = btn.getBoundingClientRect();
      const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
      for (const type of ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click']) fire(btn, type, cx, cy);
    };

    // Solo en los APK de prueba: si no apareció "Responder", mandar a la app la estructura del
    // mensaje y las etiquetas que hay a su altura (sin el texto de los mensajes), para arreglarlo
    const report = (bubble) => {
      if (!window.NoScrollBridge || !NoScrollBridge.report) return;
      const box = bubble.getBoundingClientRect();
      let row = bubble;
      for (let i = 0; i < 3 && row.parentElement && row.parentElement !== document.body; i++) row = row.parentElement;
      const clone = row.cloneNode(true);
      const KEEP = /^(role|aria-|tabindex|title|dir|data-)/;
      const walk = (n) => {
        if (n.nodeType === 3) {
          const inButton = n.parentElement && n.parentElement.closest('[role="button"], [role="menuitem"], button');
          if (n.textContent.trim() && !(inButton && n.textContent.trim().length <= 25)) n.textContent = '…';
          return;
        }
        if (n.nodeType !== 1) return;
        for (const a of [...n.attributes]) if (!KEEP.test(a.name)) n.removeAttribute(a.name);
        [...n.childNodes].forEach(walk);
      };
      walk(clone);
      const html = clone.outerHTML.replace(/<path[^>]*>(<\/path>)?/g, '').slice(0, 6000);
      const near = [...document.querySelectorAll('[aria-label], [title]')].filter((e) => {
        const r = e.getBoundingClientRect();
        return r.width > 0 && r.bottom > box.top - 150 && r.top < box.bottom + 150;
      }).map((e) => `${e.tagName.toLowerCase()}[role=${e.getAttribute('role')}] ${e.getAttribute('aria-label') || e.getAttribute('title')}`);
      const menus = [...document.querySelectorAll('[role="dialog"], [role="menu"], [role="listbox"]')]
        .map((m) => (m.innerText || '').slice(0, 300));
      NoScrollBridge.report(`Lúcido swipe-reply\n${location.pathname.replace(/\d+/g, '#')}\n` +
        `cerca: ${near.join(' | ').slice(0, 2000)}\nmenús: ${menus.join(' || ')}\n${html}`);
    };

    // La lista de mensajes con touch-action: pan-y: deslizar de costado no la mueve y el navegador
    // scrollea sin esperar a la página. Tiene que estar antes del gesto: se marca al entrar al chat
    // (lo que hay en el centro de la pantalla) y, por las dudas, al deslizar una burbuja.
    const panY = (el) => { const s = el && scrollerOf(el); if (s) s.style.touchAction = 'pan-y'; };
    const inChat = () => /^\/direct\/t\//.test(location.pathname);
    route(() => {
      if (inChat()) for (const ms of [500, 1500, 4000]) setTimeout(() => {
        if (inChat()) panY(document.elementFromPoint(innerWidth / 2, innerHeight / 2));
      }, ms);
    });

    // Al tocar solo se anota dónde: la burbuja se busca (y se mide) si el dedo va hacia la derecha
    let bubble = null, target = null, arrow = null, sx = 0, sy = 0, dx = 0, decided = false;
    addEventListener('touchstart', (e) => {
      bubble = target = null;
      if (simulating || !inChat() || e.touches.length !== 1) return;
      target = e.target;
      sx = e.touches[0].clientX;
      sy = e.touches[0].clientY;
      dx = 0;
      decided = false;
    }, { capture: true, passive: true });
    addEventListener('touchmove', (e) => {
      if (simulating || !(decided ? bubble : target)) return;
      const mx = e.touches[0].clientX - sx, my = e.touches[0].clientY - sy;
      if (!decided) {
        if (Math.abs(mx) < 10 && Math.abs(my) < 10) return;
        decided = true;
        if (!(mx > 0 && Math.abs(mx) > Math.abs(my) * 1.5)) return; // vertical: scroll normal
        if (!(bubble = bubbleOf(target))) return;
        panY(bubble);
        const r = bubble.getBoundingClientRect();
        arrow = document.createElement('div');
        arrow.textContent = '↩';
        arrow.style.cssText = `position:fixed;left:${Math.max(8, r.left - 30)}px;top:${r.top + r.height / 2 - 14}px;` +
          'font:22px/28px sans-serif;opacity:0;pointer-events:none;z-index:2147483647;color:#888;transition:transform .15s';
        document.body.appendChild(arrow);
      }
      dx = Math.max(0, Math.min(mx, 90));
      bubble.style.transition = 'none';
      bubble.style.transform = `translateX(${dx}px)`;
      arrow.style.opacity = String(Math.min(dx / 60, 1));
      arrow.style.transform = dx >= 60 ? 'scale(1.25)' : ''; // pasó el punto: soltar responde
    }, { capture: true, passive: true });
    const end = (cancel) => () => {
      if (simulating || !bubble || !decided) { bubble = null; return; }
      const b = bubble, t = target;
      bubble = null;
      b.style.transition = 'transform .2s';
      b.style.transform = '';
      if (arrow) { arrow.remove(); arrow = null; }
      if (!cancel && dx >= 60) setTimeout(() => reply(b, t, sx, sy), 220); // cuando volvió a su lugar
    };
    addEventListener('touchend', end(false), { capture: true, passive: true });
    addEventListener('touchcancel', end(true), { capture: true, passive: true });
  }

  // --- 9. Pull-to-refresh: avisarle a la app si el gesto empieza arriba de todo ---
  // Instagram y YouTube scrollean dentro de contenedores, no la ventana,
  // así que la app no puede saberlo sola.
  if (window.NoScrollBridge) {
    addEventListener('touchstart', (e) => {
      if (e.touches.length !== 1) return; // segundo dedo: no es un tirón hacia abajo nuevo
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

  // --- 10. Citas en los chats: tocar la foto o el reel de una respuesta abre el original ---
  // Instagram web no hace nada al tocar la cita. Si el original está cargado en el chat (la misma
  // imagen, más grande y con su botón), se toca ese. Si no está, queda como antes.
  if (IG) {
    const CLICKABLE = 'a[href],button,[role="button"],[role="link"]';
    const fileOf = (m) => ((m.currentSrc || m.src || m.poster || '').split('?')[0].match(/[^/]+$/) || [''])[0];
    addEventListener('click', (e) => {
      if (!e.isTrusted || !/^\/direct\/t\//.test(location.pathname)) return;
      const t = e.target.nodeType === 1 ? e.target : e.target.parentElement;
      const btn = t && t.closest(CLICKABLE);
      if (!btn || btn.hasAttribute('href')) return; // un link (perfil, post) ya lleva a algún lado
      const media = t.matches('img,video') ? [t] : btn.querySelectorAll('img,video');
      if (media.length !== 1) return;
      const m = media[0], file = fileOf(m), w = m.getBoundingClientRect().width;
      const original = file && [...document.querySelectorAll('img,video')].find((o) =>
        o !== m && fileOf(o) === file && o.getBoundingClientRect().width > w * 1.1 && o.closest(CLICKABLE));
      if (!original) return;
      note('citas: toqué el original');
      const r = original.getBoundingClientRect();
      const at = { bubbles: true, cancelable: true, composed: true, view: window, clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 };
      setTimeout(() => { // como un dedo: el sitio puede escuchar cualquiera de estos
        for (const type of ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click']) {
          original.dispatchEvent(type.startsWith('pointer')
            ? new PointerEvent(type, { ...at, pointerId: 9998, pointerType: 'touch', isPrimary: true })
            : new MouseEvent(type, at));
        }
      }, 0);
    }, true);
  }

  // --- 11. Mediciones (solo APK de prueba) ---
  // Tareas largas, cuánto tarda cada cambio de página en dibujarse y cada video en arrancar (de play()
  // a 'playing'). Al salir de la app se copia un resumen al portapapeles, para pegarlo en el chat.
  if (CFG.debug && window.NoScrollBridge && NoScrollBridge.report) {
    // Solo el tipo de pantalla: nada de usuarios ni chats
    const screen = () => {
      const p = location.pathname;
      const m = p.match(/^\/(direct\/\w+|reels?|p|stories|explore|accounts|watch|shorts|results|feed)\b/);
      return m ? '/' + m[1] : p === '/' ? '/' : '/@';
    };

    const visits = [[0, screen()]]; // [desde cuándo, pantalla]: las tareas largas se avisan tarde
    const long = {}; // pantalla → [cantidad, ms en total, ms la más larga]
    try {
      new PerformanceObserver((list) => {
        for (const e of list.getEntries()) {
          const k = visits.filter(([t]) => t <= e.startTime).pop()[1], t = long[k] || (long[k] = [0, 0, 0]);
          t[0]++; t[1] += e.duration; t[2] = Math.max(t[2], e.duration);
        }
      }).observe({ type: 'longtask', buffered: true });
    } catch (e) {}

    // Cambio de página: hasta el último cuadro con cambios, cuando pasa medio segundo sin ninguno
    // (10 s como mucho). Si cambiás de página antes, queda lo que se llegó a medir, con *.
    const navs = [];
    let stop = null;
    const measure = (t0, label) => {
      if (stop) stop(true, '*');
      if (t0) visits.push([t0, screen()]);
      let last = t0, frame = false, quiet = 0;
      const obs = new MutationObserver(() => {
        if (!frame) { frame = true; requestAnimationFrame(() => { frame = false; last = performance.now(); }); }
        clearTimeout(quiet);
        quiet = setTimeout(() => stop(true), 500);
      });
      const max = setTimeout(() => stop(true, '+'), 10000);
      stop = (save, more = '') => {
        obs.disconnect(); clearTimeout(quiet); clearTimeout(max); stop = null;
        if (save) navs.push(`${label} ${Math.round(last - t0)}${more}`);
      };
      obs.observe(document, { childList: true, subtree: true });
      quiet = setTimeout(() => stop(true), 500);
    };
    measure(0, screen() + ' (carga)');
    const push = history.pushState;
    history.pushState = function (...a) { const r = push.apply(this, a); measure(performance.now(), screen()); return r; };
    addEventListener('popstate', () => measure(performance.now(), screen()));

    const asked = new WeakMap(), videos = [];
    addEventListener('play', (e) => { if (e.target instanceof HTMLVideoElement) asked.set(e.target, performance.now()); }, true);
    addEventListener('playing', (e) => {
      const t = asked.get(e.target);
      if (t === undefined) return;
      asked.delete(e.target);
      videos.push(Math.round(performance.now() - t));
    }, true);

    // Si copiaste algo en la página (un link, un texto), salir no lo pisa: se copia la próxima vez
    let copied = false;
    addEventListener('copy', () => { copied = true; }, true);
    const write = navigator.clipboard && navigator.clipboard.writeText;
    if (write) navigator.clipboard.writeText = function (...a) { copied = true; return write.apply(this, a); };

    // Toques sobre fotos o videos en los chats: qué se tocó (la estructura, sin el texto), si el toque
    // llegó a la página, qué pasó en el segundo y medio siguiente y si hay otra igual (el original) con botón
    const taps = [];
    const shape = (u) => {
      try {
        const x = new URL(u, location.href);
        return x.hostname === location.hostname ? x.pathname.replace(/\/(reels?|p|t|stories)\/[^/]+/g, '/$1/#') : x.hostname;
      } catch (e) { return '?'; }
    };
    const desc = (el) => el.tagName.toLowerCase() + ['role', 'aria-label', 'href', 'tabindex'].map((a) => {
      const v = el.getAttribute(a);
      return v === null ? '' : `[${a}=${a === 'href' ? shape(v) : v.slice(0, 30)}]`;
    }).join('');
    const mediaIn = (el) => (el.matches('img,video') ? el : el.querySelector('img,video'));
    const key = (m) => ((m.currentSrc || m.src || m.poster || '').split('?')[0].match(/[^/]+$/) || [''])[0];
    const sanitize = (el) => {
      const c = el.cloneNode(true);
      const walk = (n) => {
        if (n.nodeType === 3) { if (n.textContent.trim()) n.textContent = '…'; return; }
        if (n.nodeType !== 1) return;
        for (const a of [...n.attributes]) {
          if (a.name === 'href') n.setAttribute('href', shape(a.value));
          else if (a.name === 'aria-label') n.setAttribute('aria-label', a.value.slice(0, 30));
          else if (!/^(role|aria-|tabindex|data-)/.test(a.name)) n.removeAttribute(a.name);
        }
        [...n.childNodes].forEach(walk);
      };
      walk(c);
      return c.outerHTML.replace(/<path[^>]*>(<\/path>)?/g, '').slice(0, 1500);
    };
    let lastTap = null;
    addEventListener('click', (e) => {
      if (!/^\/direct\/t\//.test(location.pathname) || !e.isTrusted) return;
      let card = e.target.nodeType === 1 ? e.target : e.target.parentElement;
      for (let i = 0; card && i < 8 && !mediaIn(card); i++) card = card.parentElement; // hasta la foto o el video
      const m = card && mediaIn(card);
      if (!m) return;
      const chain = [];
      for (let el = e.target; el && el !== document.body && chain.length < 12; el = el.parentElement) chain.push(desc(el));
      const k = key(m);
      const twins = k ? [...document.querySelectorAll('img,video')].filter((o) => o !== m && key(o) === k) : [];
      const tap = {
        t: e.timeStamp, event: e, reached: false, prevented: false, after: '', // hora del toque, antes que cualquier listener
        media: `${m.tagName.toLowerCase()} de ${Math.round(m.getBoundingClientRect().width)}px`,
        twins: twins.map((o) => {
          const b = o.closest('a[href],button,[role="button"],[role="link"]');
          return `${b ? desc(b) : 'sin botón'} de ${Math.round(o.getBoundingClientRect().width)}px`;
        }).join(' | ') || 'ninguna',
        chain: chain.join(' < ').slice(0, 700), card: sanitize(card),
      };
      taps.push(tap);
      if (taps.length > 2) taps.shift();
      lastTap = tap;
      const p0 = location.pathname;
      setTimeout(() => {
        const ours = trace.filter(([t]) => t >= tap.t).map(([, w]) => w).join('; ');
        tap.after = `url ${location.pathname === p0 ? 'igual' : 'cambió a ' + shape(location.pathname)}, ` +
          `ventanas abiertas ${document.querySelectorAll('[role="dialog"]').length}, el script: ${ours || 'nada'}`;
      }, 1500);
    }, true);
    addEventListener('click', (e) => { // al final del recorrido: nadie lo frenó en el camino
      if (lastTap && lastTap.event === e) { lastTap.reached = true; lastTap.prevented = e.defaultPrevented; }
    });

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState !== 'hidden') return;
      if (copied) { copied = false; return; }
      if (!navs.length && !videos.length && !Object.keys(long).length) return;
      const sorted = [...videos].sort((a, b) => a - b);
      const chrome = (navigator.userAgent.match(/Chrome\/[\d.]+/) || ['?'])[0];
      NoScrollBridge.report([
        `Lúcido rendimiento · ${location.hostname} · ${chrome} · ${Math.round(performance.now() / 60000)} min`,
        'Tareas largas (cantidad, ms en total, la más larga):',
        ...Object.entries(long).map(([k, [n, sum, max]]) => `  ${k}: ${n}, ${Math.round(sum)}, ${Math.round(max)}`),
        'Cambios de página (ms hasta dibujarse; + = seguía cambiando a los 10 s, * = cambiaste antes):',
        '  ' + navs.slice(-30).join(' · '),
        `Videos, de play() a playing (ms): ${sorted.length ? `mediana ${sorted[sorted.length >> 1]}, máx ${sorted[sorted.length - 1]} (n=${sorted.length})` : 'ninguno'}`,
        '  ' + videos.slice(-30).join(' '),
        ...taps.map((t) => `Toque en foto/video de un chat: ${t.media} · llegó a la página: ${t.reached ? (t.prevented ? 'sí (y alguien lo usó)' : 'sí') : 'no'}` +
          `\n  después: ${t.after || 'sin medir'}\n  otra igual: ${t.twins}\n  cadena: ${t.chain}\n  ${t.card}`),
      ].join('\n'));
    });
  }

  // --- 12. Abrir desde una notificación, sin recargar (Instagram) ---
  // La app llama a __lucidoGo(ruta, chat) si Instagram ya está cargado: se navega dentro del sitio
  // (como el botón atrás) y, si viene el nombre del chat, se toca su fila en la lista. Devuelve true
  // si se encarga; si no, la app carga la URL como antes. Solo toca una fila si hay una sola con ese
  // nombre exacto (si no, queda en la lista: nunca abre otro chat), y si tocás algo, deja de buscar.
  if (IG) {
    const CLICKABLE = 'a[href],button,[role="button"],[role="link"]';
    const norm = (t) => (t || '').normalize('NFC').replace(/\s+/g, ' ').trim().toLowerCase();
    const named = (name) => {
      const out = [];
      if (!document.body) return out;
      const walk = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
      for (let n; (n = walk.nextNode());) if (n.parentElement && norm(n.data) === name) out.push(n.parentElement);
      return out;
    };
    // Las filas de la lista ocupan casi todo el ancho (las notas de arriba, no)
    const rows = (name) => {
      const found = new Set();
      for (const el of named(name)) {
        const row = el.closest(CLICKABLE);
        const r = row && row.getBoundingClientRect();
        if (r && r.width >= innerWidth * 0.6 && r.height > 0 && r.height < innerHeight / 2) found.add(row);
      }
      return [...found];
    };
    // Ya en ese chat: su nombre está en el encabezado
    const inChat = (name) => /^\/direct\/t\//.test(location.pathname) &&
      named(name).some((el) => { const r = el.getBoundingClientRect(); return r.height > 0 && r.bottom < 140; });
    const tap = (el) => {
      const r = el.getBoundingClientRect();
      const at = { bubbles: true, cancelable: true, composed: true, view: window, clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 };
      for (const type of ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click']) {
        el.dispatchEvent(type.startsWith('pointer')
          ? new PointerEvent(type, { ...at, pointerId: 9997, pointerType: 'touch', isPrimary: true })
          : new MouseEvent(type, at));
      }
    };

    let stopLooking = () => {};
    const openChat = (name) => {
      stopLooking();
      let done = false;
      const stop = () => { done = true; removeEventListener('touchstart', stop, true); };
      stopLooking = stop;
      addEventListener('touchstart', stop, { capture: true, passive: true });
      const until = performance.now() + 8000; // la lista tarda en llegar
      const tick = () => {
        if (done) return;
        if (performance.now() > until || !/^\/direct\//.test(location.pathname) || inChat(name)) return stop();
        const found = rows(name);
        if (found.length === 1) { stop(); note('notificación: abrí el chat'); tap(found[0]); return; }
        setTimeout(tick, 250);
      };
      tick();
    };

    window.__lucidoGo = (to, chat) => {
      try {
        const name = chat ? norm(chat) : null;
        if (to) {
          if (!/^\/direct\//.test(to)) return false;
          if (name && inChat(name)) return true;
          if (location.pathname !== to) {
            const before = document.elementFromPoint(innerWidth / 2, innerHeight / 2);
            history.pushState(null, '', to);
            dispatchEvent(new PopStateEvent('popstate', { state: null }));
            // Si en 1 s lo que estaba en pantalla sigue ahí, el sitio no se movió: cargarla (y el chat después)
            setTimeout(() => {
              if (location.pathname !== to || !before || !before.isConnected || !before.getClientRects().length) return;
              stopLooking();
              try { if (name) sessionStorage.setItem('brl_chat', name); } catch (e) {}
              location.assign(to);
            }, 1000);
          }
        }
        if (name) openChat(name);
        return true;
      } catch (e) {
        return false;
      }
    };

    let left = null; // chat que quedó por abrir de la página anterior
    try { left = sessionStorage.getItem('brl_chat'); sessionStorage.removeItem('brl_chat'); } catch (e) {}
    if (left) document.body ? openChat(left) : addEventListener('DOMContentLoaded', () => openChat(left));
  }
})();
