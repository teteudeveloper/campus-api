package br.com.campusapi.disciplina;

import br.com.campusapi.aluno.Aluno;
import br.com.campusapi.aluno.AlunoRepository;
import br.com.campusapi.exception.RecursoNaoEncontradoException;
import br.com.campusapi.professor.Professor;
import br.com.campusapi.professor.ProfessorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisciplinaService {

    private final DisciplinaRepository disciplinaRepository;
    private final ProfessorRepository professorRepository;
    private final AlunoRepository alunoRepository;

    public List<Disciplina> listarTodos() {
        return disciplinaRepository.findAll();
    }

    public Disciplina buscarPorId(UUID id) {
        return disciplinaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Disciplina não encontrada com id: " + id));
    }

    public Disciplina criar(DisciplinaDTO dto) {
        Disciplina disciplina = new Disciplina();
        disciplina.setNome(dto.getNome());
        disciplina.setCodigo(dto.getCodigo());
        disciplina.setCargaHoraria(dto.getCargaHoraria());

        if (dto.getProfessorId() != null) {
            Professor professor = professorRepository.findById(dto.getProfessorId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Professor não encontrado com id: " + dto.getProfessorId()));
            disciplina.setProfessor(professor);
        }

        return disciplinaRepository.save(disciplina);
    }

    public Disciplina atualizar(UUID id, DisciplinaDTO dto) {
        Disciplina disciplina = buscarPorId(id);
        disciplina.setNome(dto.getNome());
        disciplina.setCodigo(dto.getCodigo());
        disciplina.setCargaHoraria(dto.getCargaHoraria());

        if (dto.getProfessorId() != null) {
            Professor professor = professorRepository.findById(dto.getProfessorId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Professor não encontrado com id: " + dto.getProfessorId()));
            disciplina.setProfessor(professor);
        }

        return disciplinaRepository.save(disciplina);
    }

    public Disciplina matricularAluno(UUID disciplinaId, UUID alunoId) {
        Disciplina disciplina = buscarPorId(disciplinaId);
        Aluno aluno = alunoRepository.findById(alunoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Aluno não encontrado com id: " + alunoId));

        disciplina.getAlunos().add(aluno);
        return disciplinaRepository.save(disciplina);
    }

    public Disciplina removerAluno(UUID disciplinaId, UUID alunoId) {
        Disciplina disciplina = buscarPorId(disciplinaId);
        Aluno aluno = alunoRepository.findById(alunoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Aluno não encontrado com id: " + alunoId));

        disciplina.getAlunos().remove(aluno);
        return disciplinaRepository.save(disciplina);
    }

    public void deletar(UUID id) {
        buscarPorId(id);
        disciplinaRepository.deleteById(id);
    }
}