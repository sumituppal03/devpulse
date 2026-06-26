# Contributing to DevPulse

Thanks for considering a contribution. This document covers how the project is organized, how to get a dev environment running, and where the most useful contributions would actually land.

---

## Before You Start

Read these two documents first — they'll save you time:

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — the system diagrams, both features' workflows, and an honest list of what's currently simplified. Several of the gaps listed there are genuinely good first contributions.
- [`BUSINESS.md`](./BUSINESS.md) — the reasoning behind non-obvious decisions already made. If you're about to propose changing one of these, check here first to understand why it's built the way it is — there may be a constraint you're not seeing yet.

---

## Project Structure

Code is organized **by feature**, not by technical layer:

```
com.devpulse/
├── tenant/        ← everything about tenant registration & API keys
├── developer/     ← everything about developer registration & ownership
├── standup/       ← the standup generation feature, end to end
├── prcontext/      ← the PR enrichment feature, end to end
└── shared/        ← infrastructure genuinely reused by 2+ features
    ├── github/     ← GitHubClient — used by both standup and prcontext
    ├── ai/         ← ChatModel config, audit logging — used by both
    ├── security/   ← API key auth filter
    ├── webhook/    ← generic webhook event storage + signature verification
    └── exception/  ← global error handling
```

**The test for `shared/`:** if a fix to a piece of code would need to be made in more than one place because two features each have their own copy, it belongs in `shared/` instead. If you're adding something that only one feature will ever use, keep it inside that feature's own folder, even if it feels "generic" in isolation.

---

## Local Setup

Follow the [Quick Start in `README.md`](./README.md#quick-start-run-it-yourself) — Docker Compose for PostgreSQL, Ollama for free local AI, no cloud accounts required to get a working dev environment.

```bash
docker-compose up -d
ollama pull llama3.2
export GITHUB_TOKEN=<your-own-github-pat>
./mvnw spring-boot:run
```

If you only want to touch one feature, you don't need a Groq key or a webhook secret unless you're specifically working on PR Context's deployed/webhook-triggered behavior.

---

## Running Tests

```bash
./mvnw clean test
```

All 14 current tests run with **zero live network calls** and **zero real LLM calls** — `GitHubClientTest` uses Spring's `MockRestServiceServer`, and the AI service tests mock `ChatModel` directly. Any new feature touching an external API should follow the same pattern: inject the client/model rather than constructing it internally, so it can be substituted with a fake in tests.

**Two test guarantees that must never regress** (and are already covered by existing tests — please don't remove or weaken these):
1. The LLM is never called when there's no real data to ground it (`verify(chatModel, never())...`)
2. A duplicate webhook delivery never double-posts a comment or double-calls the LLM

If your change touches either of these paths, please add or update the corresponding test rather than just manually verifying it once.

---

## Code Conventions

- **DTOs are Java records**, not classes with manual getters/setters
- **Entities use a private/protected no-args constructor** plus a static `create(...)` factory method — never expose a public constructor that allows building a half-initialized entity
- **Constructor injection only** — no `@Autowired` on fields (`@RequiredArgsConstructor` from Lombok handles this)
- **Every external dependency (HTTP client, AI model) is injected, not self-constructed** inside the class that uses it — this is what makes unit testing with fakes possible at all
- **No secrets in code, ever** — all credentials via environment variables, even in test fixtures

---

## What Would Actually Help

These are pulled directly from the Roadmap sections of `README.md` and `BUSINESS.md` — not a generic invitation, but the specific gaps the project currently has:

| Contribution | Why it matters | Difficulty |
|---|---|---|
| Per-tenant rate limiting on AI endpoints | Currently neither feature throttles repeated calls | Medium |
| Repository-to-tenant mapping (GitHub App model) | Closes the most significant architectural gap — see `ARCHITECTURE.md` | Large |
| Durable queue for webhook processing | `@Async` currently loses in-flight work on a crash | Large |
| Codebase Q&A via RAG (PGVector) | Not yet built at all — the third planned feature | Large |
| Linear/Jira ticket context for PR enrichment | Deliberately deferred, documented in `BUSINESS.md` decision #13 | Medium |
| Additional edge-case tests | More coverage on `PrContextEnricherService` failure paths welcome | Small |

If you want to tackle something not on this list, open an issue describing it first — happy to discuss before you put in the work.

---

## Submitting a Change

1. Fork the repo, create a branch off `main`
2. Make your change, including a test if it touches behavior (see "Running Tests" above)
3. Run `./mvnw clean test` locally — a PR with failing tests won't be reviewed
4. Open a PR with a clear description: what changed, why, and how you verified it
5. If your change affects an architectural decision documented in `BUSINESS.md` or `ARCHITECTURE.md`, please update the relevant section in the same PR — documentation drift is treated as a real bug here, not an afterthought

---

## Code of Conduct

Be respectful, assume good faith, and keep discussion focused on the technical merits of a change. This is a small, single-maintainer project — response times may be slower than a company-backed repo, but every genuine contribution is read and considered.
