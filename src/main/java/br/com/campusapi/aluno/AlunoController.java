package br.com.campusapi.aluno;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/alunos")
@RequiredArgsConstructor
public class AlunoController {

    private final AlunoService alunoService;

    @GetMapping
    public ResponseEntity<List<Aluno>> listarTodos() {
        return ResponseEntity.ok(alunoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Aluno> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(alunoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<Aluno> criar(@RequestBody AlunoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(alunoService.criar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Aluno> atualizar(@PathVariable UUID id,
                                           @RequestBody AlunoDTO dto) {
        return ResponseEntity.ok(alunoService.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        alunoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}