# System Design: X (Twitter at Scale)

## Executive Summary
Design a real-time social media platform serving billions of users globally with millions of high-profile accounts. Focus on feed generation, trending topics, fault tolerance, and database trade-offs.

---

## 1. Core Requirements & Scale

### Functional Requirements
- **Post/Tweet Creation & Deletion**: Users create, edit, delete tweets
- **Home Feed**: Personalized timeline with millions of followers visible
- **Trending Topics**: Real-time trending calculations globally and by region
- **User Profiles**: Profile with millions of followers
- **Notifications**: Real-time notifications (likes, retweets, replies)
- **Search**: Full-text search across tweets

### Non-Functional Requirements
- **Scalability**: 500M DAU, 1.5B tweets/day, 10K QPS for feed reads
- **Latency**: <200ms P99 for home feed
- **Availability**: 99.99% uptime (4.38 min/month downtime)
- **Consistency Model**: Eventual consistency for feeds, strong consistency for user data
- **Data Volume**: 100+ TB/day of new data

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Client Layer                             │
│              (Web, Mobile, Desktop Apps)                    │
└────────────────────────┬────────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
    ┌─────────────────────────────────────┐
    │    API Gateway (Load Balancer)      │
    │  (Rate Limiting, Auth, Routing)     │
    └─────────────────────────────────────┘
         │
    ┌────┴──────────────────────────────────────┐
    │                                           │
    ▼                                           ▼
┌──────────────────┐              ┌──────────────────┐
│  Service Layer   │              │  Service Layer   │
│  (Microservices) │              │  (Microservices) │
└──────────────────┘              └──────────────────┘
    │
    ├─── Feed Service           → builds & serves home timeline (§6)
    ├─── Tweet Service          → create/delete tweets, write to TweetDB (§4)
    ├─── User Service           → profiles, auth, user CRUD (§3.1)
    ├─── Fanout Service         → push tweets to follower timelines (§5)
    ├─── Trending Service       → real-time trending hashtags (§4 consumer C)
    ├─── Notification Service   → mentions, likes, follow alerts (§4 consumer D)
    ├─── Search Service         → tweet/user search via Elasticsearch (§7)
    └─── Graph Service (Follows)→ follow/unfollow, dual sharding (§3.1.2)
    │
    └─────────┬──────────────┬──────────────┬──────────────┐
              ▼              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ Cache    │  │ Database │  │ Message  │  │ Search   │
        │ (Redis)  │  │ (SQL+NoSQL) │ Queue  │  │ (ES)     │
        │          │  │          │  │ (Kafka) │  │          │
        └──────────┘  └──────────┘  └──────────┘  └──────────┘
```

---

## 3. Database Architecture

### 3.1 SQL (Primary Data Store)

**UserDB (PostgreSQL with Sharding)** `[Owner: User Service]`
```
Sharding Key: user_id (Consistent Hashing)
├─ users (user_id, handle, name, bio, followers_count, verified)
├─ follows (follower_id, following_id, created_at)
├─ user_stats (user_id, tweets_count, followers_count, engagement_score)
└─ user_profiles (user_id, profile_pic, banner, location, website)
```

### 3.1.0 How Sharding by `user_id` Works (Simple View)

When we say `Sharding Key: user_id`, it means the app computes which DB shard to use from `user_id`.

```
Shard Rule (example):
shard_id = hash(user_id) % 16

Result:
- user_id 101 -> shard_05
- user_id 202 -> shard_11
- user_id 303 -> shard_05
```

**How one app talks to many DB shards**

```
1) Request comes to User Service (e.g., GET /users/101)
2) Service computes shard_id from user_id
3) Service picks connection from shard map / connection pool
   - shard_00 -> db-user-00.internal:5432
   - shard_01 -> db-user-01.internal:5432
   - ...
4) Query runs only on that shard
5) Response returned to client
```

**Why this helps**
- Writes are distributed across 16 DBs instead of one hot DB
- Reads for one user are localized to one shard
- Easy to scale by adding more shards (with rebalancing)

**One user sample row details**

```sql
-- Assume user_id = 101, hash(101) % 16 = 5 -> shard_05

-- users
(user_id=101, handle='@vishal', name='Vishal Kumar', bio='System design', followers_count=1203400, verified=true)

-- follows (who 101 follows)
(follower_id=101, following_id=55, created_at='2026-04-12T08:10:00Z')

-- user_stats
(user_id=101, tweets_count=18900, followers_count=1203400, engagement_score=87.4)

-- user_profiles
(user_id=101, profile_pic='https://cdn.x.com/p/101.jpg', banner='https://cdn.x.com/b/101.jpg', location='Bengaluru', website='https://example.com')
```

**Important note for `follows`**
- `follows` has two user IDs (`follower_id`, `following_id`), so one shard key cannot optimize both query types.
- Practical approach at scale:
  - Primary table sharded by `follower_id` for "who I follow" (feed path)
  - Secondary index/table (or async replica) by `following_id` for "who follows me" (profile path)

### 3.1.1 Follower List Handling at Scale (Critical for VIP Users) `[Owner: Graph Service]`

**The Problem**: Users with 1M+ followers

```
Naive Approach (FAILS):
├─ Query: SELECT follower_id FROM follows WHERE following_id = @user_id LIMIT 100
├─ Problem: @elonmusk has 150M followers
├─ This query scans 150M rows even for LIMIT 100
├─ Index on (following_id, follower_id) helps, but still massive
├─ Result: P99 latency for profile loading: 5+ seconds ❌
└─ Scalability: Can't paginate through 150M followers efficiently
```

**Solution: Tiered Follower Storage**

```
Tier 1: Sorted Set of Hot Followers (Redis) - For VIP accounts only (1M+ followers) [Graph Service writes, User Service reads]
├─ Key: follower:hot:{user_id} (Sorted Set in Redis)
├─ Data Structure: {member: engagement_score}
│   └─ Member: follower_user_id
│   └─ Score: engagement_metric (timestamp of last interaction or engagement count)
├─ Max Size: 10,000 members (ZREMRANGEBYRANK to maintain)
├─ Content: Most-active followers (who liked, retweeted, replied recently)
├─ TTL: EXPIRE follower:hot:{user_id} 604800 (7 days)
├─ Redis Commands:
│   ├─ ZADD follower:hot:@elonmusk 1712000000 @user123
│   ├─ ZRANGE follower:hot:@elonmusk 0 19 WITHSCORES (get top 20 with scores)
│   ├─ ZCARD follower:hot:@elonmusk (count total)
│   └─ ZREM follower:hot:@elonmusk @user123 (remove on unfollow)
├─ Latency: <1ms (pure in-memory)
└─ Use Case: Profile page instant display "Followed by @active_1, @active_2..."

Tier 2: Set of Sample Followers (Redis) - For display/recommendations [Graph Service batch job writes, User Service reads]
├─ Key: follower:sample:{user_id} (Set in Redis)
├─ Data Structure: {member} (unordered)
│   └─ Member: follower_user_id
├─ Max Size: 10,000 members (random sample refreshed daily)
├─ Content: Random subset of followers (for variety, not engagement-based)
├─ TTL: EXPIRE follower:sample:{user_id} 86400 (24 hours)
├─ Redis Commands:
│   ├─ SADD follower:sample:@elonmusk @user456 @user789
│   ├─ SRANDMEMBER follower:sample:@elonmusk 20 (get random 20)
│   ├─ SCARD follower:sample:@elonmusk (count)
│   └─ SREM follower:sample:@elonmusk @user123 (remove on unfollow)
├─ Latency: <1ms (pure in-memory)
└─ Use Case: "Who to follow" suggestions, variety in display

Tier 3: Full Follower List (PostgreSQL) - Source of truth [Graph Service owns, all services query]
├─ Table: follows (source of all follower data)
│   Schema: (follower_id, following_id, created_at, is_active)
│   Sharded by: following_id (Consistent Hashing across 16 shards)
├─ Index: CREATE INDEX idx_follows_following_id_created_at 
│         ON follows(following_id, created_at DESC)
│   Purpose: Efficient range scans for "WHO FOLLOWS USER X" pagination
├─ Alternate Index: CREATE INDEX idx_follows_follower_id_following_id
│         ON follows(follower_id, following_id) 
│   Purpose: Fast lookup for "IS USER A FOLLOWING USER B"
├─ Use Case: Pagination (page 2+), export followers, analytics, verification
├─ Query Pattern (Pagination):
│   └─ SELECT follower_id FROM follows 
│      WHERE following_id = ? AND created_at < ?
│      ORDER BY created_at DESC LIMIT 20
├─ Cache: follower:count:{user_id} (in Redis, updated on follow/unfollow)
│   └─ INCR follower:count:@elonmusk (on new follow)
│   └─ DECR follower:count:@elonmusk (on unfollow)
│   └─ EXPIRE follower:count:@elonmusk 604800 (7 days)
└─ Latency: 50-200ms (disk I/O with index)
```

**Implementation Strategy (Java / Spring + Redis)** `[Graph Service internals]`

```java
@Service
public class HotFollowerService {

    private static final long VIP_THRESHOLD = 1_000_000L;
    private static final int MAX_HOT = 10_000;
    private static final Duration HOT_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;

    // ── Atomic Lua: ZADD + trim + EXPIRE in one server-side call ──
    // Why Lua over MULTI/EXEC? Lua supports conditional logic (if size > max).
    // MULTI/EXEC can only batch fixed commands, no branching.
    private static final DefaultRedisScript<Long> UPSERT_TRIM = new DefaultRedisScript<>(
        "redis.call('ZADD', KEYS[1], tonumber(ARGV[1]), ARGV[2]) " +
        "local sz = redis.call('ZCARD', KEYS[1]) " +
        "if sz > tonumber(ARGV[3]) then " +
        "  redis.call('ZREMRANGEBYRANK', KEYS[1], 0, sz - tonumber(ARGV[3]) - 1) " +
        "end " +
        "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4])) " +
        "return redis.call('ZCARD', KEYS[1])", Long.class);

    public HotFollowerService(StringRedisTemplate redis, JdbcTemplate jdbc) {
        this.redis = redis;
        this.jdbc = jdbc;
    }

