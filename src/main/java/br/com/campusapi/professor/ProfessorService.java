package br.com.campusapi.professor;

import br.com.campusapi.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfessorService {

    private final ProfessorRepository professorRepository;

    public List<Professor> listarTodos() {
        return professorRepository.findAll();
    }

    public Professor buscarPorId(UUID id) {
        return professorRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Professor não encontrado com id: " + id));
    }

    public Professor criar(ProfessorDTO dto) {
        Professor professor = new Professor();
        professor.setNome(dto.getNome());
        professor.setEmail(dto.getEmail());
        professor.setRegistro(dto.getRegistro());
        professor.setEspecialidade(dto.getEspecialidade());
        return professorRepository.save(professor);
    }

    public Professor atualizar(UUID id, ProfessorDTO dto) {
        Professor professor = buscarPorId(id);
        professor.setNome(dto.getNome());
        professor.setEmail(dto.getEmail());
        professor.setRegistro(dto.getRegistro());
        professor.setEspecialidade(dto.getEspecialidade());
        return professorRepository.save(professor);
    }

    public void deletar(UUID id) {
        buscarPorId(id);
        professorRepository.deleteById(id);
    }
}