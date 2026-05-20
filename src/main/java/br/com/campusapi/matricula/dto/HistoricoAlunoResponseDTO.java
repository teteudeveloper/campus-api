package br.com.campusapi.matricula.dto;

import lombok.Data;

import java.util.List;

@Data
public class HistoricoAlunoResponseDTO {
    private String nomeAluno;
    private String emailAluno;
    private String matriculaAluno;
    private List<DisciplinasAlunoResponseDTO> disciplinas;
}
