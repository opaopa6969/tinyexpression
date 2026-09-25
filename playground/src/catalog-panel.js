// Catalog panel (issue #201, stage 4): browse / search / edit the language catalog, validate it
// against catalog/tinyexpression-catalog.schema.json in the browser, export it (full JSON, the
// override the VSIX reads, a unified diff, a JSON Patch) and open a PR. Every edit is applied
// to the playground at once (completion / hover / diagnostics read the edited catalog).
import { ja, en, derivedFiles, structuralProblems } from '../../catalog/scripts/catalog-lib.mjs';
import {
  SECTIONS, clone, overrideOf, removedOf, changeSummary, formatOverride, jsonPatch, unifiedDiff, exportCatalog,
} from '../../catalog/scripts/catalog-edit.mjs';
import { validate as validateSchema } from './generated/catalog-validator.js';
import { createCatalogPullRequest, compareUrlOf, editUrlOf } from './github-pr.js';
import { saveFile, copyText, openExternal, inVsCode } from './host.js';

const LIST_LIMIT = 150;

const firstLine = (text) => String(text ?? '').split('\n')[0];

/** How each section is listed and edited. */
const VIEWS = {
  variables: {
    label: '変数', texts: ['description'], lists: ['examples'],
    title: (e) => `$${e.name}${e.match === 'prefixWithSuffix' ? `${e.separator}…` : ''}`,
    sub: (e) => `${e.type ?? ''} [${e.group}] ${ja(e.description)}`,
    template: (c) => ({ name: 'newVariable', match: 'exact', type: 'float', description: { ja: '', en: '' }, context: c.variableGroups[0]?.context ?? '', group: c.variableGroups[0]?.id ?? '' }),
  },
  functions: {
    label: '関数', texts: ['description'], lists: ['examples', 'reads'],
    title: (e) => e.name, sub: (e) => `${e.signature}  ${ja(e.description)}`,
    template: () => ({ name: 'newFunction', kind: 'function', signature: 'newFunction(x: number): number', returns: 'number', snippet: 'newFunction($1)$0', description: { ja: '', en: '' }, examples: [] }),
  },
  keywords: {
    label: 'キーワード', texts: ['description'],
    title: (e) => e.name, sub: (e) => ja(e.description),
    template: () => ({ name: 'newKeyword', description: { ja: '', en: '' } }),
  },
  values: {
    label: '値', texts: ['description'],
    title: (e) => e.name, sub: (e) => `${e.type} = ${e.value}  ${ja(e.description)}`,
    template: () => ({ name: 'NEW_VALUE', type: 'DayOfWeek', value: 0, description: { ja: '', en: '' } }),
  },
  errorCodes: {
    label: 'エラーコード', texts: ['message', 'fix'], variants: true,
    title: (e) => e.code, sub: (e) => ja(e.message),
    template: (c) => {
      const used = new Set(c.errorCodes.map((e) => e.code));
      let n = 1;
      while (used.has(`TE${String(n).padStart(3, '0')}`)) n++;
      return { code: `TE${String(n).padStart(3, '0')}`, severity: 'error', message: { ja: '', en: '' }, fix: { ja: '', en: '' } };
    },
  },
  runtimeErrors: {
    label: '実行時エラー', texts: ['description', 'fix'],
    title: (e) => e.kind, sub: (e) => ja(e.description),
    template: () => ({ kind: 'NewException', description: { ja: '', en: '' } }),
  },
  settings: {
    label: '設定', texts: ['description', 'note'], lists: ['values'],
    title: (e) => `${e.scope}/${e.name}`, sub: (e) => ja(e.description),
    template: () => ({ name: 'newSetting', scope: 'formulaInfo', type: 'string', description: { ja: '', en: '' } }),
  },
  externals: {
    label: 'external', texts: ['description'],
    title: (e) => `${e.class}#${e.method}`, sub: (e) => ja(e.description),
    template: () => ({ class: 'sample.NewClass', method: 'call', params: [], returns: 'float', description: { ja: '', en: '' } }),
  },
  variableGroups: {
    label: '変数グループ', texts: ['description'],
    title: (e) => e.id, sub: (e) => `${e.context ?? ''} ${ja(e.description)}`,
    template: () => ({ id: 'new-group', context: 'TEST', description: { ja: '', en: '' } }),
  },
};

