package br.com.campusapi.disciplina;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import br.com.campusapi.comum.PaginaResponse;
import br.com.campusapi.disciplina.dto.AssociarProfessorRequestDTO;
import br.com.campusapi.disciplina.dto.DisciplinaRequestDTO;
import br.com.campusapi.disciplina.dto.DisciplinaResponseDTO;
import br.com.campusapi.matricula.MatriculaAlunoService;
import br.com.campusapi.matricula.dto.CriarMatriculaRequestDTO;
import br.com.campusapi.matricula.dto.MatriculaResponseDTO;
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

@Tag(name = "Disciplinas", description = "Disciplinas, professor responsável e matrícula por disciplina. Escrita: ADMIN.")
@RestController
@RequestMapping("/disciplinas")
@RequiredArgsConstructor
public class DisciplinaController {

    private final DisciplinaService disciplinaService;
    private final MatriculaAlunoService matriculaAlunoService;

    @Operation(summary = "Listar disciplinas (paginado)", description = """
            Perfis: ADMIN (todas), PROFESSOR (as que leciona), ALUNO (aquelas em que está matriculado).
            Filtros: nome, codigo, professorId. Ordenação: nome, codigo, cargaHoraria, criadoEm.""")
    @GetMapping
    public ResponseEntity<PaginaResponse<DisciplinaResponseDTO>> listarTodos(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) UUID professorId,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(disciplinaService.listar(nome, codigo, professorId, pageable));
    }

    @Operation(summary = "Listar disciplinas de um professor (paginado)", description = "Perfis: ADMIN ou o próprio PROFESSOR.")
    @GetMapping("/professor/{professorId}")
    public ResponseEntity<PaginaResponse<DisciplinaResponseDTO>> listarPorProfessor(
            @PathVariable UUID professorId, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(disciplinaService.listarPorProfessor(professorId, pageable));
    }

    @Operation(summary = "Buscar disciplina por id", description = "Perfis: ADMIN, PROFESSOR responsável ou ALUNO matriculado.")
    @GetMapping("/{id}")
    public ResponseEntity<DisciplinaResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(disciplinaService.buscarPorId(id));
    }

    @Operation(summary = "Criar disciplina", description = "Perfis: ADMIN. professorId é opcional.")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ResponseEntity<DisciplinaResponseDTO> criar(@Valid @RequestBody DisciplinaRequestDTO dto) {
        DisciplinaResponseDTO criada = disciplinaService.criar(dto);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(criada.id()).toUri()).body(criada);
    }

    @Operation(summary = "Atualizar disciplina", description = """
            Perfis: ADMIN. nome, codigo e cargaHoraria são obrigatórios. professorId nulo/ausente mantém o
            professor atual; para remover use DELETE /disciplinas/{id}/professor.""")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<DisciplinaResponseDTO> atualizar(@PathVariable UUID id,
                                                           @Valid @RequestBody DisciplinaRequestDTO dto) {
        return ResponseEntity.ok(disciplinaService.atualizar(id, dto));
    }

    @Operation(summary = "Associar professor responsável", description = "Perfis: ADMIN. Substitui o professor atual.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/professor")
    public ResponseEntity<DisciplinaResponseDTO> associarProfessor(@PathVariable UUID id,
                                                                   @Valid @RequestBody AssociarProfessorRequestDTO dto) {
        return ResponseEntity.ok(disciplinaService.associarProfessor(id, dto.professorId()));
    }

    @Operation(summary = "Remover professor responsável", description = "Perfis: ADMIN. Matrículas e notas são preservadas.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/professor")
    public ResponseEntity<DisciplinaResponseDTO> removerProfessor(@PathVariable UUID id) {
        return ResponseEntity.ok(disciplinaService.removerProfessor(id));
    }

    @Operation(summary = "Matricular aluno na disciplina", description = "Perfis: ADMIN. Equivalente a POST /matriculas.")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{disciplinaId}/alunos/{alunoId}")
    public ResponseEntity<MatriculaResponseDTO> matricularAluno(@PathVariable UUID disciplinaId,
                                                                @PathVariable UUID alunoId) {
        MatriculaResponseDTO criada = matriculaAlunoService.criarMatricula(
                new CriarMatriculaRequestDTO(alunoId, disciplinaId));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/matriculas/{id}").buildAndExpand(criada.id()).toUri()).body(criada);
    }

    @Operation(summary = "Cancelar matrícula do aluno na disciplina", description = """
            Perfis: ADMIN. Só remove matrícula MATRICULADO sem notas (lançamento por engano);
            demais casos retornam 409 para preservar o histórico.""")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{disciplinaId}/alunos/{alunoId}")
    public ResponseEntity<Void> removerAluno(@PathVariable UUID disciplinaId, @PathVariable UUID alunoId) {
        matriculaAlunoService.cancelarMatricula(disciplinaId, alunoId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Excluir disciplina", description = "Perfis: ADMIN. Retorna 409 se houver matrículas.")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        disciplinaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