    // ── READ: profile page "Followed by @active_1, @active_2..." ──
    public List<String> getFollowersForProfile(long userId, int limit, String cursor) {
        if (getFollowerCount(userId) < VIP_THRESHOLD)
            return loadFromSql(userId, limit, cursor);

        if (cursor == null || cursor.isBlank()) {
            var tuples = redis.opsForZSet().reverseRangeWithScores(hotKey(userId), 0, limit - 1);
            if (tuples != null && !tuples.isEmpty())
                return tuples.stream().map(ZSetOperations.TypedTuple::getValue).toList();
            return redis.opsForSet().randomMembers(sampleKey(userId), limit)
                        .stream().distinct().toList();
        }
        return loadFromSql(userId, limit, cursor);
    }

    // ── WRITE: on like/retweet/reply → update engagement score (atomic) ──
    public void onEngagement(long followingId, long followerId, double score) {
        if (getFollowerCount(followingId) < VIP_THRESHOLD) return;
        redis.execute(UPSERT_TRIM, List.of(hotKey(followingId)),
            String.valueOf(score), String.valueOf(followerId),
            String.valueOf(MAX_HOT), String.valueOf(HOT_TTL.toSeconds()));
    }

    // ── DELETE: on unfollow ──
    public void onUnfollow(long followingId, long followerId) {
        String fid = String.valueOf(followerId);
        redis.opsForZSet().remove(hotKey(followingId), fid);
        redis.opsForSet().remove(sampleKey(followingId), fid);
        redis.opsForValue().decrement(countKey(followingId));
    }

    private List<String> loadFromSql(long userId, int limit, String cursor) {
        if (cursor == null || cursor.isBlank())
            return jdbc.queryForList(
                "SELECT follower_id FROM follows WHERE following_id=? ORDER BY created_at DESC LIMIT ?",
                String.class, userId, limit);
        return jdbc.queryForList(
            "SELECT follower_id FROM follows WHERE following_id=? AND created_at<? ORDER BY created_at DESC LIMIT ?",
            String.class, userId, Timestamp.from(Instant.ofEpochSecond(Long.parseLong(cursor))), limit);
    }

    private long getFollowerCount(long userId) {
        String v = redis.opsForValue().get(countKey(userId));
        return v == null ? 0L : Long.parseLong(v);
    }

    private String hotKey(long id)    { return "follower:hot:" + id; }
    private String sampleKey(long id) { return "follower:sample:" + id; }
    private String countKey(long id)  { return "follower:count:" + id; }
}
```

**How Max Size 10,000 cap works (`ZREMRANGEBYRANK`)**

```text
After ZADD, if ZCARD > 10,000:
  overflow = size - 10000
  ZREMRANGEBYRANK key 0 (overflow - 1)   ← removes lowest-score members

Why rank 0? ZSET ranks ascend by score. Rank 0 = lowest = least active.
We read via ZREVRANGE (highest first), so we trim from the bottom.

Example: size=10,250 → ZREMRANGEBYRANK key 0 249 → 250 removed → size=10,000
Complexity: ZADD O(log N), ZCARD O(1), ZREMRANGEBYRANK O(log N + M)
Atomicity: Lua script runs all 3 server-side in one call (no race).
```

**How Redis Sorted Set Works Internally**

```
Redis ZSET uses TWO data structures together:

1) Hash Map  (member → score)     → O(1) score lookup by member
2) Skip List (score → member)     → O(log N) ordered insert, range, rank

Why both?
  - HashMap alone: fast ZSCORE but can't do ZRANGE/ZRANK (no ordering)
  - SkipList alone: fast range/rank but ZSCORE is O(log N) not O(1)
  - Together: every operation is optimal

Small sets (< 128 members AND all values < 64 bytes):
  → Redis uses ziplist (compact, cache-friendly array) instead
  → Automatically converts to skiplist + hashmap when threshold crossed

Memory layout (conceptual):

  HashMap:                    SkipList (sorted by score):
  ┌──────────┬───────┐       Level 3:  [10] ──────────────────→ [90] → NULL
  │ member   │ score │       Level 2:  [10] ────→ [40] ──────→ [90] → NULL
  ├──────────┼───────┤       Level 1:  [10] → [25] → [40] → [70] → [90] → NULL
  │ user_A   │  10   │
  │ user_B   │  25   │       Each node: { score, member, forward[] }
  │ user_C   │  40   │
  │ user_D   │  70   │       SkipList = probabilistic balanced structure
  │ user_E   │  90   │       Each level skips ~half the nodes (coin flip on insert)
  └──────────┴───────┘       Search: start top level, go right or drop down = O(log N)
```
How each Redis command maps to internals:

  ZADD score member   → HashMap.put(member, score) + SkipList.insert(score, member)
  ZSCORE member       → HashMap.get(member)                     → O(1)
  ZRANK member        → SkipList.getRank(score, member)         → O(log N)
  ZRANGE 0 N          → SkipList.traverse(startRank, count)     → O(log N + M)
  ZRANGEBYSCORE lo hi → SkipList.findByScore(lo).walkForward(hi)→ O(log N + M)
  ZREM member         → HashMap.remove(member) + SkipList.delete(score, member)
  ZCARD               → stored as counter (incremented on add)  → O(1)
```

**Java — Simplified Sorted Set internals (SkipList + HashMap)**

```java
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simplified model of how Redis Sorted Set works internally.
 * NOT production Redis — illustrates the dual data structure concept.
 *
 * HashMap  → O(1) score lookup by member
 * SkipList → O(log N) ordered traversal, rank, range
 */
public class SortedSet {

    private static final int MAX_LEVEL = 16;

    // ── The two internal data structures ──
    private final Map<String, Double> scoreMap = new HashMap<>();  // member → score
    private final SkipListNode head = new SkipListNode(Double.MIN_VALUE, null, MAX_LEVEL);
    private int size = 0;
    private int currentLevel = 1;

    // ── ZADD: insert or update member with score ──
    public void zadd(String member, double score) {
        if (scoreMap.containsKey(member)) {
            double oldScore = scoreMap.get(member);
            if (oldScore == score) return;
            skipListDelete(oldScore, member);  // remove old position
        } else {
            size++;
        }
        scoreMap.put(member, score);           // O(1) upsert in hashmap
        skipListInsert(score, member);         // O(log N) insert in skiplist
    }

    // ── ZSCORE: O(1) via HashMap ──
    public Double zscore(String member) {
        return scoreMap.get(member);           // O(1) — this is WHY we keep the hashmap
    }

    // ── ZCARD: O(1) counter ──
    public int zcard() {
        return size;
    }

    // ── ZRANGE: traverse skiplist from rank start to end (ascending by score) ──
    public List<String> zrange(int start, int end) {
        List<String> result = new ArrayList<>();
        SkipListNode node = head.forward[0];   // level 0 = full sorted chain
        int rank = 0;
        while (node != null) {
            if (rank >= start && rank <= end)
                result.add(node.member);
            if (rank > end) break;
            node = node.forward[0];
            rank++;
        }
        return result;
    }

    // ── ZREVRANGE: same but collect in reverse ──
    public List<String> zrevrange(int start, int end) {
        List<String> all = zrange(0, size - 1);
        Collections.reverse(all);
        int safeEnd = Math.min(end, all.size() - 1);
        return all.subList(start, safeEnd + 1);
    }

    // ── ZRANK: count nodes before this member in skiplist ──
    public int zrank(String member) {
        Double score = scoreMap.get(member);
        if (score == null) return -1;
        int rank = 0;
        SkipListNode node = head.forward[0];
        while (node != null) {
            if (node.score == score && node.member.equals(member)) return rank;
            node = node.forward[0];
            rank++;
        }
        return -1;
    }

    // ── ZREM: remove from BOTH structures ──
    public boolean zrem(String member) {
        Double score = scoreMap.remove(member);
        if (score == null) return false;
        skipListDelete(score, member);
        size--;
        return true;
    }

    // ── ZREMRANGEBYRANK: trim to keep only top K (used for 10K cap) ──
    public int zremrangebyrank(int start, int end) {
        List<String> toRemove = zrange(start, end);
        toRemove.forEach(this::zrem);
        return toRemove.size();
    }

    // ── ZRANGEBYSCORE: find members with score in [min, max] ──
    public List<String> zrangebyscore(double min, double max) {
        List<String> result = new ArrayList<>();
        SkipListNode node = head.forward[0];
        while (node != null && node.score < min) node = node.forward[0]; // skip to min
        while (node != null && node.score <= max) {
            result.add(node.member);
            node = node.forward[0];
        }
        return result;
    }

    // ═══════════════════════════════════════════════════
    //  SkipList internals (what Redis implements in C)
    // ═══════════════════════════════════════════════════

    static class SkipListNode {
        double score;
        String member;
        SkipListNode[] forward;  // forward[i] = next node at level i

        SkipListNode(double score, String member, int level) {
            this.score = score;
            this.member = member;
            this.forward = new SkipListNode[level + 1];
        }
    }

    // Random level: each level has 50% chance (like coin flip)
    // This gives O(log N) average height
    private int randomLevel() {
        int level = 1;
        while (ThreadLocalRandom.current().nextBoolean() && level < MAX_LEVEL)
            level++;
        return level;
    }

    private void skipListInsert(double score, String member) {
        SkipListNode[] update = new SkipListNode[MAX_LEVEL + 1];
        SkipListNode current = head;

        // Find insert position at each level (top → bottom)
        for (int i = currentLevel - 1; i >= 0; i--) {
            while (current.forward[i] != null &&
                   (current.forward[i].score < score ||
                    (current.forward[i].score == score &&
                     current.forward[i].member.compareTo(member) < 0))) {
                current = current.forward[i];
            }
            update[i] = current;  // remember where we dropped down
        }

        int newLevel = randomLevel();
        if (newLevel > currentLevel) {
            for (int i = currentLevel; i < newLevel; i++)
                update[i] = head;
            currentLevel = newLevel;
        }

        SkipListNode newNode = new SkipListNode(score, member, newLevel);
        for (int i = 0; i < newLevel; i++) {
            newNode.forward[i] = update[i].forward[i];
            update[i].forward[i] = newNode;
        }
    }

    private void skipListDelete(double score, String member) {
        SkipListNode[] update = new SkipListNode[MAX_LEVEL + 1];
        SkipListNode current = head;

        for (int i = currentLevel - 1; i >= 0; i--) {
            while (current.forward[i] != null &&
                   (current.forward[i].score < score ||
                    (current.forward[i].score == score &&
                     current.forward[i].member.compareTo(member) < 0))) {
                current = current.forward[i];
            }
            update[i] = current;
        }

        SkipListNode target = current.forward[0];
        if (target != null && target.score == score && target.member.equals(member)) {
            for (int i = 0; i < currentLevel; i++) {
                if (update[i].forward[i] != target) break;
                update[i].forward[i] = target.forward[i];
            }
            while (currentLevel > 1 && head.forward[currentLevel - 1] == null)
                currentLevel--;
        }
    }

    // ── Demo: simulates hot follower tier usage ──
    public static void main(String[] args) {
        SortedSet hotFollowers = new SortedSet();

        // ZADD — follower engagement scores
        hotFollowers.zadd("vishal",  85.0);
        hotFollowers.zadd("alice",   92.0);
        hotFollowers.zadd("bob",     40.0);
        hotFollowers.zadd("charlie", 67.0);
        hotFollowers.zadd("diana",   99.0);

        // ZSCORE — O(1) via HashMap
        System.out.println("vishal score: " + hotFollowers.zscore("vishal"));  // 85.0

        // ZCARD — O(1)
        System.out.println("total: " + hotFollowers.zcard());                  // 5

        // ZRANGE 0 2 — top 3 by ascending score (skiplist traversal)
        System.out.println("bottom 3: " + hotFollowers.zrange(0, 2));          // [bob, charlie, vishal]

        // ZREVRANGE 0 2 — top 3 by descending score (profile page use case)
        System.out.println("top 3: " + hotFollowers.zrevrange(0, 2));          // [diana, alice, vishal]

        // ZRANK — position in skiplist
        System.out.println("vishal rank: " + hotFollowers.zrank("vishal"));    // 2

        // ZRANGEBYSCORE 60 95 — followers with score between 60-95
        System.out.println("active: " + hotFollowers.zrangebyscore(60, 95));   // [charlie, vishal, alice]

        // ZREM — remove from BOTH hashmap + skiplist
        hotFollowers.zrem("bob");
        System.out.println("after remove bob: " + hotFollowers.zcard());       // 4

        // ZREMRANGEBYRANK 0 0 — trim lowest score (10K cap logic)
        hotFollowers.zremrangebyrank(0, 0);
        System.out.println("after trim: " + hotFollowers.zrange(0, hotFollowers.zcard() - 1));
        // [vishal, alice, diana]  (charlie was lowest after bob removed)
    }
}
```