const TEXT_LABELS = { description: '説明', message: 'メッセージ', fix: '修正のヒント', note: '注記' };
const LIST_LABELS = { examples: '例（1 行に 1 つ）', reads: '読む変数（1 行に 1 つ）', values: '値の候補（1 行に 1 つ）' };

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === 'class') node.className = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else if (key in node && key !== 'list') node[key] = value;
    else node.setAttribute(key, value);
  }
  for (const child of children.flat()) {
    if (child != null) node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

/** Schema errors (ajv, compiled at build time) plus the structural rules of generate-derived. */
export function validateCatalog(catalog) {
  const ok = validateSchema(catalog);
  const schemaErrors = ok ? [] : (validateSchema.errors ?? []).map((e) => `${e.instancePath || '/'} ${e.message}${e.params?.allowedValues ? ` (${e.params.allowedValues.join(', ')})` : ''}`);
  const problems = structuralProblems(catalog);
  return { ok: schemaErrors.length === 0 && problems.length === 0, schemaErrors, problems };
}

function stamp() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}`;
}

/**
 * @param root the panel's container
 * @param bundled the repository catalog (the base of diffs, exports and PRs)
 * @param edited the catalog to start from (bundled + saved / host overrides)
 * @param onChange (edited) => void, called after every edit
 */
export function createCatalogPanel(root, { bundled, edited: initial, onChange }) {
  let edited = clone(initial);
  let section = 'functions';
  let query = '';
  let selected = null; // index into edited[section]
  let changeTimer = null;
  let token = ''; // GitHub token: memory only

  const summaryBox = el('div', { class: 'catalog-summary small' });
  const validationBox = el('div', { class: 'catalog-validation small', 'aria-live': 'polite' });
  const list = el('ul', { class: 'catalog-list', role: 'listbox', 'aria-label': 'catalog entries' });
  const editor = el('div', { class: 'catalog-editor' });
  const output = el('div', { class: 'catalog-output' });
  const sectionSelect = el('select', { title: 'catalog section', onchange: (e) => { section = e.target.value; selected = null; renderList(); renderEditor(); } });
  const search = el('input', { type: 'search', placeholder: '検索（名前・説明）', 'aria-label': '検索', oninput: (e) => { query = e.target.value.trim().toLowerCase(); renderList(); } });

  const keyOf = (entry) => SECTIONS[section](entry);
  const bundledByKey = () => new Map((bundled[section] ?? []).map((e) => [SECTIONS[section](e), e]));

  function changed() {
    renderSummary();
    clearTimeout(changeTimer);
    changeTimer = setTimeout(() => {
      renderValidation();
      renderList();
      onChange(edited);
    }, 250);
  }

  function renderSections() {
    sectionSelect.replaceChildren(...Object.entries(VIEWS).map(([key, view]) =>
      el('option', { value: key, selected: key === section }, `${view.label}（${edited[key]?.length ?? 0}）`)));
  }

  function renderSummary() {
    const summary = changeSummary(bundled, edited);
    const removed = removedOf(bundled, edited);
    summaryBox.replaceChildren(summary.length === 0 && removed.length === 0
      ? el('span', { class: 'muted' }, 'リポジトリのカタログから変更なし')
      : el('span', {}, '変更: ', summary.map((s) => `${VIEWS[s.section]?.label ?? s.section} ${s.changed ? `更新 ${s.changed}` : ''}${s.added ? ` 追加 ${s.added}` : ''}`).join(' / '),
        removed.length ? el('span', { class: 'trace-err' }, ` · 名前を変えた / 消えた項目 ${removed.length}（override では表現できないので全体 JSON か PR で）`) : null));
    renderSections();
  }

  function renderValidation() {
    const result = validateCatalog(edited);
    validationBox.replaceChildren(result.ok
      ? el('span', { class: 'ok-text' }, '✓ スキーマ検証 OK（tinyexpression-catalog.schema.json）・整合性 OK')
      : el('div', { class: 'result-error' },
        el('div', { class: 'error-head' }, `検証エラー ${result.schemaErrors.length + result.problems.length} 件`),
        el('ul', {}, [...result.schemaErrors, ...result.problems].slice(0, 12).map((m) => el('li', {}, m)))));
    return result;
  }

  function matches(entry) {
    if (!query) return true;
    const view = VIEWS[section];
    const hay = `${view.title(entry)} ${view.sub(entry)} ${en(entry.description ?? entry.message)}`.toLowerCase();
    return hay.includes(query);
  }

  function renderList() {
    const entries = edited[section] ?? [];
    const base = bundledByKey();
    const view = VIEWS[section];
    const items = [];
    let total = 0;
    entries.forEach((entry, index) => {
      if (!matches(entry)) return;
      total++;
      if (items.length >= LIST_LIMIT) return;
      const old = base.get(keyOf(entry));
      const mark = old === undefined ? '✚' : JSON.stringify(old) === JSON.stringify(entry) ? '' : '●';
      items.push(el('li', {
        class: `catalog-item${index === selected ? ' selected' : ''}`, role: 'option', tabIndex: 0,
        onclick: () => { selected = index; renderList(); renderEditor(); },
        onkeydown: (e) => { if (e.key === 'Enter') { selected = index; renderList(); renderEditor(); } },
      },
      el('span', { class: 'catalog-mark', title: mark === '✚' ? 'added' : mark ? 'edited' : '' }, mark),
      el('code', {}, view.title(entry)),
      el('span', { class: 'catalog-sub' }, firstLine(view.sub(entry)))));
    });
    if (total > items.length) items.push(el('li', { class: 'muted small' }, `他 ${total - items.length} 件（検索で絞り込み）`));
    if (total === 0) items.push(el('li', { class: 'muted small' }, '該当なし'));
    list.replaceChildren(...items);
  }

  function localizedField(entry, field) {
    const value = entry[field];
    const set = (lang, text) => {
      const current = entry[field];
      const next = typeof current === 'object' && current ? { ...current } : (current ? { ja: current } : {});
      if (text === '') delete next[lang];
      else next[lang] = text;
      if (Object.keys(next).length === 0) delete entry[field];
      else entry[field] = next;
      changed();
    };
    return el('fieldset', { class: 'catalog-field' },
      el('legend', {}, TEXT_LABELS[field] ?? field),
      el('label', {}, 'ja', el('textarea', { rows: 2, value: typeof value === 'string' ? value : value?.ja ?? '', oninput: (e) => set('ja', e.target.value) })),
      el('label', {}, 'en', el('textarea', { rows: 2, value: typeof value === 'object' ? value?.en ?? '' : '', oninput: (e) => set('en', e.target.value) })));
  }

  function listField(entry, field) {
    return el('label', { class: 'catalog-field' }, LIST_LABELS[field] ?? field,
      el('textarea', {
        rows: 2, value: (entry[field] ?? []).join('\n'),
        oninput: (e) => {
          const lines = e.target.value.split('\n').map((l) => l.trim()).filter(Boolean);
          if (lines.length) entry[field] = lines;
          else delete entry[field];
          changed();
        },
      }));
  }

  function variantsField(entry) {
    const box = el('fieldset', { class: 'catalog-field' }, el('legend', {}, `variants（旧文言・別文言 ${entry.variants?.length ?? 0} 件）`));
    (entry.variants ?? []).forEach((variant, i) => {
      box.append(el('div', { class: 'catalog-variant' },
        el('div', { class: 'muted small' }, `#${i + 1} ${variant.source ?? ''}`),
        localizedField(variant, 'message'), localizedField(variant, 'fix'),
        el('button', { class: 'link', onclick: () => { entry.variants.splice(i, 1); if (!entry.variants.length) delete entry.variants; changed(); renderEditor(); } }, 'この variant を削除')));
    });
    box.append(el('button', {
      onclick: () => {
        entry.variants = [...(entry.variants ?? []), { source: 'playground', message: { ja: '' }, fix: { ja: '' } }];
        changed();
        renderEditor();
      },
    }, 'variant を追加'));
    return box;
  }

  function renderEditor() {
    editor.replaceChildren();
    const entries = edited[section] ?? [];
    const entry = entries[selected];
    if (entry == null) {
      editor.append(el('p', { class: 'muted small' }, '左の一覧から項目を選ぶと編集できます。「追加」で新しい項目を作ります。'));
      return;
    }
    const view = VIEWS[section];
    const base = bundledByKey().get(keyOf(entry));
    const raw = el('textarea', { class: 'catalog-raw', rows: 8, spellcheck: false, value: JSON.stringify(entry, null, 2), 'aria-label': 'entry JSON' });
    const rawError = el('div', { class: 'trace-err small' });
    editor.append(...[
      el('h3', {}, el('code', {}, view.title(entry)), base === undefined ? el('span', { class: 'badge-soft' }, ' 追加') : null),
      ...(view.texts ?? []).map((f) => localizedField(entry, f)),
      ...(view.lists ?? []).map((f) => listField(entry, f)),
      view.variants ? variantsField(entry) : null,
      el('details', { class: 'catalog-field' },
        el('summary', {}, 'JSON で編集（名前・型・snippet などすべての項目）'),
        raw, rawError,
        el('button', {
          onclick: () => {
            try {
              const value = JSON.parse(raw.value);
              if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('オブジェクトではありません');
              entries[selected] = value;
              rawError.textContent = '';
              changed();
              renderList();
              renderEditor();
            } catch (error) {
              rawError.textContent = `JSON を読めません: ${error.message}`;
            }
          },
        }, 'JSON を適用')),
      el('div', { class: 'buttons' },
        base !== undefined && JSON.stringify(base) !== JSON.stringify(entry)
          ? el('button', { onclick: () => { entries[selected] = clone(base); changed(); renderList(); renderEditor(); } }, 'この項目を元に戻す') : null,
        base === undefined
          ? el('button', { onclick: () => { entries.splice(selected, 1); selected = null; changed(); renderList(); renderEditor(); } }, 'この追加を取り消す') : null),
    ].filter(Boolean));
  }

  function showOutput(title, text, actions = []) {
    output.replaceChildren(
      el('div', { class: 'catalog-output-head' }, el('strong', {}, title), ...actions,
        el('button', { class: 'link', onclick: () => output.replaceChildren() }, '閉じる')),
      el('pre', { class: 'catalog-pre' }, text.length > 200_000 ? `${text.slice(0, 200_000)}\n…（省略）` : text));
  }

  const overrideText = () => formatOverride(overrideOf(bundled, edited));
  const fullText = () => exportCatalog(edited);
  const diffText = () => unifiedDiff(exportCatalog(bundled), fullText());

  function changedFiles() {
    const before = derivedFiles(bundled);
    const after = derivedFiles(edited);
    return new Map([...after].filter(([path, text]) => before.get(path) !== text));
  }

  function prForm() {
    const owner = el('input', { value: 'opaopa6969', 'aria-label': 'owner' });
    const repo = el('input', { value: 'tinyexpression', 'aria-label': 'repository' });
    const base = el('input', { value: 'master', 'aria-label': 'base branch' });
    const branch = el('input', { value: `catalog/playground-${stamp()}`, 'aria-label': 'branch' });
    const summary = changeSummary(bundled, edited);
    const count = summary.reduce((n, s) => n + s.changed + s.added, 0);
    const title = el('input', { value: `catalog: playground edits (${count} entries)`, 'aria-label': 'title' });
    const tokenInput = el('input', {
      type: 'password', autocomplete: 'off', placeholder: 'GitHub token（contents / pull requests の書き込み）', value: token,
      oninput: (e) => { token = e.target.value; },
    });
    const log = el('div', { class: 'small', 'aria-live': 'polite' });
    const bodyText = () => [
      'Catalog edits made in the tinyexpression playground (issue #201).',
      '',
      ...summary.map((s) => `- ${s.section}: ${s.changed} changed, ${s.added} added`),
      '',
      'Files: the catalog in its canonical format and the derived files (`node catalog/scripts/generate-derived.mjs` output).',
    ].join('\n');
    const create = async () => {
      const result = validateCatalog(edited);
      if (!result.ok) { log.textContent = '検証エラーがあるので PR を作れません。'; return; }
      const files = changedFiles();
      if (!files.size) { log.textContent = '変更がありません。'; return; }
      if (!token) { log.textContent = 'token を入れてください（このページのメモリにだけ保持し、保存しません）。'; return; }
      log.replaceChildren('作成中…');
      try {
        const done = await createCatalogPullRequest({
          token, owner: owner.value.trim(), repo: repo.value.trim(), base: base.value.trim(), branch: branch.value.trim(),
          title: title.value.trim(), body: bodyText(), files,
          onProgress: (m) => { log.textContent = `${m}…`; },
        });
        const url = done.pullRequest?.html_url ?? done.compareUrl;
        log.replaceChildren(done.pullRequest ? `PR #${done.pullRequest.number} を作成しました: ` : `ブランチ ${done.branch} を作成しました（PR は作れませんでした: ${done.error}）: `,
          el('a', { href: url, target: '_blank', rel: 'noopener', onclick: (e) => { if (inVsCode) { e.preventDefault(); openExternal(url); } } }, url));
      } catch (error) {
        log.textContent = `失敗: ${error.message}（権限が無いリポジトリは fork してから owner を変えてください）`;
      }
    };
    const manual = async () => {
      const copied = await copyText(fullText());
      openExternal(editUrlOf({ owner: owner.value.trim(), repo: repo.value.trim(), base: base.value.trim(), path: 'catalog/tinyexpression-catalog.json' }));
      log.textContent = `${copied ? '全体 JSON をクリップボードにコピーしました。' : 'コピーできませんでした（「全体 JSON」で書き出してください）。'}GitHub の編集画面に貼り付けて「Propose changes」→ PR。派生ファイル（.tecatalog / error-catalog.json）は PR 上で CI が検査するので、手元で node catalog/scripts/generate-derived.mjs を実行して追加してください。`;
    };
    return el('div', { class: 'catalog-pr' },
      el('p', { class: 'small muted' }, `変更したファイル: ${[...changedFiles().keys()].join(', ') || 'なし'}。token はこのページのメモリにだけ置き、保存・送信先は api.github.com だけです。`),
      el('div', { class: 'settings' },
        el('label', {}, 'owner', owner), el('label', {}, 'repository', repo), el('label', {}, 'base', base),
        el('label', {}, 'branch', branch), el('label', { class: 'wide' }, 'title', title), el('label', { class: 'wide' }, 'token', tokenInput)),
      el('div', { class: 'buttons' },
        el('button', { onclick: create, title: 'commit to a new branch and open a pull request (GitHub API)' }, 'PR を作成（API）'),
        el('button', {
          onclick: () => openExternal(compareUrlOf({ owner: owner.value.trim(), repo: repo.value.trim(), base: base.value.trim(), branch: branch.value.trim(), title: title.value.trim(), body: bodyText() })),
          title: 'open the prefilled compare page (the branch must exist)',
        }, 'compare ページを開く'),
        el('button', { onclick: manual, title: 'no token: copy the catalog and open the GitHub web editor' }, 'token 無しで（Web エディタ）')),
      log);
  }

  const toolbar = el('div', { class: 'catalog-toolbar' },
    el('label', {}, '節 ', sectionSelect),
    search,
    el('button', {
      title: 'add a new entry to this section',
      onclick: () => {
        const entry = VIEWS[section].template(edited);
        edited[section] = [...(edited[section] ?? []), entry];
        selected = edited[section].length - 1;
        query = '';
        search.value = '';
        changed();
        renderList();
        renderEditor();
      },
    }, '追加'));

  const exportBar = el('div', { class: 'buttons' },
    el('button', { title: 'validate against the JSON schema', onclick: () => { const r = renderValidation(); if (r.ok) showOutput('検証', 'スキーマ・整合性とも OK'); } }, '検証'),
    el('button', {
      title: 'the full catalog in its canonical format (catalog/tinyexpression-catalog.json)',
      onclick: () => { const text = fullText(); saveFile('tinyexpression-catalog.json', text, { kind: 'full' }); },
    }, inVsCode ? '全体 JSON をワークスペースへ' : '全体 JSON を書き出す'),
    el('button', {
      title: 'only the edited / added entries: the file tinyExpressionP4Lsp.catalog.overridePath reads',
      onclick: () => {
        const text = overrideText();
        showOutput('override JSON（VSIX の catalog.overridePath 用）', text, [el('button', { class: 'link', onclick: () => saveFile('tinyexpression-catalog.override.json', text, { kind: 'override' }) }, inVsCode ? 'ワークスペースへ保存して適用' : 'ダウンロード')]);
      },
    }, 'override JSON'),
    el('button', {
      title: 'unified diff against the repository catalog',
      onclick: () => {
        const text = diffText() || '（差分なし）\n';
        showOutput('unified diff', text, [el('button', { class: 'link', onclick: () => saveFile('tinyexpression-catalog.diff', text, { kind: 'file' }) }, 'ダウンロード'), el('button', { class: 'link', onclick: () => copyText(text) }, 'コピー')]);
      },
    }, 'diff'),
    el('button', {
      title: 'RFC 6902 JSON Patch against the repository catalog',
      onclick: () => {
        const text = `${JSON.stringify(jsonPatch(bundled, edited), null, 2)}\n`;
        showOutput('JSON Patch (RFC 6902)', text, [el('button', { class: 'link', onclick: () => saveFile('tinyexpression-catalog.patch.json', text, { kind: 'file' }) }, 'ダウンロード')]);
      },
    }, 'JSON Patch'),
    el('button', { title: 'open a pull request with these edits', onclick: () => output.replaceChildren(el('div', { class: 'catalog-output-head' }, el('strong', {}, 'PR を作成'), el('button', { class: 'link', onclick: () => output.replaceChildren() }, '閉じる')), prForm()) }, 'PR を作成…'),
    el('button', {
      class: 'link', title: 'discard every edit',
      onclick: () => {
        if (!window.confirm('カタログの変更をすべて破棄しますか？')) return;
        edited = clone(bundled);
        selected = null;
        changed();
        renderList();
        renderEditor();
      },
    }, '変更を破棄'));

  root.replaceChildren(
    toolbar, summaryBox, validationBox,
    el('div', { class: 'catalog-body' }, list, editor),
    exportBar, output);
  renderSummary();
  renderValidation();
  renderList();
  renderEditor();

  return {
    catalog: () => edited,
    /** Replaces the edited catalog (the webview host sends the active override). */
    load(next) {
      edited = clone(next);
      selected = null;
      renderSummary();
      renderValidation();
      renderList();
      renderEditor();
    },
    overrideText,
    fullText,
    diffText,
  };
}
