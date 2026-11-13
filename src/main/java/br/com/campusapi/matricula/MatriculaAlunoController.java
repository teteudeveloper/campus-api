package br.com.campusapi.matricula;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import br.com.campusapi.comum.PaginaResponse;
import br.com.campusapi.enums.MatriculaAlunoStatusEnum;
import br.com.campusapi.matricula.dto.AtualizarNotasRequestDTO;
import br.com.campusapi.matricula.dto.CriarMatriculaRequestDTO;
import br.com.campusapi.matricula.dto.HistoricoAlunoResponseDTO;
import br.com.campusapi.matricula.dto.MatriculaResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@Tag(name = "Matrículas", description = "Matrículas, notas, trancamento e histórico. Fonte única do vínculo aluno-disciplina.")
@RestController
@RequestMapping("/matriculas")
@RequiredArgsConstructor
public class MatriculaAlunoController {

    private final MatriculaAlunoService matriculaAlunoService;

    @Operation(summary = "Criar matrícula", description = "Perfis: ADMIN. Status inicial MATRICULADO; 409 se já houver matrícula do aluno na disciplina.")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ResponseEntity<MatriculaResponseDTO> criarMatricula(@Valid @RequestBody CriarMatriculaRequestDTO dto) {
        MatriculaResponseDTO criada = matriculaAlunoService.criarMatricula(dto);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/matriculas/{id}").buildAndExpand(criada.id()).toUri()).body(criada);
    }

    @Operation(summary = "Listar matrículas (paginado)", description = """
            Perfis: ADMIN (todas), PROFESSOR (das suas disciplinas), ALUNO (as próprias).
            Filtros: alunoId, disciplinaId, status. Ordenação: status, criadoEm, aluno.nome, disciplina.nome, disciplina.codigo.""")
    @GetMapping
    public ResponseEntity<PaginaResponse<MatriculaResponseDTO>> listar(
            @RequestParam(required = false) UUID alunoId,
            @RequestParam(required = false) UUID disciplinaId,
            @RequestParam(required = false) MatriculaAlunoStatusEnum status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(matriculaAlunoService.listar(alunoId, disciplinaId, status, pageable));
    }

    @Operation(summary = "Buscar matrícula por id", description = "Perfis: ADMIN, PROFESSOR responsável pela disciplina ou o próprio ALUNO.")
    @GetMapping("/{id}")
    public ResponseEntity<MatriculaResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(matriculaAlunoService.buscarPorId(id));
    }

    @Operation(summary = "Trancar matrícula", description = "Perfis: ADMIN. Somente a partir de MATRICULADO; caso contrário 409 TRANSICAO_INVALIDA.")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("/trancar/{id}")
    public ResponseEntity<Void> trancarMatricula(@PathVariable UUID id) {
        matriculaAlunoService.trancarMatricula(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lançar ou corrigir notas", description = """
            Perfis: ADMIN ou PROFESSOR responsável pela disciplina. Campo ausente ou nulo mantém a nota atual.
            Com as duas notas, o status vira APROVADO (média >= 7,0) ou REPROVADO. Matrícula TRANCADA: 409.""")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROFESSOR')")
    @PatchMapping("/atualizar-notas/{id}")
    public ResponseEntity<MatriculaResponseDTO> atualizarNotas(@PathVariable UUID id,
                                                               @Valid @RequestBody AtualizarNotasRequestDTO dto) {
        return ResponseEntity.ok(matriculaAlunoService.atualizarNotas(id, dto));
    }

    @Operation(summary = "Emitir histórico do aluno", description = "Perfis: ADMIN ou o próprio ALUNO. Aluno sem matrículas retorna lista vazia.")
    @GetMapping("/emitir-historico/{alunoId}")
    public ResponseEntity<HistoricoAlunoResponseDTO> emitirHistorico(@PathVariable UUID alunoId) {
        return ResponseEntity.ok(matriculaAlunoService.emitirHistorico(alunoId));
    }
}
