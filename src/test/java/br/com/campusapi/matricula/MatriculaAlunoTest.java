package br.com.campusapi.matricula;

import br.com.campusapi.aluno.Aluno;
import br.com.campusapi.disciplina.Disciplina;
import br.com.campusapi.enums.MatriculaAlunoStatusEnum;
import br.com.campusapi.exception.ConflitoException;
import br.com.campusapi.exception.RequisicaoInvalidaException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatriculaAlunoTest {

    private MatriculaAluno nova() {
        return new MatriculaAluno(new Aluno(), new Disciplina());
    }

    @Test
    void iniciaMatriculadaSemNotasEPodeSerCancelada() {
        MatriculaAluno matricula = nova();
        assertThat(matricula.getStatus()).isEqualTo(MatriculaAlunoStatusEnum.MATRICULADO);
        assertThat(matricula.getMedia()).isNull();
        assertThat(matricula.podeSerCancelada()).isTrue();
    }

    @Test
    void mediaExataEAprovacaoComSete() {
        MatriculaAluno matricula = nova();
        matricula.atualizarNotas(new BigDecimal("6.5"), new BigDecimal("7.5"));
        assertThat(matricula.getMedia()).isEqualByComparingTo("7.0");
        assertThat(matricula.getStatus()).isEqualTo(MatriculaAlunoStatusEnum.APROVADO);

        matricula.atualizarNotas(new BigDecimal("6.99"), new BigDecimal("7.00"));
        assertThat(matricula.getMedia()).isEqualByComparingTo("6.995");
        assertThat(matricula.getStatus()).isEqualTo(MatriculaAlunoStatusEnum.REPROVADO);
        assertThat(matricula.podeSerCancelada()).isFalse();
    }

    @Test
    void notaNulaMantemValorAtual() {
        MatriculaAluno matricula = nova();
        matricula.atualizarNotas(new BigDecimal("9"), null);
        matricula.atualizarNotas(null, new BigDecimal("3"));
        assertThat(matricula.getNota1()).isEqualByComparingTo("9");
        assertThat(matricula.getMedia()).isEqualByComparingTo("6");
        assertThatThrownBy(() -> matricula.atualizarNotas(null, null)).isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void rejeitaNotaForaDoIntervaloSemAlterarEstado() {
        MatriculaAluno matricula = nova();
        assertThatThrownBy(() -> matricula.atualizarNotas(new BigDecimal("10.01"), null))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> matricula.atualizarNotas(new BigDecimal("-0.5"), null))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> matricula.atualizarNotas(new BigDecimal("5.555"), null))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThat(matricula.getNota1()).isNull();
    }

    @Test
    void trancamentoSoAPartirDeMatriculadoEBloqueiaNotas() {
        MatriculaAluno matricula = nova();
        matricula.trancar();
        assertThat(matricula.getStatus()).isEqualTo(MatriculaAlunoStatusEnum.TRANCADO);
        assertThatThrownBy(matricula::trancar).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> matricula.atualizarNotas(BigDecimal.TEN, null)).isInstanceOf(ConflitoException.class);
        assertThat(matricula.podeSerCancelada()).isFalse();

        MatriculaAluno aprovada = nova();
        aprovada.atualizarNotas(BigDecimal.TEN, BigDecimal.TEN);
        assertThatThrownBy(aprovada::trancar).isInstanceOf(ConflitoException.class);
    }
}
