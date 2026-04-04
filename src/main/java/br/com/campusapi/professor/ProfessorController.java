package br.com.campusapi.professor;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/professores")
@RequiredArgsConstructor
public class ProfessorController {

    private final ProfessorService professorService;

    @GetMapping
    public ResponseEntity<List<Professor>> listarTodos() {
        return ResponseEntity.ok(professorService.listarTodos());
    }

    @GetMapping("{/id}")
    public ResponseEntity<Professor> buscarPorId(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(professorService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<Professor> criar(@RequestBody ProfessorDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
        .body(professorService.criar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Professor> atualizar(@PathVariable UUID id, @RequestBody ProfessorDTO dto) {
        return ResponseEntity.ok(professorService.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        professorService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
