package br.com.campusapi.disciplina;

import br.com.campusapi.comum.PaginaResponse;
import br.com.campusapi.comum.Paginacao;
import br.com.campusapi.disciplina.dto.DisciplinaRequestDTO;
import br.com.campusapi.disciplina.dto.DisciplinaResponseDTO;
import br.com.campusapi.exception.CodigoErro;
import br.com.campusapi.exception.ConflitoException;
import br.com.campusapi.exception.RecursoNaoEncontradoException;
import br.com.campusapi.matricula.MatriculaAlunoRepository;
import br.com.campusapi.professor.ProfessorService;
import br.com.campusapi.seguranca.ControleAcesso;
import br.com.campusapi.seguranca.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisciplinaService {

    private static final Set<String> ORDENACOES = Set.of("nome", "codigo", "cargaHoraria", "criadoEm");

    private final DisciplinaRepository disciplinaRepository;
    private final ProfessorService professorService;
    private final MatriculaAlunoRepository matriculaAlunoRepository;
    private final ControleAcesso controleAcesso;

    /** ADMIN vê todas; PROFESSOR, as próprias; ALUNO, aquelas em que está matriculado. */
    @Transactional(readOnly = true)
    public PaginaResponse<DisciplinaResponseDTO> listar(String nome, String codigo, UUID professorId,
                                                        Pageable pageable) {
        UsuarioAutenticado usuario = controleAcesso.usuarioAtual();
        Pageable pagina = Paginacao.validar(pageable, ORDENACOES, Sort.by("nome"));
        return PaginaResponse.de(disciplinaRepository
                .findAll(DisciplinaSpecs.visiveisPara(usuario).and(DisciplinaSpecs.filtrar(nome, codigo, professorId)), pagina)
                .map(DisciplinaResponseDTO::de));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<DisciplinaResponseDTO> listarPorProfessor(UUID professorId, Pageable pageable) {
        controleAcesso.exigirAcessoAoProfessor(professorId);
        professorService.buscarEntidade(professorId);
        Pageable pagina = Paginacao.validar(pageable, ORDENACOES, Sort.by("nome"));
        return PaginaResponse.de(disciplinaRepository.findAll(DisciplinaSpecs.doProfessor(professorId), pagina)
                .map(DisciplinaResponseDTO::de));
    }

    @Transactional(readOnly = true)
    public DisciplinaResponseDTO buscarPorId(UUID id) {
        Disciplina disciplina = disciplinaRepository.findComProfessorById(id)
                .orElseThrow(() -> naoEncontrada(id));
        UsuarioAutenticado usuario = controleAcesso.usuarioAtual();
        boolean permitido = switch (usuario.perfil()) {
            case ADMIN -> true;
            case PROFESSOR -> usuario.professorId().equals(disciplina.getProfessorId());
            case ALUNO -> matriculaAlunoRepository.existsByAlunoIdAndDisciplinaId(usuario.alunoId(), id);
        };
        if (!permitido) {
            throw controleAcesso.negado();
        }
        return DisciplinaResponseDTO.de(disciplina);
    }

    @Transactional
    public DisciplinaResponseDTO criar(DisciplinaRequestDTO dto) {
        Disciplina disciplina = new Disciplina();
        aplicar(disciplina, dto);
        if (disciplinaRepository.existsByCodigo(disciplina.getCodigo())) {
            throw new ConflitoException(CodigoErro.CONFLITO, "Código de disciplina já cadastrado", "codigo");
        }
        if (dto.professorId() != null) {
            disciplina.setProfessor(professorService.buscarEntidade(dto.professorId()));
        }
        return DisciplinaResponseDTO.de(disciplinaRepository.saveAndFlush(disciplina));
    }

    @Transactional
    public DisciplinaResponseDTO atualizar(UUID id, DisciplinaRequestDTO dto) {
        Disciplina disciplina = buscarEntidade(id);
        aplicar(disciplina, dto);
        if (disciplinaRepository.existsByCodigoAndIdNot(disciplina.getCodigo(), id)) {
            throw new ConflitoException(CodigoErro.CONFLITO, "Código de disciplina já cadastrado", "codigo");
        }
        if (dto.professorId() != null) {
            disciplina.setProfessor(professorService.buscarEntidade(dto.professorId()));
        }
        return DisciplinaResponseDTO.de(disciplinaRepository.saveAndFlush(disciplina));
    }

    @Transactional
    public DisciplinaResponseDTO associarProfessor(UUID id, UUID professorId) {
        Disciplina disciplina = buscarEntidade(id);
        disciplina.setProfessor(professorService.buscarEntidade(professorId));
        return DisciplinaResponseDTO.de(disciplinaRepository.saveAndFlush(disciplina));
    }

    /** Remove o professor responsável; matrículas e notas da disciplina são preservadas. */
    @Transactional
    public DisciplinaResponseDTO removerProfessor(UUID id) {
        Disciplina disciplina = buscarEntidade(id);
        disciplina.setProfessor(null);
        return DisciplinaResponseDTO.de(disciplinaRepository.saveAndFlush(disciplina));
    }

    @Transactional
    public void deletar(UUID id) {
        Disciplina disciplina = buscarEntidade(id);
        if (matriculaAlunoRepository.existsByDisciplinaId(id)) {
            throw new ConflitoException(CodigoErro.REGISTRO_EM_USO,
                    "Disciplina possui matrículas e não pode ser excluída; o histórico acadêmico é preservado");
        }
        disciplinaRepository.delete(disciplina);
        disciplinaRepository.flush();
    }

    public Disciplina buscarEntidade(UUID id) {
        return disciplinaRepository.findComProfessorById(id).orElseThrow(() -> naoEncontrada(id));
    }

    private static RecursoNaoEncontradoException naoEncontrada(UUID id) {
        return new RecursoNaoEncontradoException("Disciplina não encontrada com id: " + id);
    }

    private static void aplicar(Disciplina disciplina, DisciplinaRequestDTO dto) {
        disciplina.setNome(dto.nome());
        disciplina.setCodigo(dto.codigo());
        disciplina.setCargaHoraria(dto.cargaHoraria());
    }
}
