# Contributing to DevPulse

First off — thank you for being here. DevPulse is an open project and every contribution, from a typo fix to a new feature, genuinely matters.

This guide will get you from zero to a working local environment in under 15 minutes, and explain exactly how contributions work.

---

## Table of contents

- [What we're building](#what-were-building)
- [Ways to contribute](#ways-to-contribute)
- [Good first issues](#good-first-issues)
- [Local setup](#local-setup)
- [Project structure](#project-structure)
- [How to submit a PR](#how-to-submit-a-pr)
- [Code standards](#code-standards)
- [Running the tests](#running-the-tests)
- [Architecture decisions](#architecture-decisions)

---

## What we're building

DevPulse is an AI-powered engineering intelligence platform that turns a developer's real GitHub activity into accurate standup summaries — with a hard guarantee: if there are no commits, the LLM is never called. No hallucinations by design.

It's built with **Java 25, Spring Boot 3.5, LangChain4j, PostgreSQL, Redis, and Docker**.

We want DevPulse to be a reference implementation for production-grade, AI-native Java backend systems. Every design decision is documented in [`BUSINESS.md`](./BUSINESS.md).

---

## Ways to contribute

You don't have to write code to contribute. Here's what helps:

- **⭐ Star the repo** — it helps more people discover the project
- **🐛 Report bugs** — open an issue with steps to reproduce
- **💡 Suggest features** — open an issue describing the problem you want to solve
- **📖 Improve docs** — README, BUSINESS.md, inline comments, Swagger descriptions
- **🧪 Write tests** — we always want more test coverage, especially integration tests
- **🔧 Pick up a good-first-issue** — see below

---

## Good first issues

These are well-scoped tasks that are great starting points. Look for the `good-first-issue` label on the [issues page](https://github.com/sumituppal03/devpulse/issues).

### Beginner-friendly (no deep domain knowledge needed)

| Task | Description | Skills needed |
|------|-------------|---------------|
| Add GitHub topics to repo | Add tags: `java`, `spring-boot`, `langchain4j`, `ai`, `developer-tools` | GitHub UI only |
| Improve Swagger descriptions | Some endpoint descriptions are minimal — expand them with real examples | Java, OpenAPI annotations |
| Add `@NotBlank` validation to request DTOs | Input validation is missing on some fields | Spring Boot, Bean Validation |
| Write a test for the `TenantController` registration endpoint | Currently tested via service layer only | JUnit 5, Mockito, Spring MVC Test |
| Add health check documentation to README | Explain what `/actuator/health` returns and what each status means | Markdown |

### Intermediate (some Spring Boot knowledge needed)

| Task | Description | Skills needed |
|------|-------------|---------------|
| Add per-tenant rate limiting | Use a `RateLimiter` backed by an in-memory store or Redis; throttle AI endpoint calls per API key | Spring Boot, Java |
| Add `date` validation to standup endpoint | Currently `date` can be any string — validate it's a real `LocalDate` | Spring Boot, Bean Validation |
| Add pagination to developer listing endpoint | Return paginated results instead of a flat list | Spring Data JPA |
| Write integration tests using Testcontainers | Replace H2 in-memory tests with a real PostgreSQL container for integration tests | Testcontainers, JUnit 5 |
| Add structured logging with request correlation IDs | Each request should carry a trace ID through all log statements | Spring Boot, MDC |

### Advanced (understand the AI / architecture layer)

| Task | Description | Skills needed |
|------|-------------|---------------|
| Implement PR context enrichment via GitHub webhooks | Listen to `pull_request` events and post AI-generated business context as PR comments | Spring Boot, GitHub API, webhooks |
| Add PGVector support for codebase Q&A | Store file embeddings in PGVector, answer natural language questions with file citations | LangChain4j, PGVector, RAG |
| Add Slack integration for standup posting | After a standup is generated, allow tenants to post it directly to a configured Slack channel | Slack API, Spring Boot |
| Implement standup edit-distance tracking | After a standup is generated, allow developers to edit it and track how much they changed the AI output | Spring Boot, diff algorithms |

---

## Local setup

### Prerequisites

- Java 25 (or 21 minimum)
- Maven 3.9+
- Docker and Docker Compose
- Ollama (for local AI — free, private)
- A GitHub Personal Access Token (PAT) with `repo` scope

### Step 1 — Clone the repo

```bash
git clone https://github.com/sumituppal03/devpulse.git
cd devpulse
```

### Step 2 — Start PostgreSQL via Docker Compose

```bash
docker-compose up -d
```

This spins up a PostgreSQL instance at `localhost:5432`. Flyway will run migrations automatically on startup.

### Step 3 — Install Ollama and pull the model

```bash
# Install Ollama from https://ollama.com
ollama pull llama3.2
```

This downloads the local AI model (~2GB). It runs entirely on your machine — no API key needed, no data leaves your computer.

### Step 4 — Set environment variables

```bash
export GITHUB_TOKEN=your_github_pat_here
```

### Step 5 — Run the application

```bash
./mvnw spring-boot:run
```

The API starts at `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

### Optional: run against Groq instead of Ollama

If Ollama is too slow on your machine (~45 seconds per request on CPU), get a free Groq API key at https://console.groq.com and run:

```bash
export GROQ_API_KEY=your_groq_key
./mvnw spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Same code, ~30x faster responses.

---

## Project structure

```
src/
├── main/java/com/devpulse/
│   ├── auth/           # ApiKey filter, BCrypt verification
│   ├── tenant/         # Tenant registration, management
│   ├── developer/      # Developer registration, GitHub username mapping
│   ├── standup/        # Standup generation, LLM orchestration
│   ├── github/         # GitHub REST client (commit fetching)
│   ├── audit/          # llm_calls audit table, cost tracking
│   └── shared/         # ChatModel config, shared utilities
├── main/resources/
│   ├── db/migration/   # Flyway SQL migrations (V1__, V2__, ...)
│   ├── application.properties
│   ├── application-dev.properties   # Ollama config
│   └── application-prod.properties  # Groq config
└── test/               # JUnit 5 + Mockito tests
```

The most important place to understand the system is [`BUSINESS.md`](./BUSINESS.md) — it explains every non-obvious architectural decision with full reasoning.

---

## How to submit a PR

1. **Fork the repo** and create a branch from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```

2. **Make your changes.** Keep commits focused — one logical change per commit.

3. **Write or update tests** for your change. PRs without tests for new behaviour will be asked to add them.

4. **Run the full test suite** and make sure everything passes:
   ```bash
   ./mvnw clean test
   ```

5. **Open a pull request** against `main`. In the PR description, explain:
   - What problem this solves
   - How you solved it
   - Any tradeoffs or alternatives you considered

6. **Link the related issue** if one exists (e.g. `Closes #12`).

That's it. PRs are reviewed within 48 hours.

---

## Code standards

- **Java 25 style** — use records for DTOs where appropriate, avoid unnecessary verbosity
- **No magic numbers** — extract constants with descriptive names
- **No hardcoded secrets** — all credentials via environment variables
- **Fail loudly, fail early** — throw meaningful exceptions, don't swallow errors
- **Test the behaviour, not the implementation** — test what a method does, not how it does it
- **Document non-obvious decisions** — if you make a tradeoff, add a comment explaining why

For formatting, the project uses standard IntelliJ/Eclipse defaults. No custom formatter required.

---

## Running the tests

```bash
# Run all tests
./mvnw clean test

# Run a specific test class
./mvnw test -Dtest=StandupSummaryServiceTest

# Run with verbose output
./mvnw test -Dsurefire.useFile=false
```

Current test coverage covers:
- Tenant service (API key generation, one-time display guarantee)
- GitHub client (commit parsing, empty list handling)
- Standup summary service (no-hallucination guarantee — LLM never called with zero commits)
- Full Spring context boot

We'd love more integration tests using Testcontainers — see the good-first-issues table above.

---

## Architecture decisions

Every significant architectural decision in DevPulse is documented with full reasoning in [`BUSINESS.md`](./BUSINESS.md). Before making a large change, please read the relevant sections — it will save you time and help your PR get merged faster.

Key decisions covered:
- Why split-key API auth instead of JWT
- Why 404 (not 403) for cross-tenant resource access
- Why `langchain4j-openai` for Groq integration instead of a dedicated Groq module
- Why `GitHubClient` and AI config live in `shared/`

---

## Questions?

Open an issue with the `question` label, or reach out directly:

- GitHub: [@sumituppal03](https://github.com/sumituppal03)
- LinkedIn: [sumit-uppal03](https://www.linkedin.com/in/sumit-uppal03/)
- Email: sumituppal2004@gmail.com

---

*DevPulse is built in public. Every line of production-grade thinking is visible in the repo — come build with us.*
