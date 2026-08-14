/* ============================================================
   MASCOTA.JS — Widget flotante de Monji + chat integrado
   Un solo widget: al hacer click en Monji se abre el chat
   DENTRO de su propio globo (fondo blanco, crece hacia arriba).
   Ya no existe un botón de chat aparte — bórralo de tu
   index.html si venías de una versión anterior (monji-chat.css
   / monji-chat.js): solo deja mascota.css y mascota.js.
   ============================================================ */
(function () {
  "use strict";

  // 🔧 Ajusta esta ruta si guardas las imágenes en otra carpeta.
  const CARPETA_IMAGENES = "imagenes/monji/";

  const POSES = {
    idle: "monji_transparente.png",
    saludo: "monji_saludando.png",   // opcional: si no la tienes aún, se usa "idle"
    exito: "monji_pulgar_arriba.png", // opcional
    buscando: "monji_lupa.png",       // opcional
  };

  // Cuántos píxeles debe moverse el puntero para contar como arrastre.
  const UMBRAL_ARRASTRE = 6;
  const CLAVE_POSICION = "monji-posicion";

  // ============================================================
  // PREGUNTAS FRECUENTES — edita aquí los textos.
  // Si "respuestas" tiene varias frases, Monji elige una al azar
  // cada vez, para que no suene repetitivo.
  // ============================================================
  const MONJI_FAQ = {
    inicio: {
      mensaje: "¡Hola! Soy <strong>Monji</strong> 🦊<br>¿En qué te puedo ayudar hoy?",
      opciones: [
        { texto: "🛒 ¿Cómo publico un producto?", id: "publicar_producto" },
        { texto: "👤 ¿Cómo me hago vendedor?", id: "ser_vendedor" },
        { texto: "💳 ¿Qué incluye el Plan Básico?", id: "plan_basico" },
        { texto: "⭐ ¿Qué incluye el Plan Avanzado?", id: "plan_avanzado" },
        { texto: "🔐 ¿Cómo cambio mi contraseña?", id: "cambiar_contrasena" },
        { texto: "🔑 Olvidé mi contraseña.", id: "olvide_contrasena" },
        { texto: "📞 ¿Cómo contacto a un vendedor?", id: "contactar_vendedor" },
        { texto: "🚚 ¿Tienen servicio de envíos?", id: "servicio_envios" },
      ],
    },
    publicar_producto: {
      respuestas: [
        "🛍️ ¡Con gusto! Inicia sesión como vendedor y, en cuanto nuestro equipo verifique tu suscripción, entra a \"Mi Tienda\" y selecciona \"Agregar producto\". Completa la información, guarda los cambios ¡y listo, tu producto estará visible para miles de compradores!",
      ],
      opciones: [
        { texto: "➕ Agregar otro producto", id: "publicar_producto" },
        { texto: "💳 Ver planes", id: "ver_planes" },
        { texto: "❓ Hacer otra consulta", id: "inicio" },
      ],
    },
    ser_vendedor: {
      respuestas: [
        "🚀 ¡Excelente decisión! Crea tu cuenta, ve a tu perfil y selecciona \"¿Quieres vender? Publica tus productos\". Una vez que tu solicitud sea aprobada, podrás empezar a publicar en \"Mi Tienda\" y hacer crecer tu negocio con nosotros.",
      ],
      opciones: [
        { texto: "💳 Ver planes", id: "ver_planes" },
        { texto: "🛒 ¿Cómo publico un producto?", id: "publicar_producto" },
        { texto: "❓ Hacer otra consulta", id: "inicio" },
      ],
    },
    plan_basico: {
      // ✏️ EDITA con los datos reales de tu Plan Básico
      respuestas: [
        "📦 El Plan Básico te permite publicar tus primeros productos y empezar a vender sin costo inicial.",
        "El Plan Básico es ideal si estás comenzando: publica productos y prueba la plataforma sin complicarte.",
      ],
      opciones: [
        { texto: "⭐ Ver Plan Avanzado", id: "plan_avanzado" },
        { texto: "👤 ¿Cómo me hago vendedor?", id: "ser_vendedor" },
        { texto: "❓ Hacer otra consulta", id: "inicio" },
      ],
    },
    plan_avanzado: {
      respuestas: [
        "⭐ El Plan Avanzado te permite publicar hasta 14 productos y acceder antes a nuevas funciones.",
        "🚀 Si quieres hacer crecer tu negocio, el Plan Avanzado es la mejor opción. Incluye 14 publicaciones y beneficios exclusivos.",
        "Me alegra que preguntes. 😊 El Plan Avanzado está pensado para vendedores que desean mostrar un catálogo más amplio.",
      ],
      opciones: [
        { texto: "📦 Ver Plan Básico", id: "plan_basico" },
        { texto: "👤 ¿Cómo me hago vendedor?", id: "ser_vendedor" },
        { texto: "❓ Hacer otra consulta", id: "inicio" },
      ],
    },
    cambiar_contrasena: {
      respuestas: [
        "🔐 Ve a tu <strong>perfil</strong> (ícono de usuario) y abre el menú desplegable \"Cambiar contraseña\". Ingresa tu contraseña actual, luego la nueva contraseña y confírmala, y pulsa \"Actualizar contraseña\". Por tu seguridad, primero verificamos que la actual sea correcta antes de guardar la nueva.",
      ],
      opciones: [
        { texto: "🔑 Olvidé mi contraseña actual", id: "olvide_contrasena" },
        { texto: "❓ Hacer otra consulta", id: "inicio" },
      ],
    },
    olvide_contrasena: {
      respuestas: [
        "🔑 Ve a la pantalla de inicio de sesión y pulsa \"¿Olvidaste tu contraseña?\". Te enviaremos un código de 6 dígitos a tu correo: ingrésalo junto con tu nueva contraseña y listo, ya podrás volver a iniciar sesión.",
      ],
      opciones: [
        { texto: "📞 ¿Cómo contacto a un vendedor?", id: "contactar_vendedor" },
        { texto: "❓ Hacer otra consulta", id: "inicio" },
      ],
    },
    contactar_vendedor: {
      respuestas: [
        "📞 ¡Muy fácil! Solo son 3 pasos:<br>1️⃣ Entra al producto que te interesa y pulsa \"Ver detalles\".<br>2️⃣ Selecciona el ícono de WhatsApp o de Correo.<br>3️⃣ Dale a enviar — nosotros redactamos el mensaje por ti.",
      ],
      opciones: [
        { texto: "🛒 ¿Cómo publico un producto?", id: "publicar_producto" },
        { texto: "❓ Hacer otra consulta", id: "inicio" },
      ],
    },
    servicio_envios: {
      respuestas: [
        "🚚 Depende de la zona. Si el producto que te interesa está lejos, comunícate con nosotros al correo que aparece en el ícono de notificaciones (🔔) o directamente a <a href=\"mailto:tiendamonjarrez@gmail.com?subject=Informacion%20sobre%20envio%20de%20productos&body=Hola%2C%0A%0AQuisiera%20consultar%20si%20es%20posible%20coordinar%20el%20envio%20de%20un%20producto%20hasta%20mi%20zona.%0A%0AQuedo%20atento%20a%20su%20respuesta%2C%20muchas%20gracias.%0A%0ASaludos%20cordiales.\">tiendamonjarrez@gmail.com</a>, y con gusto validamos si podemos coordinar el envío hasta tu ubicación.",
      ],
      opciones: [
        { texto: "📞 ¿Cómo contacto a un vendedor?", id: "contactar_vendedor" },
        { texto: "❓ Hacer otra consulta", id: "inicio" },
      ],
    },
    ver_planes: {
      respuestas: [
        "Tenemos dos planes: el Plan Básico para empezar, y el Plan Avanzado si quieres publicar más productos y acceder antes a nuevas funciones. ¿Cuál quieres ver?",
      ],
      opciones: [
        { texto: "📦 Plan Básico", id: "plan_basico" },
        { texto: "⭐ Plan Avanzado", id: "plan_avanzado" },
        { texto: "❓ Hacer otra consulta", id: "inicio" },
      ],
    },
  };

  let chatYaIniciado = false;

  function crearWidget() {
    if (document.getElementById("monji-widget")) return;

    const widget = document.createElement("div");
    widget.id = "monji-widget";
    widget.innerHTML = `
      <div class="monji-chat" id="monji-chat">
        <div class="monji-chat-header">
          <span class="monji-chat-titulo">
            <img src="${CARPETA_IMAGENES}monji-avatar.png" alt="Monji" class="monji-chat-avatar" id="monji-avatar-chat">
            <span class="dot"></span> Chatea con Monji
          </span>
          <button type="button" class="monji-chat-cerrar" id="monji-chat-cerrar" aria-label="Cerrar chat">✕</button>
        </div>
        <div class="monji-chat-mensajes" id="monji-chat-mensajes"></div>
        <div class="monji-chat-opciones" id="monji-chat-opciones"></div>
      </div>
      <button type="button" class="monji-boton" id="monji-boton" aria-label="Abrir chat con Monji">
        <img src="${CARPETA_IMAGENES}${POSES.idle}" alt="Monji, la mascota de Tienda Monjarrez" id="monji-imagen">
      </button>
    `;
    document.body.appendChild(widget);

    const boton = document.getElementById("monji-boton");
    const chat = document.getElementById("monji-chat");
    const cerrarBtn = document.getElementById("monji-chat-cerrar");
    const mensajesEl = document.getElementById("monji-chat-mensajes");
    const opcionesEl = document.getElementById("monji-chat-opciones");

    // Si "monji-avatar.png" no existe en el servidor, no dejamos el
    // ícono de imagen rota — se oculta silenciosamente.
    const avatarChat = document.getElementById("monji-avatar-chat");
    if (avatarChat) {
      avatarChat.onerror = () => { avatarChat.style.display = "none"; };
    }

    restaurarPosicion(widget);

    // --- Arrastre (mouse + touch) ---
    let arrastrando = false;
    let huboArrastre = false;
    let inicioPunteroX = 0;
    let inicioPunteroY = 0;
    let inicioWidgetX = 0;
    let inicioWidgetY = 0;

    boton.addEventListener("pointerdown", (e) => {
      arrastrando = true;
      huboArrastre = false;
      const rect = widget.getBoundingClientRect();
      inicioPunteroX = e.clientX;
      inicioPunteroY = e.clientY;
      inicioWidgetX = rect.left;
      inicioWidgetY = rect.top;
      boton.setPointerCapture(e.pointerId);
    });

    boton.addEventListener("pointermove", (e) => {
      if (!arrastrando) return;
      const dx = e.clientX - inicioPunteroX;
      const dy = e.clientY - inicioPunteroY;

      if (!huboArrastre && (Math.abs(dx) > UMBRAL_ARRASTRE || Math.abs(dy) > UMBRAL_ARRASTRE)) {
        huboArrastre = true;
        widget.classList.add("monji-arrastrando");
      }
      if (!huboArrastre) return;

      moverWidgetA(widget, inicioWidgetX + dx, inicioWidgetY + dy);
    });

    function terminarArrastre() {
      if (!arrastrando) return;
      arrastrando = false;
      widget.classList.remove("monji-arrastrando");
      if (huboArrastre) guardarPosicion(widget);
    }
    boton.addEventListener("pointerup", terminarArrastre);
    boton.addEventListener("pointercancel", terminarArrastre);

    window.addEventListener("resize", () => {
      const rect = widget.getBoundingClientRect();
      moverWidgetA(widget, rect.left, rect.top);
    });

    // --- Abrir / cerrar chat al hacer click en Monji ---
    boton.addEventListener("click", (e) => {
      e.stopPropagation();
      if (huboArrastre) {
        huboArrastre = false;
        return;
      }
      const yaAbierto = chat.classList.contains("monji-visible");
      if (yaAbierto) {
        cerrarChat();
      } else {
        abrirChat();
      }
    });

    cerrarBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      cerrarChat();
    });

    document.addEventListener("click", (e) => {
      if (!widget.contains(e.target)) cerrarChat();
    });
    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape") cerrarChat();
    });

    function abrirChat() {
      chat.classList.add("monji-visible");
      cambiarPose("saludo");
      if (!chatYaIniciado) {
        chatYaIniciado = true;
        renderNodo("inicio", false);
      }
    }

    function cerrarChat() {
      chat.classList.remove("monji-visible");
      cambiarPose("idle");
    }

    // ============================================================
    // Motor del chat (mismo patrón que monji-chat.js, ahora vive
    // aquí dentro para compartir el mismo globo/panel con Monji).
    // ============================================================
    function scrollAbajo() {
      mensajesEl.scrollTop = mensajesEl.scrollHeight;
    }

    function agregarBurbuja(html, quien) {
      const fila = document.createElement("div");
      fila.className = "monji-fila " + quien;
      const burbuja = document.createElement("div");
      burbuja.className = "monji-burbuja";
      burbuja.innerHTML = html;
      fila.appendChild(burbuja);
      mensajesEl.appendChild(fila);
      scrollAbajo();
    }

    function mostrarEscribiendo() {
      const fila = document.createElement("div");
      fila.className = "monji-fila bot";
      fila.id = "monji-typing-row";
      fila.innerHTML = '<div class="monji-typing"><span></span><span></span><span></span></div>';
      mensajesEl.appendChild(fila);
      scrollAbajo();
    }

    function quitarEscribiendo() {
      document.getElementById("monji-typing-row")?.remove();
    }

    function elegirAlAzar(lista) {
      return lista[Math.floor(Math.random() * lista.length)];
    }

    function renderOpciones(opciones) {
      opcionesEl.innerHTML = "";
      (opciones || []).forEach((op) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "monji-opcion-btn";
        btn.textContent = op.texto;
        btn.addEventListener("click", (e) => {
          e.stopPropagation();
          manejarClickOpcion(op);
        });
        opcionesEl.appendChild(btn);
      });
    }

    function manejarClickOpcion(opcion) {
      opcionesEl.innerHTML = "";
      agregarBurbuja(opcion.texto, "user");
      renderNodo(opcion.id, true);
    }

    function renderNodo(id, comoUsuario) {
      const nodo = MONJI_FAQ[id];
      if (!nodo) return;
      const mensaje = nodo.mensaje || elegirAlAzar(nodo.respuestas);

      if (!comoUsuario) {
        agregarBurbuja(mensaje, "bot");
        renderOpciones(nodo.opciones);
        return;
      }

      mostrarEscribiendo();
      const retraso = 1100 + Math.random() * 700;
      setTimeout(() => {
        quitarEscribiendo();
        agregarBurbuja(mensaje, "bot");
        renderOpciones(nodo.opciones);
      }, retraso);
    }

    // ============================================================
    // Reacciones espontáneas (producto agregado con éxito, búsqueda
    // sin resultados, etc.). No requieren que el chat esté abierto:
    // si está cerrado, muestra una tarjeta flotante junto a Monji;
    // si está abierto, agrega el mensaje como una burbuja más.
    // ============================================================
    let temporizadorGlobo = null;
    let temporizadorPose = null;

    function inyectarEstiloReaccion() {
      if (document.getElementById("monji-reaccion-estilos")) return;
      const style = document.createElement("style");
      style.id = "monji-reaccion-estilos";
      style.textContent = `
        #monji-reaccion-globo {
          position: absolute;
          bottom: calc(100% + 14px);
          right: -6px;
          min-width: 190px;
          max-width: min(80vw, 250px);
          background: #ffffff;
          border-radius: 14px;
          padding: 12px 16px 13px;
          box-shadow: 0 14px 32px rgba(0, 0, 0, 0.45);
          opacity: 0;
          transform: translateY(8px) scale(0.96);
          transition: opacity .28s ease, transform .28s ease;
          pointer-events: none;
          z-index: 2147483000;
          font-family: Arial, sans-serif;
          border-left: 4px solid #c81e1e;
        }
        #monji-reaccion-globo.monji-reaccion-visible {
          opacity: 1;
          transform: translateY(0) scale(1);
        }
        #monji-reaccion-globo::after {
          content: "";
          position: absolute;
          bottom: -8px;
          right: 30px;
          border-width: 8px 8px 0 8px;
          border-style: solid;
          border-color: #ffffff transparent transparent transparent;
          filter: drop-shadow(0 2px 1px rgba(0,0,0,0.12));
        }
        #monji-reaccion-globo .monji-reaccion-titulo {
          display: flex;
          align-items: center;
          gap: 6px;
          font-weight: 700;
          font-size: 13px;
          margin: 0 0 4px 0;
          color: #b0192a;
        }
        #monji-reaccion-globo .monji-reaccion-texto {
          font-size: 13.5px;
          line-height: 1.48;
          color: #262626;
          margin: 0;
        }
        #monji-reaccion-globo.monji-reaccion-exito { border-left-color: #2ecc71; }
        #monji-reaccion-globo.monji-reaccion-exito .monji-reaccion-titulo { color: #1e9e56; }
        #monji-reaccion-globo.monji-reaccion-buscando { border-left-color: #c81e1e; }
        #monji-reaccion-globo.monji-reaccion-buscando .monji-reaccion-titulo { color: #b0192a; }
      `;
      document.head.appendChild(style);
    }

    function mostrarGloboReaccion(texto, nombrePose) {
      inyectarEstiloReaccion();

      let globo = document.getElementById("monji-reaccion-globo");
      if (!globo) {
        globo = document.createElement("div");
        globo.id = "monji-reaccion-globo";
        widget.appendChild(globo);
      }

      const esExito = nombrePose === "exito";
      globo.className = esExito ? "monji-reaccion-exito" : "monji-reaccion-buscando";
      globo.innerHTML = `
        <p class="monji-reaccion-titulo">${esExito ? "🎉 ¡Listo!" : "🔍 Sin resultados"}</p>
        <p class="monji-reaccion-texto">${texto}</p>
      `;

      requestAnimationFrame(() => globo.classList.add("monji-reaccion-visible"));

      // ⏱️ Tiempo de lectura proporcional al largo del mensaje, para que
      // siempre dé tiempo a leerlo (antes era fijo y muy corto).
      const soloTexto = texto.replace(/<[^>]*>/g, "");
      const duracion = Math.min(9500, Math.max(5000, 2200 + soloTexto.length * 65));

      clearTimeout(temporizadorGlobo);
      temporizadorGlobo = setTimeout(() => {
        globo.classList.remove("monji-reaccion-visible");
      }, duracion);

      return duracion;
    }

    function reaccionar(nombrePose, mensaje) {
      const chatAbierto = chat.classList.contains("monji-visible");
      cambiarPose(nombrePose);

      let duracion = 4000;
      if (chatAbierto) {
        agregarBurbuja(mensaje, "bot");
      } else {
        duracion = mostrarGloboReaccion(mensaje, nombrePose);
      }

      clearTimeout(temporizadorPose);
      temporizadorPose = setTimeout(() => {
        cambiarPose(chatAbierto ? "saludo" : "idle");
      }, duracion);
    }

    // Expone el motor para la API pública de más abajo
    widget._monjiChat = { agregarBurbuja, mostrarEscribiendo, quitarEscribiendo, abrirChat, reaccionar };
  }

  function moverWidgetA(widget, x, y) {
    const anchoWidget = widget.offsetWidth;
    const altoWidget = widget.offsetHeight;
    const maxX = window.innerWidth - anchoWidget;
    const maxY = window.innerHeight - altoWidget;

    x = Math.min(Math.max(x, 0), Math.max(maxX, 0));
    y = Math.min(Math.max(y, 0), Math.max(maxY, 0));

    widget.style.right = "auto";
    widget.style.bottom = "auto";
    widget.style.left = x + "px";
    widget.style.top = y + "px";
  }

  function guardarPosicion(widget) {
    const rect = widget.getBoundingClientRect();
    try {
      sessionStorage.setItem(CLAVE_POSICION, JSON.stringify({ x: rect.left, y: rect.top }));
    } catch (err) {}
  }

  function restaurarPosicion(widget) {
    let guardada = null;
    try {
      guardada = JSON.parse(sessionStorage.getItem(CLAVE_POSICION));
    } catch (err) {
      guardada = null;
    }
    if (guardada && typeof guardada.x === "number" && typeof guardada.y === "number") {
      moverWidgetA(widget, guardada.x, guardada.y);
    }
  }

  function cambiarPose(nombrePose) {
    const img = document.getElementById("monji-imagen");
    if (!img || !POSES[nombrePose]) return;
    img.src = CARPETA_IMAGENES + POSES[nombrePose];
    // Si la imagen de esa pose todavía no existe en el servidor,
    // vuelve silenciosamente a la pose de reposo para no romper el diseño.
    img.onerror = () => {
      img.onerror = null;
      img.src = CARPETA_IMAGENES + POSES.idle;
    };
  }

  // ============================================================
  // API pública: llamar desde script.js cuando ocurra algo que
  // Monji deba celebrar o comentar, sin necesidad de que el chat
  // esté abierto. Ejemplos:
  //   Monji.exito("¡Tu producto ya está publicado! 🎉");
  //   Monji.buscando("No encontré productos con esos filtros 🔍");
  // ============================================================
  window.Monji = {
    exito: function (mensaje) {
      const widget = document.getElementById("monji-widget");
      if (widget && widget._monjiChat) {
        widget._monjiChat.reaccionar("exito", mensaje || "¡Listo! 🎉");
      }
    },
    buscando: function (mensaje) {
      const widget = document.getElementById("monji-widget");
      if (widget && widget._monjiChat) {
        widget._monjiChat.reaccionar("buscando", mensaje || "No encontré nada por aquí 🔍");
      }
    },
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", crearWidget);
  } else {
    crearWidget();
  }
})();