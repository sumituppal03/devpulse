# 🏗️ Architecture

> System structure, data flow, and database schema for both features — followed by an honest engineering assessment of what's production-grade versus intentionally simplified.

---

## System Components

```
┌──────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                  │
│   Authenticated API callers          GitHub (webhook sender)          │
└───────────────┬─────────────────────────────────┬─────────────────────┘
                │ Bearer token                     │ HMAC-SHA256 signed
                ▼                                   ▼
┌───────────────────────────┐         ┌───────────────────────────────┐
│ ApiKeyAuthenticationFilter │         │  GitHubWebhookSignatureVerifier│
│  (keyId lookup + BCrypt)   │         │   (constant-time HMAC check)   │
└───────────────┬────────────┘         └───────────────┬────────────────┘
                ▼                                       ▼
┌───────────────────────────┐         ┌───────────────────────────────┐
│      tenant/ feature       │         │   prcontext/ feature           │
│  TenantController           │         │  GitHubWebhookController       │
│  DeveloperController        │         │  PrContextEnricherService      │
│  StandupController          │         │   (runs @Async)                │
└───────────────┬────────────┘         └───────────────┬────────────────┘
                │                                       │
                └───────────────┬───────────────────────┘
                                ▼
                ┌───────────────────────────────┐
                │   shared/ (reused by both)      │
                │  GitHubClient                   │
                │  ChatModelConfig (Ollama/Groq)  │
                │  ActiveModelInfo                │
                │  LlmCallRepository (audit log)  │
                └───────────────┬───────────────────┘
                                ▼
                ┌───────────────────────────────┐
                │         PostgreSQL (Neon)        │
                │  tenants · developers            │
                │  standups · llm_calls            │
                │  webhook_events · pr_enrichments │
                └───────────────────────────────┘
```

**Why two separate entry paths into the same shared infrastructure:** the two features have fundamentally different trigger models — one is called by an authenticated tenant, the other is pushed by GitHub itself — but both ultimately need the same GitHub API access and the same AI provider access. Centralizing those in `shared/` means a fix or improvement to either benefits both features automatically.

---

## Workflow 1: Standup Generation (Synchronous, Pull-Based)

```
1. Client sends GET /api/v1/standup/generate
   with Authorization: Bearer <apiKey> and a developerId
                │
                ▼
2. ApiKeyAuthenticationFilter
   - Parses "Bearer dp_live_{keyId}.{secret}"
   - Looks up tenant by keyId (fast, indexed)
   - Verifies secret against stored BCrypt hash
   - Sets the authenticated tenantId in the security context
                │
                ▼
3. StandupController
   - Reads tenantId from the security context (not from the request — untrusted)
   - Calls DeveloperService.getOwnedByTenant(developerId, tenantId)
   - If the developer doesn't exist OR belongs to a different tenant → 404, stop here
                │
                ▼
4. GitHubClient.fetchCommitsForDate(...)      → today's real commits
   GitHubClient.fetchRecentCommits(...)       → a style sample of past commits
                │
                ▼
5. StandupSummaryService.summarize(...)
   - If today's commits are empty → return "No commits found for this date."
     and STOP — the LLM is never called
   - Otherwise: build a SystemMessage (rules) + UserMessage (style sample + commits)
   - Call the active ChatModel (Ollama locally, Groq in prod)
   - Record latency, token usage, and which model actually ran
                │
                ▼
6. Persist to `standups` (upsert if one already exists for this developer+date)
   Persist to `llm_calls` (the audit record)
                │
                ▼
7. Return the summary as JSON to the client
```

---

## Workflow 2: PR Context Enrichment (Asynchronous, Push-Based)

```
1. A pull request is opened on a GitHub repository with the webhook configured
                │
                ▼
2. GitHub sends POST /webhooks/github
   with header X-Hub-Signature-256 and the raw JSON payload
                │
                ▼
3. GitHubWebhookController
   - Reads the raw request body as bytes (not via @RequestBody —
     preserves the exact bytes GitHub actually signed)
   - GitHubWebhookSignatureVerifier recomputes the HMAC and compares
     using a constant-time check
   - If invalid → 401, stop here
   - If valid → save a WebhookEvent row, return 200 to GitHub immediately
                │
                ▼
4. (Still within the same request, but @Async hands off to a background thread)
   PrContextEnricherService.enrich(...)
                │
                ▼
5. Idempotency check:
   existsByGithubOwnerAndGithubRepoAndPrNumber(...)
   - If this PR was already enriched (e.g. GitHub redelivered the webhook)
     → mark the new WebhookEvent processed, do nothing further, STOP
                │
                ▼
6. GitHubClient.fetchPullRequestFiles(...)   → the actual diff, up to 5 files
                │
                ▼
7. Build a SystemMessage (rules: be brief, infer honestly, no invented reasons)
   + UserMessage (PR title + description + diff)
   Call the active ChatModel
   Record latency, token usage, model name → `llm_calls`
                │
                ▼
8. GitHubClient.postIssueComment(...)
   → a real comment appears on the PR, prefixed "🤖 DevPulse Context"
                │
                ▼
9. Persist to `pr_enrichments` (so step 5 can detect this PR next time)
   Update the original WebhookEvent: processed = true, error_message = null

   If ANY step from 6-9 throws an exception:
   → catch it, update the WebhookEvent: processed = false, error_message = <the actual error>
   → nothing partial gets left in `pr_enrichments`
```

