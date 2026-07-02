# ⚡ DevPulse

> An AI-powered engineering intelligence platform with two distinct features: an authenticated, multi-tenant standup generator grounded in real GitHub activity, and a webhook-driven PR context enricher that posts AI-generated explanations directly onto pull requests. Dual-provider AI, Redis-backed rate limiting, and a real audit trail tracking exactly what every LLM call cost in time and tokens.

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.15-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.16.3-blueviolet)](https://github.com/langchain4j/langchain4j)
[![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis)](https://redis.io/)
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
Too many requests  →  429 Too Many Requests  (Redis enforces the limit, atomically)
```

---

## Three Architectural Patterns, One System

| | Standup Generator | PR Context Enricher | Rate Limiting |
|---|---|---|---|
| **Trigger** | Authenticated API call | GitHub webhook | Every standup request |
| **Auth model** | Split-key API authentication | HMAC-SHA256 signature | Tenant-scoped Redis counter |
| **Processing** | Synchronous | Asynchronous (`@Async`) | Atomic Redis `INCR` |
| **Output** | JSON standup summary | GitHub PR comment | `429` when limit exceeded |
| **Storage** | PostgreSQL + audit log | PostgreSQL + audit log | Redis (auto-expires after window) |

---

## What Makes This Different

1. **No-hallucination guardrail** — empty commit list → early return, LLM never called. Enforced by a unit test, not just a prompt instruction.
2. **Dual-provider AI** — Ollama locally, Groq in production. One Spring profile flag switches it. Zero code changes.
3. **Redis-backed rate limiting** — `INCR` + `EXPIRE` on a per-tenant key. Atomic, fast, self-resetting after the window. Proven under concurrent load (parallel requests, mixed `200`/`429` responses verified).
4. **Idempotent webhook handling** — duplicate GitHub webhook deliveries never double-post a comment or double-call the LLM.
5. **Honest architectural self-assessment** — see [`ARCHITECTURE.md`](./ARCHITECTURE.md) for what's genuinely production-grade versus intentionally simplified.

---

## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Runtime | Java 25 + Spring Boot 3.5.15 | Latest LTS, modern records |
| Database (prod) | PostgreSQL via Neon | Free tier, no expiry |
| Database (local) | PostgreSQL via Docker Compose | Identical engine to production |
| Cache & Rate Limit | Redis 7 (Render free tier in prod, Docker locally) | Atomic `INCR` for per-tenant counters |
| AI orchestration | LangChain4j 1.16.3 | Mature chat/RAG abstractions, rare in Java ecosystem |
| AI provider (dev) | Ollama + Llama 3.2 | Free, fully local |
| AI provider (prod) | Groq + Llama 3.3 70B | ~30x faster than local CPU inference |
| Auth (API) | Spring Security + split-key auth | BCrypt-safe lookup |
| Auth (webhook) | HMAC-SHA256 signature verification | Proves webhook genuinely came from GitHub |
| API Docs | springdoc-openapi + Swagger UI | Interactive, testable docs |
| Migrations | Flyway | Every schema change versioned |
| Container | Multi-stage Docker (JDK → JRE) | Minimal runtime image |
| Testing | JUnit 5, Mockito, AssertJ, MockRestServiceServer | Zero live network/LLM calls |
| Deployment | Render | Live at `devpulse-ohby.onrender.com` |

---

## Try It — Interactive API Docs

**`https://devpulse-ohby.onrender.com/swagger-ui.html`**

Every endpoint documented with real schemas, plus an **Authorize** button — register a tenant, paste your generated key, call protected endpoints directly in the browser.

---

## API Reference

> **Every ID and secret shown below is a placeholder.** There is no shared or demo credential — each tenant generates their own by calling these endpoints.

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
Authorization: Bearer <your-own-apiKey>
{ "githubUsername": "<your-github-username>" }
```

### 3. Generate a Standup (Authenticated, Rate Limited)

```http
GET /api/v1/standup/generate?developerId=<your-developerId>&owner=<org>&repo=<repo>&date=<optional-YYYY-MM-DD>
Authorization: Bearer <your-own-apiKey>
```

Returns `429 Too Many Requests` after 10 calls within a 60-second window per tenant. The counter is stored in Redis, resets automatically when the window expires.

### 4. PR Context Enrichment (Webhook-Driven)

Configure a GitHub webhook on your repository pointing at:
```
POST https://devpulse-ohby.onrender.com/webhooks/github
```

Content type: `application/json`, event: **Pull requests**, secret: a value you choose yourself (must match `GITHUB_WEBHOOK_SECRET` on your server). Opening a PR produces an AI-generated context comment automatically within seconds — no further action needed.

---

## Quick Start

### Docker Compose (local dev, Ollama)

```bash
git clone https://github.com/sumituppal03/devpulse.git
cd devpulse

docker-compose up -d        # starts PostgreSQL + Redis

# Install Ollama: https://ollama.com
ollama pull llama3.2

export GITHUB_TOKEN=<your-github-pat>
export GITHUB_WEBHOOK_SECRET=<a-secret-you-invent>

./mvnw spring-boot:run
```

### Run Against Groq (prod profile)

```bash
export GROQ_API_KEY=<your-groq-key>
./mvnw spring-boot:run "-Dspring-boot.run.profiles=prod"
```

### Environment Variables

| Variable | Description | Required |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC connection string | ✅ |
| `SPRING_DATASOURCE_USERNAME` | Database user | ✅ |
| `SPRING_DATASOURCE_PASSWORD` | Database password | ✅ |
| `SPRING_DATA_REDIS_HOST` | Redis hostname | ✅ |
| `SPRING_DATA_REDIS_PORT` | Redis port (default: 6379) | ✅ |
| `GITHUB_TOKEN` | GitHub PAT with `repo` scope | ✅ |
| `GITHUB_WEBHOOK_SECRET` | Webhook HMAC secret | ✅ |
| `GROQ_API_KEY` | Groq Cloud API key | Only if `prod` profile |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` for Groq | Optional |

---

## Run the Tests

```bash
./mvnw clean test
```

```
Tests run: 18, Failures: 0, Errors: 0
```

**What's verified:**
- API key security — plaintext returned once, hash stored only
- GitHub JSON parsing — empty results return empty list, never `null`
- **No-hallucination guarantee** — LLM never called on empty commit input
- **Webhook signature verification** — tampered payloads and wrong secrets rejected
- **PR enrichment idempotency** — duplicate webhook never double-posts
- **Failure handling** — GitHub API failure captured in audit trail, not silently lost
- **Rate limiter under limit** — `isAllowed()` returns `true` for count ≤ 10
- **Rate limiter over limit** — `isAllowed()` returns `false` for count > 10

---

## Architecture

For the full system diagram, both feature workflows, the database schema, and an honest assessment of what's production-grade versus simplified — see [`ARCHITECTURE.md`](./ARCHITECTURE.md).

**Short version:** authenticated clients hit the Standup Generator synchronously through a Redis rate limiter; GitHub pushes to the PR Context Enricher asynchronously via signed webhook. Both share `GitHubClient`, the dual-provider AI config, and the `llm_calls` audit table. Redis handles per-tenant rate limiting with atomic counters that auto-expire.

---

## Real Measured Performance

From the `llm_calls` audit table — not estimates:

| Provider | Model | Typical Latency |
|---|---|---|
| Ollama (local CPU) | llama3.2 | ~44.7 seconds |
| Groq (cloud) | llama-3.3-70b-versatile | ~1.0–1.5 seconds |

Rate limiter latency (Redis `INCR`): sub-millisecond, adds no perceptible overhead to requests.

---

## Design Decisions

- [`BUSINESS.md`](./BUSINESS.md) — reasoning behind every individual decision
- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — system diagrams, workflows, honest gap analysis

---

## Known Limitations

**No repository-to-tenant mapping** — PR Context Enrichment runs on a single shared repository with no tenant scoping. The most significant honest gap; full reasoning in [`ARCHITECTURE.md`](./ARCHITECTURE.md).

**No durable queue** — webhook processing uses Spring `@Async` (in-memory thread pool). A crash mid-enrichment loses that job.

**Render free tier cold start** — 30–60 seconds after 15 minutes idle.

**No Linear/Jira/Slack integration** — PR context generated from PR title, description, and diff only.

---

## Roadmap

- [ ] Repository-to-tenant mapping via GitHub App installation
- [ ] Durable queue (SQS/RabbitMQ) for webhook processing reliability
- [ ] Codebase Q&A via RAG (PGVector)
- [ ] Standup edit-distance tracking
- [ ] Linear/Jira ticket context for PR enrichment

---

## Live Demo

API: **`https://devpulse-ohby.onrender.com`**
Swagger UI: **`https://devpulse-ohby.onrender.com/swagger-ui.html`**
Health: **`https://devpulse-ohby.onrender.com/actuator/health`**

> Free tier — allow 30–60 seconds on first request after idle.

---

## Author

**Sumit Uppal** — Backend Engineer
[GitHub](https://github.com/sumituppal03) · [LinkedIn](https://www.linkedin.com/in/sumit-uppal03/)

---

*Two live AI features, Redis-backed rate limiting, 18 tests passing. See [`BUSINESS.md`](./BUSINESS.md) for the full decision log.*
