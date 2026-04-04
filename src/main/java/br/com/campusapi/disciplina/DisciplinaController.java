package br.com.campusapi.disciplina;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/disciplinas")
@RequiredArgsConstructor
public class DisciplinaController {

    private final DisciplinaService disciplinaService;

    @GetMapping
    public ResponseEntity<List<Disciplina>> listarTodos() {
        return ResponseEntity.ok(disciplinaService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Disciplina> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(disciplinaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<Disciplina> criar(@RequestBody DisciplinaDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(disciplinaService.criar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Disciplina> atualizar(@PathVariable UUID id,
                                                @RequestBody DisciplinaDTO dto) {
        return ResponseEntity.ok(disciplinaService.atualizar(id, dto));
    }

    @PostMapping("/{disciplinaId}/alunos/{alunoId}")
    public ResponseEntity<Disciplina> matricularAluno(
            @PathVariable UUID disciplinaId,
            @PathVariable UUID alunoId) {
        return ResponseEntity.ok(disciplinaService.matricularAluno(disciplinaId, alunoId));
    }

    @DeleteMapping("/{disciplinaId}/alunos/{alunoId}")
    public ResponseEntity<Disciplina> removerAluno(
            @PathVariable UUID disciplinaId,
            @PathVariable UUID alunoId) {
        return ResponseEntity.ok(disciplinaService.removerAluno(disciplinaId, alunoId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        disciplinaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}