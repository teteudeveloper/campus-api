package br.com.campusapi.comum;

import br.com.campusapi.exception.RequisicaoInvalidaException;
import br.com.campusapi.usuario.PoliticaSenha;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextosEPaginacaoTest {

    @Test
    void normalizaTextos() {
        assertThat(Textos.normalizarNome("  Ana \t  Maria  ")).isEqualTo("Ana Maria");
        assertThat(Textos.normalizarNome("   ")).isNull();
        assertThat(Textos.normalizarEmail(" Ana@Email.COM ")).isEqualTo("ana@email.com");
        assertThat(Textos.normalizarIdentificador(" mat-101 ")).isEqualTo("MAT-101");
        assertThat(Textos.padraoContem("50%_a\\")).isEqualTo("%50\\%\\_a\\\\%");
    }

    @Test
    void paginacaoValidaOrdenacaoLimitaTamanhoEDesempataPorId() {
        Pageable resultado = Paginacao.validar(PageRequest.of(2, 1000), Set.of("nome"), Sort.by("nome"));
        assertThat(resultado.getPageSize()).isEqualTo(Paginacao.TAMANHO_MAXIMO);
        assertThat(resultado.getSort()).isEqualTo(Sort.by("nome").and(Sort.by("id")));

        assertThatThrownBy(() -> Paginacao.validar(PageRequest.of(0, 10, Sort.by("senha")), Set.of("nome"), Sort.by("nome")))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void politicaDeSenha() {
        PoliticaSenha politica = new PoliticaSenha();
        assertThatCode(() -> politica.validar("SenhaForte1", "senha")).doesNotThrowAnyException();
        assertThatThrownBy(() -> politica.validar("Curta1", "senha")).isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> politica.validar("semdigitosaqui", "senha")).isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> politica.validar("1234567890", "senha")).isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> politica.validar("ç".repeat(40) + "1", "senha")).isInstanceOf(RequisicaoInvalidaException.class);
    }
}
