package br.com.campusapi.exception;

/** Códigos estáveis retornados no campo {@code codigo} das respostas de erro. */
public final class CodigoErro {

    public static final String VALIDACAO = "VALIDACAO";
    public static final String JSON_INVALIDO = "JSON_INVALIDO";
    public static final String PARAMETRO_INVALIDO = "PARAMETRO_INVALIDO";
    public static final String RECURSO_NAO_ENCONTRADO = "RECURSO_NAO_ENCONTRADO";
    public static final String ROTA_NAO_ENCONTRADA = "ROTA_NAO_ENCONTRADA";
    public static final String METODO_NAO_SUPORTADO = "METODO_NAO_SUPORTADO";
    public static final String TIPO_MIDIA_NAO_SUPORTADO = "TIPO_MIDIA_NAO_SUPORTADO";
    public static final String CONFLITO = "CONFLITO";
    public static final String CONFLITO_CONCORRENCIA = "CONFLITO_CONCORRENCIA";
    public static final String TRANSICAO_INVALIDA = "TRANSICAO_INVALIDA";
    public static final String MATRICULA_DUPLICADA = "MATRICULA_DUPLICADA";
    public static final String VINCULO_EXISTENTE = "VINCULO_EXISTENTE";
    public static final String REGISTRO_EM_USO = "REGISTRO_EM_USO";
    public static final String OPERACAO_NAO_PERMITIDA = "OPERACAO_NAO_PERMITIDA";
    public static final String NAO_AUTENTICADO = "NAO_AUTENTICADO";
    public static final String TOKEN_INVALIDO = "TOKEN_INVALIDO";
    public static final String CREDENCIAIS_INVALIDAS = "CREDENCIAIS_INVALIDAS";
    public static final String SENHA_ATUAL_INCORRETA = "SENHA_ATUAL_INCORRETA";
    public static final String MUITAS_TENTATIVAS = "MUITAS_TENTATIVAS";
    public static final String ACESSO_NEGADO = "ACESSO_NEGADO";
    public static final String ERRO_INTERNO = "ERRO_INTERNO";

    private CodigoErro() {
    }
}
