# ⚡ DevPulse

> An AI-powered engineering intelligence platform with two distinct features: an authenticated, multi-tenant standup generator grounded in real GitHub activity, and a webhook-driven PR context enricher that posts AI-generated explanations directly onto pull requests. Dual-provider AI (local or cloud), with a real audit trail tracking exactly what every LLM call cost in time and tokens.

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.15-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.16.3-blueviolet)](https://github.com/langchain4j/langchain4j)
[![Docker](https://img.shields.io/badge/Docker-ready-blue?logo=docker)](https://hub.docker.com/)
[![Live Demo](https://img.shields.io/badge/Live-Render-46E3B7?logo=render)](https://devpulse-ohby.onrender.com)

---

## The Problem

Engineering teams lose real time to two specific, mechanical daily tasks:

**Standup prep** — 10-15 minutes per developer reconstructing yesterday's work from memory.
**PR review context** — reviewers asking "what ticket is this for?" and "why this approach?" before review can even begin.

DevPulse automates both, grounded in real GitHub data — not a blank chat window guessing at context it was never given.

```
No commits today  →  "No commits found for this date." (the LLM is never even called)
A PR opens         →  An AI-generated context comment appears within seconds, automatically
```

---

## Two Features, Two Architectural Patterns

| | Standup Generator | PR Context Enricher |
|---|---|---|
| **Trigger** | Authenticated API call (pull) | GitHub webhook (push) |
| **Auth model** | Per-tenant API key | HMAC-SHA256 signature verification |
| **Tenancy** | Fully multi-tenant, verified ownership | Single shared repository — see [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the honest gap |
| **Processing** | Synchronous | Asynchronous (`@Async`), idempotent |
| **Output** | JSON response | A real comment posted to the PR |

Building both deliberately, on their own terms, demonstrates two genuinely different integration patterns rather than forcing one shape onto both.

---

## What Makes This Different From a Generic AI Wrapper

1. **No-hallucination guardrail** — if there's nothing real to summarize, the LLM is never called at all. Enforced by tests, not just a prompt instruction.
2. **Dual-provider AI** — the same `ChatModel` interface runs on free, local Ollama for development, or fast, cloud-hosted Groq for production. One Spring profile flag switches it.
3. **Idempotent webhook handling** — a duplicate GitHub webhook delivery (a documented, real platform behavior) never double-posts a comment or double-calls the LLM.
4. **Honest architectural self-assessment** — see [`ARCHITECTURE.md`](./ARCHITECTURE.md) for what's genuinely production-grade versus intentionally simplified for this project's scale.

---

## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Runtime | Java 25 + Spring Boot 3.5.15 | Latest LTS, modern records, production-hardened |
| Database (prod) | PostgreSQL via Neon | Free tier with **no expiry** |
| Database (local) | PostgreSQL via Docker Compose | Identical engine to production |
| AI orchestration | LangChain4j 1.16.3 | Mature RAG/chat abstractions, rare in the Java ecosystem |
| AI provider (dev) | Ollama + Llama 3.2 | Free, fully local, zero data leaves the machine |
| AI provider (prod) | Groq + Llama 3.3 70B | OpenAI-compatible API, ~30x faster than local CPU inference |
| Auth (API) | Spring Security + split-key API auth | BCrypt-safe lookup — see [`BUSINESS.md`](./BUSINESS.md) |
| Auth (webhook) | HMAC-SHA256 signature verification | Proves a webhook genuinely came from GitHub |
| API Docs | springdoc-openapi + Swagger UI | Interactive, testable docs |
| Migrations | Flyway | Every schema change versioned and auditable |
| Container | Multi-stage Docker (JDK → JRE) | Minimal runtime image |
| Testing | JUnit 5, Mockito, AssertJ, MockRestServiceServer | Real unit tests, zero live network calls |
| Deployment | Render | Live at `devpulse-ohby.onrender.com` |

---

## Try It Yourself — Interactive API Docs

**`https://devpulse-ohby.onrender.com/swagger-ui.html`**

Every authenticated endpoint documented with real schemas, plus an **Authorize** button — register a tenant, paste your generated key, and call protected endpoints directly in the browser.

---

## API Reference

> **Every ID and secret shown below is a placeholder for illustration only.** There is no shared, fixed, or "demo" credential built into this API — each tenant generates its own by calling these endpoints themselves.

### 1. Register a Tenant (Public)

```http
POST /api/v1/tenants/register
{ "name": "Your Company Name" }
```

```json
{
  "tenantId": "<a-new-uuid-generated-for-you>",
  "name": "Your Company Name",
  "apiKey": "dp_live_<unique-to-you>.<unique-to-you>",
  "warning": "Save this API key now. It will not be shown again."
}
```

### 2. Register a Developer (Authenticated)

```http
POST /api/v1/developers
Authorization: Bearer <your-own-apiKey-from-step-1>
{ "githubUsername": "<the-github-username-you-want-to-track>" }
```

### 3. Generate a Standup (Authenticated, Tenant-Scoped)

```http
GET /api/v1/standup/generate?developerId=<your-own-developerId>&owner=<github-org>&repo=<repo-name>&date=<optional-YYYY-MM-DD>
Authorization: Bearer <your-own-apiKey>
```

A `developerId` belonging to a different tenant — or one that doesn't exist — returns a clean `404`, never a leaked result.

### 4. PR Context Enrichment (Webhook-Triggered — Not Called Directly)

This feature has no endpoint you call yourself. Instead, configure a webhook on a GitHub repository pointing at:

```
POST https://devpulse-ohby.onrender.com/webhooks/github
```

with content type `application/json`, the **Pull requests** event selected, and a secret you choose yourself (it must match the `GITHUB_WEBHOOK_SECRET` environment variable on whichever server you're running). Opening a PR on that repository will, within seconds, produce a real comment on the PR itself — no further action needed.

---

## Quick Start (Run It Yourself)

### Option 1: Docker Compose (local development, Ollama)

```bash
git clone https://github.com/sumituppal03/devpulse.git
cd devpulse

docker-compose up -d

# Install Ollama separately: https://ollama.com
ollama pull llama3.2

export GITHUB_TOKEN=<your-own-github-pat>
export GITHUB_WEBHOOK_SECRET=<a-secret-you-invent-yourself>

./mvnw spring-boot:run
```

API runs at `http://localhost:8080` — Swagger UI at `http://localhost:8080/swagger-ui.html`.

> Note: testing the webhook feature requires a publicly reachable URL (e.g., your deployed instance, or a tunneling tool like ngrok pointed at localhost) — GitHub cannot reach `localhost` directly.

### Option 2: Run Against Groq Instead of Ollama

```bash
export GROQ_API_KEY=<your-own-groq-key>
./mvnw spring-boot:run "-Dspring-boot.run.profiles=prod"
```

### Environment Variables

| Variable | Description | Required |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC connection string | ✅ |
| `SPRING_DATASOURCE_USERNAME` | Database user | ✅ |
| `SPRING_DATASOURCE_PASSWORD` | Database password | ✅ |
| `GITHUB_TOKEN` | GitHub PAT with `repo` scope | ✅ |
| `GITHUB_WEBHOOK_SECRET` | A secret you invent, matching your GitHub webhook config | ✅ (for PR Context) |
| `GROQ_API_KEY` | Groq Cloud API key | Only if `prod` profile active |
| `SPRING_PROFILES_ACTIVE` | `prod` to use Groq instead of Ollama | Optional |

> **No secrets in this repository.** Every credential is injected via environment variables at runtime.

---

## Run the Tests

```bash
./mvnw clean test
```

```
Tests run: 14, Failures: 0, Errors: 0
```

**What's actually being verified:**
- API key security: plaintext keys returned exactly once, never persisted
- GitHub JSON parsing: empty results return an empty list, never `null`
- **The no-hallucination guarantee**: the LLM is never called when there's nothing real to summarize
- **Webhook signature verification**: tampered payloads and wrong secrets are correctly rejected
- **PR enrichment idempotency**: a duplicate webhook delivery never double-posts or double-calls the LLM
- **Failure handling**: a GitHub API failure mid-enrichment is captured in the audit trail, not silently lost

---

## Architecture

```
                    ┌─────────────────────────┐
                    │   Authenticated Client    │
                    └────────────┬─────────────┘
                                 │ Bearer token
                                 ▼
                    ┌─────────────────────────┐
                    │ ApiKeyAuthenticationFilter│
                    └────────────┬─────────────┘
                                 ▼
                    ┌─────────────────────────┐
                    │   StandupController      │  ← tenant ownership verified
                    └────────────┬─────────────┘
                                 ▼
                  GitHubClient ──────► Ollama / Groq ──────► standups + llm_calls


                    ┌─────────────────────────┐
                    │      GitHub Webhook       │
                    └────────────┬─────────────┘
                                 │ HMAC-SHA256 signature
                                 ▼
                    ┌─────────────────────────┐
                    │  GitHubWebhookController  │  ← signature verified, event logged
                    └────────────┬─────────────┘
                                 │ @Async
                                 ▼
                    ┌─────────────────────────┐
                    │ PrContextEnricherService  │  ← idempotency check first
                    └────────────┬─────────────┘
                                 ▼
                  GitHubClient ──────► Ollama / Groq ──────► pr_enrichments + llm_calls
                                 │
                                 ▼
                      Comment posted to GitHub PR
```

For the honest assessment of where these two flows diverge architecturally (and why), see [`ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## Real Measured Performance

| Provider | Model | Typical Latency |
|---|---|---|
| Ollama (local CPU) | llama3.2 | ~44.7 seconds |
| Groq (cloud) | llama-3.3-70b-versatile | ~1.0-1.5 seconds |

Measured from the `llm_calls` audit table — not estimates.

---

## Design Decisions & Architecture

- [`BUSINESS.md`](./BUSINESS.md) — the reasoning behind every individual design decision
- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — an honest assessment of what's production-grade versus intentionally simplified, including the one significant tenancy gap between the two features

---

## Known Limitations

**No repository-to-tenant mapping** — PR Context Enrichment runs on a single shared GitHub repository with no tenant scoping. This is the most significant honest gap in the system; full reasoning in [`ARCHITECTURE.md`](./ARCHITECTURE.md).

**No message queue for async processing** — webhook enrichment relies on Spring's in-memory `@Async`, not a durable queue. A crash mid-processing loses that specific job.

**No rate limiting** — neither feature currently throttles repeated calls from a single source.

**Render free tier cold start** — 30-60 second delay after 15 minutes of inactivity.

**No Linear/Jira/Slack integration** — PR context is generated from the PR's own title, description, and diff only, not external ticket or discussion systems. Originally scoped, deliberately deferred.

---

## Roadmap

- [ ] Repository-to-tenant mapping via GitHub App installation (replacing the single shared PAT model)
- [ ] Per-tenant rate limiting on AI endpoints
- [ ] Durable queue (SQS/RabbitMQ) for webhook processing reliability
- [ ] Codebase Q&A via RAG (PGVector) — natural language questions answered with file citations
- [ ] Linear/Jira ticket context integration for PR enrichment

---

## Live Demo

API: **`https://devpulse-ohby.onrender.com`**
Swagger UI: **`https://devpulse-ohby.onrender.com/swagger-ui.html`**
Health check: **`https://devpulse-ohby.onrender.com/actuator/health`**

> Free tier — allow 30-60 seconds on the first request if the instance has spun down.

---

## Author

**Sumit Uppal** — Backend Engineer
[GitHub](https://github.com/sumituppal03)

---

*Built to production standard, including the bugs found and fixed, and the gaps stated honestly rather than hidden. See [`BUSINESS.md`](./BUSINESS.md) and [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the full reasoning.*