```text
Why SkipList over balanced BST (Red-Black/AVL)?

  SkipList advantages (why Redis chose it):
  ├─ Simpler to implement (no rotations)
  ├─ Range queries are natural (just walk forward at level 0)
  ├─ Lock-free concurrent reads possible (important for Redis internals)
  ├─ Same O(log N) average complexity as BST
  └─ Cache-friendly sequential traversal

  Complexity summary:
  ├─ Insert:  O(log N) — find position top-down, insert with random level
  ├─ Delete:  O(log N) — find + unlink at each level
  ├─ Search:  O(log N) — skip higher levels, drill down
  ├─ Rank:    O(log N) — count span widths while traversing
  └─ Range:   O(log N + M) — find start in O(log N), walk M nodes
```

**Redis Sorted Set Command Reference**

```text
| Command                  | Complexity   | Use Case                                     |
|--------------------------|--------------|----------------------------------------------|
| ZADD key score member    | O(log N)     | Add/update follower engagement score         |
| ZADD key NX score member | O(log N)     | Insert only if not exists (first follow)     |
| ZADD key GT score member | O(log N)     | Update only if new score > current           |
| ZINCRBY key delta member | O(log N)     | +1 like, +2 retweet, +3 reply (incremental) |
| ZSCORE key member        | O(1)         | Get single follower score / existence check  |
| ZREVRANK key member      | O(log N)     | "You are #3 most active follower"            |
| ZCARD key                | O(1)         | Total count / cap check                      |
| ZCOUNT key min max       | O(log N)     | Count followers in score range (analytics)   |
| ZREVRANGE key 0 N        | O(log N + M) | Top N active followers for profile page      |
| ZRANGEBYSCORE key min max| O(log N + M) | Followers in score bracket (notifications)   |
| ZREM key member          | O(M log N)   | Remove on unfollow / suspension              |
| ZREMRANGEBYRANK key 0 N  | O(log N + M) | Trim to 10K cap (remove lowest N)            |
| ZREMRANGEBYSCORE key 0 X | O(log N + M) | Evict stale followers below threshold        |
| ZPOPMIN / ZPOPMAX key    | O(log N)     | Pop least/most active (cleanup worker)       |

Flags: NX=insert-only, XX=update-only, GT=only-if-higher, LT=only-if-lower
Patterns: Pipeline for bulk, Lua for conditional trim, ZINCRBY over ZADD for increments
```

**Trade-offs Table**

| Scenario | Storage | Latency | Use Case |
|---|---|---|---|
| Regular user (10K followers) | SQL only | <50ms | Direct query |
| Popular user (100K followers) | SQL indexed | 100-150ms | Pagination acceptable |
| VIP (1M+) first page | Redis ZSET (hot) | **<1ms** | Instant profile display |
| VIP random display | Redis SET (sample) | **<1ms** | "Followed by" variety |
| VIP page 2+ | SQL + cursor | 100-300ms | Acceptable |

---

## 3.1.2 Dual Sharding for `follows` Table `[Owner: Graph Service]`

**Why dual sharding?**

```
Problem: follows table has two hot query patterns at 100K+ QPS:
  Q1: "Who do I follow?"   → needs follower_id as shard key
  Q2: "Who follows me?"    → needs following_id as shard key

Single shard key → one query is fast (local), the other scatters across ALL shards.
At scale, scatter-gather on core read paths kills P99 latency.

Solution: Keep two copies of the same data, sharded differently.
```

**The Two Shard Groups**

```
A) Forward Shard (Following Index)
   Shard Key: follower_id
   Table: follows_forward (follower_id, following_id, created_at)
   Index: (follower_id, created_at DESC)
   Query: "Who do I follow?" → single-shard local lookup
   Use case: Home feed — get candidate author IDs
   Read by: Feed Service (to build home timeline)

B) Reverse Shard (Followers Index)
   Shard Key: following_id
   Table: follows_reverse (follower_id, following_id, created_at)
   Index: (following_id, created_at DESC)
   Query: "Who follows me?" → single-shard local lookup
   Use case: Profile page, follower notifications
   Read by: User Service (profile), Fanout Service (push tweets), Notification Service
```

**Write Path (Atomic locally, Eventual globally)**

```
User A follows User B:

1) Sync: write to Forward shard (source of truth for A's experience)
   INSERT INTO follows_forward (follower_id=A, following_id=B, created_at=now)
   + INSERT outbox row in SAME transaction
   [Graph Service — synchronous]

2) Async (Kafka): CDC reads outbox → publishes FollowEvent
   Debezium connector watches follows_outbox on Forward shard
   → publishes to Kafka topic: follows.created
   [Graph Service infra — Debezium connector deployed & owned by Graph team]

3) Consumer: write to Reverse shard
   INSERT INTO follows_reverse (follower_id=A, following_id=B, created_at=now)
   [Graph Service — FollowEventConsumer pod (separate consumer group, same service codebase)]

4) Consumer: update Redis counters
   INCR follower:count:{B}
   INCR following:count:{A}
   [Graph Service — same FollowEventConsumer pod, or a second consumer group]

Unfollow: symmetric (DELETE + outbox + async reverse delete + cache cleanup)
All downstream ops are idempotent → safe to retry on failure.
```

**Celebrity Hot Shard Problem + Fix**

```
Problem: @elonmusk (following_id=1) has 150M followers.
  Reverse shard key = hash(1) → ALL 150M rows land on ONE shard → overloaded.

Fix: Virtual Bucketing
  bucket_id = follower_id % N  (N = 64 buckets)
  shard_key = hash(following_id + ':' + bucket_id)

  Write: each follower goes to one of 64 buckets (spread across shards)
  Read:  scatter to 64 buckets in parallel → merge results

  Normal users: N=1 (no bucketing needed, single shard)
  VIP users:    N=64 (spread load)
```

**Query-to-Shard Summary**

| Query | Shard Key | Pattern | Latency |
|---|---|---|---|
| Who do I follow? | `follower_id` | Single-shard (Forward) | `<10ms` |
| Who follows me? (normal) | `following_id` | Single-shard (Reverse) | `10-30ms` |
| Who follows me? (VIP) | `following_id + bucket` | Scatter-gather N buckets | `50-100ms` |
| Is A following B? | `follower_id` | Point lookup (Forward) | `<5ms` |

```
Trade-off: 2x storage + write amplification for much lower read latency.
Rule: Disk is cheap, P99 latency is expensive.
```

---

### 3.2 TweetDB (PostgreSQL with Sharding) `[Owner: Tweet Service]`

```
Sharding Key: user_id (co-locate all tweets by same author)

Tables:
├─ tweets (tweet_id, user_id, content, created_at, is_deleted)
│   Index: (user_id, created_at DESC) → fast "user's recent tweets"
├─ tweet_engagement (tweet_id, likes_count, retweets_count, replies_count)
├─ media (media_id, tweet_id, type, url, created_at)
└─ tweet_metadata (tweet_id, language, sentiment_score, virality_score)

tweet_id generation: Snowflake ID (timestamp + machine_id + sequence)
  → Globally unique, time-sortable, no DB coordination needed

Why shard by user_id (not tweet_id)?
  → "Get user's recent tweets" is the hottest query (profile page, pull-based feed)
  → All tweets by one author on same shard = single-shard range scan
  → tweet_id lookups use a global secondary index (or cache)
```

---

### 3.3 NoSQL (Timelines & Engagement)

**Timeline Data (Cassandra)** `[Writer: Fanout Service | Reader: Feed Service]`

```
Purpose: pre-materialized home feed for each user (push model)

Table: home_timeline
  Partition Key: user_id
  Clustering Key: created_at DESC
  Columns: tweet_id, author_id

Write: fanout inserts tweet_id into each follower's partition
Read:  SELECT tweet_id, author_id FROM home_timeline
       WHERE user_id = ? LIMIT 200
TTL: 30 days (older tweets archived to cold storage)
```

**📝 Can NoSQL (Cassandra) Tables Have CDC Events?**

