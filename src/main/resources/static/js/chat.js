/* PCMania – the customer assistant widget. Talks to POST /api/chat (server-sent events) and keeps the
   conversation's token in localStorage so it survives page loads. */
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
  const closeBtn = document.getElementById('pmChatClose');
  const whatsapp = panel.dataset.whatsapp;
  const TOKEN_KEY = 'pmChatToken';
  const OPENING = 'Përshëndetje! Më thuaj çfarë PC ke dhe çfarë luan, dhe të gjej kartën që të përshtatet.';

  let token = null;
  try { token = localStorage.getItem(TOKEN_KEY); } catch { /* private mode: the chat still works for this page */ }
  let loaded = false, busy = false, ended = false;

  const saveToken = (t) => {
    token = t;
    try { localStorage.setItem(TOKEN_KEY, t); } catch { /* ignore */ }
  };
  const scrollDown = () => { log.scrollTop = log.scrollHeight; };

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

  const renderCards = (msgEl, items) => {
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
    msgEl.appendChild(wrap);
    scrollDown();
  };

  const waButton = (msgEl, label = 'Vazhdo në WhatsApp') => {
    const a = document.createElement('a');
    a.className = 'btn btn-whatsapp btn-sm mt-2';
    a.href = whatsapp;
    a.target = '_blank';
    a.rel = 'noopener';
    a.innerHTML = '<i class="bi bi-whatsapp me-1"></i>';
    a.append(label);
    msgEl.appendChild(a);
    scrollDown();
  };

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
      const res = await fetch('/api/chat/history' + (token ? '?token=' + encodeURIComponent(token) : ''), {
        credentials: 'same-origin', headers: { Accept: 'application/json' }
      });
      if (!res.ok) throw new Error(res.status);
      const data = await res.json();
      if (data.token) saveToken(data.token);
      if (!data.messages.length) {
        bubble('assistant', OPENING);
        return;
      }
      data.messages.forEach((m) => renderCards(bubble(m.role, m.content), m.products));
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

  // Enter sends, Shift+Enter breaks a line; the box grows with the text.
  const autosize = () => { input.style.height = 'auto'; input.style.height = Math.min(input.scrollHeight, 120) + 'px'; };
  input.addEventListener('input', autosize);
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); form.requestSubmit(); }
  });

  // Parses the text/event-stream body as it arrives: blocks separated by a blank line, "event:" and "data:" lines.
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
    const replyEl = () => {
      if (!reply) reply = bubble('assistant');
      return reply;
    };
    try {
      const res = await fetch('/api/chat', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
        body: JSON.stringify({ token, message: text })
      });
      if (!res.ok && res.status !== 503) throw new Error(res.status);
      let limit = false;
      await readEvents(res, (event, data) => {
        if (event === 'session') saveToken(data.token);
        else if (event === 'status') { typingText.textContent = data.text; typing.hidden = false; scrollDown(); }
        else if (event === 'delta') {
          typing.hidden = true;
          replyEl().querySelector('.pm-chat-text').textContent += data.text;
          scrollDown();
        }
        else if (event === 'products') renderCards(replyEl(), data.items);
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
