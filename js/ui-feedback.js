// User feedback: status line, toast notifications and busy buttons.

export function setStatus(msg) {
    const el = document.getElementById('status');
    if (el) el.textContent = msg || '';
}

/** Shows `message` in the status line and as a toast. */
export function report(message, type = 'info') {
    setStatus(message);
    notify(message, type);
}

// Toast notifications -------------------------------------------------------
const TOAST_AUTO_DISMISS_MS = { success: 4000, info: 5000, error: null /* sticky */ };

/**
 * Show a toast notification.
 * @param {string} message
 * @param {'success'|'error'|'info'} type
 */
export function notify(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    // Errors use role="alert" for immediate ARIA announcement; others use the
    // container's aria-live="polite" region.
    if (type === 'error') toast.setAttribute('role', 'alert');

    const msgSpan = document.createElement('span');
    msgSpan.className = 'toast-msg';
    msgSpan.textContent = message;
    toast.appendChild(msgSpan);

    const dismissBtn = document.createElement('button');
    dismissBtn.className = 'toast-dismiss';
    dismissBtn.setAttribute('aria-label', 'Schließen');
    dismissBtn.textContent = '✕';
    dismissBtn.addEventListener('click', () => removeToast(toast));
    toast.appendChild(dismissBtn);

    container.appendChild(toast);

    const autoMs = TOAST_AUTO_DISMISS_MS[type];
    if (autoMs != null) {
        setTimeout(() => removeToast(toast), autoMs);
    }
}

function removeToast(toast) {
    if (!toast.isConnected) return;
    toast.classList.add('toast-leaving');
    toast.addEventListener('animationend', () => toast.remove(), { once: true });
}

// Button busy helper --------------------------------------------------------
/**
 * Disables `button`, shows a spinner + `busyLabel` while `asyncFn` runs,
 * then always restores the button state.
 * @param {HTMLButtonElement} button
 * @param {string} busyLabel  Text shown next to the spinner during the operation.
 * @param {() => Promise<*>} asyncFn
 * @returns {Promise<*>}  Resolves/rejects with the return value of asyncFn.
 */
export async function withButtonBusy(button, busyLabel, asyncFn) {
    const originalHTML = button.innerHTML;
    // Capture the current rendered width so the button doesn't collapse
    button.style.setProperty('--btn-stable-width', button.offsetWidth + 'px');
    button.classList.add('busy');
    button.disabled = true;

    const spinner = document.createElement('span');
    spinner.className = 'btn-spinner';
    button.innerHTML = '';
    button.appendChild(spinner);
    button.appendChild(document.createTextNode(' ' + busyLabel));

    try {
        return await asyncFn();
    } finally {
        button.innerHTML = originalHTML;
        button.disabled = false;
        button.classList.remove('busy');
        button.style.removeProperty('--btn-stable-width');
    }
}