```
Yes — Cassandra supports CDC (Change Data Capture) natively since v3.8+.

How it works:
├─ Enable per table: ALTER TABLE home_timeline WITH cdc = true;
├─ Cassandra writes mutations to a CDC commit log (separate from normal commit log)
├─ An external agent (e.g., Debezium for Cassandra, or custom reader) tails the CDC log
├─ Events are published to Kafka topics (e.g., cassandra.home_timeline.mutations)
└─ Downstream consumers process the change stream

CDC log structure (per node):
├─ Location: /var/lib/cassandra/cdc_raw/
├─ Format: commit log segments (binary, same as normal WAL)
├─ Each segment: contains INSERT/UPDATE/DELETE mutations with full row data
└─ Cleanup: consumer must delete processed segments (or they fill up disk!)

Use cases in this design:
├─ 1. Timeline analytics: CDC from home_timeline → Kafka → analytics pipeline
│      "How many timeline inserts/sec? Which users have stale feeds?"
├─ 2. Audit trail: capture all timeline mutations for compliance/debugging
├─ 3. Cross-DC sync: if not using Cassandra's native multi-DC replication
├─ 4. Feed invalidation: CDC detects new timeline write → push WebSocket
│      notification to user's open app: "New tweets available, tap to refresh"
└─ 5. Cold storage archival: CDC streams expired (TTL'd) rows to S3/HDFS
       before Cassandra compaction deletes them

Tool options:
├─ Debezium Cassandra Connector (recommended — same tool used for PG CDC)
│   → Reads CDC commit logs → publishes to Kafka
│   → Exactly-once semantics with Kafka Connect offsets
├─ DataStax CDC for Apache Kafka (commercial, more polished)
│   → Directly streams from Cassandra commit log to Kafka
└─ Custom agent: read /cdc_raw/ directory, parse commit log segments
   → More work, but full control over format and filtering

Important caveats:
├─ CDC adds ~10-15% write overhead (double commit log writes)
├─ Must monitor /cdc_raw/ disk usage — if consumer falls behind, disk fills up
│   → Cassandra will STOP accepting writes if cdc_raw exceeds cdc_total_space
├─ CDC captures mutations, NOT query results (you get raw cell-level changes)
├─ Deletes and TTL expirations appear as tombstones in the CDC stream
└─ At X's scale (500M+ timeline writes/day), CDC volume is massive
   → Filter: only enable CDC on tables where you NEED the change stream
   → Don't enable on home_timeline unless you have a clear use case

Comparison with PostgreSQL CDC:
┌─────────────────────┬──────────────────────────┬──────────────────────────┐
│                     │ PostgreSQL (WAL / Debezium)│ Cassandra (CDC log)      │
├─────────────────────┼──────────────────────────┼──────────────────────────┤
│ Mechanism           │ Logical replication slot  │ CDC commit log segments  │
│ Tool                │ Debezium PG connector     │ Debezium Cassandra conn. │
│ Overhead            │ ~5%                       │ ~10-15%                  │
│ Ordering            │ Global WAL order (strict) │ Per-partition only       │
│ Cleanup             │ Automatic (slot advances) │ Manual (consumer deletes)│
│ Risk if consumer    │ WAL retention grows       │ Disk fills → writes stop │
│   falls behind      │ (pg_wal fills up)         │ (cdc_total_space limit)  │
│ Schema changes      │ Captured in WAL           │ NOT captured in CDC log  │
│ Used in this design │ follows_outbox (§3.1.2)   │ Optional (see above)     │
└─────────────────────┴──────────────────────────┴──────────────────────────┘

Recommendation for this design:
├─ PostgreSQL CDC (via Debezium): ✅ Already used for follows_outbox (§3.1.2)
├─ Cassandra CDC: ⚠️ Enable selectively — NOT on home_timeline by default
│   → The fanout write path already publishes to Kafka (tweets.created topic)
│   → CDC on home_timeline would be redundant and expensive
│   → Good candidate: enable CDC on engagement tables if they move to Cassandra
└─ Redis: ❌ No native CDC. Use Redis Streams or Keyspace Notifications instead.
```

**Engagement Data (Redis)** `[Writer: Tweet Service / Graph Service | Reader: Feed Service / User Service]`

```
Keys:
├─ tweet:{id}:likes       → Set (who liked)
├─ tweet:{id}:retweets    → Set (who retweeted)
├─ user:{id}:feed_items   → Sorted Set (score = timestamp)
├─ trending:{region}:hourly → Sorted Set (score = trend count)
└─ follower:count:{id}    → String (INCR/DECR)
```

---

## 4. Tweet Write Path (End-to-End) `[Owner: Tweet Service → Kafka → 5 async consumers]`

```
POST /tweet { content: "Hello world", media: [...] }
    │
    ├─ 1. Validate (280 chars, sanitize, extract #hashtags @mentions)
    │
    ├─ 2. Write to TweetDB (PostgreSQL)
    │     INSERT INTO tweets (tweet_id, user_id, content, created_at)
    │     Shard: hash(user_id) → single shard write
    │
    ├─ 3. Publish to Kafka: topic = tweets.created
    │     Payload: { tweet_id, user_id, content, created_at }
    │
    ├─ 4. Return 202 Accepted { tweet_id, status: "processing" }
    │
    └─ Kafka consumers (async, parallel):
        │
        ├─ A. Fanout Service        → push tweet to followers' Cassandra timelines (§5)
        ├─ B. Search Indexer        → index in Elasticsearch [Search Service] (§7)
        ├─ C. Trending Service      → ZINCRBY trending:{region}:hourly in Redis
        ├─ D. Notification Service  → notify @mentioned users via WebSocket/Push
        └─ E. Media Service         → resize images, transcode video, upload to CDN
```

---

## 5. Fanout: How Celebrity Tweet Reaches All Followers `[Owner: Fanout Service]`

**Hybrid Push-Pull Model**

```
Decision: based on author's follower count

Regular user (followers < 100K): PUSH [Fanout Service]
├─ Fanout Service reads follows_reverse shard [via Graph Service API]
│   → gets all follower_ids for this author
├─ For each follower: INSERT INTO home_timeline (user_id=follower, tweet_id, author_id)
├─ Writes to Cassandra in batches (1000 rows/batch) [Fanout Service → Cassandra]
├─ Followers see tweet within 1-2 seconds
└─ Write amplification: 100K Cassandra inserts per tweet (acceptable)

Celebrity (followers >= 100K): PULL [Fanout Service skips, Feed Service pulls later]
├─ Fanout Service does NOT write to 150M timelines
├─ Instead: tweet is cached in Redis [Fanout Service → Redis]
│   SET tweet:cache:{tweet_id} → { content, author_id, created_at }
│   EXPIRE tweet:cache:{tweet_id} 604800 (7 days)
├─ When follower opens feed → Feed Service pulls celebrity tweets on-demand
└─ Write amplification: 0 (no fanout write)
```

**Why hybrid?**

```
If @elonmusk tweets and we PUSH to 150M timelines:
  150M Cassandra inserts × 500 bytes = 75 GB written for ONE tweet
  At 10 tweets/day = 750 GB/day just for one user
  → Unacceptable write amplification

PULL trades slight read delay (100-500ms) for massive write savings.
Most followers don't open the app immediately anyway.
```

---

## 6. Home Feed: How Follower Sees the Tweet `[Owner: Feed Service]`

```
GET /feed?limit=200&cursor=...
    │
    ├─ 1. Check Redis cache: feed:{user_id}:v2  [Feed Service → Redis]
    │     Hit → return immediately (<5ms)
    │
    ├─ 2. Cache miss → build feed:
    │     │
    │     ├─ a) PUSH tweets: read from Cassandra home_timeline  [Feed Service → Cassandra]
    │     │     SELECT tweet_id, author_id FROM home_timeline
    │     │     WHERE user_id = ? LIMIT 600  (fetch extra for filtering)
    │     │
    │     ├─ b) PULL tweets: get celebrity authors this user follows  [Feed Service → Graph Service API → Redis/TweetDB]
    │     │     following_list = Redis ZRANGE following:list:{user_id} 0 -1
    │     │     For each celebrity author:
    │     │       → query TweetDB: recent tweets WHERE user_id = author
    │     │       → or read from tweet:cache:{tweet_id} in Redis
    │     │
    │     ├─ c) Merge PUSH + PULL candidates into one list  [Feed Service]
    │     │
    │     ├─ d) Rank (ML scoring)  [Feed Service → Ranking Service (internal)]
    │     │     score = w1*recency + w2*engagement + w3*author_strength
    │     │     + w4*user_affinity (how often viewer engages with author)
    │     │
    │     ├─ e) Filter (mute list, block list, quality filters)  [Feed Service → User Service for lists]
    │     │
    │     └─ f) Cache result in Redis (TTL 5 min)  [Feed Service → Redis]
    │
    └─ 3. Return top 200 tweets + next_cursor
```

**Response format:**

```json
{
  "tweets": [
    {
      "id": "123456",
      "author": { "id": "1", "handle": "@elonmusk", "verified": true },
      "content": "Hello world",
      "created_at": "2026-04-14T10:30:00Z",
      "engagement": { "likes": 1000000, "retweets": 500000 }
    }
  ],
  "next_cursor": "eyJ0cyI6MTcxMjAwMDAwMH0="
}
```

**Cursor pagination (not offset):**

```
Why cursor over offset?
  OFFSET 4000 LIMIT 200 → scans first 4000 rows (slow)
  WHERE created_at < cursor_timestamp LIMIT 200 → index range scan (fast, constant time)
```

---

## 7. Elasticsearch: Where & How It's Used `[Owner: Search Service]`

**What goes into ES:**

```
Index: tweets
Document: { tweet_id, user_id, content, hashtags[], mentions[], created_at, language }

Index: users
Document: { user_id, handle, name, bio, verified, followers_count }
```

**How data gets there:** `[Search Service — Kafka consumer]`

```
Tweet created → Kafka topic: tweets.created
  → Search Indexer consumer reads event  [Search Service consumer]
  → Transforms to ES document
  → Bulk index into Elasticsearch (batched, every 2-5 seconds)

Latency: tweet is searchable within 2-5 seconds of creation
```

**Where ES is used in the system:**

