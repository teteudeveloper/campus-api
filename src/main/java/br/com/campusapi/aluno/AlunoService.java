package br.com.campusapi.aluno;

import br.com.campusapi.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlunoService {

    private final AlunoRepository alunoRepository;

    public List<Aluno> listarTodos() {
        return alunoRepository.findAll();
    }

    public Aluno buscarPorId(UUID id) {
        return alunoRepository.findById(id).
                orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Aluno não encontrado com id: " + id));
    }

    public Aluno criar(AlunoDTO dto) {
        Aluno aluno = new Aluno();
        aluno.setNome(dto.getNome());
        aluno.setEmail(dto.getEmail());
        aluno.setMatricula(dto.getMatricula());
        return alunoRepository.save(aluno);
    }

    public Aluno atualizar(UUID id, AlunoDTO dto) {
        Aluno aluno = buscarPorId(id);
        aluno.setNome(dto.getNome());
        aluno.setEmail(dto.getEmail());
        aluno.setMatricula(dto.getMatricula());
        return alunoRepository.save(aluno);
    }

    public void deletar(UUID id) {
        buscarPorId(id);
        alunoRepository.deleteById(id);
    }
}
