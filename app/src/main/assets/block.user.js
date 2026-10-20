// ==UserScript==
// @name         Lúcido
// @description  Bloquea el feed de Reels/Shorts pero deja ver los que te mandan
// @version      9
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
      if (later) { later.click(); return; }

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
})();
