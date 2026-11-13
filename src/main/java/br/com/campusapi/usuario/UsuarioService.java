package br.com.campusapi.usuario;

import br.com.campusapi.aluno.AlunoRepository;
import br.com.campusapi.auth.MotivoRevogacao;
import br.com.campusapi.auth.RefreshTokenService;
import br.com.campusapi.comum.PaginaResponse;
import br.com.campusapi.comum.Paginacao;
import br.com.campusapi.comum.Textos;
import br.com.campusapi.exception.CodigoErro;
import br.com.campusapi.exception.ConflitoException;
import br.com.campusapi.exception.RecursoNaoEncontradoException;
import br.com.campusapi.exception.RequisicaoInvalidaException;
import br.com.campusapi.professor.ProfessorRepository;
import br.com.campusapi.seguranca.ControleAcesso;
import br.com.campusapi.usuario.dto.AlterarPerfilRequestDTO;
import br.com.campusapi.usuario.dto.CriarUsuarioRequestDTO;
import br.com.campusapi.usuario.dto.UsuarioResponseDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/** Gestão de contas, restrita ao ADMIN (garantido nos controllers com @PreAuthorize). */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);
    private static final Set<String> ORDENACOES = Set.of("login", "perfil", "ativo", "criadoEm");

    private final UsuarioRepository usuarioRepository;
    private final AlunoRepository alunoRepository;
    private final ProfessorRepository professorRepository;
    private final PasswordEncoder passwordEncoder;
    private final PoliticaSenha politicaSenha;
    private final RefreshTokenService refreshTokenService;
    private final ControleAcesso controleAcesso;

    @Transactional
    public UsuarioResponseDTO criar(CriarUsuarioRequestDTO dto) {
        String login = dto.login();
        if (usuarioRepository.existsByLogin(login)) {
            throw new ConflitoException(CodigoErro.CONFLITO, "Login já está em uso", "login");
        }
        politicaSenha.validar(dto.senha(), "senha");
        validarVinculo(null, dto.perfil(), dto.alunoId(), dto.professorId());
        Usuario usuario = new Usuario(login, passwordEncoder.encode(dto.senha()), dto.perfil(), dto.alunoId(),
                dto.professorId());
        usuario = usuarioRepository.saveAndFlush(usuario);
        log.info("Usuário criado: id={} perfil={} por={}", usuario.getId(), usuario.getPerfil(),
                controleAcesso.usuarioAtual().id());
        return UsuarioResponseDTO.de(usuario);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResponseDTO> listar(Perfil perfil, Boolean ativo, String login, Pageable pageable) {
        Specification<Usuario> spec = Specification.unrestricted();
        if (perfil != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("perfil"), perfil));
        }
        if (ativo != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("ativo"), ativo));
        }
        if (login != null && !login.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(root.get("login"), Textos.padraoContem(login), '\\'));
        }
        Pageable pagina = Paginacao.validar(pageable, ORDENACOES, Sort.by("login"));
        return PaginaResponse.de(usuarioRepository.findAll(spec, pagina).map(UsuarioResponseDTO::de));
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO buscarPorId(UUID id) {
        return UsuarioResponseDTO.de(buscarEntidade(id));
    }

    /** Mudança de perfil/vínculo revoga sessões e invalida access tokens emitidos com as permissões anteriores. */
    @Transactional
    public UsuarioResponseDTO alterarPerfil(UUID id, AlterarPerfilRequestDTO dto) {
        exigirOutroUsuario(id, "Não é permitido alterar o próprio perfil");
        if (dto.perfil() != Perfil.ADMIN) {
            exigirOutroAdminAtivo(id);
        }
        Usuario usuario = buscarEntidade(id);
        validarVinculo(id, dto.perfil(), dto.alunoId(), dto.professorId());
        usuario.definirPerfil(dto.perfil(), dto.alunoId(), dto.professorId());
        usuario.invalidarTokensEmitidos();
        usuarioRepository.saveAndFlush(usuario);
        refreshTokenService.revogarTodos(id, MotivoRevogacao.PERFIL_ALTERADO);
        log.info("Perfil alterado: usuario={} perfil={} por={}", id, dto.perfil(), controleAcesso.usuarioAtual().id());
        return UsuarioResponseDTO.de(usuario);
    }

    @Transactional
    public UsuarioResponseDTO desativar(UUID id) {
        exigirOutroUsuario(id, "Não é permitido desativar a própria conta");
        exigirOutroAdminAtivo(id);
        Usuario usuario = buscarEntidade(id);
        if (usuario.isAtivo()) {
            usuario.desativar();
            usuario.invalidarTokensEmitidos();
            usuarioRepository.saveAndFlush(usuario);
            refreshTokenService.revogarTodos(id, MotivoRevogacao.USUARIO_DESATIVADO);
            log.info("Usuário desativado: usuario={} por={}", id, controleAcesso.usuarioAtual().id());
        }
        return UsuarioResponseDTO.de(usuario);
    }

    @Transactional
    public UsuarioResponseDTO ativar(UUID id) {
        Usuario usuario = buscarEntidade(id);
        if (!usuario.isAtivo()) {
            usuario.ativar();
            usuarioRepository.saveAndFlush(usuario);
            log.info("Usuário reativado: usuario={} por={}", id, controleAcesso.usuarioAtual().id());
        }
        return UsuarioResponseDTO.de(usuario);
    }

    @Transactional
    public void redefinirSenha(UUID id, String novaSenha) {
        Usuario usuario = buscarEntidade(id);
        politicaSenha.validar(novaSenha, "novaSenha");
        usuario.alterarSenha(passwordEncoder.encode(novaSenha));
        usuario.invalidarTokensEmitidos();
        usuarioRepository.saveAndFlush(usuario);
        refreshTokenService.revogarTodos(id, MotivoRevogacao.SENHA_REDEFINIDA);
        log.info("Senha redefinida pelo administrador: usuario={} por={}", id, controleAcesso.usuarioAtual().id());
    }

    private Usuario buscarEntidade(UUID id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado com id: " + id));
    }

    /**
     * Garante que, após rebaixar/desativar {@code id}, reste ao menos um ADMIN ativo. As linhas ADMIN são
     * bloqueadas, então dois administradores não conseguem rebaixar um ao outro simultaneamente.
     */
    private void exigirOutroAdminAtivo(UUID id) {
        boolean restaOutro = usuarioRepository.bloquearAdminsAtivos().stream().anyMatch(u -> !u.getId().equals(id));
        if (!restaOutro) {
            throw new ConflitoException(CodigoErro.OPERACAO_NAO_PERMITIDA, "Deve existir ao menos um administrador ativo");
        }
    }

    /** Impede que o administrador altere ou desative a própria conta. */
    private void exigirOutroUsuario(UUID id, String mensagem) {
        if (controleAcesso.usuarioAtual().id().equals(id)) {
            throw new ConflitoException(CodigoErro.OPERACAO_NAO_PERMITIDA, mensagem);
        }
    }

    private void validarVinculo(UUID usuarioId, Perfil perfil, UUID alunoId, UUID professorId) {
        switch (perfil) {
            case ADMIN -> {
                if (alunoId != null || professorId != null) {
                    throw new RequisicaoInvalidaException(CodigoErro.VALIDACAO,
                            "Usuário ADMIN não pode ser vinculado a aluno ou professor", "perfil");
                }
            }
            case ALUNO -> {
                if (alunoId == null || professorId != null) {
                    throw new RequisicaoInvalidaException(CodigoErro.VALIDACAO,
                            "Perfil ALUNO exige alunoId e não aceita professorId", "alunoId");
                }
                if (!alunoRepository.existsById(alunoId)) {
                    throw new RecursoNaoEncontradoException("Aluno não encontrado com id: " + alunoId);
                }
                boolean emUso = usuarioId == null ? usuarioRepository.existsByAlunoId(alunoId)
                        : usuarioRepository.existsByAlunoIdAndIdNot(alunoId, usuarioId);
                if (emUso) {
                    throw new ConflitoException(CodigoErro.VINCULO_EXISTENTE, "Aluno já está vinculado a outro usuário", "alunoId");
                }
            }
            case PROFESSOR -> {
                if (professorId == null || alunoId != null) {
                    throw new RequisicaoInvalidaException(CodigoErro.VALIDACAO,
                            "Perfil PROFESSOR exige professorId e não aceita alunoId", "professorId");
                }
                if (!professorRepository.existsById(professorId)) {
                    throw new RecursoNaoEncontradoException("Professor não encontrado com id: " + professorId);
                }
                boolean emUso = usuarioId == null ? usuarioRepository.existsByProfessorId(professorId)
                        : usuarioRepository.existsByProfessorIdAndIdNot(professorId, usuarioId);
                if (emUso) {
                    throw new ConflitoException(CodigoErro.VINCULO_EXISTENTE, "Professor já está vinculado a outro usuário", "professorId");
                }
            }
        }
    }
}
