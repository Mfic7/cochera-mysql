/* Modal de error reusable para fallas de conexión con el backend. */
(function (global) {
  const CSS = `
  .errmodal-backdrop{position:fixed;inset:0;background:rgba(0,0,0,.6);display:none;
    align-items:center;justify-content:center;z-index:9999;padding:20px}
  .errmodal-backdrop.show{display:flex}
  .errmodal{background:#262927;border-top:4px solid #e2473b;max-width:380px;width:100%;
    padding:22px;font-family:'IBM Plex Sans',sans-serif;color:#e8e6e0;
    box-shadow:0 10px 40px rgba(0,0,0,.5)}
  .errmodal-title{font-family:'Archivo Black',sans-serif;font-size:15px;color:#e2473b;
    letter-spacing:.05em;margin-bottom:10px}
  .errmodal-msg{font-size:13px;line-height:1.5;color:#e8e6e0;margin-bottom:18px}
  .errmodal-acc{display:flex;gap:10px}
  .errmodal-btn{flex:1;padding:10px;border:1px solid #3a3e3b;background:none;color:#e8e6e0;
    font-family:'IBM Plex Mono',monospace;font-size:12px;letter-spacing:.05em;cursor:pointer}
  .errmodal-btn.primary{background:#f2c200;border-color:#f2c200;color:#111}
  .errmodal-btn:hover{border-color:#f2c200;color:#f2c200}
  .errmodal-btn.primary:hover{color:#111}
  `;

  let backdrop, msgEl, retryBtn, closeBtn, retryFn;

  function init() {
    if (backdrop) return;
    const style = document.createElement('style');
    style.textContent = CSS;
    document.head.appendChild(style);

    backdrop = document.createElement('div');
    backdrop.className = 'errmodal-backdrop';
    backdrop.innerHTML =
      '<div class="errmodal">' +
        '<div class="errmodal-title">SIN CONEXIÓN CON EL SERVIDOR</div>' +
        '<div class="errmodal-msg" id="errmodalMsg"></div>' +
        '<div class="errmodal-acc">' +
          '<button class="errmodal-btn primary" id="errmodalRetry">REINTENTAR</button>' +
          '<button class="errmodal-btn" id="errmodalClose">CERRAR</button>' +
        '</div>' +
      '</div>';
    document.body.appendChild(backdrop);
    msgEl = backdrop.querySelector('#errmodalMsg');
    retryBtn = backdrop.querySelector('#errmodalRetry');
    closeBtn = backdrop.querySelector('#errmodalClose');
    closeBtn.onclick = hide;
    backdrop.onclick = e => { if (e.target === backdrop) hide(); };
    retryBtn.onclick = () => { hide(); if (retryFn) retryFn(); };
  }

  function show(mensaje, onRetry) {
    init();
    msgEl.textContent = mensaje ||
      'No se pudo conectar con el servidor. Verifica que el backend esté encendido (mvn spring-boot:run) e inténtalo de nuevo.';
    retryFn = onRetry || null;
    retryBtn.style.display = onRetry ? '' : 'none';
    backdrop.classList.add('show');
  }

  function hide() {
    if (backdrop) backdrop.classList.remove('show');
  }

  global.showErrorModal = show;
  global.hideErrorModal = hide;
})(window);
