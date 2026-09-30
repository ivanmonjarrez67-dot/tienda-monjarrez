package entidades.controladores;

import java.io.IOException;

import entidades.SesionPersistente;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Cerrar sesión de verdad: borra la sesión larga (fila + cookie) y la sesión del servidor. */
@WebServlet("/CerrarSesionServlet")
public class CerrarSesionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SesionPersistente.cerrar(request, response);
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("OK");
    }
}