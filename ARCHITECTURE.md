# Architecture

> System structure, data flows, database schema, and honest gap analysis for all three features.

---

## System Overview

```
                    ┌──────────────────────────────────────────┐
                    │            DevPulse Backend               │
                    │      Java 25 + Spring Boot 3.5.x          │
                    └──────────────┬───────────────────────────┘
                                   │
          ┌────────────────────────┼────────────────────────────┐
          │                        │                            │
  ┌───────▼───────┐      ┌────────▼────────┐        ┌─────────▼────────┐
  │   Standup      │      │  PR Context     │        │  Codebase Q&A    │
  │   Generator    │      │  Enricher       │        │  RAG Pipeline    │
  │                │      │                 │        │                  │
  │  Pull-based    │      │  Push-based     │        │  Pull-based      │
  │  Synchronous   │      │  Asynchronous   │        │  Synchronous     │
  └───────┬───────┘      └────────┬────────┘        └─────────┬────────┘
          │                        │                            │
          └────────────────────────┼────────────────────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │        shared/               │
                    │  GitHubClient                │
                    │  ChatModel (Ollama / Groq)   │
                    │  EmbeddingModel (Ollama)      │
                    │  ActiveModelInfo              │
                    │  LlmCallRepository            │
                    │  RateLimiterService (Redis)   │
                    └──────────────┬──────────────┘
                                   │
               ┌───────────────────┼───────────────────┐
               │                   │                   │
       ┌───────▼───────┐  ┌────────▼──────┐  ┌────────▼──────┐
       │  PostgreSQL    │  │    Redis 7     │  │  Groq / Ollama│
       │  (Neon, prod)  │  │  Rate limits  │  │  LLM + Embed  │
       │  PGVector ext  │  │  (in-memory)  │  │  inference    │
       └───────────────┘  └───────────────┘  └───────────────┘
```

---

## Entry Points

### Authenticated API requests

```
Authorization: Bearer dp_live_{keyId}.{secret}
         │
         ▼
ApiKeyAuthenticationFilter
  1. Parse keyId from before the dot
  2. SELECT * FROM tenants WHERE key_id = {keyId}  ← fast indexed lookup
  3. BCrypt.verify(secret, stored_hash)             ← one BCrypt check
  4. Set tenantId as Spring Security principal
         │
         ▼
Controller reads tenantId from SecurityContextHolder
(never from the request — caller cannot fake their tenant)
```

### GitHub webhook requests

```
POST /webhooks/github
X-Hub-Signature-256: sha256={signature}
         │
         ▼
GitHubWebhookSignatureVerifier
  1. Read raw request bytes (before any parsing)
  2. Recompute HMAC-SHA256(webhook_secret, raw_bytes)
  3. MessageDigest.isEqual(expected, actual)  ← constant-time, not String.equals()
         │
         ▼
GitHubWebhookController
  1. Store raw event to webhook_events
  2. Return 200 to GitHub immediately
  3. Kick off async processing via @Async
```

---

## Workflow 1: Standup Generation

```
GET /api/v1/standup/generate?developerId={uuid}&date={optional}
         │
         ▼
1. Verify developer belongs to authenticated tenant
   Wrong tenant or not found → 404 (reveals nothing)
         │
         ▼
2. Check Redis rate limit
   INCR ratelimit:standup:{tenantId}
   count > 10 within 60 seconds → 429 Too Many Requests
         │
         ▼
3. Load all repositories registered by this tenant
   No repos registered → return helpful message, stop here
         │
         ▼
4. Fetch commits from GitHub across all tenant repos:
   fetchCommitsAcrossRepos() → today's commits (or specified date)
   fetchRecentCommitsAcrossRepos() → last 20 commits for style sample
   Individual repo failures are caught and skipped silently
         │
         ▼
5. If todaysCommits.isEmpty() → return "No commits found for this date."
   LLM is NEVER called — this is structural, not a prompt instruction
         │
         ▼
6. StandupSummaryService.summarize(todaysCommits, styleSample)
   SystemMessage: rules (be specific, match style, 3 bullets, past tense)
   UserMessage: style sample + today's commits
   chatModel.chat(system, user) → Ollama locally / Groq in prod
         │
         ▼
7. Save LlmCall audit record (model name, tokens, latency)
         │
         ▼
8. Upsert standup (update if exists for this developer+date, create if not)
         │
         ▼
9. Return StandupResponse (summary, commitCount, commits)
```

### Finalize flow

```
PUT /api/v1/standup/{id}/finalize
{"content": "edited text"}
         │
         ▼
1. Verify standup belongs to authenticated tenant → 404 if not
2. Compute edit distance (character-level approximation)
3. Set final_content, edit_distance
4. IntegrationService.postStandupToSlack() — non-fatal if Slack not configured
5. Return editDistance, editPercent, postedToSlack
```

---

## Workflow 2: PR Context Enrichment

