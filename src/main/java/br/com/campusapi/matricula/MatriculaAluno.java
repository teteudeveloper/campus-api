package br.com.campusapi.matricula;

import br.com.campusapi.aluno.Aluno;
import br.com.campusapi.disciplina.Disciplina;
import br.com.campusapi.enums.MatriculaAlunoStatusEnum;
import br.com.campusapi.exception.CodigoErro;
import br.com.campusapi.exception.ConflitoException;
import br.com.campusapi.exception.RequisicaoInvalidaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Única fonte de verdade do vínculo aluno-disciplina. As regras de transição de status ficam na própria
 * entidade para que nenhum fluxo (matrícula direta ou via disciplina) as contorne.
 *
 * <pre>
 * MATRICULADO --(notas 1 e 2 lançadas)--> APROVADO (média >= 7,0) | REPROVADO (média < 7,0)
 * APROVADO/REPROVADO --(correção autorizada de nota)--> recalculado
 * MATRICULADO --(trancamento pelo ADMIN)--> TRANCADO (estado final; notas bloqueadas)
 * </pre>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "matriculas",
        uniqueConstraints = @UniqueConstraint(name = "uk_matriculas_aluno_disciplina",
                columnNames = {"aluno_id", "disciplina_id"}))
public class MatriculaAluno {

    public static final BigDecimal MEDIA_PARA_APROVACAO = new BigDecimal("7.0");
    public static final BigDecimal NOTA_MINIMA = BigDecimal.ZERO;
    public static final BigDecimal NOTA_MAXIMA = BigDecimal.TEN;
    private static final BigDecimal DOIS = BigDecimal.valueOf(2);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aluno_id", nullable = false, updatable = false)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disciplina_id", nullable = false, updatable = false)
    private Disciplina disciplina;

    @Column(precision = 4, scale = 2)
    private BigDecimal nota1;

    @Column(precision = 4, scale = 2)
    private BigDecimal nota2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatriculaAlunoStatusEnum status;

    @Version
    @Column(nullable = false)
    private Long versao;

    @Column(updatable = false)
    private LocalDateTime criadoEm;

    private LocalDateTime atualizadoEm;

    public MatriculaAluno(Aluno aluno, Disciplina disciplina) {
        this.aluno = aluno;
        this.disciplina = disciplina;
        this.status = MatriculaAlunoStatusEnum.MATRICULADO;
    }

    public void trancar() {
        if (status != MatriculaAlunoStatusEnum.MATRICULADO) {
            throw new ConflitoException(CodigoErro.TRANSICAO_INVALIDA,
                    "Só é possível trancar matrícula com status MATRICULADO (status atual: " + status + ")");
        }
        status = MatriculaAlunoStatusEnum.TRANCADO;
    }

    /**
     * Atualização parcial: nota nula mantém o valor atual (não existe remoção de nota). Com as duas notas
     * lançadas, o status é recalculado; isso vale também para correções em matrículas já finalizadas.
     */
    public void atualizarNotas(BigDecimal novaNota1, BigDecimal novaNota2) {
        if (status == MatriculaAlunoStatusEnum.TRANCADO) {
            throw new ConflitoException(CodigoErro.TRANSICAO_INVALIDA,
                    "Não é possível lançar ou corrigir notas de matrícula trancada");
        }
        if (novaNota1 == null && novaNota2 == null) {
            throw new RequisicaoInvalidaException(CodigoErro.VALIDACAO, "Informe ao menos uma nota (nota1 ou nota2)");
        }
        if (novaNota1 != null) {
            nota1 = validarNota(novaNota1);
        }
        if (novaNota2 != null) {
            nota2 = validarNota(novaNota2);
        }
        BigDecimal media = getMedia();
        if (media != null) {
            status = media.compareTo(MEDIA_PARA_APROVACAO) >= 0
                    ? MatriculaAlunoStatusEnum.APROVADO
                    : MatriculaAlunoStatusEnum.REPROVADO;
        }
    }

    /** Média aritmética exata das duas notas (sem arredondamento), ou nulo se alguma nota estiver pendente. */
    public BigDecimal getMedia() {
        if (nota1 == null || nota2 == null) {
            return null;
        }
        return nota1.add(nota2).divide(DOIS);
    }

    /** Cancelamento (exclusão) só é permitido para matrícula sem notas e ainda MATRICULADO. */
    public boolean podeSerCancelada() {
        return status == MatriculaAlunoStatusEnum.MATRICULADO && nota1 == null && nota2 == null;
    }

    private static BigDecimal validarNota(BigDecimal nota) {
        if (nota.compareTo(NOTA_MINIMA) < 0 || nota.compareTo(NOTA_MAXIMA) > 0 || nota.stripTrailingZeros().scale() > 2) {
            throw new RequisicaoInvalidaException(CodigoErro.VALIDACAO,
                    "Nota deve estar entre 0 e 10 com no máximo 2 casas decimais");
        }
        return nota.setScale(2);
    }

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = this.criadoEm;
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
