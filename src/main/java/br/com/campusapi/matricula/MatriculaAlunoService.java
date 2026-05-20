package br.com.campusapi.matricula;

import br.com.campusapi.aluno.Aluno;
import br.com.campusapi.enums.MatriculaAlunoStatusEnum;
import br.com.campusapi.exception.RecursoNaoEncontradoException;
import br.com.campusapi.matricula.dto.AtualizarNotasRequestDTO;
import br.com.campusapi.matricula.dto.DisciplinasAlunoResponseDTO;
import br.com.campusapi.matricula.dto.HistoricoAlunoResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatriculaAlunoService {

    private static final Double MEDIA_PARA_APROVACAO = 7.0;

    private final MatriculaAlunoRepository matriculaAlunoRepository;

    public void criarMatricula(MatriculaAluno matriculaAluno) {
        matriculaAluno.setStatus(MatriculaAlunoStatusEnum.MATRICULADO);
        matriculaAlunoRepository.save(matriculaAluno);
    }

    public void trancarMatricula(UUID id) {
        MatriculaAluno matricula = matriculaAlunoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Matricula nao encontrada com id: " + id));

        if (matricula.getStatus().equals(MatriculaAlunoStatusEnum.MATRICULADO)) {
            matricula.setStatus(MatriculaAlunoStatusEnum.TRANCADO);
            matriculaAlunoRepository.save(matricula);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "So e possivel trancar matricula com status MATRICULADO");
        }
    }

    public void atualizarNotas(UUID id, AtualizarNotasRequestDTO dto) {
        MatriculaAluno matricula = matriculaAlunoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Matricula nao encontrada com id: " + id));

        if (dto.getNota1() != null) {
            matricula.setNota1(dto.getNota1());
        }
        if (dto.getNota2() != null) {
            matricula.setNota2(dto.getNota2());
        }

        if (matricula.getNota1() != null && matricula.getNota2() != null) {
            Double media = (matricula.getNota1() + matricula.getNota2()) / 2;
            matricula.setStatus(media >= MEDIA_PARA_APROVACAO
                    ? MatriculaAlunoStatusEnum.APROVADO
                    : MatriculaAlunoStatusEnum.REPROVADO);
        }

        matriculaAlunoRepository.save(matricula);
    }

    public HistoricoAlunoResponseDTO emitirHistorico(UUID alunoId) {
        List<MatriculaAluno> matriculas = matriculaAlunoRepository.findByAlunoId(alunoId);

        if (matriculas.isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Nenhuma matricula encontrada para o aluno: " + alunoId);
        }

        Aluno aluno = matriculas.get(0).getAluno();
        List<DisciplinasAlunoResponseDTO> disciplinas = new ArrayList<>();

        for (MatriculaAluno matricula : matriculas) {
            DisciplinasAlunoResponseDTO disc = new DisciplinasAlunoResponseDTO();
            disc.setNomeDisciplina(matricula.getDisciplina().getNome());
            disc.setNomeProfessor(matricula.getDisciplina().getProfessor().getNome());
            disc.setNota1(matricula.getNota1());
            disc.setNota2(matricula.getNota2());
            if (matricula.getNota1() != null && matricula.getNota2() != null) {
                disc.setMedia((matricula.getNota1() + matricula.getNota2()) / 2);
            }
            disc.setStatus(matricula.getStatus());
            disciplinas.add(disc);
        }

        HistoricoAlunoResponseDTO historico = new HistoricoAlunoResponseDTO();
        historico.setNomeAluno(aluno.getNome());
        historico.setEmailAluno(aluno.getEmail());
        historico.setMatriculaAluno(aluno.getMatricula());
        historico.setDisciplinas(disciplinas);
        return historico;
    }
}
