# Contributing to DevPulse

Thanks for considering a contribution. This covers how the project is organized, how to run it locally for free, and where a contribution would actually be useful — not a generic open-source template.

---

## Read These First

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — system diagrams, all three feature workflows, database schema, and the honest gap analysis. The gaps listed there are the most valuable contributions.
- [`BUSINESS.md`](./BUSINESS.md) — reasoning behind every non-obvious decision already made. If you're about to change something, check here first — there's usually a documented reason it's built the way it is.

---

## Project Structure

Organized by feature, not by technical layer:

```
com.devpulse/
├── tenant/          ← company registration, API key generation
├── developer/       ← developer registration, ownership verification
├── standup/         ← standup generation, finalize, history
├── prcontext/       ← webhook controller, PR enrichment service
├── codeqa/          ← RAG pipeline, repo indexing, Q&A service
├── integrations/    ← Slack and Linear per-tenant configuration
└── shared/
    ├── ai/          ← ChatModel config, EmbeddingModel config, audit log
    ├── github/      ← GitHubClient (used by standup, prcontext, codeqa)
    ├── linear/      ← LinearClient (ticket context for PR enrichment)
    ├── slack/       ← SlackClient (standup delivery)
    ├── ratelimit/   ← RateLimiterService (Redis INCR + EXPIRE)
    ├── security/    ← ApiKeyAuthenticationFilter, SecurityConfig
    └── webhook/     ← signature verifier, webhook event storage
```

The rule for `shared/`: if two or more features need the same thing, it lives in `shared/`. If only one feature uses it, it stays inside that feature's folder — even if it feels generic.

---

## Local Setup

Everything runs free locally. No cloud accounts required to start.

```bash
git clone https://github.com/sumituppal03/devpulse.git
cd devpulse

# Start PostgreSQL (with pgvector) + Redis
docker-compose up -d

# Pull the AI models (runs on your laptop, free)
ollama pull llama3.2
ollama pull nomic-embed-text

export GITHUB_TOKEN=<your-github-personal-access-token>
export GITHUB_WEBHOOK_SECRET=<any-string-you-invent>

./mvnw spring-boot:run
```

Open `http://localhost:8080/swagger-ui.html` — every endpoint is documented and testable from there.

**You do not need:**
- A Groq API key (Ollama handles AI locally)
- A Slack webhook URL (Slack integration is optional)
- A Linear API key (Linear integration is optional)
- Any cloud database (Docker handles PostgreSQL)

---

## Running Tests

```bash
./mvnw clean test
```

33 tests. All pass. Zero live network calls in unit tests — GitHub, Slack, Linear, and LLM calls are all mocked.

The test suite uses Testcontainers for `DevpulseApplicationTests` — it spins up a real `pgvector/pgvector:pg16` PostgreSQL container to verify the full Spring context loads correctly. Docker must be running for this test. All other tests run without Docker.

**Two guarantees that must never regress:**

1. `verify(chatModel, never()).chat(...)` in `StandupSummaryServiceTest` — the LLM is never called when there are no commits. If you touch `StandupSummaryService`, run this test specifically and make sure it still passes.

2. The idempotency check in `PrContextEnricherServiceTest` — a duplicate webhook delivery never calls the LLM or posts a second comment. If you touch `PrContextEnricherService`, verify this test still passes.

---

## Code Conventions

- **Constructor injection only** — no `@Autowired` on fields. `@RequiredArgsConstructor` from Lombok handles this, making classes testable without Spring.
- **Entities use protected no-args constructor + static factory** — `new Tenant()` is not callable from outside the class. Use `Tenant.create(...)`.
- **DTOs are Java records** — immutable, no manual getters/setters needed.
- **Every external dependency is injected** — `GitHubClient`, `ChatModel`, `SlackClient` are never constructed inside the class that uses them. This is what makes unit testing with mocks possible.
- **No secrets in code** — all credentials via environment variables. If you're adding a new integration, follow the `IntegrationService` pattern: store credentials in the `integrations` table per tenant, not as env vars.

---

## Adding a New Migration

Every schema change needs two files:

1. `src/main/resources/db/migration/V{N}__{description}.sql` — real PostgreSQL SQL
2. (No test migration needed — Testcontainers runs the real migrations)

The production migrations use PostgreSQL-specific syntax (`gen_random_uuid()`, `TIMESTAMPTZ`, `vector(768)`). Do not use H2 syntax in production migrations.

Note: the ivfflat vector index on `code_chunks` is **not** created in the migration. It is rebuilt by `CodeIndexingService.rebuildVectorIndex()` after every successful indexing run. Building it at migration time on an empty table creates a degenerate index. See `BUSINESS.md` Decision 16.

---

## What Would Actually Help

Pulled directly from the gap analysis in `ARCHITECTURE.md`:

| Contribution | Why it matters | Difficulty |
|---|---|---|
| Durable queue for webhook processing | `@Async` loses jobs on JVM crash. SQS or RabbitMQ would fix this permanently | Large |
| GitHub App installation model | Enables proper per-tenant webhook routing — the most significant architectural gap | Large |
| Class/method boundary chunking | Current line-based chunking produces imprecise Q&A retrieval for code questions | Medium |
| Column-level encryption for integration credentials | Slack URLs and Linear API keys currently stored as plaintext JSON | Medium |
| Sliding-window rate limiting | Eliminates the fixed-window boundary doubling problem | Small |
| Additional test coverage for `IntegrationService` edge cases | More negative path coverage welcome | Small |

If you want to work on something not listed here, open an issue first — happy to discuss the approach before you invest the time.

---

## Submitting a Change

1. Fork the repo, branch off `main`
2. Make your change, including a test if it touches behavior
3. `./mvnw clean test` must pass locally — a PR with failing tests will not be reviewed
4. If your change affects a decision in `BUSINESS.md` or a diagram in `ARCHITECTURE.md`, update those files in the same PR — documentation drift is treated as a real bug here
5. Open the PR with a description of what changed, why, and how you verified it

---

## Code of Conduct

Be direct. Be respectful. Keep feedback focused on the code.

This is a single-maintainer project — response times may be slower than a company-backed repo, but every genuine contribution is read and considered.