```
GitHub opens PR → sends POST /webhooks/github
         │
         ▼
1. Verify HMAC signature (constant-time) → 401 if invalid
         │
         ▼
2. Persist WebhookEvent (raw payload, not yet processed)
3. Return 200 to GitHub immediately ← critical, prevents redelivery
         │
         ▼ (background thread via @Async)
4. Check idempotency:
   EXISTS pr_enrichments WHERE owner+repo+pr_number = this PR?
   Yes → mark event processed, return. No second comment, no second LLM call.
         │
         ▼
5. Fetch PR diff from GitHub API
         │
         ▼
6. Try Linear ticket lookup (best-effort):
   Extract ticket ID from branch name regex [A-Z][A-Z0-9]+-\d+
   Call Linear GraphQL API with tenant's configured API key
   If no key configured or ticket not found → proceed without ticket context
         │
         ▼
7. Generate context comment:
   SystemMessage: "answer from diff + ticket context only, cite file paths"
   UserMessage: PR title + description + Linear ticket (if found) + diff
   chatModel.chat() → Ollama locally / Groq in prod
         │
         ▼
8. POST comment to GitHub via GitHubClient.postIssueComment()
9. Persist PrEnrichment record
10. Save LlmCall audit record (null tenant_id — no authenticated tenant in webhook flow)
11. Mark WebhookEvent processed
```

---

## Workflow 3: Codebase Q&A

### Index phase (run once per repo, or after significant changes)

```
POST /api/v1/repos/{id}/index
         │
         ▼ (background thread via @Async)
1. Mark repository status: INDEXING
2. Delete all existing chunks for this repo (clean slate on re-index)
         │
         ▼
3. GitHub: fetch repository git tree (recursive, all files)
4. Filter: .java .kt .md .yml .yaml .properties
   Skip: /target/ /build/ /node_modules/
   Limit: 200 files
         │
         ▼
5. For each file:
   a. Fetch file content (base64 encoded)
   b. Decode base64
   c. Chunk into pieces ≤ 1500 characters
      (YAML/MD: split at size boundary; Java/Kotlin: split at line groups)
   d. For each chunk: embeddingModel.embed(chunkText)
      → 768-dimensional float[] from nomic-embed-text
   e. Convert float[] to pgvector string "[0.1,0.2,...]"
   f. Batch save to code_chunks (10 at a time)
         │
         ▼
6. Mark repository status: READY, set last_indexed_at
         │
         ▼
7. Rebuild ivfflat vector index:
   DROP INDEX IF EXISTS idx_code_chunks_embedding
   CREATE INDEX idx_code_chunks_embedding
     ON code_chunks USING ivfflat (embedding vector_cosine_ops)
     WITH (lists = 100)
   ← MUST happen after data exists. Built on empty table = degenerate index.
```

### Query phase

```
POST /api/v1/codeqa/ask
{"repositoryId": "{uuid}", "question": "How does auth work?"}
         │
         ▼
1. Verify repository belongs to authenticated tenant
         │
         ▼
2. Embed the question: embeddingModel.embed(question) → 768-dim vector
3. Convert to pgvector string
         │
         ▼
4. Similarity search:
   SELECT * FROM code_chunks
   WHERE tenant_id = {tenantId} AND repository_id = {repositoryId}
   ORDER BY embedding <=> CAST(:queryVector AS vector)
   LIMIT 5
   ← Filtered by BOTH tenant_id AND repository_id (cross-repo leakage is prevented)
         │
         ▼
5. If no chunks found → return helpful message, LLM never called
         │
         ▼
6. Build context: [file_path]\n{chunk_content} for each of the 5 chunks
7. chatModel.chat(system, user) with grounded context
   "Answer ONLY from context. If not enough context, say so."
         │
         ▼
8. Return CodeQaResponse (answer, sourcesUsed, chunksRetrieved, groundedInCode)
```

---

## Database Schema

