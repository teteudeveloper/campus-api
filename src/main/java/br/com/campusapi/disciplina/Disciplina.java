package br.com.campusapi.disciplina;

import br.com.campusapi.aluno.Aluno;
import br.com.campusapi.professor.Professor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "disciplinas")
public class Disciplina {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column
    private Integer cargaHoraria;

    @ManyToOne
    @JoinColumn(name = "professor_id")
    @JsonIgnoreProperties({"disciplinas"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Professor professor;

    @ManyToMany
    @JoinTable(
            name = "alunos_disciplinas",
            joinColumns = @JoinColumn(name = "disciplina_id"),
            inverseJoinColumns = @JoinColumn(name = "aluno_id")
    )
    @JsonIgnoreProperties({"disciplinas"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Aluno> alunos = new HashSet<>();

    @Column(updatable = false)
    private LocalDateTime criadoEm;

    private LocalDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
