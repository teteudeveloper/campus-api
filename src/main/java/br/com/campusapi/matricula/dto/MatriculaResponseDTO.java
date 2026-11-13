package br.com.campusapi.matricula.dto;

import br.com.campusapi.aluno.Aluno;
import br.com.campusapi.disciplina.Disciplina;
import br.com.campusapi.enums.MatriculaAlunoStatusEnum;
import br.com.campusapi.matricula.MatriculaAluno;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Expõe apenas dados mínimos do aluno (sem e-mail), pois também é retornado a professores. */
public record MatriculaResponseDTO(
        UUID id,
        AlunoResumoDTO aluno,
        DisciplinaResumoDTO disciplina,
        BigDecimal nota1,
        BigDecimal nota2,
        BigDecimal media,
        MatriculaAlunoStatusEnum status,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {

    public record AlunoResumoDTO(UUID id, String nome, String matricula) {
    }

    public record DisciplinaResumoDTO(UUID id, String nome, String codigo, UUID professorId, String nomeProfessor) {
    }

    public static MatriculaResponseDTO de(MatriculaAluno matricula) {
        Aluno aluno = matricula.getAluno();
        Disciplina disciplina = matricula.getDisciplina();
        return new MatriculaResponseDTO(
                matricula.getId(),
                new AlunoResumoDTO(aluno.getId(), aluno.getNome(), aluno.getMatricula()),
                new DisciplinaResumoDTO(disciplina.getId(), disciplina.getNome(), disciplina.getCodigo(),
                        disciplina.getProfessorId(),
                        disciplina.getProfessor() == null ? null : disciplina.getProfessor().getNome()),
                matricula.getNota1(),
                matricula.getNota2(),
                matricula.getMedia(),
                matricula.getStatus(),
                matricula.getCriadoEm(),
                matricula.getAtualizadoEm());
    }
}
