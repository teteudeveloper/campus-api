package br.com.campusapi.disciplina;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisciplinaDTO {

    private String nome;
    private String codigo;
    private Integer cargaHoraria;
    private UUID professorId;
}
