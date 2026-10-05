(() => {
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const post = (url, body) => fetch(url, {
        method: 'POST', body, headers: csrfHeader ? {[csrfHeader]: csrfToken} : {}
    });

    // ---- Spec editor ----
    const specRows = document.getElementById('specRows');
    const tpl = document.getElementById('specRowTpl');
    const addRow = (key = '', value = '') => {
        const row = tpl.content.firstElementChild.cloneNode(true);
        row.querySelector('[name=specKeys]').value = key;
        row.querySelector('[name=specValues]').value = value;
        specRows.appendChild(row);
        return row;
    };
    document.getElementById('addSpec').addEventListener('click', () => addRow().querySelector('input').focus());
    document.getElementById('gpuPreset').addEventListener('click', () => {
        const existing = [...specRows.querySelectorAll('[name=specKeys]')].map(i => i.value.trim());
        ['VRAM', 'Tipi i memories', 'Bus', 'TDP', 'Gjatësia']
            .filter(k => !existing.includes(k)).forEach(k => addRow(k));
    });
    specRows.addEventListener('click', e => {
        if (e.target.closest('.spec-del')) e.target.closest('.spec-row').remove();
    });
    if (window.Sortable) Sortable.create(specRows, {handle: '.drag-handle', animation: 150});

    // ---- Title combobox: pick the card's model from the GPU catalogue and prefill from it ----
    // Typing in the title queries the catalogue (debounced); choosing a row stamps gpuModelId and fills the
    // specs and the short description, but never a field the operator has already filled in. Everything
    // prefilled stays editable and carries a "nga katalogu" marker until it is edited by hand.
    const combobox = document.getElementById('gpuCombobox');
    if (combobox) {
        const title = document.getElementById('title');
        const list = document.getElementById('gpuSuggestions');
        const modelId = document.getElementById('gpuModelId');
        const info = document.getElementById('gpuModelInfo');
        const modelName = document.getElementById('gpuModelName');
        let hits = [], active = -1, timer = null, lastQuery = '';

        const close = () => { list.hidden = true; list.innerHTML = ''; hits = []; active = -1; title.setAttribute('aria-expanded', 'false'); };
        const highlight = (i) => {
            active = i;
            [...list.children].forEach((li, k) => li.classList.toggle('active', k === i));
            list.children[i]?.scrollIntoView({ block: 'nearest' });
        };
        const render = () => {
            list.innerHTML = '';
            hits.forEach((h, i) => {
                const li = document.createElement('li');
                li.className = 'list-group-item';
                li.setAttribute('role', 'option');
                li.innerHTML = '<i class="bi bi-cpu text-body-secondary"></i><span></span><span class="meta"></span>';
                li.children[1].textContent = h.name;
                li.children[2].textContent = [h.vendor, h.vramGb ? h.vramGb + ' GB' : null, h.tier ? 'tier ' + h.tier : null].filter(Boolean).join(' · ');
                li.addEventListener('mousedown', e => e.preventDefault()); // keep the focus in the field
                li.addEventListener('click', () => choose(i));
                list.appendChild(li);
            });
            list.hidden = hits.length === 0;
            title.setAttribute('aria-expanded', String(hits.length > 0));
            highlight(hits.length ? 0 : -1);
        };
        const search = async () => {
            const q = title.value.trim();
            if (q === lastQuery) return;
            lastQuery = q;
            if (q.length < 2) return close();
            try {
                const res = await fetch(combobox.dataset.searchUrl + '?q=' + encodeURIComponent(q), { credentials: 'same-origin', headers: { Accept: 'application/json' } });
                if (!res.ok || q !== title.value.trim()) return;
                hits = await res.json();
                render();
            } catch { close(); }
        };

        const mark = (el) => {
            if (!el.value) return;
            const wrap = el.closest('.spec-row') || el.parentElement;
            if (!wrap.classList.contains('spec-row')) wrap.classList.add('from-catalog-wrap');
            if (!wrap.querySelector('.from-catalog')) {
                const badge = document.createElement('span');
                badge.className = 'badge text-bg-warning from-catalog';
                badge.textContent = 'nga katalogu';
                wrap.appendChild(badge);
            }
            el.dataset.fromCatalog = '1';
            el.addEventListener('input', () => {
                delete el.dataset.fromCatalog;
                wrap.querySelector('.from-catalog')?.remove();
            }, { once: true });
        };
        const setSpec = (key, value) => {
            if (value == null || value === '') return;
            const rows = [...specRows.querySelectorAll('.spec-row')];
            let row = rows.find(r => r.querySelector('[name=specKeys]').value.trim().toLowerCase() === key.toLowerCase());
            if (row) {
                const v = row.querySelector('[name=specValues]');
                if (v.value.trim()) return; // the operator filled it in already
                v.value = value;
                mark(v);
            } else {
                row = addRow(key, value);
                mark(row.querySelector('[name=specValues]'));
            }
        };
        const prefill = (d) => {
            modelId.value = d.id;
            modelName.textContent = d.name;
            info.hidden = false;
            setSpec('VRAM', d.vramGb ? d.vramGb + ' GB' : null);
            setSpec('Tipi i memories', d.memoryType);
            setSpec('TDP', d.tdpWatts ? d.tdpWatts + ' W' : null);
            setSpec('PSU minimale', d.psuMinWatts ? d.psuMinWatts + ' W' : null);
            setSpec('Konektorët', d.pcieConnectors);
            setSpec('Gjatësia', d.lengthMm ? d.lengthMm + ' mm' : null);
            const short = document.getElementById('shortDescription');
            if (short && !short.value.trim() && d.shortDescription) {
                short.value = d.shortDescription;
                mark(short);
            }
        };
        const choose = async (i) => {
            const hit = hits[i];
            close();
            if (!hit) return;
            if (!title.value.trim()) title.value = hit.name;
            lastQuery = title.value.trim();
            try {
                const res = await fetch(combobox.dataset.detailUrl + hit.id, { credentials: 'same-origin', headers: { Accept: 'application/json' } });
                if (res.ok) prefill(await res.json());
            } catch { /* the model is still stamped on the next successful fetch */ }
        };

        title.addEventListener('input', () => {
            clearTimeout(timer);
            timer = setTimeout(search, 250);
        });
        title.addEventListener('keydown', e => {
            if (list.hidden) {
                if (e.key === 'ArrowDown') { search(); }
                return;
            }
            if (e.key === 'ArrowDown') { e.preventDefault(); highlight((active + 1) % hits.length); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); highlight((active - 1 + hits.length) % hits.length); }
            else if (e.key === 'Enter') { e.preventDefault(); choose(active); }
            else if (e.key === 'Escape') { close(); }
        });
        document.addEventListener('click', e => { if (!combobox.contains(e.target)) close(); });
        document.getElementById('gpuModelClear').addEventListener('click', () => {
            modelId.value = '';
            info.hidden = true;
        });
    }

    // ---- Switches that reveal a related field (e.g. "Pranon këmbim" shows the internal trade cap) ----
    document.querySelectorAll('[data-toggles]').forEach(sw => {
        const target = document.querySelector(sw.dataset.toggles);
        const sync = () => { if (target) target.hidden = !sw.checked; };
        sw.addEventListener('change', sync);
        sync();
    });

    // ---- Margin preview ----
    const price = document.getElementById('priceLek'), cost = document.getElementById('costLek');
    const marginInfo = document.getElementById('marginInfo');
    const fmt = n => n.toLocaleString('de-DE');
    const updateMargin = () => {
        const p = +price.value, c = +cost.value;
        if (!p) { marginInfo.textContent = ''; return; }
        const profit = p - c;
        marginInfo.className = 'col-12 small ' + (profit < 0 ? 'text-danger' : 'text-success');
        marginInfo.textContent = `Fitimi: ${fmt(profit)} Lekë (${(profit / p * 100).toFixed(1)}%)`;
    };
    price.addEventListener('input', updateMargin);
    cost.addEventListener('input', updateMargin);
    updateMargin();

    // ---- Images staged on the "new product" page ----
    // The product has no id yet, so the files ride along with the form instead of being uploaded
    // one by one. A DataTransfer holds the selection so photos can be added in several goes and removed
    // before saving.
    const stageZone = document.getElementById('stageZone');
    if (stageZone) {
        const stageInput = document.getElementById('stageInput');
        const stageGrid = document.getElementById('stageGrid');
        const stageEmpty = document.getElementById('stageEmpty');
        const maxBytes = 15 * 1024 * 1024;
        let staged = new DataTransfer();

        const render = () => {
            stageGrid.querySelectorAll('img').forEach(img => URL.revokeObjectURL(img.src));
            stageGrid.innerHTML = '';
            [...staged.files].forEach((file, i) => {
                const tile = document.createElement('div');
                tile.className = 'image-tile';
                tile.dataset.idx = i;
                tile.innerHTML = '<img alt="">'
                    + (i === 0 ? '<span class="badge text-bg-warning primary-badge">Kryesore</span>' : '')
                    + '<button type="button" class="btn btn-sm btn-danger img-del" title="Hiq"><i class="bi bi-trash"></i></button>';
                tile.querySelector('img').src = URL.createObjectURL(file);
                tile.querySelector('.img-del').addEventListener('click', () => {
                    const keep = [...staged.files].filter(f => f !== file);
                    staged = new DataTransfer();
                    keep.forEach(f => staged.items.add(f));
                    stageInput.files = staged.files;
                    render();
                });
                stageGrid.appendChild(tile);
            });
            stageEmpty.hidden = staged.files.length > 0;
        };

        const add = files => {
            const rejected = [];
            [...files].forEach(f => {
                if (!/^image\/(jpeg|png|webp)$/.test(f.type)) rejected.push(f.name + ' — vetëm JPG, PNG ose WebP');
                else if (f.size > maxBytes) rejected.push(f.name + ' — mbi 15 MB');
                else staged.items.add(f);
            });
            stageInput.files = staged.files;
            render();
            if (rejected.length) alert('Këto foto nuk u shtuan:\n' + rejected.join('\n'));
        };

        // change fires after the picker closes; read the files, then hand the input back our full selection.
        stageInput.addEventListener('change', () => {
            const picked = [...stageInput.files].filter(f => ![...staged.files].includes(f));
            add(picked);
        });
        ['dragenter', 'dragover'].forEach(ev => stageZone.addEventListener(ev, e => {
            e.preventDefault();
            stageZone.classList.add('over');
        }));
        ['dragleave', 'drop'].forEach(ev => stageZone.addEventListener(ev, e => {
            e.preventDefault();
            stageZone.classList.remove('over');
        }));
        stageZone.addEventListener('drop', e => add(e.dataTransfer.files));
        if (window.Sortable) {
            Sortable.create(stageGrid, {
                animation: 150,
                onEnd: () => {
                    // dataset.idx is the file's position before the drag; the DOM order after it is the new one.
                    const order = [...stageGrid.querySelectorAll('.image-tile')].map(t => +t.dataset.idx);
                    const files = [...staged.files];
                    staged = new DataTransfer();
                    order.forEach(i => staged.items.add(files[i]));
                    stageInput.files = staged.files;
                    render();
                }
            });
        }
        render();
    }

    // ---- Images on an existing product ----
    const dropzone = document.getElementById('dropzone');
    if (!dropzone) return;
    const fileInput = document.getElementById('fileInput');
    const progress = document.getElementById('uploadProgress');

    const replaceImages = html => {
        document.getElementById('imagesBox').outerHTML = html;
        initGrid();
    };

    const upload = async files => {
        if (!files.length) return;
        const body = new FormData();
        [...files].forEach(f => body.append('files', f));
        progress.classList.remove('d-none');
        try {
            const res = await post(dropzone.dataset.url, body);
            if (!res.ok) throw new Error(res.status === 413 ? 'Fotot janë shumë të mëdha.' : 'Ngarkimi dështoi (' + res.status + ').');
            replaceImages(await res.text());
        } catch (err) {
            alert(err.message);
        } finally {
            progress.classList.add('d-none');
            fileInput.value = '';
        }
    };

    fileInput.addEventListener('change', () => upload(fileInput.files));
    ['dragenter', 'dragover'].forEach(ev => dropzone.addEventListener(ev, e => {
        e.preventDefault();
        dropzone.classList.add('over');
    }));
    ['dragleave', 'drop'].forEach(ev => dropzone.addEventListener(ev, e => {
        e.preventDefault();
        dropzone.classList.remove('over');
    }));
    dropzone.addEventListener('drop', e => upload(e.dataTransfer.files));

    const initGrid = () => {
        const box = document.getElementById('imagesBox');
        const grid = document.getElementById('imageGrid');
        Sortable.create(grid, {
            animation: 150,
            onEnd: () => {
                const body = new URLSearchParams();
                grid.querySelectorAll('.image-tile').forEach(t => body.append('ids', t.dataset.id));
                post(box.dataset.orderUrl, body).then(r => { if (!r.ok) alert('Renditja nuk u ruajt.'); });
            }
        });
        grid.addEventListener('click', async e => {
            const btn = e.target.closest('.img-del');
            if (!btn || !confirm('Të fshihet kjo foto?')) return;
            const res = await post(btn.dataset.url, new URLSearchParams());
            if (res.ok) replaceImages(await res.text());
        });
    };
    initGrid();
})();