```
1. Tweet Search  [Search Service]
   GET /search?q=hello+world&limit=50
   → ES query: match content "hello world" + boost by recency + engagement
   → Returns tweet_ids → hydrate from TweetDB cache/SQL [Tweet Service]

2. User Search  [Search Service]
   GET /search/users?q=vishal
   → ES query: match handle/name "vishal" + boost by followers_count
   → Returns user_ids → hydrate from UserDB cache [User Service]

3. Hashtag Search  [Search Service]
   GET /search?q=%23systemdesign
   → ES query: term filter on hashtags[] field
   → Fast because hashtags are keyword type (exact match, no analysis)

4. Trending Service (input)  [Trending Service]
   → Trending Service queries ES for tweet count by hashtag in last 1 hour
   → Or: Trending Service consumes Kafka directly (faster, preferred)

5. Autocomplete / Typeahead  [Search Service]
   → ES completion suggester on user handles and trending hashtags
   → Latency: <50ms
```

**ES is NOT used for:**

```
├─ Home feed (Cassandra + Redis, not search)
├─ Follower lists (Redis + PostgreSQL)
├─ Engagement counts (Redis)
└─ Notifications (SQL + Kafka)

ES = search & discovery only. Not the primary read path for feeds.
```

---

## 8. End-to-End Flow: Celebrity Tweets → Follower Sees It `[All services in action]`

```
Example: @elonmusk (user_id=1, 150M followers) tweets "SpaceX update"
         @vishal (user_id=99) follows @elonmusk

Step-by-step:

T+0ms     [Tweet Service] Elon hits POST /tweet { content: "SpaceX update" }
T+5ms     [Tweet Service] API validates, writes to TweetDB shard (hash(1) → shard_03)
T+10ms    [Tweet Service] Kafka event published: tweets.created { tweet_id=555, user_id=1 }
T+15ms    [Tweet Service] Response: 202 Accepted { tweet_id: 555 }

T+50ms    [Fanout Service] reads event
          → Checks: user_id=1 has 150M followers → PULL model (skip fanout)
          → Caches tweet in Redis: SET tweet:cache:555 { ... } EX 604800

T+100ms   [Search Service] reads same Kafka event
          → Indexes tweet in Elasticsearch (bulk, batched)

T+200ms   [Trending Service] reads event
          → Extracts "SpaceX" → ZINCRBY trending:global:hourly in Redis

T+500ms   [Notification Service]
          → Checks @mentions in content → none here → skip
          → (If @vishal was mentioned: push notification via WebSocket)

--- Later: Vishal opens X app ---

T+30min   Vishal opens app → GET /feed?limit=200

          [Feed Service]:
          1. Cache miss → build feed
          2. PUSH tweets: read Vishal's home_timeline from Cassandra
             → gets tweets from regular users Vishal follows
          3. PULL tweets: Vishal follows @elonmusk (celebrity)
             → query TweetDB or Redis cache for Elon's recent tweets
             → finds tweet_id=555 "SpaceX update"
          4. Merge PUSH + PULL candidates
          5. ML ranking: tweet 555 scores high (engagement + author strength)
          6. Filter: not muted, not blocked  [calls User Service for mute/block lists]
          7. Cache result in Redis (5 min TTL)
          8. Return feed → Vishal sees "SpaceX update" in his timeline ✅

Total write cost for this tweet:
  [Tweet Service] 1 SQL insert + 1 Kafka event
  [Fanout Service] 1 Redis cache
  [Search Service] 1 ES index
  = 4 writes total. NOT 150M Cassandra inserts ✅
```

---

## 9. Data Consistency Trade-offs

| Data | Storage | Consistency | Owner Service | Why |
|---|---|---|---|---|
| User Profiles | SQL | Strong | User Service | Source of truth, rare changes |
| Tweet Content | SQL | Strong | Tweet Service | Immutable, legal importance |
| Engagement (likes) | Redis + SQL | Eventual | Tweet Service | Can lag seconds, acceptable |
| Home Timeline | Cassandra | Eventual | Fanout Service (write) / Feed Service (read) | Fanout lag acceptable |
| Follower List | Redis cache | Eventual | Graph Service | Derived from SQL, stale by minutes OK |
| Trending | Redis | Eventual | Trending Service | Aggregate, stale by minutes OK |
| Search Index | Elasticsearch | Eventual | Search Service | 2-5 second indexing delay OK |

### 9.1 Why SQL (PostgreSQL) for User Profiles & Tweet Content?

```
Every storage choice is a trade-off. Here's why SQL wins for these two — and why NoSQL loses.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 USER PROFILES — Why SQL?
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1) ACID Transactions — profile updates must be all-or-nothing
   ├─ User changes handle + bio + profile pic in one request
   │   → Must either ALL succeed or ALL rollback
   │   → Cassandra has no multi-row transactions (only lightweight transactions per partition)
   │   → Redis has no durable transactions at all
   └─ Example: handle uniqueness check + update must be atomic
      BEGIN;
        SELECT 1 FROM users WHERE handle = '@newname' FOR UPDATE;  -- lock
        UPDATE users SET handle = '@newname' WHERE user_id = 101;
      COMMIT;
      → If two users try to claim '@newname' simultaneously, SQL guarantees only one wins
      → Cassandra LWT (IF NOT EXISTS) works for single row but is 4× slower and not composable

2) Strong Consistency — "read your own write" guarantee
   ├─ User updates bio → refreshes profile → MUST see new bio immediately
   │   → SQL: read from primary → always latest data ✅
   │   → Cassandra (QUORUM read): usually consistent, but during node failures
   │     or network partitions, can return stale data ❌
   │   → Redis: if primary fails before replicating, update is lost ❌
   └─ For auth/login data (password hash, email, 2FA): eventual consistency is dangerous
      → User changes password → attacker could still log in with old password
        during replication lag window → unacceptable

3) Complex Queries & JOINs
   ├─ "Get user profile + follower count + recent tweet count" = multi-table JOIN
   │   SELECT u.*, us.followers_count, us.tweets_count
   │   FROM users u JOIN user_stats us ON u.user_id = us.user_id
   │   WHERE u.user_id = 101;
   │   → SQL: single query, optimized by planner ✅
   │   → Cassandra: no JOINs — requires 2 separate queries + app-side merge ❌
   │   → At 12K QPS for profile views, doubling queries = doubling load
   └─ Admin queries: "find all verified users in Bengaluru" → SQL WHERE clause trivial
      → Cassandra requires exact partition key — can't do ad-hoc filters without ALLOW FILTERING (full scan)

4) Schema Enforcement & Data Integrity
   ├─ UNIQUE constraint on handle → no two users can have same @handle
   │   → Cassandra: no UNIQUE constraint (must enforce in app code, race-prone)
   ├─ FOREIGN KEY: user_profiles.user_id REFERENCES users.user_id
   │   → Guarantees no orphan profile rows
   │   → Cassandra: no foreign keys, orphaned data is your problem
   ├─ CHECK constraints: followers_count >= 0, handle ~ '^@[a-zA-Z0-9_]+$'
   │   → SQL enforces at DB level — even if app has a bug, invalid data can't enter
   │   → Cassandra/Redis: all validation is app-side only
   └─ NOT NULL, DEFAULT values, GENERATED columns — all SQL features that prevent bad data

5) Mature Ecosystem for User Data
   ├─ Patroni: battle-tested HA with auto-failover, 0 data loss (sync replica)
   ├─ pg_dump / pg_basebackup / PITR: robust backup & point-in-time recovery
   ├─ Debezium CDC: proven for outbox pattern (already used in §3.1.2)
   ├─ Row-Level Security (RLS): GDPR compliance — restrict who can query PII
   └─ 30+ years of production hardening — user data is too critical for newer stores

Why NOT Cassandra for User Profiles?
├─ No ACID → can't safely do handle uniqueness + profile update atomically
├─ No JOINs → profile page requires multiple round trips
├─ Cassandra excels at: high-throughput append-only writes (timelines, logs)
├─ User profiles are: low-write, high-read, relational, consistency-critical
└─ Wrong tool for this job

Why NOT Redis for User Profiles?
├─ Volatile — data loss on failure (even with AOF, ~1 sec window)
├─ No schema enforcement — any app bug can corrupt user data silently
├─ Memory-only at 500M users × ~2KB/user = 1 TB RAM just for profiles → expensive
├─ Redis is a CACHE layer in front of SQL, not the source of truth
└─ We DO cache hot profiles in Redis (TTL 1 hour) for read performance

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 TWEET CONTENT — Why SQL?
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1) Legal & Compliance Requirements
   ├─ Tweets are legal records — courts can subpoena tweet content with metadata
   │   → Need PITR (Point-in-Time Recovery): "show me exactly what was stored at 2026-04-14 10:30:00"
   │   → PostgreSQL PITR: restore to ANY second in last 7 days from WAL archive ✅
   │   → Cassandra: no PITR — snapshots are periodic (hourly), can't restore to arbitrary second ❌
   ├─ GDPR "Right to Erasure": user requests deletion of all their tweets
   │   → SQL: DELETE FROM tweets WHERE user_id = 101; (+ audit log in same transaction)
   │   → Cassandra: tombstones + compaction = data lingers on disk for gc_grace_seconds (10 days default)
   │     → Harder to prove "data is actually deleted" for compliance audits
   └─ Audit trail: SQL triggers or CDC can capture every mutation with before/after values
      → Cassandra CDC captures mutations but not "before" state (no read-before-write)

2) Immutability + Consistency Guarantees
   ├─ Once a tweet is created, content never changes (edits create new version rows)
   │   → SQL: INSERT is atomic, immediately readable by any connection on primary
   │   → Response to user: 202 Accepted — tweet_id is guaranteed to exist in DB
   │   → Cassandra: write at QUORUM is "probably consistent" but not guaranteed
   │     during network partitions (hinted handoff can delay visibility)
   └─ Tweet deletion: soft delete (is_deleted = true) must be strongly consistent
      → If user deletes tweet but follower still sees it due to replication lag → legal risk
      → SQL sync replica: delete is visible everywhere within 0 seconds
      → Cassandra QUORUM: delete might not be visible on all replicas for seconds

3) Relational Integrity with Engagement Data
   ├─ tweet_engagement (likes, retweets) references tweet_id
   │   → SQL FK ensures you can't have engagement rows for non-existent tweets
   │   → On tweet deletion: CASCADE or SET NULL handles cleanup automatically
   │   → Cassandra: orphaned engagement rows accumulate silently
   ├─ media table references tweet_id
   │   → SQL: media rows always point to valid tweets
   └─ tweet_metadata (language, sentiment) → same FK guarantee

4) Sharding by user_id Co-locates Author's Tweets
   ├─ "Get @vishal's last 20 tweets" = single-shard range scan
   │   → Index: (user_id, created_at DESC) → no cross-shard scatter
   │   → This is the #1 query for profile pages (12K QPS)
   ├─ Cassandra COULD do this too (partition by user_id, cluster by created_at)
   │   → But we'd lose ACID, JOINs, FK, PITR — all critical for tweet data
   └─ SQL sharding gives us BOTH: partition locality + relational guarantees

5) Write Volume is Manageable for SQL
   ├─ 1.5B tweets/day = ~17K inserts/sec avg, ~50K/sec peak
   │   → Across 16 shards = ~3K inserts/sec per shard (peak)
   │   → PostgreSQL handles 10K+ inserts/sec per node easily ✅
   │   → No need for Cassandra's extreme write throughput here
   ├─ Compare with engagement: 5B likes/day = 200K/sec peak
   │   → THIS is why engagement uses Redis (write-heavy) + SQL (async persist)
   │   → Tweet content writes are 25× less than engagement writes
   └─ Rule: use NoSQL when SQL can't handle the write volume
      → Tweet content: SQL handles it fine
      → Engagement: SQL alone can't → Redis as write buffer + async SQL persist

Why NOT Cassandra for Tweet Content?
├─ Overkill write throughput — SQL handles 17K/sec across 16 shards easily
├─ No PITR — legal compliance requires point-in-time recovery
├─ No ACID — soft delete + audit log must be atomic
├─ No FKs — engagement/media orphans accumulate
├─ Cassandra is used WHERE IT FITS: home_timeline (append-heavy, TTL, no JOINs needed)
└─ Tweet content doesn't match Cassandra's sweet spot

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 DECISION MATRIX SUMMARY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌──────────────────┬────────┬───────────┬───────┬────────────────────────────────────┐
│ Requirement      │ SQL(PG)│ Cassandra │ Redis │ Winner for User Profiles / Tweets  │
├──────────────────┼────────┼───────────┼───────┼────────────────────────────────────┤
│ ACID Transactions│  ✅    │  ❌ (LWT) │  ❌   │ SQL — atomic profile/tweet ops     │
│ Strong Consistency│ ✅    │  ⚠️ (QUORUM)│ ❌  │ SQL — read-your-write guaranteed   │
│ JOINs            │  ✅    │  ❌       │  ❌   │ SQL — profile + stats in 1 query   │
│ UNIQUE/FK/CHECK  │  ✅    │  ❌       │  ❌   │ SQL — schema-level data integrity  │
│ PITR             │  ✅    │  ❌       │  ❌   │ SQL — legal compliance             │
│ Write throughput │  ⚠️    │  ✅       │  ✅   │ SQL is enough (17K/sec sharded)    │
│ Read latency     │  ⚠️    │  ✅       │  ✅   │ SQL + Redis cache = <5ms           │
│ TTL auto-expire  │  ❌    │  ✅       │  ✅   │ Not needed for profiles/tweets     │
│ Append-only writes│ ⚠️   │  ✅       │  ✅   │ Not the pattern here               │
└──────────────────┴────────┴───────────┴───────┴────────────────────────────────────┘

Key insight: choose storage based on ACCESS PATTERN + CONSISTENCY NEED, not hype.
├─ User profiles: low-write, high-read, relational, consistency-critical → SQL ✅
├─ Tweet content: legal record, immutable, relational, manageable write volume → SQL ✅
├─ Home timeline: append-heavy, TTL, no JOINs, eventual OK → Cassandra ✅
├─ Engagement: extreme write volume, counters, eventual OK → Redis + async SQL ✅
└─ Search: full-text, relevance scoring → Elasticsearch ✅
```

