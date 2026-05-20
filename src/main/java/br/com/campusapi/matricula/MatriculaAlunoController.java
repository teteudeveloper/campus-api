package br.com.campusapi.matricula;

import br.com.campusapi.matricula.dto.AtualizarNotasRequestDTO;
import br.com.campusapi.matricula.dto.CriarMatriculaRequestDTO;
import br.com.campusapi.matricula.dto.HistoricoAlunoResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/matriculas")
@RequiredArgsConstructor
public class MatriculaAlunoController {

    private final MatriculaAlunoService matriculaAlunoService;

    @PostMapping
    public ResponseEntity<MatriculaAluno> criarMatricula(@RequestBody CriarMatriculaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(matriculaAlunoService.criarMatricula(dto));
    }

    @PatchMapping("/trancar/{id}")
    public ResponseEntity<Void> trancarMatricula(@PathVariable UUID id) {
        matriculaAlunoService.trancarMatricula(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/atualizar-notas/{id}")
    public ResponseEntity<Void> atualizarNotas(@PathVariable UUID id,
                                               @RequestBody AtualizarNotasRequestDTO dto) {
        matriculaAlunoService.atualizarNotas(id, dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/emitir-historico/{alunoId}")
    public ResponseEntity<HistoricoAlunoResponseDTO> emitirHistorico(@PathVariable UUID alunoId) {
        return ResponseEntity.ok(matriculaAlunoService.emitirHistorico(alunoId));
    }
}
