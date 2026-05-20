package br.com.campusapi.matricula;

import br.com.campusapi.aluno.Aluno;
import br.com.campusapi.disciplina.Disciplina;
import br.com.campusapi.enums.MatriculaAlunoStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "matriculas")
public class MatriculaAluno {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "aluno_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Aluno aluno;

    @ManyToOne
    @JoinColumn(name = "disciplina_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Disciplina disciplina;

    private Double nota1;

    private Double nota2;

    @Enumerated(EnumType.STRING)
    private MatriculaAlunoStatusEnum status;
}
