
(() => {
  const root = document.documentElement;
  root.classList.add('js');
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const markLoaded = (img) => img.classList.add('is-loaded');
  document.querySelectorAll('img[data-fade]').forEach((img) => {
    if (img.complete && img.naturalWidth) return markLoaded(img);
    img.addEventListener('load', () => markLoaded(img), { once: true });
    img.addEventListener('error', () => markLoaded(img), { once: true });
  });

  const header = document.querySelector('.site-header');
  const toTop = document.getElementById('toTop');
  const onScroll = () => {
    header?.classList.toggle('is-scrolled', window.scrollY > 8);
    toTop?.classList.toggle('is-visible', window.scrollY > 900);
  };
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();
  toTop?.addEventListener('click', () => window.scrollTo({ top: 0, behavior: reduceMotion ? 'auto' : 'smooth' }));

  if (!reduceMotion && 'IntersectionObserver' in window) {
    const io = new IntersectionObserver((entries) => {
      entries.forEach((e) => {
        if (!e.isIntersecting) return;
        e.target.classList.remove('reveal-pending');
        io.unobserve(e.target);
      });
    }, { rootMargin: '0px 0px -40px 0px' });
    let stagger = 0;
    document.querySelectorAll('.reveal').forEach((el) => {
      if (el.getBoundingClientRect().top < window.innerHeight) return;
      el.style.setProperty('--reveal-delay', `${(stagger++ % 4) * 60}ms`);
      el.classList.add('reveal-pending');
      io.observe(el);
    });
  }

  document.querySelectorAll('form[data-loading]').forEach((form) => {
    form.addEventListener('submit', (e) => {
      if (e.defaultPrevented) return;
      const btn = form.querySelector('button[type=submit]');
      if (!btn) return;
      setTimeout(() => {
        btn.disabled = true;
        btn.classList.add('is-loading');
      }, 0);
    });
  });

  const grid = document.querySelector('[data-catalog-grid]');
  if (grid) {
    const busy = () => grid.classList.add('is-busy');
    document.getElementById('filterForm')?.addEventListener('submit', busy);
    document.getElementById('sortSelect')?.addEventListener('change', busy);
    document.querySelectorAll('.pagination a').forEach((a) => a.addEventListener('click', busy));
    window.addEventListener('pageshow', () => grid.classList.remove('is-busy'));
  }

  const gallery = document.getElementById('gallery');
  gallery?.addEventListener('slid.bs.carousel', (e) => {
    document.querySelectorAll('.gallery-thumbs button')[e.to]
      ?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', inline: 'center', block: 'nearest' });
  });
})();

document.querySelectorAll('[data-card-gallery]').forEach((gallery) => {
  const dots = gallery.parentElement.querySelector('.card-dots');
  if (!dots) return;
  let frame = 0;
  gallery.addEventListener('scroll', () => {
    if (frame) return;
    frame = requestAnimationFrame(() => {
      frame = 0;
      const index = Math.round(gallery.scrollLeft / Math.max(gallery.clientWidth, 1));
      Array.from(dots.children).forEach((dot, i) => dot.classList.toggle('is-active', i === index));
    });
  }, { passive: true });
});

(() => {
  const bar = document.querySelector('[data-promo-bar]');
  bar?.querySelector('[data-promo-close]')?.addEventListener('click', () => {
    bar.hidden = true;
    try { localStorage.setItem('pm-promo-closed', bar.dataset.promoBar); } catch (e) {}
  });

  const texts = document.querySelectorAll('[data-countdown]');
  const boxes = document.querySelectorAll('[data-countdown-boxes]');
  if (!texts.length && !boxes.length) return;
  const pad = (n) => String(n).padStart(2, '0');
  const parts = (ms) => {
    const s = Math.max(0, Math.floor(ms / 1000));
    return { d: Math.floor(s / 86400), h: Math.floor((s % 86400) / 3600), m: Math.floor((s % 3600) / 60), s: s % 60 };
  };
  const tick = () => {
    const now = Date.now();
    texts.forEach((el) => {
      const left = Number(el.dataset.countdown) - now;
      if (left <= 0) {
        el.textContent = el.dataset.countdownDone || '';
        el.closest('[data-promo-bar]')?.setAttribute('hidden', '');
        return;
      }
      const p = parts(left);
      el.textContent = (p.d ? p.d + 'd ' : '') + pad(p.h) + ':' + pad(p.m) + ':' + pad(p.s);
    });
    boxes.forEach((el) => {
      const p = parts(Number(el.dataset.countdownBoxes) - now);
      el.querySelectorAll('[data-unit]').forEach((b) => {
        const next = pad(p[b.dataset.unit]);
        if (b.textContent === next) return;
        b.textContent = next;
        b.classList.remove('tick');
        void b.offsetWidth;
        b.classList.add('tick');
      });
    });
  };
  tick();
  setInterval(tick, 1000);
})();
