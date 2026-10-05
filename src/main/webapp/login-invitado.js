/* ==========================================================================
   login-invitado.js — Diálogo único para invitados que necesitan iniciar sesión
   --------------------------------------------------------------------------
   Todo lo que antes mandaba al invitado a iniciar sesión (carrito, agregar al
   carrito, seguir a un vendedor, Mi tienda, mis pedidos, el aviso rojo, los
   3 puntitos...) ahora muestra ESTE mismo cuadro:
       [ Iniciar sesión o registrarme ]
                     o
            [ Continuar con Google ]
   El botón de Google es el normal (no el "Continuar como…" que Google deja de
   mostrar un rato si se cierra con la X), así que siempre se puede mostrar.

   Funciona en index.html y en las demás páginas (detalle, perfil de vendedor,
   mis pedidos, carrito...). Cargar con <script src="login-invitado.js"></script>
   (carrito.js lo carga solo si la página no lo tiene).

   Uso:  MTLoginInvitado.mostrar({ mensaje, titulo, alIniciar, alCerrar })
   ========================================================================== */
(function () {
  "use strict";
  if (window.MTLoginInvitado) return;

  // Mismo Client ID que usa script.js (solo se usa en las páginas que no cargan script.js).
  var GOOGLE_CLIENT_ID = "1084676337902-nub3qelb3qv7be1dq108iff6m5gbagfn.apps.googleusercontent.com";
  var DURACION_COMPRADOR = 90 * 24 * 60 * 60 * 1000; // igual que index.html

  var ov = null, opcionesActuales = null, vigilante = null;

  /* ------------------------------ sesión ------------------------------ */
  function haySesion() {
    try {
      if (typeof window.haySesionActiva === "function") return !!window.haySesionActiva();
      if (window.Carrito && window.Carrito.haySesion) return !!window.Carrito.haySesion();
      var s = JSON.parse(localStorage.getItem("sesionUsuario"));
      return !!(s && s.tipo && Date.now() <= s.expiracion);
    } catch (e) { return false; }
  }
  function esIndex() { return typeof window.mostrarLoginDirecto === "function"; }

  /* ------------------------------ estilos ------------------------------ */
  function estilos() {
    if (document.getElementById("mtliEstilos")) return;
    var st = document.createElement("style");
    st.id = "mtliEstilos";
    st.textContent =
      "#mtliOv{position:fixed;inset:0;z-index:2147483000;display:none;align-items:center;justify-content:center;padding:16px;box-sizing:border-box;background:rgba(0,0,0,.55);font-family:'Poppins',system-ui,sans-serif}" +
      "#mtliOv.on{display:flex}" +
      ".mtli-card{position:relative;width:min(380px,100%);box-sizing:border-box;padding:26px 22px 20px;background:#fff;color:#1a1a1a;border-radius:18px;box-shadow:0 20px 50px rgba(0,0,0,.45);text-align:center}" +
      ".mtli-x{position:absolute;top:10px;right:10px;width:32px;height:32px;border:0;border-radius:50%;background:#ececf0;color:#333;font-size:18px;line-height:1;cursor:pointer}" +
      ".mtli-card h3{margin:0 0 8px;font-size:19px;color:#1a1a1a}" +
      ".mtli-card p{margin:0 0 18px;font-size:14px;line-height:1.45;color:#555}" +
      ".mtli-principal{display:block;width:100%;box-sizing:border-box;padding:13px 14px;border:0;border-radius:12px;background:linear-gradient(135deg,#e02424,#a31212);color:#fff;font:700 15px 'Poppins',system-ui,sans-serif;cursor:pointer}" +
      ".mtli-principal:hover{filter:brightness(1.08)}" +
      ".mtli-o{display:flex;align-items:center;gap:8px;margin:14px 0 10px;font-size:13px;color:#6b6b76}" +
      ".mtli-o::before,.mtli-o::after{content:'';flex:1;height:1px;background:#e4e4e8}" +
      ".mtli-google{display:flex;justify-content:center;min-height:44px}" +
      ".mtli-ahora{margin-top:14px;border:0;background:none;color:#6b6b76;font:500 13px 'Poppins',system-ui,sans-serif;text-decoration:underline;cursor:pointer}";
    document.head.appendChild(st);
  }

  /* ------------------------------ Google ------------------------------ */
  // Páginas que no cargan script.js: se inicia Google aquí mismo.
  var gsiListo = false;
  function alCredencial(resp) {
    fetch("LoginCompradorGoogleServlet", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ credential: resp.credential }).toString()
    }).then(function (res) {
      if (res.ok) {
        try {
          localStorage.setItem("sesionUsuario", JSON.stringify({ tipo: "comprador", expiracion: Date.now() + DURACION_COMPRADOR }));
        } catch (e) {}
        var op = opcionesActuales;
        cerrar(true);
        if (op && typeof op.alIniciar === "function") op.alIniciar();
        return;
      }
      if (res.status === 404) {
        alert("Ese correo de Google todavía no tiene cuenta en Tienda Monjarrez. Te llevamos a registrarte.");
        window.location.href = "index.html?accion=registro-comprador";
        return;
      }
      return res.text().then(function (t) { alert(t || "No se pudo iniciar sesión con Google."); });
    }).catch(function (err) { alert("No se pudo iniciar sesión con Google: " + err.message); });
  }
  function cargarGsi(cb) {
    if (window.google && window.google.accounts && window.google.accounts.id) { cb(); return; }
    var s = document.getElementById("mtliGsi");
    if (!s) {
      s = document.createElement("script");
      s.id = "mtliGsi";
      s.src = "https://accounts.google.com/gsi/client";
      s.async = true;
      document.head.appendChild(s);
    }
    s.addEventListener("load", cb);
  }
  function pintarGoogle(cont, listo) {
    // index.html: script.js ya inició Google y maneja el resultado.
    if (typeof window.renderBotonGoogle === "function") { window.renderBotonGoogle(cont, 260, listo); return; }
    cargarGsi(function () {
      try {
        if (!gsiListo) {
          window.google.accounts.id.initialize({
            client_id: GOOGLE_CLIENT_ID, callback: alCredencial, auto_select: false,
            cancel_on_tap_outside: false, context: "signin", itp_support: true, use_fedcm_for_prompt: true
          });
          gsiListo = true;
        }
        window.google.accounts.id.renderButton(cont, { theme: "outline", size: "large", text: "continue_with", shape: "pill", width: 260, locale: "es" });
        listo();
      } catch (e) { /* sin Google: el cuadro queda solo con el botón rojo */ }
    });
  }

  /* ------------------------------ diálogo ------------------------------ */
  function cerrar(porLogin) {
    clearInterval(vigilante); vigilante = null;
    if (ov) ov.classList.remove("on");
    var op = opcionesActuales;
    opcionesActuales = null;
    if (!porLogin && op && typeof op.alCerrar === "function") op.alCerrar();
  }

  function mostrar(opciones) {
    opciones = opciones || {};
    if (haySesion()) return;
    estilos();
    opcionesActuales = opciones;

    if (!ov) {
      ov = document.createElement("div");
      ov.id = "mtliOv";
      ov.setAttribute("role", "dialog");
      ov.setAttribute("aria-modal", "true");
      ov.addEventListener("click", function (e) { if (e.target === ov) cerrar(false); });
      document.addEventListener("keydown", function (e) { if (e.key === "Escape" && ov.classList.contains("on")) cerrar(false); });
      document.body.appendChild(ov);
    }

    ov.innerHTML = "";
    var card = document.createElement("div");
    card.className = "mtli-card";

    var x = document.createElement("button");
    x.type = "button"; x.className = "mtli-x"; x.setAttribute("aria-label", "Cerrar"); x.textContent = "\u00D7";
    x.addEventListener("click", function () { cerrar(false); });

    var h = document.createElement("h3");
    h.textContent = opciones.titulo || "Inicia sesión para continuar";
    var p = document.createElement("p");
    p.textContent = opciones.mensaje || "Inicia sesión o regístrate en Tienda Monjarrez. Es rápido y gratis.";

    var principal = document.createElement("button");
    principal.type = "button"; principal.className = "mtli-principal";
    principal.textContent = "Iniciar sesión o registrarme";
    principal.addEventListener("click", function () {
      var op = opcionesActuales;
      if (esIndex()) {
        cerrar(true);
        window.mostrarLoginDirecto(op && op.mensaje);
      } else {
        window.location.href = "index.html?accion=login-comprador";
      }
    });

    // El "o" y el botón de Google solo se muestran si Google lo dibujó de verdad.
    var gWrap = document.createElement("div");
    gWrap.style.display = "none";
    var o = document.createElement("div"); o.className = "mtli-o"; o.innerHTML = "<span>o</span>";
    var gBtn = document.createElement("div"); gBtn.className = "mtli-google";
    gWrap.appendChild(o); gWrap.appendChild(gBtn);

    var ahora = document.createElement("button");
    ahora.type = "button"; ahora.className = "mtli-ahora"; ahora.textContent = "Ahora no";
    ahora.addEventListener("click", function () { cerrar(false); });

    card.appendChild(x); card.appendChild(h); card.appendChild(p);
    card.appendChild(principal); card.appendChild(gWrap); card.appendChild(ahora);
    ov.appendChild(card);
    ov.classList.add("on");

    pintarGoogle(gBtn, function () { gWrap.style.display = ""; });

    // Si la persona entra (por cualquier camino), el cuadro se quita solo.
    clearInterval(vigilante);
    vigilante = setInterval(function () { if (haySesion()) cerrar(true); }, 500);
  }

  window.MTLoginInvitado = { mostrar: mostrar, cerrar: function () { cerrar(true); } };

  /* ------------------------------ index.html ------------------------------ */
  // Todo lo que llamaba a mostrarLogin() para un invitado (carrito, Mi tienda,
  // avisos...) ahora abre este diálogo. El modal "Ingresar como" original queda
  // disponible como mostrarLoginDirecto() (lo usa el botón rojo del diálogo) y
  // sigue saliendo solo al cerrar sesión (forzar = true).
  if (typeof window.mostrarLogin === "function" && document.getElementById("loginModal") && !window.mostrarLoginDirecto) {
    var original = window.mostrarLogin;
    window.mostrarLoginDirecto = original;
    window.mostrarLogin = function (mensaje, forzar) {
      if (forzar || haySesion()) return original.apply(this, arguments);
      mostrar({ mensaje: mensaje });
    };
  }
})();