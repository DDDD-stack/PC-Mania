(() => {
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const post = (url, body) => fetch(url, {
        method: 'POST', body, headers: csrfHeader ? {[csrfHeader]: csrfToken} : {}
    });
    const getJson = async url => {
        const res = await fetch(url, { credentials: 'same-origin', headers: { Accept: 'application/json' } });
        if (!res.ok) throw new Error(res.status);
        return res.json();
    };

    const templates = JSON.parse(document.getElementById('fieldTemplates')?.textContent || '{}');
    const categorySelect = document.getElementById('categorySlug');
    const currentTemplate = () => templates[categorySelect?.value] || { fields: [], catalogKey: categorySelect?.value, prebuilt: false };

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
    specRows.addEventListener('click', e => {
        if (e.target.closest('.spec-del')) e.target.closest('.spec-row').remove();
    });
    if (window.Sortable) Sortable.create(specRows, {handle: '.drag-handle', animation: 150});

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
        const clear = () => {
            delete el.dataset.fromCatalog;
            wrap.querySelector('.from-catalog')?.remove();
        };
        el.addEventListener('input', clear, { once: true });
        el.addEventListener('change', clear, { once: true });
    };
    const fillIfEmpty = (el, value) => {
        if (!el || value == null || value === '' || String(el.value).trim()) return false;
        if (el.tagName === 'SELECT' && ![...el.options].some(o => o.value === String(value))) return false;
        el.value = String(value);
        mark(el);
        return true;
    };

    const fieldsBox = document.getElementById('templateFields');
    const templateEmpty = document.getElementById('templateEmpty');
    const remembered = {};
    const fieldInput = key => fieldsBox.querySelector(`.tpl-field[data-key="${CSS.escape(key)}"] [name=specValues]`);
    const readFields = () => {
        const out = {};
        fieldsBox.querySelectorAll('.tpl-field').forEach(f => {
            const v = f.querySelector('[name=specValues]').value;
            if (v !== '') out[f.dataset.key] = v;
        });
        return out;
    };
    const renderField = (f, value) => {
        const col = document.createElement('div');
        col.className = 'col-sm-6 tpl-field';
        col.dataset.key = f.key;
        const label = document.createElement('label');
        label.className = 'form-label small mb-1';
        label.htmlFor = 'tf-' + f.key;
        label.append(f.labelSq);
        if (f.unit) {
            const u = document.createElement('span');
            u.className = 'text-body-secondary';
            u.textContent = ' (' + f.unit + ')';
            label.append(u);
        }
        if (f.required) {
            const r = document.createElement('span');
            r.className = 'text-danger';
            r.textContent = ' *';
            label.append(r);
        }
        const key = document.createElement('input');
        key.type = 'hidden';
        key.name = 'specKeys';
        key.value = f.key;
        let input;
        if (f.type === 'ENUM' || f.type === 'BOOLEAN') {
            input = document.createElement('select');
            input.className = 'form-select form-select-sm';
            const options = f.type === 'ENUM' ? f.options.map(o => [o, o]) : [['true', 'Po'], ['false', 'Jo']];
            [['', '—'], ...options].forEach(([v, t]) => input.add(new Option(t, v)));
            if (value != null && ![...input.options].some(o => o.value === value)) input.add(new Option(value + ' (jo në listë)', value));
        } else {
            input = document.createElement('input');
            input.className = 'form-control form-control-sm';
            input.maxLength = 160;
            input.inputMode = f.type === 'INTEGER' ? 'numeric' : f.type === 'DECIMAL' ? 'decimal' : 'text';
        }
        input.name = 'specValues';
        input.id = 'tf-' + f.key;
        if (value != null) input.value = value;
        col.append(label, key, input);
        return col;
    };
    const prebuiltSection = document.getElementById('prebuiltSection');
    const applyTemplate = () => {
        Object.assign(remembered, readFields());
        const t = currentTemplate();
        fieldsBox.replaceChildren(...t.fields.map(f => renderField(f, remembered[f.key])));
        templateEmpty.hidden = t.fields.length > 0;
        if (prebuiltSection) prebuiltSection.hidden = !t.prebuilt;
        const prebuiltFields = document.getElementById('prebuiltFields');
        if (prebuiltFields) prebuiltFields.disabled = !t.prebuilt;
    };
    categorySelect?.addEventListener('change', applyTemplate);

    const combobox = ({ box, input, list, url, onPick, icon = 'bi-cpu' }) => {
        if (!box || !input || !list) return;
        let hits = [], active = -1, timer = null, lastQuery = '';
        const close = () => { list.hidden = true; list.innerHTML = ''; hits = []; active = -1; input.setAttribute('aria-expanded', 'false'); };
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
                li.innerHTML = '<i class="bi text-body-secondary"></i><span></span><span class="meta"></span>';
                li.children[0].classList.add(icon);
                li.children[1].textContent = h.name;
                li.children[2].textContent = [h.summary, h.verified ? null : 'pa verifikuar'].filter(Boolean).join(' · ');
                li.addEventListener('mousedown', e => e.preventDefault());
                li.addEventListener('click', () => choose(i));
                list.appendChild(li);
            });
            list.hidden = hits.length === 0;
            input.setAttribute('aria-expanded', String(hits.length > 0));
            highlight(hits.length ? 0 : -1);
        };
        const search = async () => {
            const q = input.value.trim();
            if (q === lastQuery) return;
            lastQuery = q;
            const target = url(q);
            if (q.length < 2 || !target) return close();
            try {
                const found = await getJson(target);
                if (q !== input.value.trim()) return;
                hits = found;
                render();
            } catch { close(); }
        };
        const choose = async (i) => {
            const hit = hits[i];
            close();
            if (!hit) return;
            try {
                await onPick(hit, await getJson('/admin/api/spec-catalog/' + hit.id));
            } catch { }
            lastQuery = input.value.trim();
        };
        input.addEventListener('input', () => {
            clearTimeout(timer);
            timer = setTimeout(search, 250);
        });
        input.addEventListener('keydown', e => {
            if (list.hidden) {
                if (e.key === 'ArrowDown') { lastQuery = ''; search(); }
                return;
            }
            if (e.key === 'ArrowDown') { e.preventDefault(); highlight((active + 1) % hits.length); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); highlight((active - 1 + hits.length) % hits.length); }
            else if (e.key === 'Enter') { e.preventDefault(); choose(active); }
            else if (e.key === 'Escape') { close(); }
        });
        document.addEventListener('click', e => { if (!box.contains(e.target)) close(); });
    };
    const searchUrl = (category, q) => '/admin/api/spec-catalog/search?category=' + encodeURIComponent(category) + '&q=' + encodeURIComponent(q);

    const title = document.getElementById('title');
    const modelId = document.getElementById('gpuModelId');
    const info = document.getElementById('gpuModelInfo');
    const modelName = document.getElementById('gpuModelName');
    combobox({
        box: document.getElementById('catalogCombobox'),
        input: title,
        list: document.getElementById('catalogSuggestions'),
        url: q => {
            const t = currentTemplate();
            return t.prebuilt || !t.catalogKey ? null : searchUrl(t.catalogKey, q);
        },
        onPick: (hit, d) => {
            if (!title.value.trim()) title.value = d.name;
            Object.entries(d.values || {}).forEach(([k, v]) => fillIfEmpty(fieldInput(k), v));
            if (d.gpuCatalogId && modelId) {
                modelId.value = d.gpuCatalogId;
                modelName.textContent = d.name;
                info.hidden = false;
            }
            fillIfEmpty(document.getElementById('shortDescription'), d.shortDescription);
        }
    });
    document.getElementById('gpuModelClear')?.addEventListener('click', () => {
        modelId.value = '';
        info.hidden = true;
    });

    const pbGpuSearch = document.getElementById('pbGpuSearch');
    const pbGpuId = document.getElementById('pbGpuId');
    const pbGpuInfo = document.getElementById('pbGpuInfo');
    combobox({
        box: document.getElementById('pbGpuCombobox'),
        input: pbGpuSearch,
        list: document.getElementById('pbGpuSuggestions'),
        url: q => searchUrl('gpu', q),
        onPick: (hit, d) => {
            pbGpuSearch.value = d.name;
            pbGpuId.value = d.id;
            document.getElementById('pbGpuName').textContent = d.name;
            pbGpuInfo.hidden = false;
            mark(pbGpuSearch);
        }
    });
    document.getElementById('pbGpuClear')?.addEventListener('click', () => {
        pbGpuId.value = '';
        pbGpuSearch.value = '';
        pbGpuInfo.hidden = true;
    });

    const cpuName = document.getElementById('prebuilt.cpuName');
    combobox({
        box: document.getElementById('cpuCombobox'),
        input: cpuName,
        list: document.getElementById('cpuSuggestions'),
        url: q => searchUrl('cpu', q),
        onPick: (hit, d) => {
            cpuName.value = d.name;
            mark(cpuName);
            fillIfEmpty(document.getElementById('prebuilt.cpuCores'), d.values?.cores);
            fillIfEmpty(document.getElementById('prebuilt.cpuThreads'), d.values?.threads);
        }
    });

    document.querySelectorAll('[data-toggles]').forEach(sw => {
        const target = document.querySelector(sw.dataset.toggles);
        const sync = () => { if (target) target.hidden = !sw.checked; };
        sw.addEventListener('change', sync);
        sync();
    });

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
