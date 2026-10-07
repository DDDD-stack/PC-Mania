/* PCMania – the customer assistant widget. Chat goes to POST /api/chat (server-sent events, conversation
   in an HttpOnly cookie), the guided finder to GET /api/finder/step, and the contact form posts to /api/lead.
   Names and phone numbers only ever go through that form. */
(() => {
  const panel = document.getElementById('pmChat');
  const fab = document.getElementById('pmChatFab');
  if (!panel || !fab) return;

  const log = document.getElementById('pmChatLog');
  const typing = document.getElementById('pmChatTyping');
  const typingText = document.getElementById('pmChatTypingText');
  const form = document.getElementById('pmChatForm');
  const input = document.getElementById('pmChatInput');
  const send = document.getElementById('pmChatSend');
  const cardTpl = document.getElementById('pmChatCardTpl');
  const leadTpl = document.getElementById('pmChatLeadTpl');
  const finderBtn = document.getElementById('pmChatFinderBtn');
  const closeBtn = document.getElementById('pmChatClose');
  const whatsapp = panel.dataset.whatsapp;
  const OPENING = 'Përshëndetje! Më thuaj çfarë PC ke dhe çfarë luan, dhe të gjej kartën që të përshtatet.';

  let loaded = false, busy = false, ended = false;
  const scrollDown = () => { log.scrollTop = log.scrollHeight; };

  // ---- Bubbles, cards, buttons ----
  const bubble = (role, text = '') => {
    const el = document.createElement('div');
    el.className = 'pm-chat-msg pm-chat-' + role;
    const body = document.createElement('div');
    body.className = 'pm-chat-text';
    body.textContent = text;
    el.appendChild(body);
    log.appendChild(el);
    scrollDown();
    return el;
  };
  const renderCards = (host, items) => {
    if (!items || !items.length) return;
    const wrap = document.createElement('div');
    wrap.className = 'pm-chat-cards';
    items.forEach((p) => {
      const a = cardTpl.content.firstElementChild.cloneNode(true);
      a.href = p.url;
      a.querySelector('img').src = p.image;
      a.querySelector('.pm-chat-card-title').textContent = p.title;
      a.querySelector('.pm-chat-card-price').textContent = p.price;
      a.querySelector('.pm-chat-card-cond').textContent = p.condition;
      wrap.appendChild(a);
    });
    host.appendChild(wrap);
    scrollDown();
  };
  const waButton = (host, label = 'Vazhdo në WhatsApp') => {
    const a = document.createElement('a');
    a.className = 'btn btn-whatsapp btn-sm mt-2';
    a.href = whatsapp; a.target = '_blank'; a.rel = 'noopener';
    a.innerHTML = '<i class="bi bi-whatsapp me-1"></i>';
    a.append(label);
    host.appendChild(a);
    scrollDown();
  };

  // ---- The contact form (the only PII path) ----
  const leadForm = (host, { wantedItem = '', budgetLek = '', psuWatts = '', source = 'CHAT' } = {}) => {
    if (host.querySelector('.pm-chat-lead')) return;
    const f = leadTpl.content.firstElementChild.cloneNode(true);
    f.querySelector('[name=wantedItem]').value = wantedItem;
    f.querySelector('[name=budgetLek]').value = budgetLek || '';
    f.querySelector('[name=psuWatts]').value = psuWatts || '';
    f.querySelector('[name=source]').value = source;
    f.querySelector('.pm-chat-lead-wanted').textContent = wantedItem ? 'Kërkesa: ' + wantedItem : '';
    const err = f.querySelector('.pm-chat-lead-error');
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      const btn = f.querySelector('button[type=submit]');
      btn.disabled = true;
      err.hidden = true;
      try {
        const res = await fetch('/api/lead', { method: 'POST', credentials: 'same-origin', body: new URLSearchParams(new FormData(f)) });
        const data = await res.json().catch(() => ({}));
        if (!res.ok || !data.ok) throw new Error(data.error || 'Nuk u dërgua. Provoni përsëri.');
        const done = document.createElement('div');
        done.className = 'pm-chat-lead-done';
        done.innerHTML = '<i class="bi bi-check-circle-fill me-1"></i>';
        done.append(data.message || 'Faleminderit! Do t’ju telefonojmë.');
        f.replaceWith(done);
        scrollDown();
      } catch (ex) {
        err.textContent = ex.message;
        err.hidden = false;
        btn.disabled = false;
      }
    });
    host.appendChild(f);
    scrollDown();
    f.querySelector('[name=name]').focus();
  };

  // ---- The guided finder ----
  const finder = {
    answers: {},
    host: null,
    /** Starts over: the finder button, and "Fillo nga e para". Clears the answers on purpose. */
    start(firstStep) {
      this.answers = {};
      this.host = bubble('assistant', '');
      this.host.querySelector('.pm-chat-text').remove();
      if (firstStep) this.render(firstStep, null); else this.load();
    },
    /**
     * The server offered the finder again on a later reply. If the customer is already part-way
     * through it, carry on from the next unanswered question instead of throwing them back to
     * question 1 - which is what happened on every message while a provider was down.
     */
    resume(firstStep) {
      const partWay = Object.keys(this.answers).length > 0;
      this.host = bubble('assistant', '');
      this.host.querySelector('.pm-chat-text').remove();
      if (partWay) this.load();
      else if (firstStep) this.render(firstStep, null);
      else this.load();
    },
    async load() {
      try {
        const res = await fetch('/api/finder/step?' + new URLSearchParams(this.answers), { credentials: 'same-origin', headers: { Accept: 'application/json' } });
        if (!res.ok) throw new Error(res.status);
        const data = await res.json();
        this.render(data.step || data, data.cards || []);
      } catch {
        waButton(bubble('assistant', 'Kërkimi i shpejtë nuk u hap. Na shkruani në WhatsApp.'));
      }
    },
    render(step, cards) {
      const box = document.createElement('div');
      box.className = 'pm-chat-finder';
      if (!step.done) {
        box.innerHTML = '<div class="pm-chat-finder-q"><span class="pm-chat-finder-n"></span><span class="pm-chat-finder-text"></span></div>';
        box.querySelector('.pm-chat-finder-n').textContent = step.number + '/' + step.total;
        box.querySelector('.pm-chat-finder-text').textContent = step.question;
        if (step.kind === 'range') {
          const wrap = document.createElement('div');
          wrap.className = 'pm-chat-finder-range';
          const out = document.createElement('div');
          out.className = 'pm-chat-finder-value';
          const range = document.createElement('input');
          range.type = 'range'; range.min = step.min; range.max = step.max; range.step = step.stepSize; range.value = step.defaultValue;
          range.className = 'form-range';
          const fmt = (v) => Math.round(v / 1000) + ' mijë Lekë';
          out.textContent = fmt(range.value);
          range.addEventListener('input', () => { out.textContent = fmt(range.value); });
          const go = document.createElement('button');
          go.type = 'button'; go.className = 'btn btn-accent btn-sm'; go.textContent = 'Vazhdo';
          go.addEventListener('click', () => this.answer(step.key, range.value, box));
          wrap.append(out, range, go);
          box.appendChild(wrap);
        } else {
          const opts = document.createElement('div');
          opts.className = 'pm-chat-finder-options';
          step.options.forEach((o) => {
            const b = document.createElement('button');
            b.type = 'button'; b.className = 'pm-chat-option'; b.textContent = o.label;
            b.addEventListener('click', () => this.answer(step.key, o.value, box));
            opts.appendChild(b);
          });
          box.appendChild(opts);
        }
      } else {
        const note = document.createElement('div');
        note.className = 'pm-chat-text';
        note.textContent = step.resultNote;
        box.appendChild(note);
        renderCards(box, cards);
        if (step.showLeadForm) leadForm(box, { wantedItem: step.wantedItem, budgetLek: step.budgetLek, psuWatts: step.psuWatts, source: 'FINDER' });
        const again = document.createElement('button');
        again.type = 'button'; again.className = 'btn btn-link btn-sm p-0 mt-2'; again.textContent = 'Fillo nga e para';
        again.addEventListener('click', () => this.start(null));
        box.appendChild(again);
      }
      this.host.appendChild(box);
      scrollDown();
    },
    answer(key, value, box) {
      this.answers[key] = value;
      box.querySelectorAll('button, input').forEach((el) => { el.disabled = true; });
      box.classList.add('is-answered');
      this.load();
    }
  };
  finderBtn.addEventListener('click', () => { if (!busy) finder.start(null); });

  // ---- Chat ----
  const finish = (limit) => {
    busy = false;
    typing.hidden = true;
    log.setAttribute('aria-busy', 'false');
    if (limit) {
      ended = true;
      input.disabled = true;
      send.disabled = true;
      input.placeholder = 'Biseda vazhdon në WhatsApp';
    } else {
      send.disabled = false;
      input.focus();
    }
  };

  const loadHistory = async () => {
    loaded = true;
    try {
      const res = await fetch('/api/chat/history', { credentials: 'same-origin', headers: { Accept: 'application/json' } });
      if (!res.ok) throw new Error(res.status);
      const data = await res.json();
      if (!data.messages.length) {
        if (data.provider === 'guided') { bubble('assistant', OPENING); finder.start(null); }
        else bubble('assistant', OPENING);
        return;
      }
      data.messages.forEach((m) => {
        if (m.role === 'assistant' && m.content === '[kërkimi i shpejtë]') return;
        renderCards(bubble(m.role, m.content), m.products);
      });
      if (data.limitReached) {
        waButton(bubble('assistant', 'Kemi biseduar gjatë dhe asistenti ndalet këtu. Vazhdoni me një person në WhatsApp.'));
        finish(true);
      }
    } catch {
      bubble('assistant', OPENING);
    }
  };

  const open = () => {
    panel.hidden = false;
    fab.setAttribute('aria-expanded', 'true');
    document.body.classList.add('pm-chat-open');
    if (!loaded) loadHistory();
    setTimeout(() => input.focus(), 50);
  };
  const close = () => {
    panel.hidden = true;
    fab.setAttribute('aria-expanded', 'false');
    document.body.classList.remove('pm-chat-open');
    fab.focus();
  };
  fab.addEventListener('click', () => (panel.hidden ? open() : close()));
  closeBtn.addEventListener('click', close);
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !panel.hidden) close(); });

  const autosize = () => { input.style.height = 'auto'; input.style.height = Math.min(input.scrollHeight, 120) + 'px'; };
  input.addEventListener('input', autosize);
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); form.requestSubmit(); }
  });

  // text/event-stream, read as it arrives: blocks separated by a blank line, "event:" and "data:" lines.
  const readEvents = async (res, onEvent) => {
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buf = '';
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });
      let idx;
      while ((idx = buf.indexOf('\n\n')) >= 0) {
        const block = buf.slice(0, idx);
        buf = buf.slice(idx + 2);
        let event = 'message', data = '';
        block.split('\n').forEach((line) => {
          if (line.startsWith('event:')) event = line.slice(6).trim();
          else if (line.startsWith('data:')) data += line.slice(5).trim();
        });
        if (!data) continue;
        try { onEvent(event, JSON.parse(data)); } catch { /* a partial line; the next block completes it */ }
      }
    }
  };

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const text = input.value.trim();
    if (!text || busy || ended) return;
    busy = true;
    send.disabled = true;
    bubble('user', text);
    input.value = '';
    autosize();
    typingText.textContent = 'Po shkruan…';
    typing.hidden = false;
    log.setAttribute('aria-busy', 'true');
    scrollDown();

    let reply = null;
    const replyEl = () => { if (!reply) reply = bubble('assistant'); return reply; };
    try {
      const res = await fetch('/api/chat', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
        body: JSON.stringify({ message: text })
      });
      if (!res.ok) throw new Error(res.status);
      let limit = false;
      await readEvents(res, (event, data) => {
        if (event === 'status') { typingText.textContent = data.text; typing.hidden = false; scrollDown(); }
        else if (event === 'delta') { typing.hidden = true; replyEl().querySelector('.pm-chat-text').textContent += data.text; scrollDown(); }
        else if (event === 'notice') { typing.hidden = true; bubble('assistant', data.text).classList.add('pm-chat-notice'); }
        else if (event === 'products') renderCards(replyEl(), data.items);
        else if (event === 'action' && data.action === 'show_lead_form') leadForm(replyEl(), { wantedItem: data.wantedItem, budgetLek: data.budgetLek, psuWatts: data.psuWatts, source: 'CHAT' });
        else if (event === 'finder') { typing.hidden = true; finder.resume(data); }
        else if (event === 'limit') { waButton(bubble('assistant', data.text)); limit = true; }
        else if (event === 'error') waButton(bubble('assistant', data.message));
      });
      finish(limit);
    } catch {
      waButton(bubble('assistant', 'Nuk arrita të lidhem. Provoni përsëri ose na shkruani në WhatsApp.'));
      finish(false);
    }
  });
})();
