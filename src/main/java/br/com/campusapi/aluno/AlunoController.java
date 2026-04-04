package br.com.campusapi.aluno;

import lombok.RequiredArgsConstructor;
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

    @GetMapping("{/id}")
    public ResponseEntity<Aluno> buscarPoriD(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(alunoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<Aluno> criar(@RequestBody AlunoDTO dto) {
        return ResponseEntity.ok(alunoService.criar(dto));
    }
    @PutMapping("{/id}")
    public ResponseEntity<Aluno> atualizar(@PathVariable("id") UUID id, @RequestBody AlunoDTO dto) {
        return ResponseEntity.ok(alunoService.atualizar(id, dto));
    }

    @DeleteMapping("{/id}")
    public void deletar(@PathVariable("id") UUID id) {}
}
