# 💼 Business Context & Design Decisions

> Why DevPulse exists, who it's for, and the reasoning behind every non-obvious architectural choice across both features — including the bugs found and fixed during development, and the deliberate scoping decisions that kept this buildable as a single-person project.

---

## The Problem

**Standup preparation.** A developer spends 10-15 minutes before standup manually reconstructing yesterday's work. For a 6-person team, that's roughly 90 minutes of collective daily time on something purely mechanical.

**PR review context.** Reviewers spend the first minutes of every review asking "what's this for?" and "why this approach?" — because the PR's description alone rarely explains the reasoning, and existing AI code review tools analyze the diff itself with zero awareness of business context.

Both are solved here the same way: ground the AI in real data the system already has access to, and refuse to guess when it doesn't.

---

## Feature 1: Standup Generator — Design Decisions

### 1. Split-Key API Authentication (`keyId` + `secret`)

BCrypt intentionally produces a different hash every time, even for identical input — that's what makes it secure, but it also means you can't query a database by a BCrypt hash directly. The fix: every API key is `dp_live_{keyId}.{secret}` — `keyId` stored in plaintext for fast indexed lookup, `secret`'s hash stored for verification. Two fast steps instead of an impossible query. Same pattern Stripe and GitHub use.

### 2. Tenant Ownership Returns `404`, Not `403`

A `403` confirms to a caller that a given ID is real, even if they can't access it. A `404` reveals nothing — a developer owned by another tenant looks identical to one that never existed. This was a real gap found and fixed mid-development: the original endpoint took a raw `username` parameter with zero tenant scoping at all.

### 3. Dual-Provider AI via Spring Profiles

Local Ollama (free, private, ~45s on CPU) and cloud Groq (fast, ~1-1.5s, measured) both satisfy the same `ChatModel` interface, switched by one profile flag. LangChain4j has no dedicated Groq module yet — a feature request has been open since 2024 — so this uses `langchain4j-open-ai` pointed at Groq's OpenAI-compatible endpoint, the standard current workaround.

### 4. The No-Hallucination Guardrail Is Code, Not a Prompt Instruction

Empty commit list → early return, LLM never called. Verified directly: `verify(chatModel, never()).chat(...)`. If this regresses, the test suite fails immediately rather than silently in production.

### 5. System/User Message Separation (A Bug Found and Fixed)

The first prompt version combined rules and data in one block; the model added an unwanted preamble ("Here are three bullet points:") despite explicit instructions not to. Splitting into a `SystemMessage` (rules) and `UserMessage` (data) gave the model a clearer structural signal and eliminated the preamble — confirmed by direct before/after comparison.

### 6. The Audit Log Bug — Reporting the Wrong Model Name

After adding Groq, `llm_calls.model_name` kept reporting `llama3.2` even when Groq's model demonstrably ran (proven by latency). Root cause: the field was read from a hardcoded `@Value` property, ignoring which `ChatModel` bean was actually active. Fixed by making `ActiveModelInfo` itself profile-scoped. A direct regression test now exists for this exact bug.

---

## Feature 2: PR Context Enricher — Design Decisions

### 7. Webhook Signature Verification, Not API-Key Auth

GitHub initiates this flow, not a tenant — there's no Bearer token to check. Instead, GitHub signs every payload with HMAC-SHA256 using a shared secret, and `GitHubWebhookSignatureVerifier` recomputes that signature and compares it using a **constant-time comparison** (`MessageDigest.isEqual`), specifically to prevent timing-based signature-guessing attacks. This is a fundamentally different authentication model from the rest of the system, used because the trigger itself is fundamentally different — push, not pull.

### 8. Idempotency via an Existence Check, Not Just Hope

GitHub redelivers webhooks on timeouts and transient failures — this is documented platform behavior, not a hypothetical. `PrContextEnricherService.enrich()` checks `existsByGithubOwnerAndGithubRepoAndPrNumber` before doing any work at all. A duplicate delivery is detected and skipped, rather than posting a second comment or paying for a second LLM call. This is tested directly, not just assumed to work.

### 9. Asynchronous Processing (`@Async`)

GitHub expects a fast response to a webhook delivery — a slow response risks GitHub treating the delivery as failed and retrying it unnecessarily. The controller returns `200` immediately after persisting the raw event; the actual diff fetch, LLM call, and comment posting happen on a background thread via `@Async`, with the outcome (success or specific error message) written back onto the same `WebhookEvent` row afterward. **Honest limitation:** this uses Spring's in-memory thread pool, not a durable queue — a crash mid-processing loses that specific job. See `ARCHITECTURE.md` for what a production fix would require.

### 10. `tenant_id` Made Nullable on `llm_calls`

PR enrichment has no tenant context at all — no Bearer token, no authenticated caller. Forcing a synthetic tenant ID into the audit log to satisfy a `NOT NULL` constraint would be dishonest data. The column was deliberately relaxed rather than worked around.

### 11. The Webhook Controller Lives in `prcontext/`, Not `shared/webhook/`

