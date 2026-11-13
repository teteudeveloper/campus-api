package br.com.campusapi.usuario;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import br.com.campusapi.comum.PaginaResponse;
import br.com.campusapi.usuario.dto.AlterarPerfilRequestDTO;
import br.com.campusapi.usuario.dto.CriarUsuarioRequestDTO;
import br.com.campusapi.usuario.dto.RedefinirSenhaRequestDTO;
import br.com.campusapi.usuario.dto.UsuarioResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@Tag(name = "Usuários", description = "Gestão de contas e perfis. Todas as operações exigem perfil ADMIN.")
@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @Operation(summary = "Criar usuário", description = "ALUNO exige alunoId; PROFESSOR exige professorId; ADMIN sem vínculo.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> criar(@Valid @RequestBody CriarUsuarioRequestDTO dto) {
        UsuarioResponseDTO criado = usuarioService.criar(dto);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(criado.id()).toUri()).body(criado);
    }

    @Operation(summary = "Listar usuários (paginado)", description = "Filtros: perfil, ativo, login (contém). Ordenação: login, perfil, ativo, criadoEm.")
    @GetMapping
    public ResponseEntity<PaginaResponse<UsuarioResponseDTO>> listar(
            @RequestParam(required = false) Perfil perfil,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) String login,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(usuarioService.listar(perfil, ativo, login, pageable));
    }

    @Operation(summary = "Buscar usuário por id")
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @Operation(summary = "Alterar perfil e vínculo", description = "Revoga sessões e invalida access tokens do usuário. Não se aplica à própria conta.")
    @PutMapping("/{id}/perfil")
    public ResponseEntity<UsuarioResponseDTO> alterarPerfil(@PathVariable UUID id,
                                                            @Valid @RequestBody AlterarPerfilRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.alterarPerfil(id, dto));
    }

    @Operation(summary = "Desativar usuário", description = "Bloqueia login, revoga refresh tokens e invalida access tokens imediatamente.")
    @PostMapping("/{id}/desativar")
    public ResponseEntity<UsuarioResponseDTO> desativar(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioService.desativar(id));
    }

    @Operation(summary = "Reativar usuário")
    @PostMapping("/{id}/ativar")
    public ResponseEntity<UsuarioResponseDTO> ativar(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioService.ativar(id));
    }

    @Operation(summary = "Redefinir senha de outro usuário", description = "Revoga sessões e invalida access tokens do usuário.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/{id}/senha")
    public ResponseEntity<Void> redefinirSenha(@PathVariable UUID id, @Valid @RequestBody RedefinirSenhaRequestDTO dto) {
        usuarioService.redefinirSenha(id, dto.novaSenha());
        return ResponseEntity.noContent().build();
    }
}
