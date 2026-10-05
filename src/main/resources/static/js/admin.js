/* PCMania admin – toasts, button loading states and live new-order alerts. */
(() => {
  const stack = () => {
    let el = document.querySelector('.toast-stack');
    if (!el) {
      el = document.createElement('div');
      el.className = 'toast-stack';
      el.setAttribute('aria-live', 'polite');
      document.body.appendChild(el);
    }
    return el;
  };

  const dismiss = (toast) => {
    toast.classList.add('is-leaving');
    toast.addEventListener('animationend', () => toast.remove(), { once: true });
  };

  const wire = (toast) => {
    toast.querySelector('.btn-close')?.addEventListener('click', () => dismiss(toast));
    if (toast.hasAttribute('data-autohide')) setTimeout(() => dismiss(toast), 4000);
  };
  document.querySelectorAll('.pm-toast').forEach(wire);

  window.pmToast = (message, { tone = 'info', href } = {}) => {
    const t = document.createElement(href ? 'a' : 'div');
    t.className = `pm-toast pm-toast-${tone}`;
    if (href) t.href = href;
    t.setAttribute('data-autohide', '');
    const icon = document.createElement('i');
    icon.className = tone === 'error' ? 'bi bi-exclamation-triangle-fill' : tone === 'success' ? 'bi bi-check-circle-fill' : 'bi bi-bell-fill';
    const text = document.createElement('span');
    text.textContent = message;
    t.append(icon, text);
    stack().appendChild(t);
    wire(t);
  };

  // Spinner + disabled state on the button that submitted a form (prevents double submits).
  document.addEventListener('submit', (e) => {
    const form = e.target;
    if (e.defaultPrevented || form.hasAttribute('data-no-loading') || form.method.toLowerCase() === 'get') return;
    const btn = e.submitter || form.querySelector('button[type=submit]');
    if (!btn) return;
    setTimeout(() => {
      btn.disabled = true;
      btn.classList.add('is-loading');
    }, 0);
  });
  window.addEventListener('pageshow', () =>
    document.querySelectorAll('.is-loading').forEach((b) => { b.disabled = false; b.classList.remove('is-loading'); }));

  // Live counters: badge the navbar and announce orders that arrive while a page is open.
  const baseTitle = document.title;
  let lastOrderId = null;
  const setBadge = (selector, countSelector, n) => document.querySelectorAll(selector).forEach((el) => {
    el.hidden = !n;
    el.querySelectorAll(countSelector).forEach((c) => { c.textContent = n; });
  });

  const poll = async () => {
    try {
      const res = await fetch('/admin/live', { headers: { Accept: 'application/json' }, credentials: 'same-origin' });
      if (!res.ok || !res.headers.get('content-type')?.includes('json')) return;
      const data = await res.json();
      setBadge('[data-live-orders]', '[data-live-count]', data.newOrders);
      setBadge('[data-live-builds]', '[data-live-build-count]', data.newBuilds);
      setBadge('[data-live-wishes]', '[data-live-wish-count]', data.newWishes);
      setBadge('[data-live-trades]', '[data-live-trade-count]', data.newTrades);
      setBadge('[data-live-leads]', '[data-live-lead-count]', data.newLeads);
      document.title = data.newOrders ? `(${data.newOrders}) ${baseTitle}` : baseTitle;
      if (lastOrderId !== null && data.latestOrderId > lastOrderId) {
        window.pmToast('Porosi e re! Klikoni për ta hapur.', { tone: 'info', href: '/admin/orders?status=NEW' });
      }
      lastOrderId = data.latestOrderId;
    } catch {
      // Offline for a moment – try again next tick.
    }
  };
  if (document.querySelector('[data-live-orders]')) {
    poll();
    setInterval(() => { if (!document.hidden) poll(); }, 30000);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) poll(); });
  }
})();
