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

O DropSales nasceu da necessidade real de empreendedores acompanharem vendas, custos e estoque em um único lugar, eliminando a dependência de planilhas complexas. A plataforma possui uma identidade **SaaS moderna, responsiva e com temas claro e escuro**, pensada para a rotina de pequenas lojas no computador e no celular.

## ✨ Funcionalidades

- 📊 **Dashboard Financeiro Inteligente**: Faturamento, lucro bruto, CMV, taxas, valores recebidos e contas a receber calculados em tempo real, com gráficos e vendas recentes.
- 📦 **Gestão de Produtos**: Controle por SKU, preço de custo/venda, definição de estoque mínimo e alertas visuais para estoque baixo/crítico.
- 🛒 **PDV (Registro de Vendas)**: Múltiplos itens, baixa automática de estoque, edição, cancelamento seguro e proteção contra vendas duplicadas.
- 💳 **Pagamentos Completos**: Pix, dinheiro, débito, crédito, pagamentos divididos, parcelamento, troco e cálculo automático de taxas.
- 🧾 **Caixa e Contas a Receber**: Visão geral, agenda por parcela, entradas confirmadas, taxas e prévia diária de fechamento.
- 🏪 **Empresas e Lojas**: Estoque, vendas, financeiro e configurações separados por unidade.
- 👥 **Equipe e Permissões**: Convites individuais e papéis de proprietário, administrador, gerente e operador.
- 🤖 **Suporte com IA 24 horas**: Assistente Drop integrado ao site pelo webchat treinado do Botpress.
- 🛡️ **Auditoria de Vendas**: Histórico de criação, edição e cancelamento, preservando os dados financeiros.
- 📥 **Importação em Massa**: Cadastro instantâneo de dezenas de produtos via upload de planilhas Excel (`.xls` e `.xlsx`).
- 👤 **Perfil e Segurança**: Gestão de perfil com foto, autenticação JWT e isolamento dos dados por empresa e loja.

---

## 🛠️ Stack Tecnológica

### Backend (API REST)
- **Java 17** + **Spring Boot 3.2**
- **Spring Security** com **JWT** para autenticação stateless
- **Spring Data JPA** (Hibernate) para persistência
- Banco de Dados **PostgreSQL**
- **Apache POI** (Leitura de planilhas Excel)

### Frontend (SPA)
- **Angular 21** (Standalone components, Control Flow, Lazy Loading)
- **TailwindCSS 3** (Design System Dark Premium customizado)
- **Chart.js** + `ng2-charts` para visualização de dados
- **TypeScript 5.9**

### Infraestrutura & Deploy
- **Database**: PostgreSQL Serverless via [Neon.tech](https://neon.tech)
- **Backend**: [Render.com](https://render.com) (Dockerizado via Multi-stage build)
- **Frontend**: Hospedado no [GitHub Pages](https://pages.github.com/)

---

## ⚙️ Arquitetura

O sistema adota uma arquitetura limpa e separada:

- **Autenticação:** O frontend intercepta requisições via `JwtInterceptor` para injetar o `Bearer token`. O backend valida o token JWT antes do processamento.
- **Multitenancy por loja:** estoque, categorias, vendas, transações, configurações de taxa e recebimentos pertencem à `Loja` selecionada. O `Usuario` permanece como autor/responsável para auditoria. Contas com mais de uma loja devem enviar `X-Loja-Id` nas operações de escrita.
- **Papéis e permissões:** operadores consultam estoque e registram vendas; gerentes administram produtos e vendas; administradores e proprietários também gerenciam empresa, equipe e configurações financeiras.
- **Pagamentos e caixa:** uma venda aceita até cinco pagamentos, incluindo split, troco e parcelamento. Taxas são snapshots das regras da loja; parcelas a receber separam faturamento por competência do caixa efetivamente recebido.
- **Auditoria de vendas:** criação, edição e cancelamento são rastreados. Cancelar reverte estoque, transações e parcelas a receber uma única vez, sem apagar a venda.
- **Suporte inteligente:** o balão global carrega o Botpress somente quando o usuário o abre. O iframe possui permissões restritas e a interface orienta a não compartilhar senhas, dados bancários ou documentos pessoais.

---

## 💻 Rodando Localmente

### Pré-requisitos
- Java 17+
- Node.js 22 LTS e Angular CLI 21
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

### Banco existente (Neon/PostgreSQL)

As migrations são manuais e devem ser executadas em ordem numérica a partir de `database/migrations`. Para habilitar empresas/lojas, pagamentos avançados, identidade de e-mail normalizada, reforços de integridade e a validação do payload idempotente, aplique as migrations `004`, `005`, `006`, `007`, `008` e `009` nessa ordem.

Faça backup e use uma janela de manutenção: a migration 004 torna `loja_id` obrigatório e troca as chaves de isolamento; portanto, o backend antigo não deve continuar gravando durante a atualização. Depois do deploy, use `JPA_DDL_AUTO=validate` em produção. O arquivo `database/init.sql` representa o schema consolidado para instalações novas.

### 2. Configurando o Frontend

```bash
cd frontend
npm install

# Inicie o servidor de desenvolvimento do Angular
ng serve
```
Acesse `http://localhost:4200`.

> **Nota**: No modo de desenvolvimento, o Angular usa `environment.development.ts` e encaminha `/api` para `http://localhost:8080` pelo proxy configurado. `environment.ts` permanece reservado ao backend publicado.

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
  Feito por TH. <br>
  Licença MIT
</div>
