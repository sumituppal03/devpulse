# Business Context & Design Decisions

> Every non-obvious architectural decision, every bug found and fixed, and every deliberate trade-off — documented with reasoning so the next engineer doesn't have to rediscover why things are built the way they are.

---

## The Problem

**Standup preparation.** 10-15 minutes per developer, every day. For a 6-person team, ~90 minutes of collective daily time reconstructing yesterday's work from memory. Purely mechanical.

**PR review context.** Reviewers spend the opening minutes of every review asking "what's this for?" and "why this approach?" — because diff-only AI tools show WHAT changed with no awareness of WHY.

**Codebase navigation.** New developers and even experienced ones waste time asking "where does X happen?" questions that the codebase can answer — if you can search it intelligently.

**Unthrottled AI endpoints.** Without rate limiting, one tenant could exhaust a metered LLM provider's quota with no cost control. Most early-stage projects skip this until it hurts.

---

## Feature 1: Standup Generator

### Decision 1 — Split-Key API Authentication

BCrypt produces a different hash every time — that's what makes it secure. But it also means you cannot query a database by a BCrypt hash: `SELECT * FROM tenants WHERE hash = bcrypt(input)` would require BCrypt-checking every row, which is deliberately slow.

Solution: every key is `dp_live_{keyId}.{secret}`. `keyId` stored in plaintext, indexed. `secret` BCrypt-hashed. Every request: one fast indexed lookup by `keyId`, then one BCrypt verify against that row. Same pattern Stripe and GitHub use.

### Decision 2 — Tenant Ownership Returns 404, Not 403

403 Forbidden confirms a resource exists but you cannot access it — it leaks information. 404 reveals nothing. A developer owned by Tenant B is indistinguishable from a developer that never existed, from Tenant A's perspective. This is not an accident.

### Decision 3 — Dual-Provider AI via Spring Profiles

Ollama (free, local, ~45 seconds on CPU) and Groq (~1.5 seconds, measured) both implement the same `ChatModel` interface. One Spring profile flag switches the active implementation. LangChain4j has no dedicated Groq module — `langchain4j-open-ai` pointed at Groq's OpenAI-compatible endpoint is the current standard workaround.

### Decision 4 — No-Hallucination Is a Code Constraint, Not a Prompt Instruction

Empty commit list → early return → LLM never called. This is structural, not advisory. The test `verify(chatModel, never()).chat(...)` fails immediately if the guard is removed. Prompt instructions can be ignored by the model; a missing early-return cannot.

### Decision 5 — Multi-Repo Standup Generation

Original design required the caller to pass `owner` and `repo` parameters on every standup request. This was wrong for a multi-tenant product: a developer commits to multiple repos in one day, and the caller shouldn't need to know which repos the company has.

Fixed: standup generation loads all repos registered by the tenant, fetches commits across all of them, and combines. The caller passes only `developerId`. The system knows which repos belong to the tenant.

### Decision 6 — System/User Message Separation (Bug Fixed)

First working version combined rules and data in a single prompt block. The model added "Here are three bullet points:" as a preamble despite explicit instructions not to. Splitting into `SystemMessage` (rules, never changes) and `UserMessage` (data, changes per request) eliminated it. Standard LLM prompt engineering — not obvious until you see the failure mode.

### Decision 7 — Audit Log Model Name Bug (Bug Fixed)

After adding Groq, `llm_calls.model_name` kept recording `llama3.2` even when Groq was demonstrably running (1.5-second responses; Ollama takes 45 seconds). Root cause: `StandupSummaryService` read the model name from `@Value("${ollama.model-name}")` — a hardcoded property key that was always the Ollama name regardless of which bean was active.

Fixed by making `ActiveModelInfo` a profile-scoped Spring bean: the dev profile bean holds the Ollama model name, the prod profile bean holds the Groq model name. Whichever is active gets injected. Regression test verifies the recorded model name matches the configured profile.

### Decision 8 — Standup Finalize + Edit Distance Tracking

After generation, a developer can edit and finalize their standup. The system records:
- `final_content` — what they actually posted
- `edit_distance` — how many characters changed from the AI draft

This is the core product quality metric. Low edit distance = AI was accurate. High edit distance = prompts need improvement. Without this data, you cannot tell whether the product is actually useful.