---

## 10. Capacity Planning & Infrastructure

### 10.1 Traffic Estimation

```
Given: 500M DAU, 1.5B tweets/day

Writes:
├─ Tweets:       1.5B / 86400s = ~17K tweets/sec (avg), ~50K/sec (peak)
├─ Likes:        ~5B/day       = ~58K likes/sec (avg), ~200K/sec (peak)
├─ Retweets:     ~500M/day     = ~6K/sec
├─ Follows:      ~50M/day      = ~600/sec
└─ Total writes: ~80K/sec avg, ~250K/sec peak

Reads:
├─ Feed reads:   500M DAU × 10 opens/day = 5B/day = ~58K QPS avg, ~150K QPS peak
├─ Profile views: ~1B/day     = ~12K QPS
├─ Search:       ~500M/day    = ~6K QPS
├─ Trending:     ~200M/day    = ~2K QPS
└─ Total reads:  ~78K QPS avg, ~200K QPS peak

Read:Write ratio ≈ 1000:1 (heavily read-dominant)
```

### 10.2 Storage Estimation

```
Per-day new data:
├─ Tweets:          1.5B × 500 bytes avg (content + metadata)  = 750 GB/day
├─ Engagement rows: 5B likes × 50 bytes (user_id, tweet_id)    = 250 GB/day
├─ Follows:         50M × 50 bytes                              = 2.5 GB/day
├─ Media:           20% tweets have image, 2MB avg              = 600 TB/day (CDN/S3, not DB)
├─ Timeline rows:   fanout writes (push model only)             = ~500 GB/day
└─ Search index:    1.5B docs × 200 bytes                      = 300 GB/day

Annual growth (DB only, excluding media):
├─ Raw:          ~1.8 TB/day × 365 = ~650 TB/year
├─ Replication:  × 3 replicas      = ~2 PB/year
├─ With indexes: + 30%             = ~2.6 PB/year

Media (S3/CDN):
├─ ~600 TB/day × 365 = ~220 PB/year
├─ Tiered storage: hot (30 days SSD) + cold (S3 Glacier)
└─ 30-day hot: 600 TB × 30 = 18 PB on CDN edge
```

### 10.3 Cluster Sizing Per Service

```
┌────────────────────┬──────────────────┬─────────┬─────────┬──────────────────────────────────┐
│ Component          │ Cluster Size     │ CPU     │ Memory  │ Storage                          │
├────────────────────┼──────────────────┼─────────┼─────────┼──────────────────────────────────┤
│ PostgreSQL         │ 16 shards        │ 32 core │ 256 GB  │ 4 TB SSD per shard               │
│ (UserDB)           │ × 3 replicas     │ per node│ per node│ = 192 TB total (16×3×4)          │
│ [User Service]     │ = 48 nodes       │         │         │                                  │
├────────────────────┼──────────────────┼─────────┼─────────┼──────────────────────────────────┤
│ PostgreSQL         │ 16 shards        │ 32 core │ 128 GB  │ 8 TB SSD per shard               │
│ (TweetDB)          │ × 3 replicas     │ per node│ per node│ = 384 TB total (16×3×8)          │
│ [Tweet Service]    │ = 48 nodes       │         │         │                                  │
├────────────────────┼──────────────────┼─────────┼─────────┼──────────────────────────────────┤
│ PostgreSQL         │ 16 shards fwd    │ 16 core │ 64 GB   │ 2 TB SSD per shard               │
│ (FollowsDB)       │ + 16 shards rev  │ per node│ per node│ = 192 TB total (32×3×2)          │
│ [Graph Service]    │ × 3 replicas     │         │         │ (dual sharding = 2× storage)     │
│                    │ = 96 nodes       │         │         │                                  │
├────────────────────┼──────────────────┼─────────┼─────────┼──────────────────────────────────┤
│ Cassandra          │ 3 DCs            │ 16 core │ 64 GB   │ 4 TB SSD per node                │
│ (Timelines)        │ × 12 nodes/DC    │ per node│ per node│ = 144 TB total                   │
│ [Fanout/Feed]      │ = 36 nodes       │         │         │ RF=3, 30-day TTL auto-compacts   │
├────────────────────┼──────────────────┼─────────┼─────────┼──────────────────────────────────┤
│ Redis Cluster      │ 20 masters       │ 8 core  │ 128 GB  │ In-memory only                   │
│ (Cache + Hot data) │ + 20 replicas    │ per node│ per node│ = 2.5 TB usable RAM              │
│ [All services]     │ = 40 nodes       │         │         │ (hot followers, counters, feed    │
│                    │                  │         │         │  cache, engagement, trending)     │
├────────────────────┼──────────────────┼─────────┼─────────┼──────────────────────────────────┤
│ Elasticsearch      │ 3 master + 30    │ 16 core │ 64 GB   │ 2 TB SSD per data node           │
│ (Search index)     │ data + 5 coord   │ per node│ per node│ = 60 TB total                    │
│ [Search Service]   │ = 38 nodes       │         │         │ 30-day index, older rolled off    │
├────────────────────┼──────────────────┼─────────┼─────────┼──────────────────────────────────┤
│ Kafka              │ 12 brokers       │ 16 core │ 64 GB   │ 2 TB SSD per broker              │
│ (Event bus)        │ × 3 DCs          │ per node│ per node│ = 72 TB total                    │
│ [All services]     │ = 36 brokers     │         │         │ RF=3, 7-day retention            │
├────────────────────┼──────────────────┼─────────┼─────────┼──────────────────────────────────┤
│ App Servers        │ Per service:     │ 8 core  │ 16 GB   │ Stateless (no local storage)     │
│ (Microservices)    │ Feed: 50 pods    │ per pod │ per pod │ Auto-scale on CPU/QPS            │
│                    │ Tweet: 30 pods   │         │         │                                  │
│                    │ Fanout: 40 pods  │         │         │                                  │
│                    │ Search: 20 pods  │         │         │                                  │
│                    │ Graph: 20 pods   │         │         │                                  │
│                    │ User: 20 pods    │         │         │                                  │
│                    │ Trending: 10 pods│         │         │                                  │
│                    │ Notif: 20 pods   │         │         │                                  │
│                    │ = ~230 pods      │         │         │                                  │
└────────────────────┴──────────────────┴─────────┴─────────┴──────────────────────────────────┘

Total node count: ~570 nodes (DB + cache + search + streaming + app)
```

### 10.4 Redis Memory Breakdown

