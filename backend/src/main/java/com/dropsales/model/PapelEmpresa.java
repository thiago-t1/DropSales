package com.dropsales.model;

/**
 * Papel do usuario dentro de uma empresa. O perfil global legado de Usuario nao
 * deve ser usado para autorizar operacoes de uma loja.
 */
public enum PapelEmpresa {
    PROPRIETARIO,
    ADMINISTRADOR,
    GERENTE,
    OPERADOR
}
