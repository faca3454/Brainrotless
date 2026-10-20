// ==UserScript==
// @name         Brainrotless
// @description  Bloquea el feed de Reels/Shorts pero deja ver los que te mandan
// @version      5
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
    { igFeed: 'normal', igBlockExplore: true, igTapToPlay: false, igReels: false, restricted: false, restrictedMsg: '' },
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
  // igReels: la app deja ver Reels mientras no se llegue al límite diario (ni sea de noche)
  const hidden = [!CFG.igReels && 'a[href="/reels/"]', CFG.igBlockExplore && 'a[href="/explore/"]'].filter(Boolean);
  const CSS = IG
    ? (hidden.length ? `${hidden.join(', ')} { display:none !important; }` : '') +
      (CFG.restricted ? 'a[href="/"], video { display:none !important; }' : '')
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
  if (IG && !CFG.igReels) {
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
  let prevFollowing = false; // veníamos del feed de seguidos
  const check = (nav) => {
    const p = location.pathname;

    if (YT) {
      // Short -> reproductor clásico: se ve igual, pero sin swipe al siguiente
      const m = p.match(/^\/shorts\/([\w-]{11})/);
      if (m) location.replace('/watch?v=' + m[1] + location.search.replace(/^\?/, '&'));
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
  // Tocar "Inicio" va directo al feed de seguidos, sin mostrar el otro un instante
  if (IG && CFG.igFeed === 'following') {
    addEventListener('click', (e) => {
      if (!(e.target.closest && e.target.closest('a[href="/"]'))) return;
      e.preventDefault();
      e.stopPropagation();
      location.assign('/?variant=following');
    }, true);
  }
  addEventListener('popstate', () => check(true));
  setInterval(check, 400); // respaldo por si la SPA cambia la URL por otra vía
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
  if (IG) {
    const APP = /^(abrir|usar|usá|usa|descargar|obtener|ver|cambiar a|open|use|get|download|view|switch to)( en| in)?( la| the)? (app|aplicación|instagram)$/i;
    const LATER = /^(ahora no|no, gracias|cancelar|cerrar|(seguir|continuar) en (el navegador|la web)|not now|no thanks|cancel|close|continue (in|on) (the )?(browser|web))$/i;
    const text = (el) => (el.textContent || '').replace(/\s+/g, ' ').trim();
    const CLICKABLE = 'a,button,[role="button"]';

    const sweep = () => {
      for (const el of document.querySelectorAll(CLICKABLE)) {
        if (!APP.test(text(el))) continue;
        const dialog = el.closest('[role="dialog"]');
        const later = dialog && [...dialog.querySelectorAll(CLICKABLE)].find((b) => LATER.test(text(b)));
        if (later) { later.click(); continue; }

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
      }
    };
    let pending = false;
    new MutationObserver(() => {
      if (pending) return;
      pending = true;
      setTimeout(() => { pending = false; sweep(); }, 300);
    }).observe(document, { childList: true, subtree: true }); // document: el <html> puede no existir todavía
  }

  // --- 7. Pull-to-refresh: avisarle a la app si el gesto empieza arriba de todo ---
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