`WebhookEvent`, its repository, and the signature verifier are genuinely generic — any future webhook source could reuse them, so they stay in `shared/webhook/`. The controller itself, once it started parsing `pull_request`-specific payloads and deciding what to do with them, became feature logic — so it moved to `prcontext/`. The same "will more than one feature need this exact thing" test used everywhere else in this codebase.

### 12. `GitHubClient` Gained Two New Methods, Not a New Client

`fetchPullRequestFiles` and `postIssueComment` were added directly onto the existing `GitHubClient` rather than creating a second GitHub client class. Both features genuinely share the same authentication setup and base URL — splitting them would have meant either duplicated connection logic or one feature awkwardly depending on another feature's client.

### 13. Deliberately Scoped Out: Linear/Jira/Slack Integration

The original plan included pulling ticket context and Slack discussion into the PR comment. Building three additional third-party integrations would have roughly doubled the remaining work for proportionally thin payoff on a single-person project. The feature works meaningfully from the PR's own title, description, and diff alone — external context integration is documented as real future work, not silently dropped.

---

## The Most Important Honest Limitation: Two Tenancy Models

The Standup Generator is fully multi-tenant — authenticated, ownership-verified. The PR Context Enricher has **no tenant scoping at all** — it runs on whatever single repository the webhook is configured against, using one shared token and one shared secret.

This is not an oversight; it's a direct consequence of the trigger model. A pull-based feature naturally carries tenant context (the caller authenticates). A push-based webhook from GitHub has no native concept of "which DevPulse tenant does this belong to" — that would require either per-tenant webhook secrets with a routing table, or migrating to a GitHub App installation model where GitHub itself tracks the tenant relationship. Full reasoning and the real fix required is in [`ARCHITECTURE.md`](./ARCHITECTURE.md) — stated there directly rather than smoothed over here.

---

## Success Metrics

| Metric | Target | Verified By |
|---|---|---|
| No-hallucination guarantee (standup) | LLM never called on empty input | Unit test |
| No-hallucination guarantee (PR context) | N/A — diff always provides some content | — |
| Tenant isolation (standup) | Cross-tenant access returns 404 | Manual + code-level test |
| Webhook authenticity | Tampered/wrong-secret signatures rejected | 5 unit tests |
| Idempotent PR enrichment | Duplicate webhook never double-posts | Unit test |
| Audit log accuracy | `model_name` matches the provider that actually ran | Regression test |
| Failure isolation | A failed enrichment is logged, not silently lost | Unit test |

---

## Who Would Use This

**Engineering teams** wanting to remove daily standup overhead and reduce PR review ramp-up time, without adopting a heavyweight all-in-one engineering analytics platform.

**Platform engineers evaluating LangChain4j** in a Java-first stack, including a real dual-provider setup and a real webhook-driven async AI pipeline — both genuinely uncommon to find documented together in open source.

**Developers studying multi-tenant SaaS patterns**, including the honest limits of those patterns when a feature's trigger model doesn't naturally carry tenant context — a real, common problem in webhook-driven systems, not unique to this project.

---

## Known Limitations (Honest Assessment)

See [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the full assessment. In summary: no repository-to-tenant mapping for PR Context, no durable queue for async processing, no rate limiting on either feature, free-tier infrastructure ceilings (Render cold starts, Neon storage cap), and no external ticket/chat system integration.

---

## Prioritised Future Improvements

| Priority | Improvement | Business Justification |
|---|---|---|
| 1 | Repository-to-tenant mapping (GitHub App model) | Closes the most significant architectural gap in the system |
| 2 | Durable async queue (SQS/RabbitMQ) | Prevents silent job loss on crash; required before real production traffic |
| 3 | Per-tenant rate limiting | Cost control and fairness once using a metered LLM provider |
| 4 | Codebase Q&A via RAG (PGVector) | The core LangChain4j RAG skill, not yet demonstrated |
| 5 | Linear/Jira/Slack context integration | Closes the gap deliberately deferred in decision #13 above |

---

## Design Decision Log

| Decision | Alternative Considered | Why This Choice Won |
|---|---|---|
| Split-key auth | Single opaque API key | BCrypt hashes can't be queried directly |
| 404 for cross-tenant access | 403 Forbidden | Reveals nothing about whether a resource exists |
| Dual `ChatModel` beans via `@Profile` | Single hardcoded provider | Free local dev + fast cloud production, zero duplication |
| `langchain4j-open-ai` for Groq | Wait for a native Groq module | No dedicated module exists yet; this is the standard workaround |
| HMAC signature verification for webhooks | API-key auth for webhooks too | GitHub initiates this flow — there's no tenant token to check |
| Idempotency via existence check | Trust GitHub never redelivers | Webhook redelivery is documented, real platform behavior |
| `@Async` for webhook processing | Fully synchronous | GitHub expects a fast response; processing shouldn't block it |
| `tenant_id` made nullable on `llm_calls` | Synthetic placeholder tenant ID | A fake ID would be dishonest audit data |
| Controller moved to `prcontext/` | Left in `shared/webhook/` | Feature-specific parsing logic isn't shared infrastructure |
| No Linear/Jira/Slack integration (yet) | Build all three now | Disproportionate scope increase for a single-person project; documented as future work |

---

*See [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the deeper, system-level assessment.*
