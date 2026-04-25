# AGENTS.md — AI Agent Guide for Bheembali

This repo is a **personal learning & interview-prep workspace** covering distributed systems, Java concurrency, Kafka, and system design. There is no single deployable app — each sub-module is standalone.

---

## Repository Layout

| Directory | Purpose |
|-----------|---------|
| `ratelimit/` | Spring Boot app: hybrid local+Redis rate limiter (Bucket4j, Caffeine, Resilience4j) |
| `kafka/` | 7-day Kafka study plan with Java/Python examples, labs, and interview Q&A |
| `java_programming_languages/` | Java 8 feature demos (lambdas, streams, Optional, ArrayList internals) |
| `systemdesign/` | System design markdown notes (SQL vs NoSQL, Twitter/X design) |
| `algo_ds_copies/` | Algorithm and data structure practice |
| `Redis/` | Redis depth-dive notes |

---

## `ratelimit/` — Spring Boot Module

**Architecture:** APISIX Gateway → consistent-hash routing (same tenant → same pod) → `HybridRateLimitService` → 90% served from `LocalQuotaManager` (CAS + Caffeine), 10% sync via `RedisQuotaManager` (Lua script atomic ops).

**Key files:**
- `HybridRateLimitService.java` — orchestrates local cache + Redis quota allocation
- `LocalQuotaManager.java` — CAS-based thread-safe local counter
- `RedisQuotaManager.java` — atomic Lua script Redis calls
- `RateLimitingFilter.java` — servlet filter entry point
- `CONCURRENCY_DEEP_DIVE.md` — read before touching concurrency code (explains CAS, locks, thread states)
- `RATELIMIT_SYSTEM_DESIGN.md` — explains APISIX config, consistent hashing, failure modes

**Build & run:**
```bash
cd ratelimit
docker run -d -p 6379:6379 redis:latest   # Redis required
./mvnw spring-boot:run
curl -H "X-Tenant-ID: tenant-123" http://localhost:8080/api/data
curl http://localhost:8080/api/status/tenant-123
./test-hybrid-ratelimit.sh                # integration smoke test
```

**Key dependencies:** Spring Boot 3.3.3, Bucket4j 8.10.1 (redis+core), Caffeine 3.1.8, Resilience4j 2.1.0, Lettuce (Redis client).

**Convention:** Redis operations use Lua scripts (not multi-command) to guarantee atomicity in one roundtrip. Circuit breaker pattern via Resilience4j wraps Redis calls — pod continues on Redis failure using local state.

---

## `kafka/` — Study Module

**Structure:** `docker-compose.yml` spins up the Kafka cluster. All Java examples live under `examples/java/` with separate Maven projects for `producer/`, `consumer/`, `consumer-spring/`, `streams/`.

**Run Kafka:**
```bash
cd kafka
docker-compose up -d
docker exec -it kafka-broker-1 bash
```

**Java examples** each have their own `pom.xml`; build individually:
```bash
cd kafka/examples/java/producer && mvn spring-boot:run
cd kafka/examples/java/consumer-spring && mvn spring-boot:run
```

**Study order:** `interview-questions/day{1-7}-*.md` maps to `labs/day*/`.

---

## `java_programming_languages/` — Java 8 Demos

Standalone `.java` files, no Maven — compile and run directly:
```bash
cd java_programming_languages
javac *.java
java StreamsDemo        # or LambdaDemo, MapReduceExamples, OptionalDemo, etc.
./run_all.sh            # runs all demos sequentially
```

---

## Cross-Cutting Patterns

- **No shared framework** — each module is self-contained; don't assume classes from one module are available in another.
- **Markdown-first documentation** — design decisions and trade-offs are in `.md` files alongside code, not in comments. Read the relevant `.md` before editing implementation files.
- **Interview-oriented code** — classes like `CASConcurrencyDemo.java`, `DeadlockDemo.java`, `ProductionConnectionPool.java` are intentionally verbose and educational, not production-optimized. Preserve explanatory comments when editing.
- **No test suite in `ratelimit/`** — use the shell scripts (`test-ratelimit.sh`, `test-hybrid-ratelimit.sh`, `verify-ratelimit.sh`) for validation.

