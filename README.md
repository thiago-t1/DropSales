<div align="center">
  
# 🚀 DropSales

**Gestão financeira e controle de estoque inteligente para pequenos negócios.**

[![Angular](https://img.shields.io/badge/Angular-DD0031?style=for-the-badge&logo=angular&logoColor=white)](https://angular.io/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-38B2AC?style=for-the-badge&logo=tailwind-css&logoColor=white)](https://tailwindcss.com/)

**[👉 Acessar a Aplicação Web](https://thiago-t1.github.io/DropSales)**

</div>

---

## 💡 Sobre o Projeto

O DropSales nasceu da necessidade real de empreendedores acompanharem vendas, custos e estoque em um único lugar, eliminando a dependência de planilhas complexas. A plataforma possui um design **Dark Theme Premium** focado na usabilidade e performance, entregando uma experiência de nível de software SaaS.

## ✨ Funcionalidades

- 📊 **Dashboard Financeiro Inteligente**: Saldo atual, faturamento, lucro líquido, despesas e CMV (Custo de Mercadoria Vendida) calculados em tempo real com gráficos dinâmicos.
- 📦 **Gestão de Produtos**: Controle por SKU, preço de custo/venda, definição de estoque mínimo e alertas visuais para estoque baixo/crítico.
- 🛒 **PDV (Registro de Vendas)**: Sistema ágil para registro de vendas com múltiplos itens, recálculo automático de subtotais e baixa de estoque em tempo real.
- 💸 **Controle de Transações**: Gestão de receitas e despesas com status de pagamento (pendente/pago) e vencimentos.
- 📥 **Importação em Massa**: Cadastro instantâneo de dezenas de produtos via upload de planilhas Excel (`.xlsx`).
- 👤 **Perfil e Segurança**: Gestão de perfil do usuário com upload de foto, autenticação via JWT e isolamento total de dados entre contas.

---

## 🛠️ Stack Tecnológica

### Backend (API REST)
- **Java 17** + **Spring Boot 3.2**
- **Spring Security** com **JWT** para autenticação stateless
- **Spring Data JPA** (Hibernate) para persistência
- Banco de Dados **PostgreSQL**
- **Apache POI** (Leitura de planilhas Excel)

### Frontend (SPA)
- **Angular 17** (Standalone components, Control Flow, Lazy Loading)
- **TailwindCSS 3** (Design System Dark Premium customizado)
- **Chart.js** + `ng2-charts` para visualização de dados
- **TypeScript 5.4**

### Infraestrutura & Deploy
- **Database**: PostgreSQL Serverless via [Neon.tech](https://neon.tech)
- **Backend**: [Render.com](https://render.com) (Dockerizado via Multi-stage build)
- **Frontend**: Hospedado no [GitHub Pages](https://pages.github.com/)

---

## ⚙️ Arquitetura

O sistema adota uma arquitetura limpa e separada:

- **Autenticação:** O frontend intercepta requisições via `JwtInterceptor` para injetar o `Bearer token`. O backend valida o token JWT antes do processamento.
- **Multitenancy Lógico:** Cada registro (Produto, Venda) é fortemente vinculado à conta do `Usuario` logado. Não há risco de vazamento de dados entre empresas.

---

## 💻 Rodando Localmente

### Pré-requisitos
- Java 17+
- Node.js 18+ e Angular CLI 17
- Maven 3.9+
- Instância do PostgreSQL rodando

### 1. Configurando o Backend

```bash
cd backend

# Configure suas variáveis de ambiente copiando o template
cp ../.env.example ../.env
```
Preencha o `.env` com suas credenciais de banco e uma chave JWT longa.
```bash
# Inicie o Spring Boot
mvn spring-boot:run
```
A API estará disponível em `http://localhost:8080`.

### 2. Configurando o Frontend

```bash
cd frontend
npm install

# Inicie o servidor de desenvolvimento do Angular
ng serve
```
Acesse `http://localhost:4200`.

> **Nota**: Para que o frontend local aponte para seu backend local, certifique-se de que o arquivo `src/environments/environment.ts` esteja com `apiUrl: 'http://localhost:8080/api'`.

---

## 🚀 Como fazer o Deploy do Frontend

O frontend está hospedado no GitHub Pages. **Apenas fazer o `git push` NÃO atualiza a página automaticamente** (a menos que seja configurada uma GitHub Action). 

Para atualizar a versão que está no ar após realizar alterações, siga os passos abaixo:

```bash
cd frontend

# 1. Gere a build de produção (com o caminho base correto do repositório)
ng build --base-href=/DropSales/

# 2. Faça o deploy da pasta compilada para a branch gh-pages
npx angular-cli-ghpages --dir=dist/dropsales-frontend/browser
```
*(Após rodar o comando acima, o GitHub pode levar alguns minutos para refletir a nova versão online).*

---

## 🔒 Variáveis de Ambiente Necessárias (Backend)

| Variável | Descrição |
|---|---|
| `DB_URL` | JDBC URL do PostgreSQL (ex: `jdbc:postgresql://localhost:5432/dropsales`) |
| `DB_USERNAME` | Usuário do banco de dados |
| `DB_PASSWORD` | Senha do banco de dados |
| `JWT_SECRET` | Chave criptográfica forte (mínimo 256 bits) para assinar os tokens |
| `CORS_ALLOWED_ORIGINS` | URLs do frontend permitidas (ex: `http://localhost:4200,https://thiago-t1.github.io`) |
| `JPA_DDL_AUTO` | Use `update` para dev local e `validate` para produção |

---

<div align="center">
  Feito com 💙 por Thiago. <br>
  Licença MIT
</div>
