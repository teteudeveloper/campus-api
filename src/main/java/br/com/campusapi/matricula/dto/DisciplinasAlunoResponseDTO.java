package br.com.campusapi.matricula.dto;

import br.com.campusapi.disciplina.Disciplina;
import br.com.campusapi.enums.MatriculaAlunoStatusEnum;
import br.com.campusapi.matricula.MatriculaAluno;

import java.math.BigDecimal;
import java.util.UUID;

/** Linha do histórico. Mantém os campos do contrato anterior e acrescenta identificadores e código. */
public record DisciplinasAlunoResponseDTO(
        UUID matriculaId,
        UUID disciplinaId,
        String codigoDisciplina,
        String nomeDisciplina,
        Integer cargaHoraria,
        String nomeProfessor,
        BigDecimal nota1,
        BigDecimal nota2,
        BigDecimal media,
        MatriculaAlunoStatusEnum status) {

    public static DisciplinasAlunoResponseDTO de(MatriculaAluno matricula) {
        Disciplina disciplina = matricula.getDisciplina();
        return new DisciplinasAlunoResponseDTO(matricula.getId(), disciplina.getId(), disciplina.getCodigo(),
                disciplina.getNome(), disciplina.getCargaHoraria(),
                disciplina.getProfessor() == null ? null : disciplina.getProfessor().getNome(),
                matricula.getNota1(), matricula.getNota2(), matricula.getMedia(), matricula.getStatus());
    }
}