---

## Feature 2: PR Context Enricher

### Decision 9 — HMAC-SHA256 with Constant-Time Comparison

GitHub signs every webhook payload with HMAC-SHA256. Verification is done server-side using `MessageDigest.isEqual()` — not `String.equals()`.

`String.equals()` short-circuits at the first differing character. This leaks timing information: comparing "abc" vs "xyz" returns faster than "abcxxx" vs "abcyyy". An attacker sending many requests with slight variations and measuring response times could gradually guess the correct signature. Constant-time comparison always takes the same time regardless of where strings diverge.

### Decision 10 — Return 200 Before Processing

GitHub expects a webhook response within 10 seconds. LLM calls + GitHub API calls routinely take longer. If the controller waited for processing before returning, GitHub would time out, mark the delivery failed, and redeliver — causing duplicate processing.

Pattern: store the raw event, return 200 immediately, process asynchronously via `@Async`. The processing outcome (success or specific error message) is written back to the same `webhook_events` row afterward.

### Decision 11 — Idempotent Processing

GitHub explicitly documents webhook redelivery as expected behavior. `PrContextEnricherService.enrich()` checks for an existing `pr_enrichments` record before doing any work. Duplicate delivery → silent skip. No second comment, no second LLM call. Verified by a unit test that mocks the existence check returning true and asserts the LLM is never called.

### Decision 12 — Linear Integration Per-Tenant

Original implementation used a single global `LINEAR_API_KEY` environment variable. This meant all tenants shared one Linear workspace — wrong for a multi-tenant product.

Fixed: each tenant configures their own Linear API key via `POST /api/v1/integrations/linear`. The key is stored in the `integrations` table against the tenant ID. `PrContextEnricherService` looks up the key at enrichment time. Each company's PR comments reference their own Linear workspace.

### Decision 13 — tenant_id Nullable on llm_calls

PR enrichment is triggered by GitHub — no Bearer token, no authenticated tenant. Forcing a synthetic tenant ID into the audit record would be dishonest data. NULL tenant_id honestly means "this LLM call was triggered by a webhook, not by an authenticated tenant."

---

## Feature 3: Codebase Q&A (RAG Pipeline)

### Decision 14 — PGVector Over a Dedicated Vector Database

Adding a separate vector database (Pinecone, Weaviate, Qdrant) would mean another managed service to configure, maintain, and pay for. PostgreSQL with the pgvector extension handles vector storage, cosine similarity search, and standard SQL queries in a single service that's already running. No additional infrastructure for a feature at this scale.

### Decision 15 — nomic-embed-text for Embeddings

768-dimensional embeddings, runs locally via Ollama, free, no API key. The dimensionality matches the `vector(768)` column in the database. Important constraint: the same model must be used for both indexing and querying — embeddings from different models are not comparable.

### Decision 16 — ivfflat Index Built After Data, Not at Migration Time

The V7 migration intentionally does NOT create the ivfflat vector index. IVFFlat works by clustering vectors into groups at build time. Built on an empty table, it creates a degenerate index — every similarity query returns the same row regardless of input.

`CodeIndexingService.rebuildVectorIndex()` drops and recreates the index at the end of every successful indexing run, after real data exists. This is the fix for the "every question returns the same file" bug discovered during testing.

### Decision 17 — Line-Based Chunking (Known Limitation)

The current chunker splits code files by line groups, not by class or method boundaries. This produces chunks where a question about `StandupSummaryService` might retrieve `StandupGenerationResult` (in the same package) instead, because both files share vocabulary.

Class/method boundary chunking (parsing Java AST to chunk per method) would fix this. It's the documented next improvement.

### Decision 18 — Repository Scoping in Similarity Search

The original `findTopKSimilar` query filtered by `tenant_id` only. A question about repo A could return results from repo B if the tenant had multiple repos indexed.

Fixed: the query now filters by both `tenant_id` AND `repository_id`. Questions always return results from the specific repo the user asked about.

---

## Feature 4: Redis Rate Limiting

### Decision 19 — Redis INCR Over a Database Counter

A database counter under concurrent load requires a transaction and a lock: read current count → increment → write back. Redis `INCR` is a single atomic operation — no transaction, no lock, sub-millisecond latency.

