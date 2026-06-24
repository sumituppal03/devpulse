# ⚡ DevPulse

> An AI-powered engineering intelligence platform that turns a developer's real GitHub activity into accurate, grounded standup summaries — multi-tenant, dual-provider AI (local or cloud), with a real audit trail tracking exactly what every LLM call cost in time and tokens.

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.15-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.16.3-blueviolet)](https://github.com/langchain4j/langchain4j)
[![Docker](https://img.shields.io/badge/Docker-ready-blue?logo=docker)](https://hub.docker.com/)
[![Live Demo](https://img.shields.io/badge/Live-Render-46E3B7?logo=render)](https://devpulse-ohby.onrender.com)

---

## The Problem

Every developer spends 10-15 minutes before standup manually reconstructing what they did yesterday — scanning commits, half-remembering tickets, composing three bullet points nobody will remember by lunch. AI "wrapper" tools that summarize text exist everywhere, but most have no real data moat: swap the prompt, swap the product.

DevPulse is built differently — it's grounded in a tenant's **actual GitHub history**, not a blank chat window, and it refuses to invent activity that didn't happen.

```
No commits today  →  "No commits found for this date." (the LLM is never even called)
Real commits today →  3 grounded bullet points, written in the developer's own style
```

---

## What Makes This Different

| Approach | Data grounding | Hallucination risk | Cost/latency tradeoff |
|---|---|---|---|
| Generic ChatGPT wrapper | None — whatever you type | High | Single provider, no choice |
| Most "AI standup" tools | Vague, often just a text box | Medium | Locked to one vendor |
| **DevPulse** | Real commits, real style sample | **Near-zero — empty input never reaches the LLM** | **Swap providers via one config flag** |

**Two architectural guarantees:**

1. **No-hallucination guardrail** — if there are zero commits for a date, the LLM is never called at all. This is enforced in code and verified by a unit test, not just a prompt instruction.
2. **Dual-provider AI** — the exact same `ChatModel` interface runs on free, private, local Ollama for development, or fast, cloud-hosted Groq for production. One Spring profile flag switches it. No code changes.

---

## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Runtime | Java 25 + Spring Boot 3.5.15 | Latest LTS, modern records, production-hardened |
| Database (prod) | PostgreSQL via Neon | Free tier with **no expiry** — unlike most free-tier Postgres offerings |
| Database (local) | PostgreSQL via Docker Compose | Fast local spin-up, identical engine to production |
| AI orchestration | LangChain4j 1.16.3 | Mature RAG/chat abstractions, rare in the Java ecosystem |
| AI provider (dev) | Ollama + Llama 3.2 | Free, fully local, zero data leaves the machine |
| AI provider (prod) | Groq + Llama 3.3 70B | OpenAI-compatible API, ~30x faster than local CPU inference |
| Auth | Spring Security + custom split-key API auth | BCrypt-safe lookup — see [`BUSINESS.md`](./BUSINESS.md) for why |
| API Docs | springdoc-openapi + Swagger UI | Interactive, testable docs — not just a README wall of text |
| Migrations | Flyway | Every schema change versioned, auditable, reproducible |
| Container | Multi-stage Docker (JDK → JRE) | Minimal runtime image, no build tools shipped |
| Testing | JUnit 5, Mockito, AssertJ, MockRestServiceServer | Real unit tests, zero live network calls |
| Deployment | Render | Live at `devpulse-ohby.onrender.com` |

---

## Try It Yourself — Interactive API Docs (Recommended Starting Point)

Rather than copying static examples below, the fastest way to actually explore this API is the live Swagger UI:

**`https://devpulse-ohby.onrender.com/swagger-ui.html`**

It documents every endpoint with real request/response schemas, and includes an **Authorize** button — register a tenant via the UI itself, paste your generated key into Authorize, and call every other endpoint directly in the browser. No `curl`, no Postman setup required.

---

## API Reference

> **Every ID shown below is a placeholder for illustration only.** There is no shared, fixed, or "demo" ID built into this API — each tenant generates its own unique `apiKey` and `developerId` by calling these endpoints themselves, exactly as shown.

### 1. Register a Tenant (Public)

```http
POST /api/v1/tenants/register
{ "name": "Your Company Name" }
```

**Response shape:**
```json
{
  "tenantId": "<a-new-uuid-generated-for-you>",
  "name": "Your Company Name",
  "apiKey": "dp_live_<unique-to-you>.<unique-to-you>",
  "warning": "Save this API key now. It will not be shown again."
}
```

**Save the `apiKey` from your own response right now** — it is shown exactly once and never stored anywhere, by design. You'll use it as a Bearer token for every call below.

---

### 2. Register a Developer (Authenticated)

```http
POST /api/v1/developers
Authorization: Bearer <your-own-apiKey-from-step-1>
{ "githubUsername": "<the-github-username-you-want-to-track>" }
```

**Response shape:**
```json
{
  "developerId": "<a-new-uuid-generated-for-you>",
  "githubUsername": "<the-username-you-provided>",
  "timezone": "UTC"
}
```

**Save the `developerId` from your own response** — you'll need it for the next call.

---

### 3. Generate a Standup (Authenticated, Tenant-Scoped)

```http
GET /api/v1/standup/generate?developerId=<your-own-developerId-from-step-2>&owner=<github-org-or-username>&repo=<repo-name>&date=<optional-YYYY-MM-DD>
Authorization: Bearer <your-own-apiKey-from-step-1>
```

**Response shape:**
```json
{
  "summary": "* [an AI-generated bullet reflecting a REAL commit]\n* [another real, grounded bullet]\n* [a third real, grounded bullet]",
  "commitCount": 4,
  "commits": [ "...raw commit objects, included for transparency..." ]
}
```

**Important behavior to understand:**
- `developerId` must belong to a developer **you registered under your own tenant** in step 2. A `developerId` belonging to a different tenant — or one that doesn't exist at all — returns a clean `404`, not a `403` and not a leaked result. See [`BUSINESS.md`](./BUSINESS.md) for why that distinction is intentional.
- If the specified date has zero commits, `summary` will simply say `"No commits found for this date."` — the AI is never invoked when there's nothing real to summarize.

---

## Quick Start (Run It Yourself)

### Option 1: Docker Compose (local development, Ollama)

```bash
git clone https://github.com/sumituppal03/devpulse.git
cd devpulse

docker-compose up -d        # starts PostgreSQL

# Install Ollama separately: https://ollama.com
ollama pull llama3.2

export GITHUB_TOKEN=your_github_pat

./mvnw spring-boot:run
```

API runs at `http://localhost:8080` — Swagger UI at `http://localhost:8080/swagger-ui.html`.

---

### Option 2: Run Against Groq Instead of Ollama

```bash
export GROQ_API_KEY=your_groq_key
./mvnw spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Same codebase, same `ChatModel` interface — just a different bean activated by the `prod` Spring profile.

---

### Environment Variables

| Variable | Description | Required |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC connection string | ✅ |
| `SPRING_DATASOURCE_USERNAME` | Database user | ✅ |
| `SPRING_DATASOURCE_PASSWORD` | Database password | ✅ |
| `GITHUB_TOKEN` | GitHub PAT with `repo` scope | ✅ |
| `GROQ_API_KEY` | Groq Cloud API key | Only if `prod` profile active |
| `SPRING_PROFILES_ACTIVE` | `prod` to use Groq instead of Ollama | Optional |

> **No secrets in this repository.** Every credential is injected via environment variables at runtime.

---

## Run the Tests

```bash
./mvnw clean test
```

```
Tests run: 6, Failures: 0, Errors: 0
```

**What's actually being verified:**
- `TenantServiceTest` — the plaintext API key is returned exactly once and never persisted
- `GitHubClientTest` — GitHub's JSON is parsed correctly; "no commits" returns an empty list, never `null`
- `StandupSummaryServiceTest` — **the LLM is never called when there are zero commits** (the no-hallucination guarantee, enforced by a real assertion, not a comment)
- `DevpulseApplicationTests` — the full Spring context boots correctly, every bean wires together

---

## Architecture

```
Client Request
      │
      ▼
┌─────────────────────┐
│ ApiKeyAuthentication │  ← Bearer token parsed, tenant looked up by keyId,
│       Filter         │     secret verified via BCrypt
└──────────┬───────────┘
           │
           ▼
┌─────────────────────┐
│  StandupController   │  ← Verifies developerId belongs to THIS tenant
└──────────┬───────────┘     (404 if not — never leaks existence)
           │
     ┌─────┴─────┐
     ▼           ▼
┌─────────┐  ┌──────────────────┐
│ GitHub  │  │ StandupSummary    │
│ Client  │  │ Service           │
└─────────┘  └────────┬──────────┘
                       │
              ┌────────┴────────┐
              ▼                 ▼
        ┌──────────┐      ┌──────────┐
        │  Ollama   │      │   Groq   │
        │  (!prod)  │      │  (prod)  │
        └──────────┘      └──────────┘
                       │
                       ▼
              ┌─────────────────┐
              │  PostgreSQL      │
              │  standups +      │
              │  llm_calls audit │
              └─────────────────┘
```

---

## Real Measured Performance

From the `llm_calls` audit table — not estimates:

| Provider | Model | Typical Latency | Notes |
|---|---|---|---|
| Ollama (local CPU) | llama3.2 | ~44.7 seconds | No GPU, runs entirely on a laptop CPU |
| Groq (cloud) | llama-3.3-70b-versatile | ~1.0-1.5 seconds | ~30x faster, costs nothing on free tier |

This is the real, measured tradeoff between privacy/cost and speed — documented honestly because it's a genuine architectural decision, not a flaw.

---

## Design Decisions

Full rationale for every architectural choice — the split-key auth design, why tenant ownership returns 404 not 403, why Groq integration uses `langchain4j-open-ai` instead of a dedicated module, why `GitHubClient` and the AI config live in `shared/` — is documented in [`BUSINESS.md`](./BUSINESS.md).

---

## Known Limitations

**Render free tier cold start** — spins down after 15 minutes idle; first request after that takes 30-60 seconds.

**Neon free tier storage** — 0.5 GB per project. Ample for this demo's scale, not for real production volume.

**Local Ollama latency** — CPU-only inference takes ~45 seconds per call. Acceptable for development, not for a real-time product experience.

**No event-driven features yet** — standup generation is request-triggered (`GET`), not webhook-driven. PR context enrichment (originally planned) would require this.

**No rate limiting yet** — a single tenant could call the AI endpoint repeatedly with no throttling. Planned for a future iteration.

---

## Roadmap

- [ ] Per-tenant rate limiting on AI endpoints
- [ ] PR Context Enricher — webhook-driven, posts business context to GitHub PRs automatically
- [ ] Codebase Q&A via RAG (PGVector) — natural language questions answered with file citations
- [ ] Standup edit-distance tracking — measure how much developers actually edit AI drafts
- [ ] Slack integration for posting finalized standups directly

---

## Live Demo

API is deployed at: **`https://devpulse-ohby.onrender.com`**

> Free tier — allow 30-60 seconds on the first request if the instance has spun down.

Interactive API docs: **`https://devpulse-ohby.onrender.com/swagger-ui.html`**

Health check: **`https://devpulse-ohby.onrender.com/actuator/health`**

---

## Author

**Sumit Uppal** — Backend Engineer
[GitHub](https://github.com/sumituppal03)

---

*Built to production standard, including the bugs found and fixed along the way — see [`BUSINESS.md`](./BUSINESS.md) for the full decision log.*
