// =====================================================================
// carrito.js — Módulo de carrito de compras, estilo Temu.
//
// Se incluye tal cual (<script src="carrito.js"></script>) en CUALQUIER
// página del sitio: index.html, detalle-nacional.html, carrito.html y
// factura.html. No depende de script.js ni de las funciones de sesión
// de index.html (comprobarSesion, mostrarLogin, etc.) porque
// detalle-nacional.html NO carga ninguna de esas — así que este archivo
// trae su propia copia mínima de "¿hay sesión?" leyendo la misma llave
// de localStorage ("sesionUsuario") que ya usa el resto del sitio.
//
// Uso desde cualquier tarjeta de producto:
//   window.Carrito.botonHTML(producto)   -> string HTML del botón 🛒+
//   (el click ya queda enganchado solo, vía delegación de eventos)
// =====================================================================
(function () {
  const CLAVE_SESION = "sesionUsuario";

  function fmtCrc(n) {
    const num = Number(n);
    if (isNaN(num)) return "₡0";
    return "₡" + Math.round(num).toLocaleString("es-CR", { maximumFractionDigits: 0 });
  }

  function esc(s) {
    if (s === null || s === undefined) return "";
    const div = document.createElement("div");
    div.textContent = s;
    return div.innerHTML;
  }

  // ---- Sesión (copia liviana de la de index.html/script.js) ----------
  function haySesion() {
    try {
      const sesion = JSON.parse(localStorage.getItem(CLAVE_SESION));
      if (!sesion || !sesion.tipo) return false;
      if (Date.now() > sesion.expiracion) return false;
      return true;
    } catch (e) {
      return false;
    }
  }

  // Pide iniciar sesión antes de tocar el carrito. Si estamos en
  // index.html (existe window.mostrarLogin, definido ahí), se abre el
  // login sin salir de la página. Si estamos en cualquier otra página
  // (detalle-nacional.html, carrito.html, factura.html, que no cargan
  // ese login), se ofrece ir a la página principal a iniciar sesión.
  function pedirLogin() {
    if (typeof window.mostrarLogin === "function") {
      window.mostrarLogin("Inicia sesión para agregar productos al carrito");
      return;
    }
    if (confirm("Debes iniciar sesión para agregar productos al carrito.\n\n¿Quieres ir a la página principal para iniciar sesión?")) {
      // "login-comprador" reutiliza el mismo mecanismo de deep-link que
      // ya usan los correos de EmailService (?accion=login-vendedor,
      // etc., ver el <script> al final de index.html): abre el login y
      // hace clic en "Comprador/a" automáticamente al cargar.
      window.location.href = "index.html?accion=login-comprador";
    }
  }

  // ---- Estilos (inyectados una sola vez) ------------------------------
  function inyectarEstilos() {
    if (document.getElementById("carritoEstilos")) return;
    const style = document.createElement("style");
    style.id = "carritoEstilos";
    style.textContent = `
      .btn-agregar-carrito {
        position: absolute;
        right: 8px;
        bottom: 8px;
        z-index: 3;
        width: 34px;
        height: 34px;
        border-radius: 50%;
        border: none;
        background: #a13341;
        color: #fff;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        box-shadow: 0 2px 8px rgba(0,0,0,0.25);
        font-size: 14px;
        transition: transform 0.12s ease, background 0.15s ease;
      }
      .btn-agregar-carrito:hover { background: #8a2b37; transform: scale(1.07); }
      .btn-agregar-carrito .fa-plus {
        position: absolute;
        font-size: 8px;
        bottom: 5px;
        right: 5px;
        background: #fff;
        color: #a13341;
        border-radius: 50%;
        width: 13px;
        height: 13px;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      #cartHeaderBtn {
        position: relative;
        background: none;
        border: none;
        color: #fff;
        font-size: 20px;
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 38px;
        height: 38px;
      }
      #cartHeaderBtn .cart-badge {
        position: absolute;
        top: 0;
        right: 0;
        background: #a13341;
        color: #fff;
        font-size: 10px;
        font-weight: 700;
        min-width: 16px;
        height: 16px;
        border-radius: 8px;
        display: none;
        align-items: center;
        justify-content: center;
        padding: 0 3px;
      }
      #cartHeaderBtn .cart-badge.visible { display: flex; }

      .carrito-overlay {
        position: fixed;
        inset: 0;
        background: rgba(0,0,0,0.45);
        z-index: 9998;
        display: none;
        align-items: center;
        justify-content: center;
        font-family: "Poppins", sans-serif;
      }
      .carrito-overlay.visible { display: flex; }

      .carrito-modal-confirmar {
        background: #fff;
        border-radius: 14px;
        width: min(420px, 92vw);
        max-height: 88vh;
        overflow-y: auto;
        padding: 20px;
        box-shadow: 0 12px 40px rgba(0,0,0,0.3);
      }
      .carrito-modal-confirmar h3 {
        margin: 0 0 14px;
        font-size: 1.05rem;
        color: #1a1a1a;
      }
      .carrito-modal-confirmar-producto {
        display: flex;
        gap: 12px;
        margin-bottom: 16px;
      }
      .carrito-modal-confirmar-producto img {
        width: 88px;
        height: 88px;
        object-fit: cover;
        border-radius: 10px;
        border: 1px solid #eee;
        background: #fafafa;
        flex-shrink: 0;
      }
      .carrito-modal-confirmar-info { flex: 1; min-width: 0; }
      .carrito-modal-confirmar-info .nombre {
        font-weight: 600;
        font-size: 0.92rem;
        color: #1a1a1a;
        margin: 0 0 6px;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }
      .carrito-modal-confirmar-info .precio {
        font-weight: 700;
        color: #a13341;
        font-size: 1.05rem;
        margin: 0;
      }
      .carrito-modal-confirmar-info .precio-anterior {
        text-decoration: line-through;
        color: #999;
        font-size: 0.8rem;
        margin-left: 6px;
        font-weight: 500;
      }
      .carrito-cantidad-selector {
        display: flex;
        align-items: center;
        gap: 14px;
        margin: 10px 0 18px;
      }
      .carrito-cantidad-selector button {
        width: 30px;
        height: 30px;
        border-radius: 50%;
        border: 1.5px solid #ddd;
        background: #fff;
        font-size: 16px;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .carrito-cantidad-valor {
        display: inline-block;
        min-width: 30px;
        text-align: center;
        font-weight: 700;
        font-size: 1.05rem;
        color: #1a1a1a !important;
        background: #f6f6f6;
        border-radius: 6px;
        padding: 4px 0;
      }
      .carrito-modal-confirmar-botones {
        display: flex;
        gap: 10px;
      }
      .carrito-modal-confirmar-botones button {
        flex: 1;
        padding: 11px;
        border-radius: 8px;
        border: none;
        font-weight: 600;
        font-size: 0.9rem;
        cursor: pointer;
        font-family: inherit;
      }
      .btn-carrito-cancelar { background: #f0f0f0; color: #333; }
      .btn-carrito-confirmar { background: #a13341; color: #fff; }
      .btn-carrito-confirmar:hover { background: #8a2b37; }

      .carrito-drawer {
        position: fixed;
        top: 0;
        right: -360px;
        width: 340px;
        max-width: 90vw;
        height: 100vh;
        background: #fff;
        box-shadow: -6px 0 24px rgba(0,0,0,0.25);
        z-index: 9999;
        transition: right 0.25s ease;
        display: flex;
        flex-direction: column;
        font-family: "Poppins", sans-serif;
      }
      .carrito-drawer.visible { right: 0; }
      .carrito-drawer-header {
        padding: 16px;
        border-bottom: 1px solid #eee;
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .carrito-drawer-header h3 { margin: 0; font-size: 1rem; }
      .carrito-drawer-header button {
        background: none; border: none; font-size: 20px; cursor: pointer; color: #777;
      }
      .carrito-drawer-item {
        display: flex;
        gap: 10px;
        padding: 12px 16px;
        border-bottom: 1px solid #f2f2f2;
      }
      .carrito-drawer-item img {
        width: 56px; height: 56px; object-fit: cover; border-radius: 8px; border: 1px solid #eee;
      }
      .carrito-drawer-item .nombre { font-size: 0.82rem; margin: 0 0 4px; color: #1a1a1a;
        display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
      .carrito-drawer-item .precio { font-size: 0.85rem; font-weight: 700; color: #a13341; margin: 0; }
      .carrito-drawer-footer {
        margin-top: auto;
        padding: 16px;
        border-top: 1px solid #eee;
      }
      .carrito-drawer-total {
        display: flex;
        justify-content: space-between;
        font-weight: 700;
        margin-bottom: 12px;
        font-size: 0.95rem;
      }
      .carrito-drawer-footer button {
        width: 100%;
        padding: 12px;
        border-radius: 8px;
        border: none;
        font-weight: 700;
        cursor: pointer;
        font-family: inherit;
        margin-bottom: 8px;
      }
      .btn-ir-carrito { background: #a13341; color: #fff; }
      .btn-ir-carrito:hover { background: #8a2b37; }
      .btn-seguir-comprando { background: #fff; color: #a13341; border: 1.5px solid #a13341 !important; }
    `;
    document.head.appendChild(style);
  }

  // ---- Estado en memoria ----------------------------------------------
  let carritoItems = []; // [{producto_id, nombre, imagen, precio_unitario, cantidad, categoria, precio_anterior, precio_cambio}]

  function actualizarBadge() {
    const total = carritoItems.reduce((acc, it) => acc + it.cantidad, 0);
    document.querySelectorAll(".cart-badge").forEach((badge) => {
      badge.textContent = total > 99 ? "99+" : String(total);
      badge.classList.toggle("visible", total > 0);
    });
  }

  function cargarCarrito() {
    if (!haySesion()) { carritoItems = []; actualizarBadge(); return Promise.resolve([]); }
    return fetch("/api/carrito")
      .then((res) => (res.ok ? res.json() : []))
      .then((items) => {
        carritoItems = items || [];
        actualizarBadge();
        return carritoItems;
      })
      .catch(() => []);
  }

  // ---- Botón 🛒+ para insertar en cualquier tarjeta --------------------
  // `producto` acepta las mismas llaves que ya usan las tarjetas del
  // sitio (id/nombre/imagen/precio/precio_anterior/categoria), así que
  // se puede pasar directo el objeto "producto" que ya trae cada fetch.
  function botonHTML(producto) {
    const id = producto.id ?? producto.producto_id ?? "";
    return `<button type="button" class="btn-agregar-carrito"
        data-carrito-id="${esc(id)}"
        data-carrito-nombre="${esc(producto.nombre || "")}"
        data-carrito-imagen="${esc(producto.imagen || "")}"
        data-carrito-precio="${esc(producto.precio ?? "")}"
        data-carrito-precio-anterior="${esc(producto.precio_anterior ?? "")}"
        data-carrito-categoria="${esc(producto.categoria || "")}"
        title="Agregar al carrito"
        aria-label="Agregar al carrito">
      <i class="fa-solid fa-cart-shopping"></i><i class="fa-solid fa-plus"></i>
    </button>`;
  }

  // ---- Modal de confirmación -------------------------------------------
  function abrirModalConfirmar(producto) {
    inyectarEstilos();
    let cantidad = 1;
    const precioNum = parseFloat(producto.precio);
    const precioAntNum = parseFloat(producto.precio_anterior);
    const tienePrecioAnterior = !isNaN(precioAntNum) && !isNaN(precioNum) && precioAntNum > precioNum;

    const overlay = document.createElement("div");
    overlay.className = "carrito-overlay visible";
    overlay.innerHTML = `
      <div class="carrito-modal-confirmar">
        <h3>Agregar al carrito</h3>
        <div class="carrito-modal-confirmar-producto">
          <img src="${esc(producto.imagen)}" alt="${esc(producto.nombre)}">
          <div class="carrito-modal-confirmar-info">
            <p class="nombre">${esc(producto.nombre)}</p>
            <p class="precio">${fmtCrc(precioNum)}${tienePrecioAnterior ? `<span class="precio-anterior">${fmtCrc(precioAntNum)}</span>` : ""}</p>
          </div>
        </div>
        <div class="carrito-cantidad-selector">
          <span style="font-weight:500;color:#555;">Cantidad:</span>
          <button type="button" class="carrito-restar">−</button>
          <span class="carrito-cantidad-valor">1</span>
          <button type="button" class="carrito-sumar">+</button>
        </div>
        <div class="carrito-modal-confirmar-botones">
          <button type="button" class="btn-carrito-cancelar">Cancelar</button>
          <button type="button" class="btn-carrito-confirmar">Agregar</button>
        </div>
      </div>
    `;
    document.body.appendChild(overlay);

    const valorEl = overlay.querySelector(".carrito-cantidad-valor");
    overlay.querySelector(".carrito-restar").addEventListener("click", () => {
      cantidad = Math.max(1, cantidad - 1);
      valorEl.textContent = cantidad;
    });
    overlay.querySelector(".carrito-sumar").addEventListener("click", () => {
      cantidad = Math.min(99, cantidad + 1);
      valorEl.textContent = cantidad;
    });
    function cerrar() { overlay.remove(); }
    overlay.querySelector(".btn-carrito-cancelar").addEventListener("click", cerrar);
    overlay.addEventListener("click", (e) => { if (e.target === overlay) cerrar(); });

    overlay.querySelector(".btn-carrito-confirmar").addEventListener("click", () => {
      cerrar();
      agregarAlCarrito(producto, cantidad);
    });
  }

  // ---- Llamada al backend + mini-carrito lateral -----------------------
  function agregarAlCarrito(producto, cantidad) {
    const id = producto.id ?? producto.producto_id;
    fetch("/api/carrito", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ accion: "agregar", producto_id: id, cantidad: cantidad }).toString(),
    })
      .then((res) => {
        if (!res.ok) return res.json().then((d) => { throw new Error(d.error || "No se pudo agregar el producto."); });
        return cargarCarrito();
      })
      .then(() => mostrarDrawer(producto))
      .catch((err) => alert(err.message));
  }

  function mostrarDrawer() {
    inyectarEstilos();
    let drawer = document.getElementById("carritoDrawer");
    if (!drawer) {
      drawer = document.createElement("div");
      drawer.id = "carritoDrawer";
      drawer.className = "carrito-drawer";
      document.body.appendChild(drawer);
    }

    const subtotal = carritoItems.reduce((acc, it) => acc + it.precio_unitario * it.cantidad, 0);
    const itemsHtml = carritoItems
      .slice(0, 4)
      .map(
        (it) => `
        <div class="carrito-drawer-item">
          <img src="${esc(it.imagen)}" alt="${esc(it.nombre)}">
          <div>
            <p class="nombre">${esc(it.nombre)}</p>
            <p class="precio">${fmtCrc(it.precio_unitario)} × ${it.cantidad}</p>
          </div>
        </div>`
      )
      .join("");

    drawer.innerHTML = `
      <div class="carrito-drawer-header">
        <h3>Tu carrito</h3>
        <button type="button" class="carrito-drawer-cerrar" aria-label="Cerrar">&times;</button>
      </div>
      <div style="overflow-y:auto;">${itemsHtml || '<p style="padding:16px;color:#777;">Tu carrito está vacío.</p>'}</div>
      <div class="carrito-drawer-footer">
        <div class="carrito-drawer-total"><span>Subtotal</span><span>${fmtCrc(subtotal)}</span></div>
        <button type="button" class="btn-ir-carrito">Ir al carrito</button>
        <button type="button" class="btn-seguir-comprando">Seguir comprando</button>
      </div>
    `;
    drawer.querySelector(".carrito-drawer-cerrar").addEventListener("click", () => drawer.classList.remove("visible"));
    drawer.querySelector(".btn-seguir-comprando").addEventListener("click", () => drawer.classList.remove("visible"));
    drawer.querySelector(".btn-ir-carrito").addEventListener("click", () => {
      window.open("carrito.html", "_blank", "noopener");
    });

    requestAnimationFrame(() => drawer.classList.add("visible"));
  }

  // ---- Delegación de eventos: funciona en cualquier página que
  // incluya este script, sin importar cómo se generó la tarjeta.
  document.addEventListener("click", function (e) {
    const btn = e.target.closest(".btn-agregar-carrito");
    if (!btn) return;
    e.preventDefault();
    e.stopPropagation(); // no debe disparar el click de la tarjeta completa (abre "Ver detalles")

    if (!haySesion()) { pedirLogin(); return; }

    const producto = {
      id: btn.dataset.carritoId,
      nombre: btn.dataset.carritoNombre,
      imagen: btn.dataset.carritoImagen,
      precio: btn.dataset.carritoPrecio,
      precio_anterior: btn.dataset.carritoPrecioAnterior,
      categoria: btn.dataset.carritoCategoria,
    };
    if (!producto.id) return;
    abrirModalConfirmar(producto);
  });

  // Se recarga el carrito (y el badge) apenas carga cualquier página que
  // incluya este script, y cada vez que la sesión cambie (login/logout
  // disparan estos mismos eventos de actividad en index.html).
  document.addEventListener("DOMContentLoaded", cargarCarrito);

  window.Carrito = {
    botonHTML: botonHTML,
    cargar: cargarCarrito,
    haySesion: haySesion,
    pedirLogin: pedirLogin,
    fmtCrc: fmtCrc,
    esc: esc,
    obtenerItems: () => carritoItems.slice(),
  };
})();