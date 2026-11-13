package br.com.campusapi.professor;

import br.com.campusapi.comum.PaginaResponse;
import br.com.campusapi.comum.Paginacao;
import br.com.campusapi.disciplina.DisciplinaRepository;
import br.com.campusapi.exception.CodigoErro;
import br.com.campusapi.exception.ConflitoException;
import br.com.campusapi.exception.RecursoNaoEncontradoException;
import br.com.campusapi.professor.dto.ProfessorRequestDTO;
import br.com.campusapi.professor.dto.ProfessorResponseDTO;
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
public class ProfessorService {

    private static final Set<String> ORDENACOES = Set.of("nome", "email", "registro", "especialidade", "criadoEm");

    private final ProfessorRepository professorRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ControleAcesso controleAcesso;

    @Transactional(readOnly = true)
    public PaginaResponse<ProfessorResponseDTO> listar(String nome, String especialidade, String registro,
                                                       Pageable pageable) {
        Pageable pagina = Paginacao.validar(pageable, ORDENACOES, Sort.by("nome"));
        return PaginaResponse.de(professorRepository
                .findAll(ProfessorSpecs.filtrar(nome, especialidade, registro), pagina)
                .map(ProfessorResponseDTO::de));
    }

    @Transactional(readOnly = true)
    public ProfessorResponseDTO buscarPorId(UUID id) {
        controleAcesso.exigirAcessoAoProfessor(id);
        return ProfessorResponseDTO.de(buscarEntidade(id));
    }

    @Transactional
    public ProfessorResponseDTO criar(ProfessorRequestDTO dto) {
        Professor professor = new Professor();
        aplicar(professor, dto);
        if (professorRepository.existsByEmail(professor.getEmail())) {
            throw new ConflitoException(CodigoErro.CONFLITO, "E-mail já cadastrado para outro professor", "email");
        }
        if (professorRepository.existsByRegistro(professor.getRegistro())) {
            throw new ConflitoException(CodigoErro.CONFLITO, "Registro já cadastrado", "registro");
        }
        return ProfessorResponseDTO.de(professorRepository.saveAndFlush(professor));
    }

    @Transactional
    public ProfessorResponseDTO atualizar(UUID id, ProfessorRequestDTO dto) {
        Professor professor = buscarEntidade(id);
        aplicar(professor, dto);
        if (professorRepository.existsByEmailAndIdNot(professor.getEmail(), id)) {
            throw new ConflitoException(CodigoErro.CONFLITO, "E-mail já cadastrado para outro professor", "email");
        }
        if (professorRepository.existsByRegistroAndIdNot(professor.getRegistro(), id)) {
            throw new ConflitoException(CodigoErro.CONFLITO, "Registro já cadastrado", "registro");
        }
        return ProfessorResponseDTO.de(professorRepository.saveAndFlush(professor));
    }

    /** O professor não pode ser excluído enquanto for responsável por disciplinas ou tiver usuário vinculado. */
    @Transactional
    public void deletar(UUID id) {
        Professor professor = buscarEntidade(id);
        if (disciplinaRepository.existsByProfessor_Id(id)) {
            throw new ConflitoException(CodigoErro.REGISTRO_EM_USO,
                    "Professor é responsável por disciplinas; remova-o das disciplinas antes de excluir");
        }
        if (usuarioRepository.existsByProfessorId(id)) {
            throw new ConflitoException(CodigoErro.REGISTRO_EM_USO,
                    "Professor possui usuário vinculado; altere o vínculo do usuário antes de excluir");
        }
        professorRepository.delete(professor);
        professorRepository.flush();
    }

    public Professor buscarEntidade(UUID id) {
        return professorRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Professor não encontrado com id: " + id));
    }

    private static void aplicar(Professor professor, ProfessorRequestDTO dto) {
        professor.setNome(dto.nome());
        professor.setEmail(dto.email());
        professor.setRegistro(dto.registro());
        professor.setEspecialidade(dto.especialidade());
    }
}
