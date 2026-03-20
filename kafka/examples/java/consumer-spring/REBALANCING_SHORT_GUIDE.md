# Kafka Consumer Rebalancing - Short Practical Guide

This guide explains when rebalancing happens, what it does to processing, and how to choose safe throughput settings in Spring Kafka.

## 1) What Rebalancing Is

Rebalancing is the process where Kafka redistributes topic partitions across consumers in the same `group.id`.

- Goal: every partition is assigned, and only one group member reads a partition at a time.
- Impact: partition ownership changes, so processing can pause briefly.

## 2) When Rebalancing Happens

Common triggers:

1. A consumer joins the group (new pod starts, or `concurrency` increases).
2. A consumer leaves/crashes (pod restart, heartbeat timeout).
3. Topic partition count changes (for example, `1 -> 3`).
4. Consumer appears stuck (no `poll()` within `max.poll.interval.ms`).

## 3) What Happens During Rebalance (Step-by-Step)

1. Group coordinator detects membership/topology change.
2. Existing assignments are revoked from members.
3. New assignment is computed.
4. Partitions are reassigned to active members.
5. Consumers resume from committed offsets.

If offsets were not committed safely before revoke/failure, duplicate processing can happen.

## 4) Cases You Will See Often

### Case A: `partitions = 1`, `concurrency = 10`

- Active consumers: 1
- Idle consumers: 9
- Throughput gain from extra threads: none

### Case B: `partitions = 3`, `concurrency = 2`

- Two threads are active.
- One thread will own two partitions.
- Better than Case A, but still not full parallelism.

### Case C: scale from 1 pod to 2 pods in same group

- Rebalance happens immediately.
- Partitions get split between pods.
- Short pause is normal during reassignment.

### Case D: slow processing + long business logic

- If listener takes too long and poll loop is blocked, consumer can be removed from group.
- Rebalance occurs and another member may reprocess records.

## 5) Ack/Commit Strategy Tradeoffs

### `AckMode.BATCH`

- Commit behavior: Spring commits after processing records from the poll.
- Pros: less code, good throughput.
- Cons: replay window is larger on failure (more duplicates possible).

### `AckMode.MANUAL`

- Commit behavior: app commits only when `ack.acknowledge()` is called.
- Pros: strongest control; commit after DB/API success.
- Cons: more code and error-handling discipline.

## 6) Processing Strategy Tradeoffs

| Strategy | Throughput | Ordering | Duplicate Risk | Latency | Complexity |
|---|---|---|---|---|---|
| Single message + batch ack | Medium | Good per partition | Medium | Low | Low |
| Single message + manual ack | Medium | Good per partition | Low to medium | Low to medium | Medium |
| Batch listener + batch ack | High | Good per partition | Medium to high | Medium to high | Medium |
| Batch listener + manual control | High | Good per partition | Lower (if designed well) | Medium to high | High |

## 7) Best-Practice Defaults for This Repo

Given your current examples and local setup:

1. Set partitions first, then set `concurrency` to match useful parallelism.
2. Use `AckMode.BATCH` for simple demos and throughput-focused flows.
3. Use `AckMode.MANUAL` when side effects (DB/API) must succeed before commit.
4. Keep processing time bounded so consumer keeps polling regularly.
5. Make processing idempotent (safe to run twice) to handle retries/replays.

## 8) Quick Decision Guide

- Need simplicity and speed now -> `AckMode.BATCH`.
- Need stronger correctness for business side effects -> `AckMode.MANUAL`.
- Need more parallelism -> increase topic partitions, then increase concurrency.
- Seeing frequent rebalances -> reduce per-record processing time or tune poll/session settings.

## 9) Mapping to Your Classes

- `ConcurrentKafkaListener` + `kafkaListenerContainerFactory`: baseline concurrent processing.
- `AdvancedKafkaListener` + `manualCommitContainerFactory`: explicit commit control.
- `BatchKafkaListener` + `batchContainerFactory`: high-throughput batch processing.
- `KafkaConsumerConfig`: where concurrency, ack mode, and poll-related behavior are configured.

