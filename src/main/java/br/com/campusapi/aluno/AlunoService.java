package br.com.campusapi.aluno;

import br.com.campusapi.aluno.dto.AlunoRequestDTO;
import br.com.campusapi.aluno.dto.AlunoResponseDTO;
import br.com.campusapi.comum.PaginaResponse;
import br.com.campusapi.comum.Paginacao;
import br.com.campusapi.exception.CodigoErro;
import br.com.campusapi.exception.ConflitoException;
import br.com.campusapi.exception.RecursoNaoEncontradoException;
import br.com.campusapi.matricula.MatriculaAlunoRepository;
import br.com.campusapi.seguranca.ControleAcesso;
import br.com.campusapi.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlunoService {

    private static final Set<String> ORDENACOES = Set.of("nome", "email", "matricula", "criadoEm");

    private final AlunoRepository alunoRepository;
    private final MatriculaAlunoRepository matriculaAlunoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ControleAcesso controleAcesso;

    @Transactional(readOnly = true)
    public PaginaResponse<AlunoResponseDTO> listar(String nome, String email, String matricula, Pageable pageable) {
        Pageable pagina = Paginacao.validar(pageable, ORDENACOES, Sort.by("nome"));
        return PaginaResponse.de(alunoRepository.findAll(AlunoSpecs.filtrar(nome, email, matricula), pagina)
                .map(AlunoResponseDTO::de));
    }

    @Transactional(readOnly = true)
    public AlunoResponseDTO buscarPorId(UUID id) {
        controleAcesso.exigirAcessoAoAluno(id);
        return AlunoResponseDTO.de(buscarEntidade(id));
    }

    @Transactional
    public AlunoResponseDTO criar(AlunoRequestDTO dto) {
        Aluno aluno = new Aluno();
        aplicar(aluno, dto);
        if (alunoRepository.existsByEmail(aluno.getEmail())) {
            throw new ConflitoException(CodigoErro.CONFLITO, "E-mail já cadastrado para outro aluno", "email");
        }
        if (alunoRepository.existsByMatricula(aluno.getMatricula())) {
            throw new ConflitoException(CodigoErro.CONFLITO, "Número de matrícula já cadastrado", "matricula");
        }
        return AlunoResponseDTO.de(alunoRepository.saveAndFlush(aluno));
    }

    @Transactional
    public AlunoResponseDTO atualizar(UUID id, AlunoRequestDTO dto) {
        Aluno aluno = buscarEntidade(id);
        aplicar(aluno, dto);
        if (alunoRepository.existsByEmailAndIdNot(aluno.getEmail(), id)) {
            throw new ConflitoException(CodigoErro.CONFLITO, "E-mail já cadastrado para outro aluno", "email");
        }
        if (alunoRepository.existsByMatriculaAndIdNot(aluno.getMatricula(), id)) {
            throw new ConflitoException(CodigoErro.CONFLITO, "Número de matrícula já cadastrado", "matricula");
        }
        return AlunoResponseDTO.de(alunoRepository.saveAndFlush(aluno));
    }

    /** Exclusão física só é permitida sem matrículas e sem usuário vinculado, preservando o histórico acadêmico. */
    @Transactional
    public void deletar(UUID id) {
        Aluno aluno = buscarEntidade(id);
        if (matriculaAlunoRepository.existsByAlunoId(id)) {
            throw new ConflitoException(CodigoErro.REGISTRO_EM_USO,
                    "Aluno possui matrículas e não pode ser excluído; o histórico acadêmico é preservado");
        }
        if (usuarioRepository.existsByAlunoId(id)) {
            throw new ConflitoException(CodigoErro.REGISTRO_EM_USO,
                    "Aluno possui usuário vinculado; altere o vínculo do usuário antes de excluir");
        }
        alunoRepository.delete(aluno);
        alunoRepository.flush();
    }

    public Aluno buscarEntidade(UUID id) {
        return alunoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno não encontrado com id: " + id));
    }

    private static void aplicar(Aluno aluno, AlunoRequestDTO dto) {
        aluno.setNome(dto.nome());
        aluno.setEmail(dto.email());
        aluno.setMatricula(dto.matricula());
    }
}