```
Key                             | Count        | Size/key   | Total RAM
────────────────────────────────┼──────────────┼────────────┼──────────
follower:hot:{id}   (ZSET)     | 50K VIPs     | ~800 KB    | 40 GB
follower:sample:{id} (SET)     | 50K VIPs     | ~400 KB    | 20 GB
follower:count:{id} (STRING)   | 500M users   | ~50 bytes  | 25 GB
following:list:{id} (ZSET)     | 500M users   | ~200 bytes | 100 GB
tweet:cache:{id} (STRING)      | 50M hot      | ~1 KB      | 50 GB
tweet:{id}:likes (SET)         | 10M active   | ~10 KB     | 100 GB
feed:{id}:v2 (STRING)          | 50M cached   | ~5 KB      | 250 GB
trending:{region}:hourly (ZSET)| 500 regions  | ~50 KB     | 25 MB
engagement:count:{id} (STRING) | 100M users   | ~50 bytes  | 5 GB
────────────────────────────────┼──────────────┼────────────┼──────────
Total                           |              |            | ~590 GB
+ overhead (fragmentation, OS)  |              |            | ~750 GB
Cluster capacity (20 × 128GB)  |              |            | 2.5 TB
Utilization                     |              |            | ~30% ✅
```

### 10.5 Kafka Throughput Sizing

```
Topics and throughput:

Topic               | Producers           | Consumers                        | Msgs/sec | Partitions
────────────────────┼─────────────────────┼──────────────────────────────────┼──────────┼───────────
tweets.created      | Tweet Service       | Fanout, Search, Trending, Notif  | 50K peak | 64
engagement.liked    | Tweet Service       | Notification, Analytics          | 200K peak| 128
engagement.retweeted| Tweet Service       | Notification, Analytics          | 20K peak | 32
follows.created     | Graph Service       | Graph consumer (reverse shard)   | 1K peak  | 16
follows.deleted     | Graph Service       | Graph consumer (reverse shard)   | 500 peak | 16

Broker sizing:
├─ 12 brokers handle ~300K msgs/sec aggregate (peak)
├─ Each broker: ~25K msgs/sec → well within single-broker capacity (~200K)
├─ Replication factor 3 → 3× network writes
├─ Retention: 7 days → 2 TB/broker sufficient
└─ Headroom: ~40% utilization at peak ✅
```

### 10.6 Cluster Topology (Multi-DC)

```
3 Data Centers (US-East, US-West, EU-West):

┌──────────────────────────────────────────────────────────┐
│                      US-EAST (Primary)                    │
│                                                          │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐   │
│  │ PG-User │  │PG-Tweet │  │PG-Follow│  │ PG-User │   │
│  │ Shard   │  │ Shard   │  │ Fwd+Rev │  │ Shard   │   │
│  │ 0-5     │  │ 0-5     │  │ 0-10    │  │ 6-10    │   │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘   │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Cassandra    │  │ Redis Cluster│  │ Elasticsearch│  │
│  │ 12 nodes     │  │ 14 nodes     │  │ 13 nodes     │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                          │
│  ┌──────────────┐  ┌──────────────────────────────────┐ │
│  │ Kafka 12     │  │ App Pods: ~80 (Feed, Tweet,     │ │
│  │ brokers      │  │ Fanout, Graph, Search, User,    │ │
│  │              │  │ Trending, Notification)          │ │
│  └──────────────┘  └──────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
            │ async replication │
            ▼                  ▼
┌────────────────────┐  ┌────────────────────┐
│    US-WEST         │  │    EU-WEST         │
│  (read replica)    │  │  (read replica)    │
│                    │  │                    │
│  PG replicas       │  │  PG replicas       │
│  Cassandra 12      │  │  Cassandra 12      │
│  Redis 13 nodes    │  │  Redis 13 nodes    │
│  ES 13 nodes       │  │  ES 12 nodes       │
│  Kafka 12 brokers  │  │  Kafka 12 brokers  │
│  App pods ~75      │  │  App pods ~75      │
└────────────────────┘  └────────────────────┘

Routing:
├─ Writes: routed to US-East (primary) via global LB
├─ Reads:  served from nearest DC (latency-based DNS)
├─ Failover: if US-East down → US-West promotes to primary (<5 min)
└─ Async replication lag between DCs: <1 second (normal), <5 second (peak)
```

### 10.7 Cost Estimate (Monthly)

```
Component            | Nodes | Instance type    | $/node/mo | Total $/mo
─────────────────────┼───────┼──────────────────┼───────────┼───────────
PostgreSQL (all DBs) | 192   | r6g.8xlarge      | $2,500    | $480,000
Cassandra            | 36    | i3.4xlarge       | $1,800    | $64,800
Redis                | 40    | r6g.4xlarge      | $1,200    | $48,000
Elasticsearch        | 38    | r5.4xlarge       | $1,400    | $53,200
Kafka                | 36    | m6g.4xlarge      | $900      | $32,400
App Servers (pods)   | 230   | m6g.2xlarge      | $400      | $92,000
Load Balancers       | 6     | ALB/NLB          | $2,000    | $12,000
S3 / CDN (media)     | —     | S3 + CloudFront  | —         | $300,000
Networking (cross-DC)| —     | Data transfer    | —         | $80,000
Monitoring (Datadog) | —     | Per-host billing | —         | $30,000
─────────────────────┼───────┼──────────────────┼───────────┼───────────
Total                | ~570  |                  |           | ~$1.2M/mo

Annual: ~$14.4M (infrastructure only, excludes engineering salaries)

Cost optimization:
├─ Reserved instances (1-year): saves ~30% → ~$10M/year
├─ Spot instances for batch jobs (Trending, Analytics): saves ~60%
├─ Tiered storage (hot/cold) for media: saves ~40% on S3
└─ Right-sizing: monitor utilization quarterly, downsize idle nodes
```

### 10.8 Capacity Planning Summary Table

```
┌─────────────────┬────────────────────┬──────────────────────────────────┐
│ Metric          │ Current Need       │ Headroom / Scale Plan            │
├─────────────────┼────────────────────┼──────────────────────────────────┤
│ Total nodes     │ ~570               │ Add 20% per year with user growth│
│ Total CPU cores │ ~8,000             │ App pods auto-scale on demand    │
│ Total RAM       │ ~18 TB             │ Redis at 30% util (can absorb 3×)│
│ Total SSD       │ ~1 PB              │ Add shards when >70% disk usage  │
│ Network (inter) │ ~50 Gbps aggregate │ 100 Gbps links between DCs      │
│ Write IOPS      │ ~250K/sec peak     │ PG + Cass handle 500K/sec        │
│ Read QPS        │ ~200K peak         │ Redis + cache absorbs 80%        │
│ Kafka msgs/sec  │ ~300K peak         │ Brokers at 40% util              │
│ Cost/month      │ ~$1.2M             │ Optimize to ~$850K with reserved │
└─────────────────┴────────────────────┴──────────────────────────────────┘
```

---

## 11. Disaster Recovery & Fault Tolerance

### 11.1 RTO / RPO Targets Per Component

```
┌─────────────────────┬────────────┬────────────┬─────────────────────────────────────┐
│ Component           │ RTO        │ RPO        │ Strategy                            │
├─────────────────────┼────────────┼────────────┼─────────────────────────────────────┤
│ PostgreSQL (UserDB) │ 30 sec     │ 0 sec      │ Sync replica, auto-failover Patroni │
│ PostgreSQL (TweetDB)│ 30 sec     │ 0 sec      │ Sync replica, auto-failover Patroni │
│ PostgreSQL (Follows)│ 30 sec     │ 0 sec      │ Sync replica, auto-failover Patroni │
│ Cassandra           │ 0 (self)   │ <1 sec     │ RF=3, quorum reads/writes           │
│ Redis Cluster       │ 1-2 min    │ ~10 sec    │ Replica promotion, AOF persistence  │
│ Elasticsearch       │ 5 min      │ 2-5 sec    │ Replica shards, re-index from Kafka │
│ Kafka               │ 0 (self)   │ 0 sec      │ RF=3, ISR-based leader election     │
│ App Servers (pods)  │ 15 sec     │ N/A        │ Stateless, K8s auto-restart/rescale │
│ Entire DC failover  │ <5 min     │ <1 min     │ DNS failover to secondary DC        │
└─────────────────────┴────────────┴────────────┴─────────────────────────────────────┘

RTO = Recovery Time Objective (max acceptable downtime)
RPO = Recovery Point Objective (max acceptable data loss)
```

### 11.2 Per-Component Failure Handling

**PostgreSQL (all DBs)** `[User Service / Tweet Service / Graph Service]`

```
Architecture per shard:
  Primary → Sync Replica (same DC) → Async Replica (other DC)

Single node failure:
├─ Detection: Patroni health check every 5 sec
├─ Failover: sync replica auto-promoted to primary
├─ RTO: ~30 sec (Patroni switchover)
├─ RPO: 0 (sync replication = no data loss)
└─ Client impact: ~5 sec of write errors, reads continue from replica

Shard corruption / disk failure:
├─ Patroni promotes sync replica
├─ Rebuild failed node from backup + WAL replay
├─ WAL archiving: continuous to S3 (every 60 sec)
├─ Point-in-time recovery (PITR): restore to any second in last 7 days
└─ Rebuild time: ~2-4 hours for full shard

Cross-DC replication:
├─ Async replica in secondary DC (lag <1 sec normally)
├─ On DC failover: async replica promoted (may lose <1 sec of writes)
└─ After failover: reconcile conflicts via last-write-wins or manual review
```

**Cassandra (Timelines)** `[Fanout Service / Feed Service]`

```
Architecture: 3 DCs × 12 nodes, RF=3

Single node failure:
├─ Cassandra is self-healing — no manual intervention
├─ Hinted handoff: other nodes store writes for failed node
├─ When node returns: replays missed writes automatically
├─ No downtime: quorum reads/writes continue (2 of 3 replicas)
└─ Impact: slightly higher latency during repair

Full rack / AZ failure:
├─ Rack-aware replication: replicas spread across racks
├─ 1 rack down: 2 replicas still available → quorum maintained
├─ No data loss, no downtime
└─ Repair runs automatically when rack recovers

Node replacement:
├─ Add new node → Cassandra streams data from peers
├─ ~4-6 hours for 4 TB node to rebuild
└─ Zero downtime during rebuild
```

**Redis Cluster** `[All Services — cache + hot data]`

