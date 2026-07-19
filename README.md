# ⚡ DevPulse

> A multi-tenant AI platform that automates three things engineering teams do manually every day: writing standups, explaining pull requests, and answering questions about their own codebase. Three distinct architectural patterns, one deployed system.

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.x-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.16.3-blueviolet)](https://github.com/langchain4j/langchain4j)
[![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis)](https://redis.io/)
[![PGVector](https://img.shields.io/badge/PGVector-0.8-blue)](https://github.com/pgvector/pgvector)
[![Docker](https://img.shields.io/badge/Docker-ready-blue?logo=docker)](https://hub.docker.com/)
[![Live Demo](https://img.shields.io/badge/Live-Render-46E3B7?logo=render)](https://devpulse-ohby.onrender.com)
[![Tests](https://img.shields.io/badge/Tests-33%20passing-success)](https://github.com/sumituppal03/devpulse)

---

## What It Does

```
Developer registers with company API key
         │
         ├── Standup Generator
         │   GET /api/v1/standup/generate
         │   Fetches commits from ALL company repos → AI writes standup in your voice
         │   No commits? Returns "No commits found" — LLM never called
         │   Finalize → posts to Slack automatically
         │
         ├── PR Context Enricher
         │   GitHub webhook fires when PR opens
         │   Fetches diff + Linear ticket from branch name → AI posts context comment
         │   Returns 200 immediately, processes async — no GitHub timeouts
         │
         └── Codebase Q&A
             POST /api/v1/codeqa/ask
             Indexes your repos into PGVector → answer questions with file citations
             Answers from real code only — never invents
```

---

## Three Features, Three Architectural Patterns

| | Standup Generator | PR Context Enricher | Codebase Q&A |
|---|---|---|---|
| **Trigger** | Authenticated API call | GitHub webhook (HMAC-SHA256) | Authenticated API call |
| **Processing** | Synchronous | Asynchronous (`@Async`) | Synchronous |
| **AI input** | GitHub commits + style sample | PR diff + Linear ticket | PGVector similarity search |
| **Output** | Standup summary → Slack | GitHub PR comment | Answer + file citations |
| **Hallucination guard** | Empty commits → early return | GitHub failure → error log | No chunks → early return |

---

## What Makes This Different

**1. No-hallucination is a code constraint, not a prompt instruction**
Empty data → early return. LLM never called. Verified by `verify(chatModel, never()).chat(...)` in the test suite. If someone removes the guard, the test fails loudly.

**2. Dual-provider AI with zero code changes**
Ollama locally (free, ~45s), Groq in production (~1.5s). One Spring profile flag. Same `ChatModel` interface for both. Measured from real `llm_calls` audit data, not estimates.

**3. Multi-repo standup generation**
A company registers all their GitHub repos once. Standup generation automatically fetches commits across ALL of them — the developer never specifies which repo. This is how it should work when a team works across multiple services.

**4. Linear ticket context in PR comments**
Branch name `feature/LIN-234-auth-refactor` → fetches the actual Linear ticket → adds business WHY to the AI comment alongside the technical WHAT from the diff.

**5. Slack delivery after finalize**
Developer reviews and edits the AI standup. Clicks finalize. Standup posts to the team's Slack channel automatically. Edit distance is tracked as a product quality metric.

**6. Idempotent webhook handling**
GitHub redelivers webhooks on timeouts — documented platform behavior. Existence check before processing means duplicate delivery never causes a second comment or second LLM call. Verified by unit test.

**7. Redis rate limiting tested under real concurrency**
11 parallel requests, `Start-Job` in PowerShell, fired simultaneously. Confirmed mixed `200`/`429` responses in a single run. Not theory — verified behavior.

---

## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Runtime | Java 25 + Spring Boot 3.5.x | Modern records, latest LTS |
| AI orchestration | LangChain4j 1.16.3 | Rare in Java ecosystem — mature chat + RAG abstractions |
| AI provider (dev) | Ollama + Llama 3.2 | Free, fully local, no API key needed |
| AI provider (prod) | Groq + Llama 3.3 70B | ~30x faster than local CPU inference |
| Vector search | PostgreSQL + pgvector | Cosine similarity, ivfflat index, same DB as everything else |
| Embeddings | nomic-embed-text (Ollama) | 768 dimensions, free, runs locally |
| Database | PostgreSQL via Neon (prod) / Docker (local) | Real engine in both environments |
| Cache + Rate limit | Redis 7 | Atomic `INCR` + `EXPIRE` for per-tenant counters |
| Auth (API) | Spring Security + split-key BCrypt | Fast indexed lookup + BCrypt verify |
| Auth (webhook) | HMAC-SHA256 constant-time comparison | Prevents timing attacks on signature guessing |
| Integrations | Slack Incoming Webhooks, Linear GraphQL API | Per-tenant configuration |
| Schema migrations | Flyway | Every change versioned, reproducible across environments |
| Container | Multi-stage Docker (JDK build → JRE runtime) | Minimal production image |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers | 33 tests, zero live network calls in unit tests |
| CI/CD | GitHub Actions + Render auto-deploy | Push to main → live in ~3 minutes |

---

## Live Demo

**Swagger UI:** `https://devpulse-ohby.onrender.com/swagger-ui.html`
**Health:** `https://devpulse-ohby.onrender.com/actuator/health`

> Render free tier — first request after 15 minutes idle takes 30-60 seconds.

---

## Quick Start (Local, Free, No API Keys Needed)

```bash
git clone https://github.com/sumituppal03/devpulse.git
cd devpulse

# Start PostgreSQL (pgvector) + Redis
docker-compose up -d

# Pull the AI models (free, runs on your laptop)
ollama pull llama3.2
ollama pull nomic-embed-text

export GITHUB_TOKEN=<your-github-pat>
export GITHUB_WEBHOOK_SECRET=<a-string-you-invent>

./mvnw spring-boot:run
```

Open `http://localhost:8080/swagger-ui.html` — everything is interactive from there.

### With Groq (production-speed inference)

```bash
export GROQ_API_KEY=<your-groq-key>
./mvnw spring-boot:run "-Dspring-boot.run.profiles=prod"
```

---

## API Reference

### Company Setup (do this once)

```http
# 1. Register your company
POST /api/v1/tenants/register
{"name": "Acme Corp"}
→ returns dp_live_xxx.yyy  ← save this, shown once only

# 2. Register your GitHub repos
POST /api/v1/repos
Authorization: Bearer dp_live_xxx.yyy
{"githubOwner": "your-org", "githubRepo": "backend", "defaultBranch": "main"}

# 3. Index a repo for codebase Q&A
POST /api/v1/repos/{repositoryId}/index
Authorization: Bearer dp_live_xxx.yyy
→ runs in background, check status at GET /api/v1/repos/{id}/index/status

# 4. Configure Slack (sends a test message first)
POST /api/v1/integrations/slack
Authorization: Bearer dp_live_xxx.yyy
{"webhookUrl": "https://hooks.slack.com/services/..."}

# 5. Configure Linear
POST /api/v1/integrations/linear
Authorization: Bearer dp_live_xxx.yyy
{"apiKey": "lin_api_..."}
```

### Developer Daily Flow

```http
# Register yourself (use the company API key)
POST /api/v1/developers
Authorization: Bearer dp_live_xxx.yyy
{"githubUsername": "your-github-username"}

# Generate today's standup (auto-fetches all company repos)
GET /api/v1/standup/generate?developerId={uuid}
Authorization: Bearer dp_live_xxx.yyy
→ 429 after 10 requests/minute per tenant

# Finalize (posts to Slack if configured)
PUT /api/v1/standup/{id}/finalize
Authorization: Bearer dp_live_xxx.yyy
{"content": "edited standup text here"}

# View history
GET /api/v1/standup/history?developerId={uuid}&days=7
Authorization: Bearer dp_live_xxx.yyy

# Ask a codebase question
POST /api/v1/codeqa/ask
Authorization: Bearer dp_live_xxx.yyy
{"repositoryId": "{uuid}", "question": "How does authentication work?"}
```

### PR Context (zero configuration after webhook setup)

```
Configure webhook at: https://devpulse-ohby.onrender.com/webhooks/github
Event: Pull requests
Content type: application/json
Secret: your GITHUB_WEBHOOK_SECRET value

Open a PR → AI comment appears automatically within seconds.
Branch named feature/LIN-234-something → Linear ticket context included automatically.
```

---

## Environment Variables

| Variable | Description | Required |
|---|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | Yes |
| `SPRING_DATASOURCE_USERNAME` | Database user | Yes |
| `SPRING_DATASOURCE_PASSWORD` | Database password | Yes |
| `SPRING_DATA_REDIS_HOST` | Redis hostname only (no redis:// prefix) | Yes |
| `SPRING_DATA_REDIS_PORT` | Redis port (default 6379) | Yes |
| `GITHUB_TOKEN` | GitHub PAT with `repo` scope | Yes |
| `GITHUB_WEBHOOK_SECRET` | Webhook HMAC signing secret | Yes |
| `GROQ_API_KEY` | Groq Cloud API key | Only with `prod` profile |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` for Groq | Optional |

---

## Tests

```bash
./mvnw clean test
```

```
Tests run: 33, Failures: 0, Errors: 0
```

What's verified:
- API key security — hash stored only, plaintext returned once and never again
- No-hallucination guarantee — LLM never called on empty commit data
- Webhook signature verification — tampered payloads and wrong secrets rejected
- PR enrichment idempotency — duplicate webhook never double-posts
- GitHub API failure — captured in audit trail, nothing partial persisted
- Rate limiter correctness — under and over limit both verified
- Slack integration — broken webhook URL rejected before saving
- Linear integration — all early-exit paths verified without live API calls
- Context loads — full Spring context wires correctly via Testcontainers

---

## Performance (Measured From llm_calls Audit Table)

| Provider | Model | Latency |
|---|---|---|
| Ollama (local CPU) | llama3.2 | ~44,700ms |
| Groq (cloud) | llama-3.3-70b-versatile | ~933–1,492ms |

Redis INCR latency: sub-millisecond. No perceptible overhead on standup requests.

---

## Known Limitations

**No durable queue** — webhook processing uses Spring `@Async`. A crash mid-enrichment loses that job. SQS or RabbitMQ is the documented next step.

**Chunking quality** — codebase Q&A uses line-based chunking. Class/method boundary chunking would improve retrieval accuracy for code-specific questions.

**Render free tier cold start** — 30-60 seconds after 15 minutes idle.

See [`ARCHITECTURE.md`](./ARCHITECTURE.md) for full gap analysis.

---

## Roadmap

- [ ] Durable queue for webhook processing reliability
- [ ] Class/method boundary chunking for better Q&A retrieval
- [ ] GitHub App installation model for proper per-tenant webhook routing
- [ ] VS Code extension — standup and Q&A without leaving the editor
- [ ] Stripe billing — free tier + team plan

---

## Design Decisions

Every non-obvious decision documented with its reasoning in [`BUSINESS.md`](./BUSINESS.md).
Full system architecture in [`ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## Author

**Sumit Uppal** — Backend Engineer
[GitHub](https://github.com/sumituppal03) · [LinkedIn](https://www.linkedin.com/in/sumit-uppal03/)

---

*Three live AI features. 33 tests passing. Every gap documented honestly.*
