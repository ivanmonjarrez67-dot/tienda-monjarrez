/* Aviso de la promoción "producto adicional GRATIS" para carrito.html y pedido-confirmado.html.
   Uso: <script src="promo-aviso.js"></script> al final del <body> (opcional: <div id="promoAviso"></div>
   donde quieras que salga; si no existe, se pone arriba del contenido).
   En pedido-confirmado.html el enlace debe llevar ?pedido=ID para confirmar que ese pedido ganó el regalo. */
(function () {
  var qs = new URLSearchParams(location.search);
  var pedido = qs.get("pedido") || qs.get("pedido_id") || qs.get("id");
  fetch("/promoCupon" + (pedido ? "?pedido=" + encodeURIComponent(pedido) : ""), { cache: "no-store" })
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (c) {
      if (!c || !c.activa) return;
      var vigente = new Date(c.fechaFin).getTime() > Date.now() && c.restantes > 0;
      var gana = c.tuPedidoGana === true;
      if (!vigente && !gana) return;
      var cat = c.categoria || "Joyería y Accesorios";
      var quien = c.entrega === "comprador" ? "Podrás elegirlo tú." : "Te lo asignamos nosotros.";
      var html = gana
        ? "<strong>¡Tu pedido incluye un producto adicional GRATIS de " + cat + "!</strong> " + quien
        : "<strong>Tu compra participa:</strong> recibe un producto adicional GRATIS de " + cat + ". Quedan " + c.restantes + " de " + c.cupos + ".";
      var box = document.createElement("div");
      box.setAttribute("role", "status");
      box.style.cssText = "background:#fdf0f2;border:2px dashed #c8102e;border-left:8px solid #c8102e;border-radius:10px;" +
        "color:#1a1a1a;padding:12px 14px;margin:12px auto;max-width:900px;text-align:left;font:14px/1.4 system-ui,Segoe UI,sans-serif;";
      box.innerHTML = "🎁 " + html;
      var dest = document.getElementById("promoAviso") || document.querySelector("main") || document.querySelector(".container") || document.body;
      dest.insertBefore(box, dest.firstChild);
    })
    .catch(function () {});
})();