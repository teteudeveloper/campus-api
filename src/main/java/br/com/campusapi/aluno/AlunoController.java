package br.com.campusapi.aluno;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import br.com.campusapi.aluno.dto.AlunoRequestDTO;
import br.com.campusapi.aluno.dto.AlunoResponseDTO;
import br.com.campusapi.comum.PaginaResponse;
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

@Tag(name = "Alunos", description = "Cadastro de alunos. Escrita: ADMIN. Leitura: ADMIN ou o próprio aluno.")
@RestController
@RequestMapping("/alunos")
@RequiredArgsConstructor
public class AlunoController {

    private final AlunoService alunoService;

    @Operation(summary = "Listar alunos (paginado)", description = "Perfis: ADMIN. Ordenação: nome, email, matricula, criadoEm.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<PaginaResponse<AlunoResponseDTO>> listarTodos(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String matricula,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(alunoService.listar(nome, email, matricula, pageable));
    }

    @Operation(summary = "Buscar aluno por id", description = "Perfis: ADMIN ou o próprio ALUNO.")
    @GetMapping("/{id}")
    public ResponseEntity<AlunoResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(alunoService.buscarPorId(id));
    }

    @Operation(summary = "Criar aluno", description = "Perfis: ADMIN.")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ResponseEntity<AlunoResponseDTO> criar(@Valid @RequestBody AlunoRequestDTO dto) {
        AlunoResponseDTO criado = alunoService.criar(dto);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(criado.id()).toUri()).body(criado);
    }

    @Operation(summary = "Substituir dados do aluno", description = "Perfis: ADMIN. Todos os campos são obrigatórios.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<AlunoResponseDTO> atualizar(@PathVariable UUID id, @Valid @RequestBody AlunoRequestDTO dto) {
        return ResponseEntity.ok(alunoService.atualizar(id, dto));
    }

    @Operation(summary = "Excluir aluno", description = "Perfis: ADMIN. Retorna 409 se houver matrículas ou usuário vinculado.")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        alunoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
