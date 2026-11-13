package br.com.campusapi.seguranca;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class UsuarioAuthenticationToken extends AbstractAuthenticationToken {

    private final UsuarioAutenticado usuario;

    public UsuarioAuthenticationToken(UsuarioAutenticado usuario) {
        super(List.of(new SimpleGrantedAuthority(usuario.perfil().authority())));
        this.usuario = usuario;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public UsuarioAutenticado getPrincipal() {
        return usuario;
    }
}
