# 💼 Business Context & Design Decisions

> Why DevPulse exists, who it's for, and the reasoning behind every non-obvious architectural choice — including bugs found and fixed, deliberate scoping decisions, and the one real gap between the two features' tenancy models stated plainly.

---

## The Problem

**Standup preparation.** 10-15 minutes per developer, every day, reconstructing yesterday's work. For a 6-person team, ~90 minutes of collective daily time on something purely mechanical.

**PR review context.** Reviewers spend the first minutes of every review asking "what's this for?" and "why this approach?" — because existing AI code review tools analyze the diff with zero awareness of business context.

**Unthrottled AI endpoints.** Without rate limiting, a single tenant could exhaust a metered LLM provider's quota with no cost control, no fairness guarantee, and no signal to the system operator. Most early-stage API projects skip this until it hurts; this one addressed it deliberately.

---

## Feature 1: Standup Generator

### 1. Split-Key API Authentication

BCrypt produces a different hash every time — that's what makes it secure, but it also means you can't query a database by a BCrypt hash directly. Solution: every API key is `dp_live_{keyId}.{secret}`. `keyId` stored in plaintext for fast indexed lookup, `secret`'s hash stored for BCrypt verification. Same pattern Stripe and GitHub use.

### 2. Tenant Ownership Returns `404`, Not `403`

A `403` confirms a given ID is real. A `404` reveals nothing — a developer owned by another tenant looks identical to one that never existed. This was a real gap found mid-development: the original endpoint accepted a raw `username` parameter with zero tenant scoping.

### 3. Dual-Provider AI via Spring Profiles

Ollama (free, local, ~45s on CPU) and Groq (~1.5s, measured) both satisfy the same `ChatModel` interface, switched by one profile flag. LangChain4j has no dedicated Groq module — a feature request has been open since 2024 — so this uses `langchain4j-open-ai` pointed at Groq's OpenAI-compatible endpoint. The standard current workaround.

### 4. No-Hallucination Guardrail Is Code, Not a Prompt Instruction

Empty commit list → early return, LLM never called. Verified: `verify(chatModel, never()).chat(...)`. If this regresses, the test suite fails immediately.

### 5. System/User Message Separation (Bug Found and Fixed)

First working version combined rules and data in one block. The model added "Here are three bullet points:" as a preamble despite instructions not to. Splitting into `SystemMessage` (rules) and `UserMessage` (data) eliminated it immediately — confirmed by before/after comparison.

### 6. Audit Log Reported Wrong Model Name (Bug Found and Fixed)

After adding Groq, `llm_calls.model_name` kept reporting `llama3.2` even though Groq demonstrably ran (proven by latency). Root cause: the field read from a hardcoded `@Value("${ollama.model-name}")` property regardless of which `ChatModel` bean was actually active. Fixed by making `ActiveModelInfo` a profile-scoped Spring bean. A direct regression test exists for this exact bug.

---

## Feature 2: PR Context Enricher

### 7. HMAC-SHA256 Webhook Signature Verification

GitHub initiates this flow — there's no Bearer token to check. Instead, GitHub signs every payload with HMAC-SHA256 using a shared secret, verified server-side using **constant-time comparison** (`MessageDigest.isEqual`) to prevent timing-based signature-guessing attacks.

### 8. Idempotency via Existence Check

GitHub redelivers webhooks on timeouts — documented platform behavior, not a hypothetical. `PrContextEnricherService.enrich()` checks `existsByGithubOwnerAndGithubRepoAndPrNumber` before doing any work. Duplicate delivery → skipped, no double-post, no double LLM call. Verified by a unit test.

### 9. Asynchronous Processing (`@Async`)

GitHub expects a fast webhook response. The controller returns `200` immediately after persisting the raw event; diff fetch, LLM call, and comment posting happen on a background thread. Outcome (success or specific error message) written back to the same `WebhookEvent` row afterward.

**Honest limitation:** Spring's in-memory thread pool means a crash mid-processing loses that job. A real message queue (SQS, RabbitMQ) is the documented next step.

### 10. `tenant_id` Made Nullable on `llm_calls`

PR enrichment has no tenant context — no Bearer token, no authenticated caller. Forcing a synthetic tenant ID would be dishonest audit data. The column was relaxed rather than worked around.

---

## Feature 3: Redis-Backed Rate Limiting

### 11. Why Redis `INCR` + `EXPIRE`, Not a Database Counter

The standup endpoint is the only one that triggers a real LLM call per request — and LLM calls cost real tokens. Without throttling, one tenant could hammer it continuously with no cost control.

**Why Redis instead of a database counter:** a database `UPDATE counter = counter + 1 WHERE tenant_id = ?` under concurrent load requires a transaction and a lock. Redis `INCR` is a single atomic operation — no transaction, no lock needed, sub-millisecond latency, adds no perceptible overhead to request handling.

**The implementation:**
```
INCR ratelimit:standup:{tenantId}     → returns new count atomically
EXPIRE ratelimit:standup:{tenantId} 60  → set only on first INCR (count == 1)
```

The key auto-expires after 60 seconds — no cleanup job needed, no stale counters accumulating.

### 12. Why Fixed-Window, Not Sliding-Window

