package br.com.campusapi.aluno;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AlunoDTO {

    private String nome;
    private String email;
    private String matricula;
}
