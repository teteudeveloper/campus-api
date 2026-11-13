package br.com.campusapi.matricula;

import br.com.campusapi.aluno.Aluno;
import br.com.campusapi.aluno.AlunoService;
import br.com.campusapi.comum.PaginaResponse;
import br.com.campusapi.comum.Paginacao;
import br.com.campusapi.disciplina.Disciplina;
import br.com.campusapi.disciplina.DisciplinaService;
import br.com.campusapi.enums.MatriculaAlunoStatusEnum;
import br.com.campusapi.exception.CodigoErro;
import br.com.campusapi.exception.ConflitoException;
import br.com.campusapi.exception.RecursoNaoEncontradoException;
import br.com.campusapi.matricula.dto.AtualizarNotasRequestDTO;
import br.com.campusapi.matricula.dto.CriarMatriculaRequestDTO;
import br.com.campusapi.matricula.dto.DisciplinasAlunoResponseDTO;
import br.com.campusapi.matricula.dto.HistoricoAlunoResponseDTO;
import br.com.campusapi.matricula.dto.MatriculaResponseDTO;
import br.com.campusapi.seguranca.ControleAcesso;
import br.com.campusapi.seguranca.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Todas as operações sobre o vínculo aluno-disciplina passam por aqui, inclusive as rotas
 * /disciplinas/{id}/alunos/{alunoId}, garantindo uma única regra de matrícula.
 */
@Service
@RequiredArgsConstructor
public class MatriculaAlunoService {

    private static final Set<String> ORDENACOES = Set.of("status", "criadoEm", "aluno.nome", "disciplina.nome",
            "disciplina.codigo");

    private final MatriculaAlunoRepository matriculaAlunoRepository;
    private final AlunoService alunoService;
    private final DisciplinaService disciplinaService;
    private final ControleAcesso controleAcesso;

    /**
     * A verificação prévia gera a mensagem amigável; a constraint uk_matriculas_aluno_disciplina garante a
     * unicidade sob concorrência (a requisição perdedora recebe 409 pelo GlobalExceptionHandler).
     */
    @Transactional
    public MatriculaResponseDTO criarMatricula(CriarMatriculaRequestDTO dto) {
        Aluno aluno = alunoService.buscarEntidade(dto.alunoId());
        Disciplina disciplina = disciplinaService.buscarEntidade(dto.disciplinaId());
        if (matriculaAlunoRepository.existsByAlunoIdAndDisciplinaId(aluno.getId(), disciplina.getId())) {
            throw new ConflitoException(CodigoErro.MATRICULA_DUPLICADA, "Aluno já possui matrícula nesta disciplina");
        }
        MatriculaAluno matricula = matriculaAlunoRepository.saveAndFlush(new MatriculaAluno(aluno, disciplina));
        return MatriculaResponseDTO.de(matricula);
    }

    @Transactional(readOnly = true)
    public MatriculaResponseDTO buscarPorId(UUID id) {
        MatriculaAluno matricula = matriculaAlunoRepository.findComDetalhesById(id)
                .orElseThrow(() -> naoEncontrada(id));
        exigirLeitura(matricula);
        return MatriculaResponseDTO.de(matricula);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<MatriculaResponseDTO> listar(UUID alunoId, UUID disciplinaId,
                                                       MatriculaAlunoStatusEnum status, Pageable pageable) {
        UsuarioAutenticado usuario = controleAcesso.usuarioAtual();
        if (usuario.isAluno() && alunoId != null && !alunoId.equals(usuario.alunoId())) {
            throw controleAcesso.negado();
        }
        Pageable pagina = Paginacao.validar(pageable, ORDENACOES, Sort.by("criadoEm"));
        return PaginaResponse.de(matriculaAlunoRepository
                .findAll(MatriculaSpecs.visiveisPara(usuario).and(MatriculaSpecs.filtrar(alunoId, disciplinaId, status)), pagina)
                .map(MatriculaResponseDTO::de));
    }

    @Transactional
    public void trancarMatricula(UUID id) {
        MatriculaAluno matricula = matriculaAlunoRepository.findParaAtualizacaoById(id)
                .orElseThrow(() -> naoEncontrada(id));
        matricula.trancar();
    }

    /**
     * ADMIN ou professor responsável pela disciplina da matrícula. A linha é bloqueada antes da leitura para que
     * o estado usado na verificação e na atualização seja o mesmo; uma tentativa não autorizada só mantém o
     * bloqueio até o rollback.
     */
    @Transactional
    public MatriculaResponseDTO atualizarNotas(UUID id, AtualizarNotasRequestDTO dto) {
        MatriculaAluno matricula = matriculaAlunoRepository.findParaAtualizacaoById(id)
                .orElseThrow(() -> naoEncontrada(id));
        controleAcesso.exigirResponsavelPelaDisciplina(matricula.getDisciplina().getProfessorId());
        matricula.atualizarNotas(dto.nota1(), dto.nota2());
        matriculaAlunoRepository.flush();
        return MatriculaResponseDTO.de(matricula);
    }

    /**
     * Cancela (exclui) uma matrícula criada por engano. Matrículas com notas, finalizadas ou trancadas fazem
     * parte do histórico e não podem ser removidas.
     */
    @Transactional
    public void cancelarMatricula(UUID disciplinaId, UUID alunoId) {
        MatriculaAluno matricula = matriculaAlunoRepository
                .findParaAtualizacaoByAlunoIdAndDisciplinaId(alunoId, disciplinaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Matrícula não encontrada para o aluno " + alunoId + " na disciplina " + disciplinaId));
        if (!matricula.podeSerCancelada()) {
            throw new ConflitoException(CodigoErro.TRANSICAO_INVALIDA,
                    "Só é possível remover matrícula MATRICULADO sem notas; use o trancamento para preservar o histórico");
        }
        matriculaAlunoRepository.delete(matricula);
        matriculaAlunoRepository.flush();
    }

    /** ADMIN ou o próprio aluno. Aluno inexistente: 404; aluno sem matrículas: histórico vazio. */
    @Transactional(readOnly = true)
    public HistoricoAlunoResponseDTO emitirHistorico(UUID alunoId) {
        controleAcesso.exigirAcessoAoAluno(alunoId);
        Aluno aluno = alunoService.buscarEntidade(alunoId);
        var disciplinas = matriculaAlunoRepository
                .findByAlunoId(alunoId, Sort.by("disciplina.nome", "id"))
                .stream()
                .map(DisciplinasAlunoResponseDTO::de)
                .toList();
        return new HistoricoAlunoResponseDTO(aluno.getId(), aluno.getNome(), aluno.getEmail(), aluno.getMatricula(),
                disciplinas);
    }

    private void exigirLeitura(MatriculaAluno matricula) {
        UsuarioAutenticado usuario = controleAcesso.usuarioAtual();
        boolean permitido = switch (usuario.perfil()) {
            case ADMIN -> true;
            case PROFESSOR -> Objects.equals(usuario.professorId(), matricula.getDisciplina().getProfessorId());
            case ALUNO -> Objects.equals(usuario.alunoId(), matricula.getAluno().getId());
        };
        if (!permitido) {
            throw controleAcesso.negado();
        }
    }

    private static RecursoNaoEncontradoException naoEncontrada(UUID id) {
        return new RecursoNaoEncontradoException("Matrícula não encontrada com id: " + id);
    }
}