A proper sliding window (tracking exact timestamps of all requests in a ring buffer) is more precise but adds meaningful implementation complexity for a portfolio project. Fixed window (`INCR` + `EXPIRE`) is the correct starting point — simpler to reason about, verifiable under concurrent load (tested with 11 parallel background jobs, confirmed mixed `200`/`429` responses), and a well-established industry pattern used by major API providers.

**Known limitation of fixed-window:** a tenant can make 10 requests at the end of one window and 10 more at the start of the next, effectively doubling the rate at the window boundary. Acceptable for this use case; sliding-window is the documented upgrade path.

### 13. Testing Under Real Concurrency

Sequential requests don't prove a rate limiter works — they're too slow to land inside the same window when each call involves a real LLM round-trip. Testing required 11 genuinely parallel requests (PowerShell `Start-Job`) fired simultaneously. The result: mixed `200 OK` and `429` responses in a single test run, confirming the Redis counter accumulates correctly across concurrent requests and the `429` path is genuinely reachable.

### 14. Rate Limiting Applied to Standup Only, Not PR Context

The PR context enricher is webhook-triggered — it's called by GitHub, not by a tenant, and one PR opening triggers exactly one enrichment. There's no realistic way a legitimate use pattern would exhaust a limit on this endpoint, and adding rate limiting there would introduce complexity with no benefit. Standup generation is the one endpoint where a tenant could genuinely and deliberately make many calls in quick succession.

---

## The Most Important Honest Gap: Two Tenancy Models

The Standup Generator is fully multi-tenant. The PR Context Enricher is not — it runs against one shared repository, with one shared `GITHUB_TOKEN` and one shared `GITHUB_WEBHOOK_SECRET`. This is a direct consequence of GitHub's webhook model having no native concept of "which DevPulse tenant does this webhook belong to." A real fix requires either per-tenant webhook secrets with a routing table, or migrating to a GitHub App installation model. Full reasoning in [`ARCHITECTURE.md`](./ARCHITECTURE.md).

---

## Success Metrics

| Metric | Target | Verified By |
|---|---|---|
| No-hallucination guarantee | LLM never called on empty input | Unit test (`never()` assertion) |
| Tenant isolation | Cross-tenant developer access returns 404 | Manual negative test + code |
| Audit log accuracy | `model_name` matches provider that actually ran | Regression unit test |
| API key security | Plaintext never persisted, shown exactly once | Unit test |
| Webhook authenticity | Tampered signatures rejected | 5 unit tests |
| PR enrichment idempotency | Duplicate webhook never double-posts | Unit test |
| Rate limit enforcement | Request 11 returns 429, not 200 | Concurrent integration test (11 parallel jobs) |
| Rate limit reset | Counter auto-expires, confirmed by empty Redis `KEYS *` after window | Manual verification |

---

## Known Limitations

**No repository-to-tenant mapping** — see [`ARCHITECTURE.md`](./ARCHITECTURE.md).
**No durable queue** — `@Async` loses in-flight work on crash.
**No Linear/Jira/Slack integration** — PR context from diff/description only.
**Fixed-window rate limiting** — boundary-doubling possible; sliding-window is the upgrade path.
**Render free tier cold start** — 30–60 seconds after 15 minutes idle.

---

## Prioritised Future Improvements

| Priority | Improvement | Business Justification |
|---|---|---|
| 1 | Repository-to-tenant mapping (GitHub App) | Closes the most significant architectural gap |
| 2 | Durable queue (SQS/RabbitMQ) | Prevents silent job loss on crash |
| 3 | Sliding-window rate limiting | Eliminates the boundary-doubling edge case |
| 4 | Codebase Q&A via RAG (PGVector) | Core LangChain4j RAG skill, not yet built |
| 5 | Linear/Jira/Slack context for PR enrichment | Closes the deliberately-deferred integration |

---

## Design Decision Log

| Decision | Alternative Considered | Why This Choice Won |
|---|---|---|
| Split-key auth | Single opaque API key | BCrypt hashes can't be queried directly |
| 404 for cross-tenant access | 403 Forbidden | Reveals nothing about whether resource exists |
| Dual `ChatModel` beans via `@Profile` | Single hardcoded provider | Free local dev + fast cloud production |
| `langchain4j-open-ai` for Groq | Wait for native Groq module | No dedicated module exists yet |
| HMAC signature for webhooks | API-key auth for webhooks | GitHub initiates this flow — no tenant token exists |
| Idempotency via existence check | Trust GitHub never redelivers | Webhook redelivery is documented platform behavior |
| `@Async` for webhook processing | Fully synchronous | GitHub expects a fast response |
| `tenant_id` nullable on `llm_calls` | Synthetic placeholder ID | Fake ID would be dishonest audit data |
| Redis `INCR` + `EXPIRE` for rate limiting | Database counter | Atomic, sub-millisecond, no lock needed |
| Fixed-window rate limiting | Sliding-window | Simpler, correct for this scale, upgrade path documented |
| Rate limiting on standup only | Rate limit all endpoints | Only standup triggers a metered LLM call per request |
| No Linear/Jira/Slack integration (yet) | Build all three | Disproportionate scope increase; documented as future work |

---

*Every decision documented with its reasoning. Every bug named and explained. Every gap stated plainly — because a system that hides its own limitations is less trustworthy than one that states them clearly.*
