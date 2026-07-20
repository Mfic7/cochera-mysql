/* =====================================================================
 *  inactividad.js  —  cierra la sesión tras 5 minutos sin actividad
 *  Uso: incluir en la página DESPUÉS de definir cerrarSesion(), así:
 *     <script>window.CERRAR_SESION = cerrarSesion;</script>
 *     <script src="js/inactividad.js"></script>
 *  Si no defines CERRAR_SESION, usa uno genérico según el tipo de token.
 * ===================================================================== */
(function () {
  const MINUTOS_LIMITE   = 5;              // tiempo total sin actividad
  const SEGUNDOS_AVISO   = 30;             // el aviso aparece 30s antes de cerrar
  const LIMITE_MS        = MINUTOS_LIMITE * 60 * 1000;
  const AVISO_MS         = LIMITE_MS - SEGUNDOS_AVISO * 1000;

  let timerAviso, timerCierre, cuentaRegresiva, segundos;

  // ---- cómo cerrar sesión ----
  function cerrarSesion() {
    if (typeof window.CERRAR_SESION === 'function') { window.CERRAR_SESION(); return; }
    // genérico: detecta si es admin o usuario por el token guardado
    if (localStorage.getItem('admin_token')) {
      localStorage.removeItem('admin_token'); localStorage.removeItem('admin_nombre');
      location.href = 'admin-login.html';
    } else {
      localStorage.removeItem('user_token'); localStorage.removeItem('user_nombre');
      location.href = 'usuario-login.html';
    }
  }

  // ---- el modal (estilo Yape/oscuro con amarillo) ----
  function crearModal() {
    if (document.getElementById('inactBackdrop')) return;
    const css = `
      #inactBackdrop{position:fixed;inset:0;background:rgba(0,0,0,.72);display:none;
        align-items:center;justify-content:center;z-index:99999;padding:20px;
        font-family:'IBM Plex Sans',system-ui,sans-serif}
      #inactBackdrop.show{display:flex}
      .inact-card{background:#262927;border-top:4px solid #f2c200;max-width:380px;width:100%;
        padding:26px 24px;text-align:center;color:#e8e6e0}
      .inact-ico{font-size:42px;margin-bottom:10px}
      .inact-title{font-family:'Archivo Black','Arial Black',sans-serif;font-size:18px;color:#f2c200;margin-bottom:8px}
      .inact-txt{font-size:14px;color:#8f938d;line-height:1.5;margin-bottom:6px}
      .inact-count{font-family:'IBM Plex Mono',monospace;font-size:34px;color:#e2473b;margin:12px 0 20px}
      .inact-btns{display:flex;gap:10px}
      .inact-btn{flex:1;padding:13px;font-family:'Archivo Black','Arial Black',sans-serif;font-size:13px;
        letter-spacing:.04em;cursor:pointer;border:1px solid #3a3e3b;background:none;color:#e8e6e0}
      .inact-btn.primary{background:#f2c200;border-color:#f2c200;color:#111}
    `;
    const style = document.createElement('style'); style.textContent = css; document.head.appendChild(style);

    const back = document.createElement('div');
    back.id = 'inactBackdrop';
    back.innerHTML = `
      <div class="inact-card">
        <div class="inact-ico">⏳</div>
        <div class="inact-title">¿SIGUES AHÍ?</div>
        <div class="inact-txt">No detectamos actividad. Por seguridad, tu sesión se cerrará en:</div>
        <div class="inact-count" id="inactCount">30</div>
        <div class="inact-btns">
          <button class="inact-btn" id="inactSalir">SALIR AHORA</button>
          <button class="inact-btn primary" id="inactSeguir">SEGUIR AQUÍ</button>
        </div>
      </div>`;
    document.body.appendChild(back);

    document.getElementById('inactSeguir').onclick = reiniciar;
    document.getElementById('inactSalir').onclick  = cerrarSesion;
  }

  function mostrarAviso() {
    crearModal();
    segundos = SEGUNDOS_AVISO;
    document.getElementById('inactCount').textContent = segundos;
    document.getElementById('inactBackdrop').classList.add('show');
    cuentaRegresiva = setInterval(() => {
      segundos--;
      const c = document.getElementById('inactCount');
      if (c) c.textContent = segundos;
      if (segundos <= 0) clearInterval(cuentaRegresiva);
    }, 1000);
  }

  function ocultarAviso() {
    const b = document.getElementById('inactBackdrop');
    if (b) b.classList.remove('show');
    clearInterval(cuentaRegresiva);
  }

  // ---- reinicia los contadores cada vez que hay actividad ----
  function reiniciar() {
    clearTimeout(timerAviso);
    clearTimeout(timerCierre);
    ocultarAviso();
    timerAviso  = setTimeout(mostrarAviso, AVISO_MS);
    timerCierre = setTimeout(cerrarSesion, LIMITE_MS);
  }

  // eventos que cuentan como "actividad"
  ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'click'].forEach(ev =>
    document.addEventListener(ev, () => {
      // si el aviso ya está visible, moverse NO lo cancela: hay que tocar "SEGUIR AQUÍ".
      if (document.getElementById('inactBackdrop')?.classList.contains('show')) return;
      reiniciar();
    }, { passive: true })
  );

  reiniciar();   // arranca el contador al cargar la página
})();