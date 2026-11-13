package br.com.campusapi.usuario;

public enum Perfil {
    ADMIN,
    PROFESSOR,
    ALUNO;

    public String authority() {
        return "ROLE_" + name();
    }
}
