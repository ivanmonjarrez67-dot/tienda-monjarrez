/* ==========================================================================
   encabezado-movil.js — Encabezado y menús SOLO para teléfono
   --------------------------------------------------------------------------
   - No cambia la lógica de filtros: las pestañas y la pantalla de Categorías
     "tocan por detrás" los botones de siempre (.filter / .main / #searchButton
     / #qrBtn / #btnAbrirPerfil ...), que quedan en la página pero ocultos.
   - Solo actúa en pantallas de hasta 768px; en computador no hace nada.
   - Cargar al FINAL del <body>, después de script.js y carrito.js.
   ========================================================================== */
(function () {
  "use strict";
  if (window.__mtInit) return;
  window.__mtInit = true;

  /* ----------------------------- CONFIGURACIÓN ---------------------------- */

  // Las categorías, ejemplos y avisos se editan en encabezado-config.js
  var MT = window.MT || {};
  var CATEGORIAS = MT.CATEGORIAS || [];
  var DESTACADOS = (MT.DESTACADOS || []).map(function (d) { return d.nombre; });
  var AVISOS = MT.AVISOS || [{ icono: "fa-shield-halved", texto: "Tienda Monjarrez" }];
  var MS_ROTAR_AVISO = 4500;

  /* ------------------------------- UTILIDADES ------------------------------ */

  var MQ = window.matchMedia("(max-width: 768px)");

  function q(sel, raiz) { return (raiz || document).querySelector(sel); }
  function qa(sel, raiz) { return Array.prototype.slice.call((raiz || document).querySelectorAll(sel)); }
  function crear(tag, clase, html) {
    var e = document.createElement(tag);
    if (clase) e.className = clase;
    if (html != null) e.innerHTML = html;
    return e;
  }
  function esc(s) {
    return String(s).replace(/[&<>"]/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c];
    });
  }
  function haySesion() {
    try { return typeof window.haySesionActiva === "function" ? !!window.haySesionActiva() : false; }
    catch (e) { return false; }
  }
  function botonFiltro(categoria) {
    var l = qa("button.filter");
    for (var i = 0; i < l.length; i++) if (l[i].getAttribute("data-category") === categoria) return l[i];
    return null;
  }
  function botonMain(nombre) {
    var l = qa("button.main");
    for (var i = 0; i < l.length; i++) if (l[i].getAttribute("data-main") === nombre) return l[i];
    return null;
  }
  function clic(el) { if (el) el.click(); }
  function subirInicio() { window.scrollTo(0, 0); }

  /* --------------------------------- ESTADO -------------------------------- */

  var top, nav, tabs, avisoBtn, avisoTxt, avisoIco, overlay, sheetTu, sheetAvisos, pantallaCat;
  var cuerpoTu, cuerpoAvisos, listaCat, panelCat;
  var navInicio, navCat, navTu, navCarrito, navTienda, badgeCarrito, candadoTienda;
  var header, marcadorHeader;
  var catActual = null;
  var hojaAbierta = null;     // "tu" | "avisos" | "cat" | null
  var timerAviso = null, idxAviso = 0;

  /* ------------------------ FILTROS (tocan los botones viejos) ------------------------ */

  function limpiarFiltros() {
    // Las pestañas son de selección única: al elegir una, se
    // anula el otro tipo de filtro para que no queden dos combinados.
    try { filtroPrincipal = null; } catch (e) {}
    try { categoriaSeleccionada = null; } catch (e) {}
    qa(".main").forEach(function (b) { b.classList.remove("active"); });
    qa(".filter").forEach(function (b) { b.classList.remove("active"); });
  }

  function catalogoVisible() {
    try { if (typeof window.mostrarCatalogoPublico === "function") window.mostrarCatalogoPublico(); } catch (e) {}
  }

  function aplicarFiltro(tipo, valor) {
    catalogoVisible();
    limpiarFiltros();
    clic(tipo === "main" ? botonMain(valor) : botonFiltro(valor));
    subirInicio();
  }

  function buscarTexto(texto) {
    catalogoVisible();
    limpiarFiltros();
    var inp = q("#search");
    if (inp) inp.value = texto;
    clic(q("#searchButton"));
    subirInicio();
  }

  /* ------------------------------ PESTAÑAS ------------------------------ */

  function definicionTabs() {
    var t = [{ tipo: "filter", valor: "Todo", etiqueta: "Todo" }];
    DESTACADOS.forEach(function (n) { t.push({ tipo: "main", valor: n, etiqueta: n }); });
    CATEGORIAS.forEach(function (c) { t.push({ tipo: "filter", valor: c.nombre, etiqueta: c.nombre }); });
    return t;
  }

  function construirTabs() {
    definicionTabs().forEach(function (d) {
      var b = crear("button", "mt-tab", esc(d.etiqueta));
      b.type = "button";
      b.setAttribute("data-tipo", d.tipo);
      b.setAttribute("data-valor", d.valor);
      b.addEventListener("click", function () { aplicarFiltro(d.tipo, d.valor); });
      tabs.appendChild(b);
    });
  }

  // La pestaña marcada sale siempre de los botones originales, así también
  // se refleja un filtro aplicado desde otro lado (enlaces, correos, etc.).
  function sincronizarTabs() {
    var tipo = "filter", valor = "Todo";
    var m = q("button.main.active");
    if (m) { tipo = "main"; valor = m.getAttribute("data-main"); }
    else {
      var f = qa("button.filter.active").filter(function (b) {
        return b.getAttribute("data-category") !== "Mi tienda";
      })[0];
      if (f) valor = f.getAttribute("data-category");
    }
    var activa = null;
    qa(".mt-tab", tabs).forEach(function (b) {
      var on = b.getAttribute("data-tipo") === tipo && b.getAttribute("data-valor") === valor;
      b.classList.toggle("on", on);
      if (on) activa = b;
    });
    if (navInicio) navInicio.classList.toggle("on", hojaAbierta === null);
    if (activa && tabs.scrollTo) {
      tabs.scrollTo({ left: activa.offsetLeft - (tabs.clientWidth - activa.offsetWidth) / 2, behavior: "smooth" });
    }
  }

  function observarFiltros() {
    var obs = new MutationObserver(sincronizarTabs);
    qa("button.main, button.filter").forEach(function (b) {
      obs.observe(b, { attributes: true, attributeFilter: ["class"] });
    });
  }

  /* ------------------------------ AVISOS ------------------------------ */

  function pintarAviso() {
    var a = AVISOS[idxAviso % AVISOS.length];
    avisoIco.className = "fa-solid " + a.icono;
    avisoTxt.textContent = a.texto;
  }

  function rotarAviso() {
    if (!MQ.matches || hojaAbierta) return;
    avisoTxt.classList.add("out");
    setTimeout(function () {
      idxAviso++;
      pintarAviso();
      avisoTxt.classList.remove("out");
    }, 250);
  }

  function bannerVigente() {
    var ids = ["heroBanner", "heroBannerVendedor"];
    for (var i = 0; i < ids.length; i++) {
      var b = document.getElementById(ids[i]);
      if (b && b.style.display !== "none") return b;
    }
    return document.getElementById("heroBanner");
  }

  function llenarAvisos() {
    cuerpoAvisos.innerHTML = "";
    var original = bannerVigente();
    if (!original) return;
    var copia = original.cloneNode(true);
    copia.removeAttribute("id");
    copia.style.display = "";
    qa("[id]", copia).forEach(function (n) { n.removeAttribute("id"); });
    // El botón "¿Quieres vender?" vive en la hoja "Tú"; aquí solo los avisos.
    qa(".cta-vender-btn", copia).forEach(function (n) { n.parentNode.removeChild(n); });
    // Los avisos originales se muestran de a uno y se ocultan solos; aquí van todos.
    qa("*", copia).forEach(function (n) {
      if (n.style && n.style.display === "none") n.style.display = "";
      n.classList.remove("ocultando", "reapareciendo");
    });
    cuerpoAvisos.appendChild(copia);
  }

  /* ------------------------------ HOJAS ------------------------------ */

  function abrirHoja(cual) {
    cerrarHojas(true);
    hojaAbierta = cual;
    document.body.classList.add("mt-sheet-open");
    document.body.classList.remove("mt-hide");
    if (cual === "cat") {
      pantallaCat.classList.add("on");
    } else {
      overlay.classList.add("on");
      (cual === "tu" ? sheetTu : sheetAvisos).classList.add("on");
    }
    navCat.classList.toggle("on", cual === "cat");
    navTu.classList.toggle("on", cual === "tu");
    navInicio.classList.toggle("on", false);
  }

  function cerrarHojas(sinActualizarNav) {
    overlay.classList.remove("on");
    sheetTu.classList.remove("on");
    sheetAvisos.classList.remove("on");
    pantallaCat.classList.remove("on");
    document.body.classList.remove("mt-sheet-open");
    hojaAbierta = null;
    if (!sinActualizarNav) {
      navCat.classList.remove("on");
      navTu.classList.remove("on");
      sincronizarTabs();
    }
  }

  /* ------------------------------ HOJA "TÚ" ------------------------------ */

  function nombreSesion() {
    try {
      var s = JSON.parse(localStorage.getItem("sesionUsuario"));
      return (s && (s.nombre || s.nombreUsuario)) || "";
    } catch (e) { return ""; }
  }

  function fila(icono, texto, accion, extraClase, href) {
    var e = crear(href ? "a" : "button", "mt-fila" + (extraClase ? " " + extraClase : ""),
      '<i class="fa-solid ' + icono + '"></i><span>' + esc(texto) + "</span>");
    if (href) { e.href = href; e.target = "_blank"; e.rel = "noopener"; }
    else e.type = "button";
    if (accion) {
      e.addEventListener("click", function () {
        cerrarHojas();
        setTimeout(accion, 180);
      });
    }
    return e;
  }

  function llenarTu() {
    var conSesion = haySesion();
    var esVendedor = false;
    var hv = document.getElementById("heroBannerVendedor");
    if (hv && hv.style.display !== "none") esVendedor = true;

    cuerpoTu.innerHTML = "";
    var saludo = crear("p", "mt-saludo", conSesion ? "Tu cuenta" : "Estás navegando como invitado/a");
    cuerpoTu.appendChild(saludo);
    if (conSesion && MT.nombre) {
      MT.nombre().then(function (n) { if (n) saludo.textContent = "Hola, " + MT.primerNombre(n); });
    }

    if (!conSesion) {
      cuerpoTu.appendChild(fila("fa-right-to-bracket", "Iniciar sesión o registrarme", function () {
        if (typeof window.mostrarLogin === "function") window.mostrarLogin();
      }, "principal"));
    } else {
      cuerpoTu.appendChild(fila("fa-user", "Perfil", function () { clic(q("#btnAbrirPerfil")); }));
      cuerpoTu.appendChild(fila("fa-truck-fast", "Mis pedidos", null, "", "mis-pedidos.html"));
    }

    cuerpoTu.appendChild(fila(
      esVendedor ? "fa-plus" : "fa-store",
      esVendedor ? "Publicar un nuevo producto" : "¿Quieres vender? Publica tus productos",
      function () { clic(document.getElementById(esVendedor ? "btnPublicarHeroVendedor" : "btnVenderHero")); }
    ));
    cuerpoTu.appendChild(fila("fa-circle-question", "Ayuda", function () {
      if (typeof window.mostrarAyuda === "function") window.mostrarAyuda();
    }));
    cuerpoTu.appendChild(fila("fa-qrcode", "QR de la tienda", function () { clic(q("#qrBtn")); }));

    var temas = crear("div", "mt-temas");
    [
      ["fa-circle-half-stroke", "Tema", "themeToggle"],
      ["fa-meteor", "Estrellado", "starThemeBtn"],
      ["fa-heart", "Rosado", "pinkThemeBtn"]
    ].forEach(function (t) {
      var b = crear("button", "mt-fila", '<i class="fa-solid ' + t[0] + '"></i><span>' + t[1] + "</span>");
      b.type = "button";
      b.addEventListener("click", function () { clic(document.getElementById(t[2])); });
      temas.appendChild(b);
    });
    cuerpoTu.appendChild(temas);

    if (conSesion) {
      cuerpoTu.appendChild(fila("fa-right-from-bracket", "Cerrar sesión", function () {
        if (typeof window.manejarClicSesion === "function") window.manejarClicSesion();
      }, "peligro"));
    }
  }

  /* --------------------------- PANTALLA CATEGORÍAS --------------------------- */

  function pintarListaCat() {
    listaCat.innerHTML = "";
    var items = [{ nombre: "Destacados" }].concat(CATEGORIAS);
    items.forEach(function (c) {
      var b = crear("button", "mt-cat-item" + (c.nombre === catActual ? " on" : ""), esc(c.nombre));
      b.type = "button";
      b.addEventListener("click", function () { catActual = c.nombre; pintarListaCat(); pintarPanelCat(); });
      listaCat.appendChild(b);
    });
  }

  function pintarPanelCat() {
    panelCat.innerHTML = "";
    panelCat.scrollTop = 0;
    var chips = crear("div", "mt-chips");

    if (catActual === "Destacados") {
      panelCat.appendChild(crear("h3", null, "Destacados"));
      var todo = crear("button", "mt-chip", "Ver todo");
      todo.type = "button";
      todo.addEventListener("click", function () { cerrarHojas(); aplicarFiltro("filter", "Todo"); });
      chips.appendChild(todo);
      DESTACADOS.forEach(function (n) {
        var b = crear("button", "mt-chip", esc(n));
        b.type = "button";
        b.addEventListener("click", function () { cerrarHojas(); aplicarFiltro("main", n); });
        chips.appendChild(b);
      });
    } else {
      var cat = CATEGORIAS.filter(function (c) { return c.nombre === catActual; })[0];
      if (!cat) return;
      var ver = crear("button", "mt-ver-todo", "<span>Ver todo en " + esc(cat.nombre) + '</span><i class="fa-solid fa-chevron-right"></i>');
      ver.type = "button";
      ver.addEventListener("click", function () { cerrarHojas(); aplicarFiltro("filter", cat.nombre); });
      panelCat.appendChild(ver);
      panelCat.appendChild(crear("h3", null, "Productos de ejemplo"));
      chips.className = "mt-chips mt-prod-grid";
      for (var k = 0; k < 6; k++) chips.appendChild(crear("div", "mt-prod mt-prod-sk", '<span class="sk-img"></span><span class="sk-l"></span><span class="sk-l corto"></span>'));
      var pedida = cat.nombre;
      var respaldo = function () {
        chips.className = "mt-chips";
        chips.innerHTML = "";
        cat.ejemplos.forEach(function (ej) {
          var b = crear("button", "mt-chip", esc(ej));
          b.type = "button";
          b.addEventListener("click", function () { cerrarHojas(); buscarTexto(ej); });
          chips.appendChild(b);
        });
      };
      if (MT.productosDeCategoria) {
        MT.productosDeCategoria(pedida, 8).then(function (lista) {
          if (catActual !== pedida) return; // ya cambió de categoría
          if (!lista.length) { respaldo(); return; }
          chips.innerHTML = "";
          lista.forEach(function (p) { chips.appendChild(MT.tarjetaProducto(p, "mt-prod", function () { cerrarHojas(); })); });
        });
      } else respaldo();
    }
    panelCat.appendChild(chips);
  }

  /* ------------------------- CARRITO / CANDADO (espejo) ------------------------- */

  function actualizarBadgeCarrito() {
    var origen = q("#cartHeaderBtn .cart-badge");
    var n = origen ? origen.textContent.trim() : "";
    if (!n || n === "0") badgeCarrito.style.display = "none";
    else { badgeCarrito.style.display = ""; badgeCarrito.textContent = n; }
  }

  function actualizarCandado() {
    var b = botonFiltro("Mi tienda");
    var bloqueado = !b || b.getAttribute("data-bloqueado") === "true";
    candadoTienda.style.display = bloqueado ? "" : "none";
  }

  function observarEspejos() {
    var cart = q("#cartHeaderBtn");
    if (cart) new MutationObserver(actualizarBadgeCarrito).observe(cart, { childList: true, subtree: true, characterData: true, attributes: true });
    var tienda = botonFiltro("Mi tienda");
    if (tienda) new MutationObserver(actualizarCandado).observe(tienda, { attributes: true });
    // Respaldo por si carrito.js cambia el número de otra forma.
    setInterval(function () { actualizarBadgeCarrito(); actualizarCandado(); }, 1500);
  }

  /* ------------------------------ CONSTRUCCIÓN ------------------------------ */

  function btnNav(icono, texto, clase) {
    var b = crear("button", "mt-nav-btn" + (clase ? " " + clase : ""), '<i class="fa-solid ' + icono + '"></i><span>' + texto + "</span>");
    b.type = "button";
    return b;
  }

  function construir() {
    header = q(".container > header") || q("header");

    top = crear("div");
    top.id = "mtTop";
    tabs = crear("div", "mt-tabs");
    tabs.setAttribute("role", "tablist");
    avisoBtn = crear("button", "mt-aviso");
    avisoBtn.type = "button";
    avisoIco = crear("i");
    var icoWrap = crear("span", "mt-aviso-ico");
    icoWrap.appendChild(avisoIco);
    avisoTxt = crear("span", "mt-aviso-txt");
    avisoBtn.appendChild(icoWrap);
    avisoBtn.appendChild(avisoTxt);
    avisoBtn.appendChild(crear("i", "fa-solid fa-chevron-right"));
    top.appendChild(tabs);
    top.appendChild(avisoBtn);

    nav = crear("nav");
    nav.id = "mtNav";
    navInicio = btnNav("fa-house", "Inicio", "on");
    navCat = btnNav("fa-table-cells-large", "Categorías");
    navTienda = btnNav("fa-star", "Mi tienda", "mt-nav-tienda");
    candadoTienda = crear("i", "fa-solid fa-lock mt-nav-lock");
    navTienda.appendChild(candadoTienda);
    navTu = btnNav("fa-user", "Tú");
    navCarrito = btnNav("fa-cart-shopping", "Carrito");
    badgeCarrito = crear("span", "mt-nav-badge", "0");
    badgeCarrito.style.display = "none";
    navCarrito.appendChild(badgeCarrito);
    [navInicio, navCat, navTienda, navTu, navCarrito].forEach(function (b) { nav.appendChild(b); });

    overlay = crear("div", "mt-overlay");

    sheetTu = crear("div", "mt-sheet");
    sheetTu.innerHTML = '<div class="mt-sheet-top"><h2>Tú</h2><button type="button" class="mt-x" aria-label="Cerrar">&times;</button></div>';
    cuerpoTu = crear("div");
    sheetTu.appendChild(cuerpoTu);

    sheetAvisos = crear("div", "mt-sheet");
    sheetAvisos.innerHTML = '<div class="mt-sheet-top"><h2>Avisos</h2><button type="button" class="mt-x" aria-label="Cerrar">&times;</button></div>';
    cuerpoAvisos = crear("div", "mt-avisos-cuerpo");
    sheetAvisos.appendChild(cuerpoAvisos);

    pantallaCat = crear("div", "mt-full");
    pantallaCat.innerHTML = '<div class="mt-full-top"><h2>Categorías</h2><button type="button" class="mt-x" aria-label="Cerrar">&times;</button></div>' +
      '<div class="mt-full-cuerpo"><div class="mt-cat-lista"></div><div class="mt-cat-panel"></div></div>';
    listaCat = q(".mt-cat-lista", pantallaCat);
    panelCat = q(".mt-cat-panel", pantallaCat);

    document.body.appendChild(top);
    document.body.appendChild(nav);
    document.body.appendChild(overlay);
    document.body.appendChild(sheetTu);
    document.body.appendChild(sheetAvisos);
    document.body.appendChild(pantallaCat);

    construirTabs();
    pintarAviso();

    // Eventos
    overlay.addEventListener("click", function () { cerrarHojas(); });
    qa(".mt-x").forEach(function (b) { b.addEventListener("click", function () { cerrarHojas(); }); });
    avisoBtn.addEventListener("click", function () { llenarAvisos(); abrirHoja("avisos"); });

    navInicio.addEventListener("click", function () { cerrarHojas(); aplicarFiltro("filter", "Todo"); });
    navCat.addEventListener("click", function () {
      if (!catActual) catActual = CATEGORIAS[0].nombre;
      pintarListaCat(); pintarPanelCat(); abrirHoja("cat");
    });
    navTienda.addEventListener("click", function () { cerrarHojas(); clic(botonFiltro("Mi tienda")); });
    navTu.addEventListener("click", function () { llenarTu(); abrirHoja("tu"); });
    navCarrito.addEventListener("click", function () { cerrarHojas(); clic(q("#cartHeaderBtn")); });
  }

  /* ------------------- LOGO PEQUEÑO DELANTE DEL BUSCADOR ------------------- */

  var logoMini = null;
  // Logo pequeño (favicon 32x32). Si tus imágenes están en otra carpeta, cambia esta ruta.
  var LOGO_MINI = "/favicon-32x32.png";
  var LOGO_RESPALDO = "/icon-512.png";
  function buscarFuenteLogo() {
    var sel = ['img[class*="logo" i]', 'img[alt*="logo" i]', 'img[src*="logo" i]'];
    var base = header || document;
    for (var i = 0; i < sel.length; i++) {
      var im = q(sel[i], base) || q(sel[i]);
      if (im && im.getAttribute("src") && !im.closest("#mtTop .search-group")) return im.getAttribute("src");
    }
    var im2 = q(".header-content img", base);
    if (im2 && im2.getAttribute("src")) return im2.getAttribute("src");
    var ic = q('link[rel~="icon"]');
    return ic ? ic.getAttribute("href") : "";
  }
  function ponerLogoMini() {
    if (logoMini || !header) return;
    var grupo = q(".search-group", header);
    if (!grupo) return;
    logoMini = crear("img", "mt-logo-mini");
    logoMini.src = LOGO_MINI;
    logoMini.addEventListener("error", function onErr() {
      logoMini.removeEventListener("error", onErr);
      var otra = (logoMini.getAttribute("src") === LOGO_MINI) ? LOGO_RESPALDO : "";
      var auto = buscarFuenteLogo();
      if (otra) {
        logoMini.addEventListener("error", function () {
          if (auto) logoMini.src = auto; else logoMini.style.display = "none";
        }, { once: true });
        logoMini.src = otra;
      } else if (auto) logoMini.src = auto;
      else logoMini.style.display = "none";
    });
    logoMini.alt = "Tienda Monjarrez";
    logoMini.addEventListener("click", function () { cerrarHojas(); aplicarFiltro("filter", "Todo"); });
    grupo.parentNode.insertBefore(logoMini, grupo);
  }

  function inyectarEstilos() {
    if (document.getElementById("mtExtraCss")) return;
    var st = crear("style");
    st.id = "mtExtraCss";
    st.textContent =
      "img.mt-logo-mini{display:none}" +
      "body.mt-on img.mt-logo-mini{display:block;width:22px;height:22px;object-fit:contain;flex:0 0 22px;margin:0 4px 0 6px;cursor:pointer}" +
      ".mt-flota{transition:opacity .25s ease,visibility .25s ease}" +
      "body.mt-on.mt-hide .mt-flota{opacity:0!important;visibility:hidden!important;pointer-events:none!important}";
    document.head.appendChild(st);
  }

  /* ---------- MONJI Y REDES SOCIALES: se ocultan igual que el encabezado ---------- */

  // Si conoces los selectores exactos de Monji y de las redes, ponlos aquí.
  var SELECTORES_FLOTANTES = [
    '[id*="monji" i]', '[class*="monji" i]',
    '[id*="redes" i]', '[class*="redes" i]',
    '[id*="social" i]', '[class*="social" i]'
  ];
  var NO_TOCAR = "#mtTop, #mtNav, .mt-overlay, .mt-sheet, .mt-full, #cartHeaderBtn, [id*='modal' i], [class*='modal' i], [class*='perfil' i], [id*='perfil' i]";

  function marcarFlotantes() {
    var cand = [];
    SELECTORES_FLOTANTES.forEach(function (s) { cand = cand.concat(qa(s)); });
    // Además, cualquier elemento pequeño con position:fixed pegado al borde (burbujas, botones sueltos).
    qa("body > *, body > * > *").forEach(function (e) { cand.push(e); });
    cand.forEach(function (e) {
      if (e.classList.contains("mt-flota") || e.closest(NO_TOCAR)) return;
      var cs = getComputedStyle(e);
      if (cs.position !== "fixed" || cs.display === "none") return;
      var r = e.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) return;
      // Descarta lo grande (modales, pantallas completas) y lo que no es flotante lateral.
      if (r.width > window.innerWidth * 0.5 || r.height > window.innerHeight * 0.4) return;
      e.classList.add("mt-flota");
    });
  }

  /* --------------------- OCULTAR / MOSTRAR AL HACER SCROLL --------------------- */

  var ultimoY = 0;
  function alHacerScroll() {
    if (!MQ.matches) return;
    var y = window.pageYOffset || document.documentElement.scrollTop || 0;
    var d = y - ultimoY;
    if (Math.abs(d) < 6) return;
    var buscando = document.activeElement && document.activeElement.id === "search";
    if (y < 80 || d < 0) document.body.classList.remove("mt-hide");
    else if (!hojaAbierta && !buscando) {
      if (!document.body.classList.contains("mt-hide")) marcarFlotantes();
      document.body.classList.add("mt-hide");
    }
    ultimoY = y;
  }

  /* ------------------- ACTIVAR / DESACTIVAR SEGÚN EL ANCHO ------------------- */

  function medirAlto() {
    document.documentElement.style.setProperty("--mt-top-h", top.offsetHeight + "px");
  }

  function activar() {
    if (document.body.classList.contains("mt-on")) return;
    document.body.classList.add("mt-on");
    inyectarEstilos();
    // El <header> original (con la búsqueda y el camioncito) se mueve a la barra fija.
    if (header && header.parentNode !== top) {
      marcadorHeader = document.createComment("mt-header");
      header.parentNode.insertBefore(marcadorHeader, header);
      top.insertBefore(header, top.firstChild);
    }
    ponerLogoMini();
    medirAlto();
    sincronizarTabs();
    actualizarBadgeCarrito();
    actualizarCandado();
    clearInterval(timerAviso);
    timerAviso = setInterval(rotarAviso, MS_ROTAR_AVISO);
  }

  function desactivar() {
    if (!document.body.classList.contains("mt-on")) return;
    cerrarHojas();
    document.body.classList.remove("mt-on", "mt-hide");
    document.documentElement.style.removeProperty("--mt-top-h");
    if (header && marcadorHeader && marcadorHeader.parentNode) {
      marcadorHeader.parentNode.insertBefore(header, marcadorHeader);
      marcadorHeader.parentNode.removeChild(marcadorHeader);
    }
    clearInterval(timerAviso);
  }

  function alCambiarAncho() { if (MQ.matches) activar(); else desactivar(); }

  /* ---------------------------------- INICIO ---------------------------------- */

  function iniciar() {
    construir();
    observarFiltros();
    observarEspejos();
    window.addEventListener("scroll", alHacerScroll, { passive: true });
    window.addEventListener("resize", function () { if (MQ.matches) medirAlto(); });
    if (window.ResizeObserver) new ResizeObserver(function () { if (MQ.matches) medirAlto(); }).observe(top);
    document.addEventListener("keydown", function (e) { if (e.key === "Escape" && hojaAbierta) cerrarHojas(); });
    if (MQ.addEventListener) MQ.addEventListener("change", alCambiarAncho);
    else if (MQ.addListener) MQ.addListener(alCambiarAncho);
    alCambiarAncho();
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", iniciar);
  else iniciar();
})();