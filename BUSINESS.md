# 💼 Business Context & Design Decisions

> Why DevPulse exists, who it's for, and the reasoning behind every non-obvious architectural choice — including the bugs found and fixed during development, which are as instructive as the decisions themselves.

---

## The Problem

Engineering teams lose real time to mechanical, low-value work every single day:

**Standup preparation.** A developer spends 10-15 minutes before standup manually reconstructing yesterday's work — scanning commits, half-remembering ticket numbers, composing three bullet points. For a 6-person team, that's roughly 90 minutes of collective time spent daily on something purely mechanical.

**Generic AI tools don't solve this well.** A blank ChatGPT window has no idea what a developer actually did. It either produces generic filler or requires the developer to type out their own summary anyway — defeating the purpose.

DevPulse is grounded in a tenant's **real GitHub history**. It doesn't ask "what did you do?" — it already knows, and it refuses to guess when it doesn't.

---

## Core Design Decisions & Their Business Justifications

### 1. Split-Key API Authentication (`keyId` + `secret`)

**The problem this solves:** BCrypt is intentionally designed to produce a *different* hash every time, even for the same input (it embeds a random salt). That's exactly what makes it secure against rainbow-table attacks — but it also means there's no way to write `SELECT * FROM tenants WHERE api_key_hash = bcrypt(candidate)`, because you can never compute the matching hash to search for.

**The fix:** every API key is structured as `dp_live_{keyId}.{secret}`.
- `keyId` — short, stored in **plaintext**, indexed for fast lookup. Knowing it alone proves nothing.
- `secret` — the real credential. Only its BCrypt hash is ever stored.

Authentication becomes two fast steps: look up the one row by `keyId` (instant, indexed), then BCrypt-verify just the `secret` against that row's hash — never against the whole table. This is the same pattern Stripe and GitHub use for their own API keys.

---

### 2. Tenant Ownership Returns `404`, Not `403`

**The problem:** when `StandupController` receives a `developerId`, it must verify that developer actually belongs to the authenticated tenant — not some other tenant entirely. The naive approach returns `403 Forbidden` when the ID exists but belongs to someone else.

**Why that's wrong:** a `403` confirms to an attacker that the ID is real — they've just learned a valid UUID exists, even if they can't use it. A `404 Not Found` reveals nothing: a real developer owned by another tenant looks **identical** to an ID that never existed at all. `TenantOwnershipException` is deliberately mapped to `404` for exactly this reason.

