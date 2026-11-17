package br.com.campusapi.config;

/**
 * Corpos de requisição prontos para o Swagger UI, na ordem do roteiro de demonstração (docs/roteiro-demo.md).
 * Valores fictícios, pensados para um banco vazio; marcadores COLE-AQUI-... devem ser trocados pelo id retornado no
 * passo anterior. As senhas de exemplo só existem se as contas forem criadas pelo próprio roteiro.
 */
public final class ExemplosOpenApi {

    public static final String MARCADOR_IDS =
            "Substitua os marcadores COLE-AQUI-... pelo id retornado no passo anterior (campo \"id\" da resposta).";

    public static final String LOGIN_ADMIN = """
            {"login": "admin", "senha": "DIGITE-A-SENHA-DE-BOOTSTRAP_ADMIN_PASSWORD"}""";
    public static final String LOGIN_PROFESSOR = """
            {"login": "carlos.mendes", "senha": "Professor2026"}""";
    public static final String LOGIN_ALUNO = """
            {"login": "lucas.oliveira", "senha": "Aluno2026x"}""";

    public static final String PROFESSOR = """
            {"nome": "Carlos Mendes", "email": "carlos.mendes@campus.edu", "registro": "PROF-001", "especialidade": "Matemática"}""";
    public static final String PROFESSOR_2 = """
            {"nome": "Ana Paula Ferreira", "email": "ana.ferreira@campus.edu", "registro": "PROF-002", "especialidade": "Física"}""";

    public static final String ALUNO = """
            {"nome": "Lucas Oliveira", "email": "lucas.oliveira@campus.edu", "matricula": "2026001"}""";
    public static final String ALUNO_2 = """
            {"nome": "Mariana Costa", "email": "mariana.costa@campus.edu", "matricula": "2026002"}""";

    public static final String DISCIPLINA_COM_PROFESSOR = """
            {"nome": "Cálculo I", "codigo": "MAT-101", "cargaHoraria": 80, "professorId": "COLE-AQUI-O-ID-DO-PROFESSOR"}""";
    public static final String DISCIPLINA_SEM_PROFESSOR = """
            {"nome": "Física I", "codigo": "FIS-101", "cargaHoraria": 60}""";

    public static final String USUARIO_PROFESSOR = """
            {"login": "carlos.mendes", "senha": "Professor2026", "perfil": "PROFESSOR", "professorId": "COLE-AQUI-O-ID-DO-PROFESSOR"}""";
    public static final String USUARIO_ALUNO = """
            {"login": "lucas.oliveira", "senha": "Aluno2026x", "perfil": "ALUNO", "alunoId": "COLE-AQUI-O-ID-DO-ALUNO"}""";
    public static final String USUARIO_ALUNO_2 = """
            {"login": "mariana.costa", "senha": "Aluna2026x", "perfil": "ALUNO", "alunoId": "COLE-AQUI-O-ID-DA-ALUNA"}""";

    public static final String MATRICULA = """
            {"alunoId": "COLE-AQUI-O-ID-DO-ALUNO", "disciplinaId": "COLE-AQUI-O-ID-DA-DISCIPLINA"}""";

    public static final String NOTAS_APROVADO = """
            {"nota1": 8.0, "nota2": 7.5}""";
    public static final String NOTAS_MEDIA_EXATA_7 = """
            {"nota1": 6.0, "nota2": 8.0}""";
    public static final String NOTAS_REPROVADO = """
            {"nota1": 5.0, "nota2": 6.0}""";
    public static final String NOTAS_PARCIAL = """
            {"nota1": 9.0}""";
    public static final String NOTAS_INVALIDA = """
            {"nota1": 11}""";

    private ExemplosOpenApi() {
    }
}
