package br.com.campusapi.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import br.com.campusapi.auth.dto.AlterarSenhaRequestDTO;
import br.com.campusapi.auth.dto.LoginRequestDTO;
import br.com.campusapi.auth.dto.RefreshTokenRequestDTO;
import br.com.campusapi.auth.dto.TokenResponseDTO;
import br.com.campusapi.seguranca.UsuarioAutenticado;
import br.com.campusapi.usuario.dto.UsuarioResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação", description = "Login, renovação e logout (públicos); usuário atual e troca de senha (autenticados).")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Login", description = """
            Público. Retorna access token JWT (Bearer, curta duração) e refresh token opaco.
            Credenciais inválidas, conta inexistente ou inativa: 401 CREDENCIAIS_INVALIDAS (mesma resposta).
            Excesso de tentativas: 429 MUITAS_TENTATIVAS com Retry-After.""")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto, HttpServletRequest request) {
        return semCache(authService.login(dto, request.getRemoteAddr()));
    }

    @Operation(summary = "Renovar tokens", description = """
            Público. Rotaciona o refresh token: o informado é invalidado e um novo par é emitido.
            Reapresentar um refresh token já utilizado revoga toda a sessão (401).""")
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO dto) {
        return semCache(authService.renovar(dto.refreshToken()));
    }

    @Operation(summary = "Logout", description = """
            Público. Revoga a sessão de renovação do refresh token informado; sempre 204.
            O access token já emitido continua válido até expirar (app.security.jwt.access-token-ttl).""")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequestDTO dto) {
        authService.logout(dto.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Usuário autenticado", description = "Qualquer perfil autenticado.")
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(authService.usuarioAtual(usuario));
    }

    @Operation(summary = "Alterar a própria senha", description = """
            Qualquer perfil autenticado. Exige a senha atual. Revoga todos os refresh tokens e invalida os
            access tokens emitidos; é necessário novo login.""")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/senha")
    public ResponseEntity<Void> alterarSenha(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                             @Valid @RequestBody AlterarSenhaRequestDTO dto,
                                             HttpServletRequest request) {
        authService.alterarSenha(usuario, dto, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<TokenResponseDTO> semCache(TokenResponseDTO tokens) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(tokens);
    }
}
