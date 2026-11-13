package br.com.campusapi.comum;

import java.util.Locale;

/** Normalização única aplicada antes de persistir e de consultar campos textuais. */
public final class Textos {

    private Textos() {
    }

    /** Remove espaços nas extremidades e colapsa espaços internos repetidos. */
    public static String normalizarNome(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.strip().replaceAll("\\s+", " ");
        return normalizado.isEmpty() ? null : normalizado;
    }

    public static String normalizarEmail(String valor) {
        return valor == null ? null : valor.strip().toLowerCase(Locale.ROOT);
    }

    /** Identificadores institucionais (matrícula, registro, código) são comparados sem diferenciar caixa. */
    public static String normalizarIdentificador(String valor) {
        return valor == null ? null : valor.strip().toUpperCase(Locale.ROOT);
    }

    public static String normalizarLogin(String valor) {
        return valor == null ? null : valor.strip().toLowerCase(Locale.ROOT);
    }

    /** Padrão LIKE com curingas escapados, para buscas parciais sem diferenciar caixa. */
    public static String padraoContem(String valor) {
        String escapado = valor.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escapado + "%";
    }
}