The EXPIRE is set only when the count reaches 1 (first request in a window). Setting it on every increment would keep resetting the window — the key would never expire under sustained load.

### Decision 20 — Fixed-Window vs Sliding-Window

Fixed-window has a known boundary problem: a tenant can make 10 requests at 11:59:59 and 10 more at 12:00:01 — 20 requests in 2 seconds. A sliding-window counter (Redis sorted sets with timestamps) eliminates this but adds implementation complexity.

Fixed-window is the correct starting implementation. The boundary problem is acceptable at this scale. Sliding-window is documented as the upgrade path.

### Decision 21 — Rate Limiting on Standup Only

The standup endpoint is the only one that triggers a metered LLM call per user request. The PR enricher is triggered by GitHub — one PR opening → one enrichment, no realistic abuse pattern. Adding rate limiting to the enricher would add complexity with no real benefit.

---

## Feature 5: Slack + Linear Integrations

### Decision 22 — Per-Tenant Credentials, Not Global Env Vars

Every company has its own Slack workspace and its own Linear workspace. Global environment variables would mean all tenants share one workspace — obviously wrong.

Both integrations store credentials in the `integrations` table per tenant. Slack webhook URLs and Linear API keys are fetched at runtime by `IntegrationService` and used only for the specific tenant's operations.

### Decision 23 — Test Slack Webhook Before Saving

`IntegrationService.configureSlack()` sends a real test message to the webhook URL before saving it. A broken webhook URL saved to the database means standups silently disappear — no error visible to the developer. Failing fast at configuration time catches the problem when the admin can fix it.

### Decision 24 — Credentials Stored as Plaintext JSON

Integration credentials (Slack webhook URL, Linear API key) are stored as plaintext JSON strings in the `config` column. The database itself is the security boundary — it is not publicly accessible.

Column-level encryption is the documented next step for a production-grade deployment.

---

## The Honest Gaps

### Gap 1 — No Durable Queue for Webhook Processing

`@Async` uses an in-memory thread pool. A JVM crash between "received webhook" and "posted comment" loses that job silently. The `webhook_events` table records every incoming event, so you can replay missed ones manually, but there is no automatic retry.

Real fix: SQS, RabbitMQ, or any durable queue. Each event becomes a message; the processor acknowledges only on success. This is the highest-priority infrastructure improvement.

### Gap 2 — PR Enrichment Has No Tenant Context

GitHub webhooks have no concept of "which DevPulse tenant does this belong to." The current implementation uses best-effort Linear key lookup (tries all configured Linear integrations) which works correctly for single-tenant deployments.

Real fix: GitHub App installation model, where GitHub tracks which installation belongs to which tenant. This is a meaningful architectural change requiring GitHub App registration, OAuth flow, and installation-to-tenant mapping.

### Gap 3 — Chunking Quality

Line-based chunking produces chunks where semantically adjacent files outscore each other for specific questions. See Decision 17.

---

## Success Metrics (All Verified, Not Estimated)

| Metric | How Verified |
|---|---|
| No-hallucination guarantee | Unit test: `verify(chatModel, never()).chat(...)` |
| Tenant isolation | 404 for cross-tenant access |
| Audit log accuracy | Regression test: model name matches active profile |
| API key security | Unit test: plaintext never persisted |
| Webhook authenticity | 5 unit tests across valid/invalid/tampered scenarios |
| PR idempotency | Unit test: duplicate webhook never calls LLM |
| Rate limit correctness | 11 parallel requests, confirmed `429` response |
| Rate limit reset | Redis key auto-expires, confirmed empty after window |
| Slack validation | Integration test: broken URL rejected before saving |
| Context loads | Testcontainers: full Spring context verified on every run |

---

## Prioritised Future Work

| Priority | Work | Reason |
|---|---|---|
| 1 | Durable queue for webhook processing | Silent job loss on crash |
| 2 | GitHub App installation model | Proper per-tenant webhook routing |
| 3 | Class/method boundary chunking | Better Q&A retrieval accuracy |
| 4 | Column-level credential encryption | Credential security at rest |
| 5 | VS Code extension | Standup and Q&A without leaving the editor |
| 6 | Stripe billing | Free tier + paid team plan |
| 7 | Sliding-window rate limiting | Eliminates fixed-window boundary problem |
