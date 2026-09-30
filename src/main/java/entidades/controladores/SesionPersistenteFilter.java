package entidades.controladores;

import java.io.IOException;

import entidades.SesionPersistente;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Si no hay sesión activa pero existe la cookie larga, restaura la sesión del comprador. */
@WebFilter("/*")
public class SesionPersistenteFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpSession s = req.getSession(false);
        boolean sinSesion = s == null || s.getAttribute("usuarioId") == null;
        if (sinSesion) {
            SesionPersistente.restaurar(req, (HttpServletResponse) response);
        }
        chain.doFilter(request, response);
    }
}