This was a real gap found mid-development — the original `StandupController` took a raw `username` query parameter with **zero tenant scoping**, meaning any authenticated tenant could request standups for any GitHub user at all. The fix (developer registration + ownership verification) closed this before it shipped, and a negative test (`developerId` that doesn't exist → confirmed `404`) proves it.

---

### 3. Dual-Provider AI: Ollama for Development, Groq for Production

**The problem:** local LLMs (Ollama) are free and fully private, but slow on CPU-only hardware (~45 seconds per call, measured). Cloud providers (Groq) are dramatically faster (~1-1.5 seconds, measured) but require sending data off the local machine and depend on a paid-tier-adjacent free quota.

**The decision:** both options stay available, switched by a single Spring profile flag — `ChatModelConfig` defines two `@Profile`-scoped beans (`!prod` → Ollama, `prod` → Groq), both satisfying the exact same `ChatModel` interface. `StandupSummaryService` never knows or cares which one is active.

**The technical detail that matters:** LangChain4j has no dedicated Groq integration module — a feature request for native Groq support has been open since 2024. Since Groq's API is OpenAI-compatible, the correct current workaround is reusing `langchain4j-open-ai` and pointing its `baseUrl` at `https://api.groq.com/openai/v1` instead of OpenAI's endpoint. This is the standard, documented approach used across the industry for Groq integrations — not a hack specific to this project.

---

### 4. The No-Hallucination Guardrail Is Code, Not Just a Prompt Instruction

**The problem:** an LLM asked to "summarize today's work" will often invent plausible-sounding activity even when given nothing to summarize.

**The fix:** `StandupSummaryService.summarize()` checks the commit list **before** ever constructing a prompt:
```java
if (todaysCommits.isEmpty()) {
    return new StandupGenerationResult("No commits found for this date.", ...);
}
```

This isn't a "please don't hallucinate" instruction hoping the model complies — it's a code path that makes the LLM call structurally impossible when there's nothing real to summarize. `StandupSummaryServiceTest` enforces this directly: `verify(chatModel, never()).chat(...)` — if this guarantee ever regresses, the test suite fails immediately, not silently in production.

---

### 5. System/User Message Separation (A Bug Found and Fixed)

**What happened:** the first working version of the prompt combined rules and data into a single block of text sent as one message. The model would respond with `"Here are three standup bullet points:"` before the actual bullets — violating the explicit instruction to output *only* three bullets.

**Why it happened:** cramming everything into one undifferentiated block gives a model room to "narrate" rather than strictly comply, since nothing distinguishes instruction from content.

**The fix:** splitting into a `SystemMessage` (rules only) and a `UserMessage` (style sample + actual commits) gave the model a much stronger structural signal that the system message is binding instruction, not conversational content. The preamble disappeared immediately after this change — verified by direct before/after comparison during development.

---

### 6. Style-Matching via a Commit History Sample

**The idea:** rather than just summarizing today's commits in a generic voice, `fetchRecentCommits()` pulls a sample of the developer's past commit messages, and the prompt explicitly asks the model to study that sample's vocabulary and tone before writing today's summary.

**Honest limitation:** this works best on repositories with substantial commit history. On a 3-day-old repository, there isn't much distinctive personal voice yet for the model to detect — the feature is designed to improve naturally as real commit history accumulates over weeks, not something achievable through prompt tuning alone.

---

### 7. Why `GitHubClient` and AI Configuration Live in `shared/`, Not `standup/`

**The test applied:** will more than one feature ever need this exact tool? Both `GitHubClient` (GitHub API access) and `ChatModelConfig`/`ActiveModelInfo` (LLM access) will be needed by features beyond the standup generator — the planned PR Context Enricher needs GitHub diffs and an LLM call; the planned Codebase Q&A needs both too.

Putting shared infrastructure in feature-named folders would mean duplicating authentication setup across multiple copies, or having feature folders awkwardly depend on each other — both violate the principle that feature folders should be self-contained. Fixing a bug in `GitHubClient` once, in `shared/`, fixes it for every feature that will ever use it.

---

### 8. `GitHubClient` Receives Its `RestClient`, It Doesn't Build One

**The problem:** the original implementation built its own `RestClient` inside its constructor. This made the class fundamentally untestable — there was no way to substitute a fake HTTP server, since the client always constructed a real one internally.

**The fix:** `GitHubClientConfig` builds the `RestClient` as a separate Spring bean; `GitHubClient` simply receives the finished object via constructor injection. This is a general testing principle, not specific to this project: **depend on the finished thing, don't construct it yourself.** `GitHubClientTest` uses Spring's `MockRestServiceServer` to substitute a fake HTTP server entirely — zero real network calls happen during the test suite, yet the real parsing logic is fully exercised.

---

### 9. The Audit Log Bug — Reporting the Wrong Model Name

**What happened:** after building the Groq/Ollama dual-provider system, the `llm_calls` audit table continued reporting `modelName: llama3.2` even when Groq's `llama-3.3-70b-versatile` was demonstrably the one that ran (confirmed by the dramatically faster latency).

**Root cause:** `StandupSummaryService` read the model name from a hardcoded `@Value("${ollama.model-name}")` field — regardless of which `ChatModel` bean Spring had actually injected based on the active profile. The audit log was quietly lying about which provider handled each request.

**The fix:** `ActiveModelInfo` is now itself a profile-scoped bean — `!prod` resolves to the Ollama model name, `prod` resolves to the Groq model name — injected directly into `StandupSummaryService` instead of read from a static property. `StandupSummaryServiceTest` includes a direct regression test for this exact bug: it asserts `result.modelName()` matches what was actually configured for that test, not a hardcoded default.

**Why this matters beyond the fix itself:** an audit log that silently reports incorrect data is arguably worse than no audit log at all — it creates false confidence. Catching this required actually reading the data the system produced, not just trusting that the feature "worked" because the response came back fast.

---

### 10. Database Provider Choice: Neon Over a Second Render Postgres

**The constraint:** Render's free tier allows only one free PostgreSQL database per workspace, and that allocation was already used by an earlier project (`advanced-wallet-ledger-api`).

**The decision:** Neon, a separate provider, was used for DevPulse's production database instead. This wasn't purely a workaround — Neon's free tier has no expiration, unlike Render's free Postgres, which expires 30 days after creation. For a portfolio project meant to stay demonstrable indefinitely, this is arguably the better choice regardless of the Render constraint.

**Zero code changes required** — Neon speaks standard PostgreSQL wire protocol; only the JDBC connection string, username, and password differ from a Render-hosted database.

---

## Success Metrics

| Metric | Target | How it's verified |
|---|---|---|
| No-hallucination guarantee | LLM never called on empty input | Unit test (`verify(chatModel, never())...`) |
| Tenant isolation | Cross-tenant developer access returns 404 | Manual negative test + code review |
| Audit log accuracy | `modelName` matches the provider that actually ran | Regression unit test |
| API key security | Plaintext key never persisted, shown exactly once | Unit test on `TenantService.register()` |
| Provider swap correctness | Switching `prod` profile changes both behavior and audit data | Manually verified: ~45s/llama3.2 vs ~1.5s/llama-3.3-70b-versatile |

---

## Who Would Use This

**Engineering teams at startups** who want to eliminate daily standup-prep overhead without adopting a heavyweight, all-in-one engineering analytics platform.

**Platform engineers evaluating LangChain4j** — there are few production-grade, open-source Java examples of LangChain4j with a real dual-provider setup; this project demonstrates one concretely.

**Developers learning multi-tenant SaaS patterns** — the split-key auth design, tenant ownership verification, and shared-vs-feature folder structure are all patterns directly transferable to other B2B API projects.

---

## Known Limitations (Honest Assessment)

**Render free tier cold start** — 30-60 second delay on the first request after 15 minutes of inactivity. A paid tier or a different always-on host would eliminate this.

**No event-driven architecture yet** — standup generation is purely request-triggered. The originally-planned PR Context Enricher requires webhook-driven processing, which hasn't been built yet.

**No rate limiting** — a tenant could call the AI endpoint repeatedly with no throttling, which would matter at real scale or with a paid LLM provider with per-call costs.

**Style-matching quality is data-dependent** — on a young repository with limited commit history, the AI summary reads more generically. This is expected to improve naturally over time, not a fixable prompt issue.

**Neon free tier storage (0.5 GB)** — sufficient for demo and portfolio purposes, not for real production data volume.

---

## Prioritised Future Improvements

| Priority | Improvement | Business Justification |
|---|---|---|
| 1 | Per-tenant rate limiting | Prevents one tenant from exhausting shared AI quota |
| 2 | PR Context Enricher (webhook-driven) | Proves event-driven architecture, not just request/response |
| 3 | Codebase Q&A via RAG (PGVector) | Demonstrates the core LangChain4j RAG skill explicitly |
| 4 | Standup edit-distance tracking | Real product metric — measures whether the AI output is actually useful |
| 5 | Idempotency keys on standup generation | Protects against double-submission race conditions |

---

## Design Decision Log

| Decision | Alternative Considered | Why This Choice Won |
|---|---|---|
| Split-key auth (`keyId` + `secret`) | Single opaque API key | BCrypt hashes can't be queried directly; splitting enables fast indexed lookup |
| 404 for cross-tenant access | 403 Forbidden | 404 reveals nothing about whether the resource exists at all |
| Dual ChatModel beans via `@Profile` | Single hardcoded provider | Free local development + fast cloud production, zero code duplication |
| `langchain4j-open-ai` for Groq | Wait for native Groq module | No dedicated module exists yet; OpenAI-compatibility is the standard workaround |
| System/User message split | Single combined prompt block | Eliminated AI preamble leakage that violated explicit output format rules |
| `RestClient` injected, not self-built | `GitHubClient` builds its own client | Made the class genuinely unit-testable with a fake HTTP server |
| Neon for production database | Second Render Postgres | Render allows only one free DB per workspace; Neon's free tier never expires |
| Profile-scoped `ActiveModelInfo` bean | Hardcoded `@Value` model name field | Fixed a real bug where the audit log misreported which AI provider actually ran |

---

*This document follows the same documentation standard as [`advanced-wallet-ledger-api`](https://github.com/sumituppal03/advanced-wallet-ledger-api) — every decision documented with its reasoning, including the bugs found and fixed, because that's what an actual production engineering process looks like.*
