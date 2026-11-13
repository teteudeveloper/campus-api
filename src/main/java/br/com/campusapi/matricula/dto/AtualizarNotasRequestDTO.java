package br.com.campusapi.matricula.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/**
 * Atualização parcial de notas. Campo ausente ou nulo mantém o valor atual; não há remoção de nota.
 * Uma requisição sem nenhuma nota é rejeitada com 400.
 */
public record AtualizarNotasRequestDTO(
        @Schema(description = "Nota 1 (0 a 10, até 2 casas decimais). Ausente/nulo mantém o valor atual.", example = "8.5")
        @DecimalMin("0.0") @DecimalMax("10.0") @Digits(integer = 2, fraction = 2) BigDecimal nota1,
        @Schema(description = "Nota 2 (0 a 10, até 2 casas decimais). Ausente/nulo mantém o valor atual.", example = "7.0")
        @DecimalMin("0.0") @DecimalMax("10.0") @Digits(integer = 2, fraction = 2) BigDecimal nota2) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Informe ao menos uma nota (nota1 ou nota2)")
    public boolean isAlgumaNotaInformada() {
        return nota1 != null || nota2 != null;
    }
}
