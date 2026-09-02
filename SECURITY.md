# Politica e baseline de seguranca

## Reporte responsavel

Nao publique vulnerabilidades em issues. Envie um relato privado ao proprietario do
repositorio com o endpoint afetado, impacto, passos minimos de reproducao e uma forma
segura de contato. Nao inclua dados reais de clientes.

## Controles implementados

- consultas persistentes via Spring Data/JPA com parametros, sem concatenacao de SQL;
- isolamento por empresa e loja aplicado na camada de servico e nos repositorios;
- senhas com BCrypt fator 12 e tamanho de entrada limitado;
- bloqueio temporario por conta apos falhas repetidas;
- rate limit por origem nos endpoints de login e cadastro;
- JWT assinado com `issuer`, `audience`, `jti`, expiracao curta e limite de tamanho;
- CORS com lista explicita, sem curingas e sem credenciais cross-origin;
- API stateless, sem sessao de servidor e com CSRF desabilitado somente porque a
  autenticacao usa Bearer token, nao cookies;
- CSP, bloqueio de frames, Referrer-Policy e Permissions-Policy;
- validacao de DTOs, erros genericos e ausencia de stack traces nas respostas;
- limites para uploads, formularios e cabecalhos;
- deteccao de membros JSON duplicados;
- Dependabot e CI com testes, build e auditoria das dependencias de producao.

## Requisitos obrigatorios de producao

1. Use TLS do navegador ate o proxy e do proxy ate a aplicacao.
2. Gere `JWT_SECRET` aleatorio com pelo menos 256 bits; nunca reutilize chaves de
   desenvolvimento e mantenha a chave em um secret manager.
3. Mantenha `JPA_DDL_AUTO=validate`, `JPA_SHOW_SQL=false` e CORS apenas para dominios
   controlados.
4. O usuario PostgreSQL da aplicacao nao deve ser superusuario nem dono do banco.
   Conceda somente conexao e operacoes DML nas tabelas e sequences necessarias.
5. Aplique rate limiting compartilhado no WAF/API gateway. O limitador interno e uma
   segunda camada e nao substitui protecao distribuida quando houver varias replicas.
6. Restrinja o banco por rede, exija TLS, habilite backups testados e rotacione
   credenciais periodicamente.
7. Nao exponha `ng serve`, H2, Maven, portas administrativas ou logs ao publico.
8. Centralize logs de autenticacao e respostas 401/403/429, crie alertas e preserve-os
   sem tokens, senhas ou dados pessoais.
9. Execute DAST em homologacao e um pentest autorizado antes de receber dados reais
   ou processar pagamentos.

## Limites conhecidos

Rate limiting em memoria e local a cada instancia. Revogacao imediata de JWT exige uma
lista compartilhada ou rotacao de chave; por isso os tokens usam expiracao curta. Os
alertas das ferramentas locais de build nao fazem parte do bundle de producao, mas
devem continuar acompanhados ate que seus mantenedores publiquem correcoes.
