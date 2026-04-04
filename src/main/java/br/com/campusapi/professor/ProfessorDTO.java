package br.com.campusapi.professor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfessorDTO {

    private String nome;
    private String email;
    private String registro;
    private String especialidade;
}
