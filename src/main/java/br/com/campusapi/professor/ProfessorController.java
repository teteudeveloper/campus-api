package br.com.campusapi.professor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import br.com.campusapi.comum.PaginaResponse;
import br.com.campusapi.professor.dto.ProfessorRequestDTO;
import br.com.campusapi.professor.dto.ProfessorResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@Tag(name = "Professores", description = "Cadastro de professores. Escrita: ADMIN. Leitura: ADMIN ou o próprio professor.")
@RestController
@RequestMapping("/professores")
@RequiredArgsConstructor
public class ProfessorController {

    private final ProfessorService professorService;

    @Operation(summary = "Listar professores (paginado)", description = "Perfis: ADMIN. Ordenação: nome, email, registro, especialidade, criadoEm.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<PaginaResponse<ProfessorResponseDTO>> listarTodos(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String especialidade,
            @RequestParam(required = false) String registro,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(professorService.listar(nome, especialidade, registro, pageable));
    }

    @Operation(summary = "Buscar professor por id", description = "Perfis: ADMIN ou o próprio PROFESSOR.")
    @GetMapping("/{id}")
    public ResponseEntity<ProfessorResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(professorService.buscarPorId(id));
    }

    @Operation(summary = "Criar professor", description = "Perfis: ADMIN.")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ResponseEntity<ProfessorResponseDTO> criar(@Valid @RequestBody ProfessorRequestDTO dto) {
        ProfessorResponseDTO criado = professorService.criar(dto);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(criado.id()).toUri()).body(criado);
    }

    @Operation(summary = "Substituir dados do professor", description = "Perfis: ADMIN. Campos ausentes opcionais (especialidade) são limpos.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ProfessorResponseDTO> atualizar(@PathVariable UUID id,
                                                          @Valid @RequestBody ProfessorRequestDTO dto) {
        return ResponseEntity.ok(professorService.atualizar(id, dto));
    }

    @Operation(summary = "Excluir professor", description = "Perfis: ADMIN. Retorna 409 se for responsável por disciplinas ou tiver usuário vinculado.")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        professorService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
