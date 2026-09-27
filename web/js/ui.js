// Tiny DOM toolkit. User data is only ever inserted as text nodes (never innerHTML).

export function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs ?? {})) {
    if (value == null || value === false) continue;
    if (key === 'class') node.className = value;
    else if (key === 'dataset') Object.assign(node.dataset, value);
    else if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2).toLowerCase(), value);
    else if (key === 'value') node.value = value;
    else if (key === 'checked') node.checked = !!value;
    else if (key === 'disabled') node.disabled = !!value;
    else if (value === true) node.setAttribute(key, '');
    else node.setAttribute(key, String(value));
  }
  append(node, children);
  return node;
}

function append(node, children) {
  for (const child of children.flat(Infinity)) {
    if (child == null || child === false || child === '') continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
}

export const text = (tag, cls, value, attrs = {}) => el(tag, { class: cls, ...attrs }, value);

export function pill(label, tone = 'neutral') {
  return el('span', { class: `pill pill-${tone}` }, label);
}

export function button(label, onClick, { variant = 'primary', testid, disabled = false, type = 'button', small = false } = {}) {
  return el('button', { type, class: `btn btn-${variant}${small ? ' btn-small' : ''}`, 'data-testid': testid, disabled, onclick: onClick }, label);
}

export function iconButton(label, symbol, onClick, testid, tone = 'neutral') {
  return el('button', { type: 'button', class: `icon-btn icon-${tone}`, 'aria-label': label, title: label, 'data-testid': testid, onclick: onClick }, symbol);
}

export function loading(label = 'Loading…') {
  return el('div', { class: 'state state-loading', role: 'status' }, el('span', { class: 'spinner', 'aria-hidden': 'true' }), label);
}

export function empty(message) {
  return el('div', { class: 'state state-empty', 'data-testid': 'empty-state' }, message);
}

export function errorBox(message) {
  return el('div', { class: 'state state-error', role: 'alert', 'data-testid': 'error-state' }, message);
}

/** Renders loading / error / empty / content for a live collection. */
export function remoteList(remote, items, emptyText, renderItem) {
  if (remote.error) return errorBox(remote.error);
  if (remote.loading && !items.length) return loading();
  if (!items.length) return empty(emptyText);
  return el('div', { class: 'list' }, items.map(renderItem));
}

export function card(...children) {
  return el('div', { class: 'card' }, ...children);
}

export function labeled(label, value) {
  return el('div', { class: 'kv' }, el('span', { class: 'kv-label' }, label), el('span', { class: 'kv-value' }, value || '—'));
}

// ---------- form fields (each returns {node, get()} ) ----------

export function field(label, { value = '', type = 'text', testid, placeholder = '', multiline = false, validate, autocomplete } = {}) {
  const input = multiline
    ? el('textarea', { rows: 4, 'data-testid': testid, placeholder })
    : el('input', { type, 'data-testid': testid, placeholder, autocomplete });
  input.value = value ?? '';
  const hint = el('small', { class: 'field-error', 'aria-live': 'polite' });
  const refresh = () => {
    const msg = validate && input.value !== '' ? validate(input.value) : null;
    hint.textContent = msg ?? '';
    input.classList.toggle('invalid', !!msg);
  };
  input.addEventListener('input', refresh);
  return { node: el('label', { class: 'field' }, el('span', {}, label), input, hint), get: () => input.value, input };
}

export function select(label, choices, { value = '', testid, placeholder } = {}) {
  const sel = el('select', { 'data-testid': testid },
    placeholder != null ? el('option', { value: '' }, placeholder) : null,
    choices.map((c) => el('option', { value: c.id }, c.label)));
  sel.value = value ?? '';
  return { node: el('label', { class: 'field' }, el('span', {}, label), sel), get: () => sel.value, input: sel };
}

export function multiSelect(label, choices, selected, { testid, emptyText = 'Nothing available yet.' } = {}) {
  const chosen = new Set(selected ?? []);
  const box = el('fieldset', { class: 'multi', 'data-testid': testid },
    el('legend', {}, label),
    choices.length ? null : el('small', { class: 'muted' }, emptyText),
    choices.map((c) => el('label', { class: 'check' },
      el('input', { type: 'checkbox', value: c.id, checked: chosen.has(c.id), 'data-testid': `${testid}-${c.id}`,
        onchange: (e) => (e.target.checked ? chosen.add(c.id) : chosen.delete(c.id)) }),
      el('span', {}, c.label))));
  return { node: box, get: () => [...chosen] };
}

export function checkbox(label, checked, testid) {
  const input = el('input', { type: 'checkbox', checked, 'data-testid': testid });
  return { node: el('label', { class: 'check' }, input, el('span', {}, label)), get: () => input.checked };
}

// ---------- modal dialogs ----------

const modalRoot = () => document.getElementById('modal-root');

/**
 * Opens a form dialog. onSubmit returns a Promise; the dialog stays open and shows the
 * error if it rejects, disables the submit button while saving (no double submits) and
 * closes on success.
 */
export function openForm({ title, submitLabel = 'Save', body, onSubmit, testid = 'form-dialog' }) {
  const errorNode = el('div', { class: 'form-error', role: 'alert', 'data-testid': 'form-error', hidden: true });
  const submit = el('button', { type: 'submit', class: 'btn btn-primary', 'data-testid': 'form-submit' }, submitLabel);
  const cancel = el('button', { type: 'button', class: 'btn btn-ghost', 'data-testid': 'form-cancel', onclick: () => close() }, 'Cancel');
  const form = el('form', { class: 'modal', 'data-testid': testid, novalidate: true },
    el('h2', {}, title), errorNode, el('div', { class: 'modal-body' }, body), el('div', { class: 'modal-actions' }, cancel, submit));
  const overlay = el('div', { class: 'overlay', onclick: (e) => { if (e.target === overlay && !submit.disabled) close(); } }, form);
  const onKey = (e) => { if (e.key === 'Escape' && !submit.disabled) close(); };
  function close() {
    document.removeEventListener('keydown', onKey);
    overlay.remove();
  }
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (submit.disabled) return;
    submit.disabled = true;
    cancel.disabled = true;
    submit.textContent = 'Saving…';
    errorNode.hidden = true;
    try {
      await onSubmit();
      close();
    } catch (err) {
      errorNode.textContent = err?.friendly ?? err?.message ?? 'The change could not be saved.';
      errorNode.hidden = false;
      submit.disabled = false;
      cancel.disabled = false;
      submit.textContent = submitLabel;
    }
  });
  document.addEventListener('keydown', onKey);
  modalRoot().append(overlay);
  form.querySelector('input, select, textarea')?.focus();
  return { close };
}

export function confirmDialog(message, onConfirm) {
  openForm({
    title: 'Please confirm',
    submitLabel: 'Delete',
    testid: 'confirm-dialog',
    body: el('p', {}, message, el('br'), el('small', { class: 'muted' }, 'This cannot be undone.')),
    onSubmit: onConfirm,
  });
}
