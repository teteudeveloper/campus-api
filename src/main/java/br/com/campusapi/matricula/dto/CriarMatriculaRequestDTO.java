package br.com.campusapi.matricula.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CriarMatriculaRequestDTO {
    private UUID alunoId;
    private UUID disciplinaId;
}