---

## Data Model

```
tenants                          developers
├── id (PK)                      ├── id (PK)
├── name                         ├── tenant_id (FK → tenants)
├── key_id (unique, indexed)     ├── github_username
├── api_key_hash                 ├── timezone
├── plan                         └── created_at
└── created_at                          │
       │                                │
       │         ┌──────────────────────┘
       ▼         ▼
   standups                          llm_calls
   ├── id (PK)                       ├── id (PK)
   ├── tenant_id (FK)                ├── tenant_id (FK, NULLABLE — see below)
   ├── developer_id (FK)             ├── developer_id (FK, nullable)
   ├── standup_date                  ├── feature ("STANDUP" | "PR_CONTEXT")
   ├── generated_content             ├── model_name
   ├── commits_used                  ├── prompt_tokens / completion_tokens
   └── UNIQUE(developer_id, date)    ├── latency_ms
                                     └── created_at

webhook_events                   pr_enrichments
├── id (PK)                      ├── id (PK)
├── source                       ├── github_owner
├── event_type                   ├── github_repo
├── payload                      ├── pr_number
├── processed                    ├── context_comment
├── error_message                ├── github_comment_id
└── received_at                  ├── created_at
                                  └── UNIQUE(owner, repo, pr_number)
```

**The one deliberate inconsistency, visible directly in this schema:** `standups` and `developers` both carry a `tenant_id` foreign key with real ownership semantics. `webhook_events` and `pr_enrichments` carry **no tenant reference at all** — there's no tenant concept in that flow yet. `llm_calls.tenant_id` had to be made nullable specifically to accommodate this, rather than forcing a synthetic value into otherwise-honest audit data. This is explored in full below.

---

## Engineering Judgment: What's Solid, What's Simplified

### What's Genuinely Production-Grade

**The security model.** Split-key API authentication and `404`-not-`403` tenant isolation are correct, defensible choices — the same patterns real API providers use, not simplified placeholders.

**The no-hallucination guardrails.** Both the empty-commit-list check and the idempotency check are enforced in code and verified by tests — not just comments hoping the AI behaves.

**Audit trail accuracy.** `llm_calls` correctly attributes which model actually ran, after a real bug (hardcoded model name) was found and fixed mid-development, with a regression test guarding against it recurring.

**Webhook authenticity.** Constant-time HMAC comparison specifically guards against timing-based signature-guessing — a detail easy to skip, not skipped here.

### The Most Significant Honest Gap: Two Tenancy Models

The Standup Generator is fully multi-tenant. The PR Context Enricher has **none** — it runs against whatever single repository the webhook happens to be configured on, using one shared `GITHUB_TOKEN` and one shared `GITHUB_WEBHOOK_SECRET`.

**Why:** a pull-based feature naturally carries tenant context, because the caller authenticates. A push-based GitHub webhook has no native concept of "which DevPulse tenant" it belongs to.

**What a real fix requires:** either (a) per-tenant webhook secrets with a lookup table routing incoming webhooks to the correct tenant, or (b) migrating from a static Personal Access Token to a **GitHub App** installation — where GitHub itself tracks which installation, and therefore which tenant, a given webhook belongs to. GitHub Apps use JWT-based authentication rather than a static token, which is a meaningfully larger integration effort, not a quick patch.

**The judgment call:** for a single-person portfolio project demonstrating two genuinely different integration patterns — authenticated pull APIs versus webhook-driven async processing — building both well on their own terms was the right scope. Claiming both are "fully multi-tenant" would not have been honest; this document says so directly instead of leaving it to be discovered.

### What Would Need to Change for Real Production Scale

**No durable queue.** `@Async` runs on Spring's in-memory thread pool. A crash mid-enrichment loses that job permanently — there's no retry beyond whatever GitHub itself attempts on delivery failure. A production system would use SQS, RabbitMQ, or Kafka here.

**No rate limiting.** Neither feature throttles repeated calls, which matters once a metered LLM provider is involved at real volume.

**No horizontal scaling story.** A second instance of this app would not coordinate with the first on `@Async` work — that requires a real distributed queue, not Spring's default executor.

**Default connection pool sizing.** Never load-tested or deliberately tuned for this system's actual concurrency profile, unlike the explicit, justified HikariCP configuration in the companion wallet project.

**Free-tier infrastructure ceilings.** Render's cold start and Neon's storage cap are appropriate, deliberate choices for a demo — and the first things to change, not re-architect, if this needed to run as a real product.

### What This Judgment Is Meant to Demonstrate

A system that states its own limits clearly is more trustworthy than one that hides them. The goal here isn't to claim DevPulse is production-scale — it isn't, yet, by design — but to show the difference between "this works" and "this is ready for production traffic" is understood and stated plainly, not discovered later by someone else.

---

*See [`README.md`](./README.md) for what the system does and how to run it, and [`BUSINESS.md`](./BUSINESS.md) for the specific business reasoning behind each individual decision referenced above.*