```
Architecture: 20 masters + 20 replicas

Single master failure:
├─ Detection: Redis Sentinel / Cluster gossip (15 sec timeout)
├─ Failover: replica promoted to master automatically
├─ RTO: ~60-90 sec (election + promotion)
├─ RPO: ~10 sec (async replication lag to replica)
└─ Impact: cache misses during failover → DB queries spike briefly

Complete Redis cluster failure:
├─ THIS IS A CACHE — not source of truth
├─ App servers fall back to SQL/Cassandra (degraded but functional)
├─ Feed latency: 5ms → 200ms (acceptable, not an outage)
├─ Recovery: restart cluster + warm cache with batch job (~30 min)
└─ Lesson: never put critical data ONLY in Redis

Persistence:
├─ AOF (Append Only File): fsync every second → RPO ~1 sec
├─ RDB snapshots: every 15 min (backup to S3)
├─ On restart: replay AOF for fast recovery
└─ Hot followers / counters: can also be rebuilt from SQL if needed
```

**Elasticsearch** `[Search Service]`

```
Architecture: 30 data nodes, 1 replica per shard

Single data node failure:
├─ Replica shards auto-promoted to primary
├─ Cluster re-balances shards to healthy nodes
├─ RTO: ~0 (automatic), RPO: 0 (replica was in sync)
└─ Impact: search continues, slightly slower during rebalance

Full ES cluster failure:
├─ Search goes down — but this is NOT the home feed path
├─ Impact: /search returns 503, home feed / profile / notifications all work
├─ Recovery option A: rebuild from Kafka (re-consume tweets.created from offset 0)
│   → Time: ~6-12 hours for 30-day index
├─ Recovery option B: restore from S3 snapshot (taken every 6 hours)
│   → Time: ~2-4 hours
└─ During outage: disable search in UI, show "Search temporarily unavailable"
```

**Kafka** `[All Services — event bus]`

```
Architecture: 12 brokers per DC, RF=3, min.insync.replicas=2

Single broker failure:
├─ Leader partitions re-elected to other brokers (~10 sec)
├─ Producers/consumers reconnect automatically
├─ No data loss (min ISR=2, so 2 copies always exist)
└─ Impact: ~5 sec of increased latency, then normal

All brokers down (catastrophic):
├─ Events stop flowing → fanout, search, trending all stall
├─ Tweets still written to TweetDB (sync path doesn't need Kafka)
├─ Users can still read cached feeds (Redis)
├─ Recovery: restart brokers, consumers resume from last committed offset
├─ Kafka data on disk: 7-day retention, survives restart
└─ Worst case: replay from beginning of retention window
```

### 11.3 Entire Data Center Failure

```
Scenario: US-East DC goes offline (network, power, or fire)

Detection (automated):
├─ Global health checker pings all DCs every 5 sec
├─ If US-East fails 3 consecutive checks (15 sec) → trigger failover
└─ Alert: page on-call + auto-post to #incident Slack channel

Failover steps:

1) DNS failover (T+0 to T+60 sec)  [Infra / Route53]
   ├─ Route53 health check detects US-East down
   ├─ DNS TTL: 60 sec → traffic shifts to US-West within 60 sec
   └─ Reads: immediately served from US-West replicas

2) Write promotion (T+60 sec to T+3 min)  [DBA automation]
   ├─ PostgreSQL: promote async replicas in US-West to primary
   │   → May lose <1 sec of writes (async replication lag)
   ├─ Cassandra: already multi-master, no promotion needed
   ├─ Redis: US-West cluster takes over (independent cluster per DC)
   └─ Kafka: US-West brokers already have replicated topics

3) Verify (T+3 min to T+5 min)  [On-call]
   ├─ Run smoke tests: can create tweet? can read feed? can search?
   ├─ Check error rates on US-West dashboard
   └─ Confirm all Kafka consumers are processing

4) Communicate (T+5 min)
   └─ Status page: "We're experiencing issues. Service is degraded but operational."

Total RTO: <5 min for full DC failover
RPO: <1 min (async replication lag)
```

```
After DC recovers:

1) DO NOT immediately fail back — validate DC health first
2) Re-sync: replay missed writes from Kafka + WAL logs
3) Run data reconciliation jobs (compare row counts, checksums)
4) Gradual failback: route 5% traffic → 25% → 50% → 100% over 2 hours
5) Post-incident review within 24 hours
```

### 11.4 Backup Strategy

```
┌─────────────────────┬──────────────────────┬───────────────┬────────────────────────┐
│ Component           │ Backup Type          │ Frequency     │ Retention              │
├─────────────────────┼──────────────────────┼───────────────┼────────────────────────┤
│ PostgreSQL          │ WAL archiving to S3  │ Continuous    │ 7 days (PITR capable)  │
│                     │ Full pg_basebackup   │ Daily 2 AM   │ 30 days                │
├─────────────────────┼──────────────────────┼───────────────┼────────────────────────┤
│ Cassandra           │ Snapshot (nodetool)  │ Daily 3 AM   │ 7 days                 │
│                     │ Incremental backup   │ Hourly        │ 24 hours               │
├─────────────────────┼──────────────────────┼───────────────┼────────────────────────┤
│ Redis               │ RDB snapshot to S3   │ Every 15 min  │ 48 hours               │
│                     │ AOF persistence      │ Every second   │ On-disk (current only) │
├─────────────────────┼──────────────────────┼───────────────┼────────────────────────┤
│ Elasticsearch       │ Snapshot to S3       │ Every 6 hours │ 7 days                 │
│                     │ Can re-index from    │ N/A           │ Kafka 7-day retention  │
│                     │ Kafka if needed      │               │                        │
├─────────────────────┼──────────────────────┼───────────────┼────────────────────────┤
│ Kafka               │ Topic data on disk   │ Always (log)  │ 7 days retention       │
│                     │ MirrorMaker to DR DC │ Continuous    │ Same as source         │
├─────────────────────┼──────────────────────┼───────────────┼────────────────────────┤
│ S3 / Media          │ Cross-region repl.   │ Continuous    │ Indefinite             │
└─────────────────────┴──────────────────────┴───────────────┴────────────────────────┘

Backup validation:
├─ Weekly: restore random PG shard backup to test environment, verify data
├─ Monthly: full DR drill — simulate DC failure, measure actual RTO/RPO
└─ Quarterly: restore ES from S3 snapshot, verify search results match
```

### 11.5 Incident Response Runbook (3 AM playbook)

**Scenario 1: Feed Service P99 > 500ms**

```
Severity: HIGH — page on-call immediately

T+0 min  Triage:
├─ Check: recent deploy? → rollback immediately
├─ Check: Redis cluster healthy? → if down, feeds degrade to 200ms (SQL fallback)
├─ Check: Cassandra latency? → if high, enable circuit breaker on timeline reads
└─ Check: one user or all users? → if one, likely hot shard

T+2 min  Mitigate:
├─ Enable circuit breaker on Ranking Service → fallback to recency sort
├─ Increase feed cache TTL from 5 min → 15 min
├─ Reduce feed page size from 200 → 100 tweets
└─ If Cassandra slow: switch reads to a different DC

T+10 min Root cause & fix:
├─ Check slow query logs, Cassandra compaction status
├─ Check JVM heap / GC pauses on app pods
└─ Scale up pods if CPU > 80%
```

**Scenario 2: Kafka consumer lag > 100K messages**

```
Severity: MEDIUM — events are delayed (fanout, search, trending)

T+0 min  Triage:
├─ Which consumer group is lagging?
│   ├─ Fanout? → followers get delayed timeline updates
│   ├─ Search? → new tweets not searchable yet
│   └─ Trending? → trends are stale
├─ Is consumer crashed or just slow?

T+2 min  Mitigate:
├─ If crashed: restart consumer pods
├─ If slow: increase consumer parallelism (add pods + Kafka partitions)
├─ If DB bottleneck: scale read replicas for the target DB

T+5 min  DO NOT:
├─ Skip messages (data loss)
├─ Restart Kafka brokers (causes rebalance storm)
└─ Increase retention blindly (disk fills up)
```

**Scenario 3: Redis cluster partial failure (5 of 20 masters down)**

```
Severity: HIGH — cache degradation

T+0 min  Impact:
├─ 25% of cached data unavailable
├─ Hot follower lookups for some VIPs: fallback to SQL (50ms vs 1ms)
├─ Feed cache miss rate spikes: Cassandra/PG load increases

T+2 min  Mitigate:
├─ Redis replicas should auto-promote (check if promotion happened)
├─ If not: manually trigger CLUSTER FAILOVER on stuck replicas
├─ Enable in-process local cache (Caffeine) as L1 cache in app pods
├─ Increase rate limiting to protect downstream DBs from cache stampede

T+10 min Recovery:
├─ Replace failed nodes, let cluster rebalance slots
├─ Warm cache: run batch job to re-populate hot keys from SQL
└─ Monitor: cache hit rate should recover to >90% within 30 min
```

### 11.6 DR Summary

```
┌────────────────────────────┬──────────────────────────────────────────────────────┐
│ Failure                    │ What happens + recovery                              │
├────────────────────────────┼──────────────────────────────────────────────────────┤
│ 1 PG node dies             │ Patroni auto-promotes sync replica. 30 sec. 0 loss. │
│ 1 Cassandra node dies      │ Self-healing. Hinted handoff. Zero downtime.         │
│ 1 Redis master dies        │ Replica promoted. ~60 sec. ~10 sec data lag.         │
│ 1 ES data node dies        │ Replica shard promoted. Auto-rebalance. No downtime. │
│ 1 Kafka broker dies        │ Leader re-election. ~10 sec. No data loss (ISR=2).   │
│ Full Redis cluster down    │ App falls back to SQL. Feeds slower but work.        │
│ Full ES cluster down       │ Search unavailable. Feed/profile/notif still work.   │
│ Full Kafka down            │ Tweets still saved to DB. Async consumers stall.     │
│                            │ Feeds served from cache. Recover when Kafka returns.  │
│ Entire DC down             │ DNS failover <60 sec. Write promotion <3 min.        │
│                            │ RPO <1 min. Full service in <5 min on secondary DC.  │
│ Data corruption (human)    │ PG: PITR to any second in last 7 days.               │
│                            │ ES: re-index from Kafka 7-day retention.             │
│                            │ Cass: restore from hourly incremental backup.        │
│ Accidental mass delete     │ Soft deletes + audit log. Restore from backup.       │
│                            │ Detection: data validation job catches within 1 hour.│
└────────────────────────────┴──────────────────────────────────────────────────────┘

Key principle: No single failure takes down the system.
Every component has a degradation path — the system gets slower, never fully dead.
```
