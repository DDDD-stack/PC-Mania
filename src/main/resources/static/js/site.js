/* PCMania – small progressive enhancements. The site works fully without this file. */
(() => {
  const root = document.documentElement;
  root.classList.add('js');
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // Fade images in once decoded (the shimmer placeholder shows until then).
  const markLoaded = (img) => img.classList.add('is-loaded');
  document.querySelectorAll('img[data-fade]').forEach((img) => {
    if (img.complete && img.naturalWidth) return markLoaded(img);
    img.addEventListener('load', () => markLoaded(img), { once: true });
    img.addEventListener('error', () => markLoaded(img), { once: true });
  });

  // Header gains a shadow once the page scrolls.
  const header = document.querySelector('.site-header');
  const toTop = document.getElementById('toTop');
  const onScroll = () => {
    header?.classList.toggle('is-scrolled', window.scrollY > 8);
    toTop?.classList.toggle('is-visible', window.scrollY > 900);
  };
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();
  toTop?.addEventListener('click', () => window.scrollTo({ top: 0, behavior: reduceMotion ? 'auto' : 'smooth' }));

  // Reveal only what starts below the fold, so the first screen is always fully visible.
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

  // Loading state for forms marked data-loading: disable the button and show a spinner.
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

  // Dim the product grid while a filter or sort change loads the next page.
  const grid = document.querySelector('[data-catalog-grid]');
  if (grid) {
    const busy = () => grid.classList.add('is-busy');
    document.getElementById('filterForm')?.addEventListener('submit', busy);
    document.getElementById('sortSelect')?.addEventListener('change', busy);
    document.querySelectorAll('.pagination a').forEach((a) => a.addEventListener('click', busy));
    window.addEventListener('pageshow', () => grid.classList.remove('is-busy'));
  }

  // Keep the active gallery thumbnail scrolled into view.
  const gallery = document.getElementById('gallery');
  gallery?.addEventListener('slid.bs.carousel', (e) => {
    document.querySelectorAll('.gallery-thumbs button')[e.to]
      ?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', inline: 'center', block: 'nearest' });
  });
})();