```sql
tenants
  id UUID PK
  name VARCHAR
  api_key_hash VARCHAR UNIQUE      -- BCrypt of secret portion
  key_id VARCHAR UNIQUE            -- plaintext, indexed, for fast lookup
  plan VARCHAR DEFAULT 'FREE'
  created_at TIMESTAMPTZ

developers
  id UUID PK
  tenant_id UUID FK → tenants
  github_username VARCHAR
  timezone VARCHAR DEFAULT 'UTC'
  UNIQUE(tenant_id, github_username)

repositories
  id UUID PK
  tenant_id UUID FK → tenants
  github_owner VARCHAR
  github_repo VARCHAR
  default_branch VARCHAR DEFAULT 'main'
  index_status VARCHAR DEFAULT 'PENDING'  -- PENDING/INDEXING/READY/FAILED
  last_indexed_at TIMESTAMPTZ
  UNIQUE(tenant_id, github_owner, github_repo)

code_chunks
  id UUID PK
  tenant_id UUID FK → tenants
  repository_id UUID FK → repositories
  source_type VARCHAR          -- java/kotlin/markdown/yaml/config
  file_path TEXT
  content TEXT
  embedding vector(768)        -- nomic-embed-text embeddings
  embedding_model VARCHAR
  indexed_at TIMESTAMPTZ
  INDEX idx_code_chunks_tenant_id (tenant_id)
  INDEX idx_code_chunks_embedding USING ivfflat(embedding vector_cosine_ops)

standups
  id UUID PK
  tenant_id UUID FK → tenants
  developer_id UUID FK → developers
  standup_date DATE
  generated_content TEXT       -- AI output
  final_content TEXT           -- developer's edited version (NULL until finalized)
  edit_distance INTEGER        -- character-level diff between generated and final
  commits_used INTEGER
  created_at TIMESTAMPTZ
  UNIQUE(developer_id, standup_date)

llm_calls
  id UUID PK
  tenant_id UUID               -- nullable: NULL for webhook-triggered calls
  developer_id UUID
  feature VARCHAR              -- STANDUP / PR_CONTEXT
  model_name VARCHAR           -- actual model that ran (profile-scoped bean)
  prompt_tokens INTEGER
  completion_tokens INTEGER
  latency_ms BIGINT
  created_at TIMESTAMPTZ

webhook_events
  id UUID PK
  source VARCHAR               -- GITHUB
  event_type VARCHAR           -- pull_request
  payload TEXT                 -- raw JSON from GitHub
  processed BOOLEAN DEFAULT FALSE
  error_message TEXT           -- set if processing failed
  received_at TIMESTAMPTZ

pr_enrichments
  id UUID PK
  github_owner VARCHAR
  github_repo VARCHAR
  pr_number INTEGER
  context_comment TEXT
  github_comment_id BIGINT
  created_at TIMESTAMPTZ
  UNIQUE(github_owner, github_repo, pr_number)  -- idempotency constraint

integrations
  id UUID PK
  tenant_id UUID FK → tenants
  integration_type VARCHAR     -- SLACK / LINEAR
  config TEXT                  -- JSON: {"webhookUrl":"..."} or {"apiKey":"..."}
  enabled BOOLEAN DEFAULT TRUE
  created_at TIMESTAMPTZ
  updated_at TIMESTAMPTZ
  UNIQUE(tenant_id, integration_type)
```

---

## AI Provider Configuration

```
Dev profile (default):
  ChatModel  → OllamaChatModel   → localhost:11434 → llama3.2
  EmbeddingModel → OllamaEmbeddingModel → nomic-embed-text

Prod profile:
  ChatModel  → OpenAiChatModel   → api.groq.com/openai/v1 → llama-3.3-70b-versatile
  EmbeddingModel → OllamaEmbeddingModel → (same, embeddings always local)

ActiveModelInfo (profile-scoped bean):
  Dev:  "llama3.2"
  Prod: "llama-3.3-70b-versatile"
  → Written to llm_calls.model_name on every call
  → This is what fixed the audit log bug where prod showed dev model name
```

---

## Rate Limiting Design

```
On every GET /api/v1/standup/generate:

  key = "ratelimit:standup:{tenantId}"
  count = INCR key          ← atomic, no lock needed
  if count == 1:
    EXPIRE key 60           ← start 60-second window on first request
  if count > 10:
    return 429 Too Many Requests

Window resets automatically when the Redis key expires.
No cleanup job. No stale state.

Tested with 11 parallel PowerShell background jobs:
→ Confirmed mixed 200/429 responses in single run
→ Confirms atomic accumulation under real concurrency
```

---

## Honest Gap Analysis

### Production-Grade

- Multi-tenant data isolation (every query scoped by tenant_id)
- Split-key BCrypt API authentication
- HMAC-SHA256 webhook verification with constant-time comparison
- Idempotent webhook processing
- Dual AI provider with profile-scoped switching
- Full LLM audit log (model, tokens, latency — every call)
- Redis rate limiting tested under concurrent load
- Flyway migrations (every schema change versioned)
- Testcontainers integration tests (real PostgreSQL, not H2)
- 33 unit tests, zero live network/LLM calls in unit tests

### Intentionally Simplified (Documented Gaps)

**No durable queue for webhook processing**
Spring `@Async` uses an in-memory thread pool. JVM crash between webhook receipt and comment posting loses that job. `webhook_events` table records every incoming event for manual replay, but no automatic retry exists.
*Fix: SQS, RabbitMQ, or any persistent queue.*

**PR enrichment has no per-tenant GitHub connection**
The system uses one shared `GITHUB_TOKEN` for all PR fetches and comment posts. GitHub webhooks carry no concept of "which DevPulse tenant does this repo belong to."
*Fix: GitHub App installation model — each tenant installs the app, GitHub tracks the installation-to-tenant mapping, webhooks carry the installation ID.*

**Line-based chunking for Q&A**
Code is chunked by line groups, not by class or method boundaries. Semantically adjacent files in the same package can outscore each other for specific questions.
*Fix: AST-based chunking that treats each method as a chunk.*

**Integration credentials stored as plaintext JSON**
Slack webhook URLs and Linear API keys in the `integrations` table are not encrypted at rest.
*Fix: Column-level encryption or a secrets manager.*

**Fixed-window rate limiting boundary**
A tenant can fire 10 requests at the end of one window and 10 at the start of the next — 20 in 2 seconds. Acceptable at this scale.
*Fix: Sliding-window counter using Redis sorted sets.*
