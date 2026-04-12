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
    ├─── Feed Service
    ├─── Tweet Service
    ├─── User Service
    ├─── Trending Service
    ├─── Notification Service
    ├─── Search Service
    └─── Graph Service (Follows)
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

**UserDB (PostgreSQL with Sharding)**
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

### 3.1.1 Follower List Handling at Scale (Critical for VIP Users)

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
Tier 1: Sorted Set of Hot Followers (Redis) - For VIP accounts only (1M+ followers)
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

Tier 2: Set of Sample Followers (Redis) - For display/recommendations
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

Tier 3: Full Follower List (PostgreSQL) - Source of truth, used for pagination
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

**Implementation Strategy (Java / Spring + Redis)**

```java
@Service
public class HotFollowerService {

    private static final long VIP_THRESHOLD = 1_000_000L;
    private static final int MAX_HOT_FOLLOWERS = 10_000;
    private static final Duration HOT_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;

    public HotFollowerService(StringRedisTemplate redis, JdbcTemplate jdbc) {
        this.redis = redis;
        this.jdbc = jdbc;
    }

    public List<String> getFollowersForProfile(long userId, int limit, String cursor) {
        long followerCount = getFollowerCount(userId);
        boolean isVip = followerCount >= VIP_THRESHOLD;

        if (!isVip) {
            return loadFollowersFromSql(userId, limit, cursor);
        }

        // First page for VIP: use hot cache (highest scores first)
        if (cursor == null || cursor.isBlank()) {
            String hotKey = hotKey(userId);
            Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeWithScores(hotKey, 0, limit - 1);

            if (tuples != null && !tuples.isEmpty()) {
                return tuples.stream().map(ZSetOperations.TypedTuple::getValue).toList();
            }

            // Fallback to random sample if hot set is empty
            String sampleKey = sampleKey(userId);
            List<String> sample = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                String picked = redis.opsForSet().randomMember(sampleKey);
                if (picked != null) sample.add(picked);
            }
            return sample;
        }

        // Page 2+ uses SQL cursor pagination
        return loadFollowersFromSql(userId, limit, cursor);
    }

    public void onFollowerEngagement(long followingId, long followerId, double engagementScore) {
        if (getFollowerCount(followingId) < VIP_THRESHOLD) return;

        String key = hotKey(followingId);
        String member = String.valueOf(followerId);

        redis.opsForZSet().add(key, member, engagementScore);
        trimToTopK(key, MAX_HOT_FOLLOWERS);
        redis.expire(key, HOT_TTL);
    }

    public void onUnfollow(long followingId, long followerId) {
        redis.opsForZSet().remove(hotKey(followingId), String.valueOf(followerId));
        redis.opsForSet().remove(sampleKey(followingId), String.valueOf(followerId));
        redis.opsForValue().decrement(countKey(followingId));
    }

    private void trimToTopK(String key, int maxSize) {
        Long size = redis.opsForZSet().size(key);
        if (size == null || size <= maxSize) return;

        long overflow = size - maxSize;
        // Remove lowest-score members by ascending rank.
        redis.opsForZSet().removeRange(key, 0, overflow - 1);
    }

    private List<String> loadFollowersFromSql(long userId, int limit, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return jdbc.queryForList(
                "SELECT follower_id FROM follows WHERE following_id = ? " +
                "ORDER BY created_at DESC LIMIT ?",
                String.class,
                userId,
                limit
            );
        }

        Instant ts = Instant.ofEpochSecond(Long.parseLong(cursor));
        return jdbc.queryForList(
            "SELECT follower_id FROM follows WHERE following_id = ? AND created_at < ? " +
            "ORDER BY created_at DESC LIMIT ?",
            String.class,
            userId,
            Timestamp.from(ts),
            limit
        );
    }

    private long getFollowerCount(long userId) {
        String value = redis.opsForValue().get(countKey(userId));
        return value == null ? 0L : Long.parseLong(value);
    }

    private String hotKey(long userId) { return "follower:hot:" + userId; }
    private String sampleKey(long userId) { return "follower:sample:" + userId; }
    private String countKey(long userId) { return "follower:count:" + userId; }
}
```

**How Max Size 10,000 works (`ZREMRANGEBYRANK`) - internal details**

```text
Goal: keep only the top 10,000 highest-score followers in follower:hot:{user_id}

1) Add/update follower score
   ZADD follower:hot:{user_id} <score> <follower_id>

2) Get current size
   ZCARD follower:hot:{user_id}

3) If size > 10,000, remove overflow from LOWEST ranks
   overflow = size - 10000
   ZREMRANGEBYRANK follower:hot:{user_id} 0 (overflow - 1)

Why rank 0...? 
 - ZSET rank is ascending by score (rank 0 = lowest score)
 - We read hottest followers using ZREVRANGE (highest score first)
 - So we delete from the low end and keep the high end

Example:
 - Current size = 10,250
 - overflow = 250
 - ZREMRANGEBYRANK key 0 249
 - 250 least-engaged followers are removed
 - New size = 10,000

Time complexity:
 - ZADD: O(log N)
 - ZCARD: O(1)
 - ZREMRANGEBYRANK: O(log N + M), M = removed members

Redis internals (high level):
 - Redis tracks ZSET ordering by score and supports fast rank operations
 - Trim by rank avoids scanning all followers from app code
 - Work is done server-side in one command path, reducing network overhead

Production note:
 - For strict atomicity under concurrency, run ZADD + ZCARD + ZREMRANGEBYRANK in Lua.
```

**Atomic Redis Lua script (production-ready) for Hot Followers trim**

```lua
-- KEYS[1] = follower:hot:{user_id}
-- ARGV[1] = member (follower_id)
-- ARGV[2] = score (engagement score)
-- ARGV[3] = max_size (10000)
-- ARGV[4] = ttl_seconds (604800)

local key = KEYS[1]
local member = ARGV[1]
local score = tonumber(ARGV[2])
local max_size = tonumber(ARGV[3])
local ttl_seconds = tonumber(ARGV[4])

-- 1) Upsert score
redis.call('ZADD', key, score, member)

-- 2) Trim low-score overflow
local size = redis.call('ZCARD', key)
if size > max_size then
  local overflow = size - max_size
  redis.call('ZREMRANGEBYRANK', key, 0, overflow - 1)
end

-- 3) Refresh TTL only when key is touched
redis.call('EXPIRE', key, ttl_seconds)

-- Return current size after trim
return redis.call('ZCARD', key)
```

**Java usage (Spring Data Redis) - execute atomically**

```java
private static final DefaultRedisScript<Long> HOT_FOLLOWER_UPSERT_SCRIPT =
    new DefaultRedisScript<>(
        "local key = KEYS[1] " +
        "local member = ARGV[1] " +
        "local score = tonumber(ARGV[2]) " +
        "local maxSize = tonumber(ARGV[3]) " +
        "local ttl = tonumber(ARGV[4]) " +
        "redis.call('ZADD', key, score, member) " +
        "local size = redis.call('ZCARD', key) " +
        "if size > maxSize then " +
        "  local overflow = size - maxSize " +
        "  redis.call('ZREMRANGEBYRANK', key, 0, overflow - 1) " +
        "end " +
        "redis.call('EXPIRE', key, ttl) " +
        "return redis.call('ZCARD', key)",
        Long.class
    );

public long upsertHotFollowerAtomic(long followingId, long followerId, double engagementScore) {
    String key = hotKey(followingId);
    Long size = redis.execute(
        HOT_FOLLOWER_UPSERT_SCRIPT,
        Collections.singletonList(key),
        String.valueOf(followerId),
        String.valueOf(engagementScore),
        String.valueOf(MAX_HOT_FOLLOWERS),
        String.valueOf(HOT_TTL.toSeconds())
    );
    return size == null ? 0L : size;
}
```

**Lua vs `MULTI/EXEC` for this use case**

```text
Both can provide atomicity, but they solve different problems:

1) Lua script (recommended here)
   - Best when logic is conditional ("if size > 10K then trim")
   - Runs entire read-modify-write on Redis server in one execution
   - No client-side race between ZCARD and ZREMRANGEBYRANK
   - Lower network round trips for compound operations

2) MULTI/EXEC transaction
   - Good for grouping fixed command lists atomically
   - Commands execute sequentially at EXEC time, but no branching logic inside
   - If condition depends on intermediate result, app must pre-read value (race-prone)
   - WATCH can help optimistic locking, but adds retry complexity under high write rates

Rule of thumb:
 - Use Lua for atomic conditional workflows (this hot follower trim flow)
 - Use MULTI/EXEC for simple, unconditional command batches

Operational notes:
 - Keep scripts deterministic and small
 - In Redis Cluster, all keys touched by script must hash to same slot
 - Monitor script latency; avoid heavy loops in Lua
```

**Trade-offs Table**

| Scenario | Storage | Data Structure | Latency | Use Case |
|----------|---------|-----------------|---------|----------|
| **Regular user (10K followers)** | SQL only | B-tree index | <50ms | Direct query, no caching needed |
| **Popular user (100K followers)** | SQL (indexed) | B-tree index (following_id, created_at DESC) | 100-150ms | Pagination acceptable |
| **VIP user (1M+ followers) - first page** | Redis hot cache | Sorted Set (ZSET) | **<1ms** ✅ | Instant profile display |
| **VIP user - random display** | Redis sample | Set (unordered) | **<1ms** ✅ | Variety for "Followed by" |
| **VIP user - pagination page 2+** | SQL + cursor | B-tree index scan + cursor | 100-300ms | Slower but acceptable |
| **Who user follows** | Redis cache | Sorted Set (ZSET) | **<1ms** ✅ | Feed generation, must be <50ms |
| **Following count** | Redis counter | String (INCR/DECR) | **<1ms** ✅ | Profile metric, always cached |

**Sharding Strategy for `follows` Table**

```
Problem: follows table grows to 500B rows
├─ 500M users × 1000 avg followers = 500B follow relationships
└─ Single database: too large to handle

Sharding Key Decision:
├─ Option 1: Shard by following_id (who has followers)
│   ├─ Pros: Get followers efficiently for profile page
│   ├─ Cons: Finding who user follows (following_id WHERE follower_id) is slow
│   └─ Queries: "Who follows @user?" ✅ Fast, "What does @user follow?" ❌ Slow
│
├─ Option 2: Shard by follower_id (who follows whom)
│   ├─ Pros: Get who this user follows efficiently (for feed)
│   ├─ Cons: Get followers of a user is slow
│   └─ Queries: "What does @user follow?" ✅ Fast, "Who follows @user?" ❌ Slow
│
└─ Option 3: Dual Shard (RECOMMENDED)
    ├─ Primary shard: by following_id (for profile pages)
    ├─ Secondary shard: by follower_id (for feed generation)
    ├─ Both tables have same data, different sharding
    ├─ Async replication: write to primary, replicate to secondary
    └─ Cost: 2x storage, but both queries fast ✅

Implementation:
├─ FollowersDB (shard by following_id)
│   └─ follows_primary (follower_id, following_id, created_at)
│       └─ Index: (following_id, created_at DESC)
│
├─ FollowingDB (shard by follower_id)
│   └─ follows_secondary (follower_id, following_id, created_at)
│       └─ Index: (follower_id, created_at DESC)
│
└─ Write Path:
    ├─ Write to FollowersDB (primary) first
    ├─ Publish to Kafka topic: "follows.created"
    ├─ FollowingDB consumes and replicates
    └─ Async replication lag: <1 second acceptable
```

**Handling Massive Follower Writes**

```
Scenario: Celebrity posts → millions of notifications
├─ @elonmusk tweets
├─ System needs to notify 150M followers
├─ Naive: 150M inserts into notification table
│   └─ Would take hours
└─ Solution: Denormalization + Batch Aggregation

Optimized Path:
├─ Don't insert 150M notification rows
├─ Instead:
│   ├─ Store: notification (user_id, event_type, actor_id, created_at)
│   │   └─ NOT: 150M rows, just 1 row: notify 150M about this tweet
│   ├─ When user opens notifications:
│   │   └─ Check if @elonmusk tweeted since last check
│   │   └─ Retrieve tweet via actor_id
│   └─ Fanout only when user requests (lazy)
│
└─ Cost: 1 row instead of 150M rows ✅
```


**TweetDB (PostgreSQL with Sharding)**
```
Sharding Key: tweet_id or user_id
├─ tweets (tweet_id, user_id, content, created_at, is_deleted)
├─ tweet_engagement (tweet_id, likes_count, retweets_count, replies_count)
├─ media (media_id, tweet_id, type, url, created_at)
└─ tweet_metadata (tweet_id, language, sentiment_score, virality_score)
```

### 3.2 NoSQL (Real-time Data & Timelines)

**Timeline Data (Cassandra - Time-series optimized)**
```
Primary Key: (user_id, created_at DESC) with bucketing
├─ home_timeline (user_id, tweet_created_at, tweet_id, author_id)
├─ user_timeline (user_id, created_at, tweet_id)
└─ mentions_timeline (user_id, created_at, tweet_id)

Write Path: Insert-heavy, immutable once written
Read Path: Scan by time range, limited to 200 tweets
TTL: 30 days (older tweets archived)
```

**Engagement Data (Redis)**
```
Keys:
├─ tweet:{tweet_id}:likes -> Set (user_ids liking the tweet)
├─ tweet:{tweet_id}:retweets -> Set
├─ user:{user_id}:feed_items -> Sorted Set (score = timestamp)
├─ trending:{region}:hourly -> Sorted Set (score = count)
└─ user:{user_id}:followers_sample -> Set (10K sample for VIP users)
```

### 3.3 Data Consistency Trade-offs

| Data | Storage | Consistency | Justification |
|------|---------|-------------|---------------|
| User Profiles | SQL | Strong | Source of truth, infrequent changes |
| Tweet Content | SQL | Strong | Immutable, legal/audit importance |
| Engagement (likes) | Redis + SQL | Eventual | Reads can lag by seconds, eventual consistency acceptable |
| Timeline | Cassandra | Eventual | Fanout during write, consistency lag acceptable |
| Follower List | Redis Cache | Eventual | Derived from SQL, can be stale by minutes |
| Trending Topics | Redis | Eventual | Aggregate data, stale by minutes acceptable |

---

## 4. Core Services Deep Dive

### 4.1 Feed Service (Home Timeline Generation)

**Challenge**: Generate personalized feed for users with 10M+ followers in <200ms

**Architecture**:
```
Feed Request
    │
    ├─ Check Redis Cache (last 200 tweets, 24h TTL)
    │   └─ Hit: Return immediately
    │
    └─ Miss/Refresh:
        ├─ Query Following List (Cached)
        ├─ Get Recent Tweets from Followed Users (Cassandra)
        └─ Score & Rank
            ├─ Recency (timestamp)
            ├─ Engagement (likes, retweets)
            ├─ Author_strength (author's followers, verified)
            ├─ ML Ranking (likes this user gives to similar tweets)
            └─ Freshness (favor 1-4 hour old tweets over older)
        ├─ Apply Filters
        │   ├─ Mute list
        │   ├─ Block list
        │   └─ Quality filters
        └─ Cache Result (Redis, 5 min TTL)
            └─ Return to Client
```

**Fanout Optimization**:
```
Approach: Hybrid Push-Pull

High-Profile Users (followers > 100K): PULL model
├─ Followers fetch their own feed
├─ Reduces write amplification
└─ Followers see slight (100-500ms) delay

Regular Users (followers < 100K): PUSH model
├─ Write tweet to all 100K followers' timelines (Cassandra)
├─ Followers see tweets within 1-2 seconds
└─ Write heavy but acceptable for this scale
```

**Code Sketch**:
```python
class FeedService:
    def get_home_feed(user_id, limit=200, cursor=None):
        # 1. Check cache
        cache_key = f"feed:{user_id}:v2"
        cached = redis.get(cache_key)
        if cached and not cursor:
            return cached
        
        # 2. Get following list
        following = user_service.get_following(user_id, limit=1000)
        
        # 3. Query recent tweets
        tweets = cassandra.query(
            f"SELECT * FROM home_timeline WHERE user_id = {user_id} 
             LIMIT {limit * 3}"  # Fetch extra for filtering
        )
        
        # 4. Rank and filter
        ranked = self.rank_tweets(tweets, user_id)
        filtered = self.apply_filters(ranked, user_id)
        
        # 5. Cache and return
        redis.set(cache_key, filtered, ex=300)
        return filtered[:limit]
    
    def rank_tweets(tweets, user_id):
        # ML model: score = w1*recency + w2*engagement + w3*author_strength
        scored = [(tweet, score_tweet(tweet, user_id)) for tweet in tweets]
        return sorted(scored, key=lambda x: x[1], reverse=True)
```

### 4.2 Tweet Service

**Write Path**:
```
POST /tweet (User sends tweet)
    │
    ├─ Validate & Sanitize
    │   ├─ Check length (280 chars)
    │   ├─ Remove malicious content
    │   └─ Extract mentions, hashtags
    │
    ├─ Write to SQL (TweetDB)
    │   └─ Atomic: insert tweet + increment user tweet count
    │
    ├─ Write to Search Index (Elasticsearch)
    │   └─ Async, indexed within 2-5 seconds
    │
    ├─ Fanout to Followers
    │   ├─ If user is low-profile: Push to all followers' timelines (Cassandra)
    │   └─ If user is high-profile: Async, followers pull
    │
    ├─ Publish to Event Stream (Kafka)
    │   └─ Topic: tweets.created
    │       ├─ Notification Service consumes
    │       ├─ Trending Service consumes
    │       └─ Analytics consumes
    │
    └─ Return tweet_id (202 Accepted)
```

**Delete Path**:
```
DELETE /tweet/{tweet_id}
    │
    ├─ Soft delete in SQL (set is_deleted = true)
    ├─ Remove from Cassandra timeline
    ├─ Remove from Redis engagement data
    ├─ Remove from Elasticsearch
    └─ Publish tweet.deleted event
```

### 4.3 Trending Service (Real-Time Trending Topics)

**Challenge**: Calculate trending topics globally & regionally with millions of tweets/min

**Architecture**:
```
Streaming Architecture:
    
Tweet Event Stream (Kafka)
    │
    ├─ Spark Streaming / Flink Job
    │   ├─ 1-min tumbling window
    │   ├─ Extract hashtags & keywords
    │   ├─ Group by: global, region (country/city), language
    │   ├─ Calculate: count, velocity (increase rate), decay (older trends fade)
    │   └─ Score: trend_score = count^0.8 * velocity * decay
    │
    └─ Redis Write (every 1 minute)
        ├─ trending:global:hourly -> Sorted Set
        ├─ trending:US:hourly -> Sorted Set
        ├─ trending:region_{geo}:hourly -> Sorted Set
        └─ trending:topic_metadata:{topic_id}
            ├─ tweet_count
            ├─ unique_users
            ├─ velocity
            └─ sample_tweets (top 10)
```

**Ranking Formula**:
```
trend_score = (
    0.4 * log(tweet_count) +           # Volume
    0.3 * velocity_score +              # Velocity (how fast it's rising)
    0.2 * diversity_score +             # % unique users tweeting
    0.1 * recency_bonus                 # Recent tweets boost
)

velocity_score = (tweets_last_5min - tweets_last_10min) / tweets_last_10min
decay_factor = exp(-age_hours / 6)     # 6 hour half-life
final_score = trend_score * decay_factor
```

**Query Path**:
```python
def get_trending(region="global", limit=50):
    return redis.zrange(
        f"trending:{region}:hourly",
        0, limit-1,
        withscores=True,
        reverse=True
    )
```

### 4.4 Notification Service

**Architecture**:
```
Event-Driven Notifications:

Kafka Topics:
├─ tweet.created (trigger mentions notifications)
├─ engagement.liked (trigger like notifications)
├─ engagement.retweeted
├─ user.followed
└─ user.mentioned

Processing Pipeline:
    Event → Notification Service
        ├─ Check user preferences
        ├─ Check mute/block list
        ├─ Batch notifications (aggregate likes)
        ├─ Store in SQL (NotificationDB)
        ├─ Cache in Redis (unread count)
        └─ Push to clients
            ├─ WebSocket (real-time)
            ├─ Mobile Push
            └─ Email (digest)
```

---

## 5. Handling High-Profile Users (10M+ Followers)

**Problem**: Fanout to 10M followers takes too long (write amplification)

**Solution: Hybrid Approach**

```
For Users with followers > 1M:
├─ PUSH Strategy for top 1% followers (100K users)
│   └─ Write to their home_timeline immediately
│
├─ PULL Strategy for the remaining followers
│   ├─ Followers fetch tweets when they open the feed
│   ├─ Query: tweets from following.user_id within the last 24h
│   └─ Slight delay (100-500ms) acceptable for large follower bases
│
└─ Caching Strategy
    ├─ Cache tweet in Redis for 7 days
    ├─ Cache "top 10K tweets by this user" for their followers
    └─ Query Cassandra only if not in cache
```

**Real Example - Popular Creator Tweet**:
```
@popular_user (100M followers) tweets at 3 PM

Timeline:
├─ T+0: Tweet written to DB
├─ T+0-5s: Write to top 100K followers' Cassandra rows (async, batched)
├─ T+5s: Write to cache, publish to event stream
├─ T+5-10s: Other 99.9M followers see it when they refresh
│           (via pull query to user's recent tweets)
├─ T+60s: Trending algorithm picks it up
└─ T+5min: Within top tweets by engagement
```

---

## 6. Home Screen Trade-offs

### 6.1 Consistency vs Latency

| Choice | Pros | Cons | Use Case |
|--------|------|------|----------|
| **Strong Consistency** | No duplicates/missing tweets | >500ms latency, write lock contention | Profile views (less frequent) |
| **Eventual Consistency** | <200ms latency, high throughput | Possible duplicates/gaps for 1-2s | Home feed (most visited) |
| **Session Consistency** | Good latency, user sees own writes | Cross-device gaps | Hybrid approach |

**Implementation**:
```
Home Feed: Eventual consistency
├─ User sees their own tweet immediately (optimistic UI)
├─ Others may see it with 1-5s delay
└─ Acceptable because: users follow millions, single tweet is noise

Profile Feed: Strong consistency
├─ User's profile shows exact state
├─ Slower writes (apply to SQL + replicate)
└─ Acceptable because: less frequently viewed
```

### 6.2 Freshness vs Personalization

```
Feed Ranking Strategy:

Freshness-Heavy (30% of users):
├─ Sort by recency (reverse chronological)
├─ <100ms latency, easy to cache
└─ Simple, but lower engagement

Personalization-Heavy (50% of users):
├─ ML ranking: engagement, author strength, diversity
├─ 150-200ms latency
├─ Higher engagement, but cache harder
└─ Requires pre-ranked candidate sets

Hybrid (20% of users):
├─ 70% recent, 30% personalized
├─ Balance latency and engagement
└─ Default for new users
```

### 6.3 Pagination vs Cursors

```
Pagination Problem:
├─ Offset pagination: LIMIT 200 OFFSET 4000
│   └─ Slow: must scan first 4000 rows
└─ Keyset/Cursor pagination: WHERE created_at < 1712000000 LIMIT 200
    └─ Fast: indexed range scan, constant time

Implementation:
{
  "tweets": [...],
  "next_cursor": "eyJ0aW1lc3RhbXAiOjE3MTIwMDAwMDAsInR3ZWV0X2lkIjo5ODc2NTQzfQ==",
  "prev_cursor": "..."
}

Query:
SELECT * FROM home_timeline 
WHERE user_id = ? 
  AND created_at < ?  // cursor decoded here
ORDER BY created_at DESC 
LIMIT 200
```

---

## 7. Fault Tolerance & Reliability

### 7.1 Service Failures

**Feed Service Down**:
```
├─ Fallback 1: Serve stale feed from cache (Redis)
│   └─ Users see 1-5 min old feed
├─ Fallback 2: Serve only user's own tweets
├─ Fallback 3: Simple reverse-chronological from followed users (slow)
└─ Circuit Breaker: Disable Feed Service after 3 failures, switch to fallback
```

**Database Failures**:

```
PostgreSQL (User/Tweet DB):
├─ Replication: Master + 2 synchronous replicas
├─ Read replicas: 5+ asynchronous for scaling reads
├─ Failover: Automatic via Patroni, <30s detection
└─ RTO: 30s, RPO: 0 (sync replicas)

Cassandra (Timeline):
├─ Replication Factor: 3 (across 3 datacenters)
├─ Quorum reads: Read from 2/3 replicas
├─ Quorum writes: Write to 2/3 replicas
├─ Eventual consistency with bounded staleness
└─ RTO: Immediate (self-healing), RPO: <1s
```

### 7.2 Network Partitions (Split Brain)

```
Scenario: Network splits US datacenter from EU

Strategy: "Data > Availability"
├─ US partition: majority has users/data
│   └─ Continue serving, write to local DB
├─ EU partition: minority, loses quorum
│   └─ Go read-only (serve from cache)
│
After partition heals:
├─ Conflict resolution: Last-write-wins for engagement
├─ Re-sync: EU replays missed writes
└─ Manual intervention: If write conflicts

Risk: EU users see cached stale data for 10-30 min
Trade-off: Better than corrupting data globally
```

### 7.3 Cascading Failures

**Problem**: Feed Service depends on 20+ services, failure cascade

**Solution**: Bulkheads & Degradation
```
Feed Service Dependency Tree:

Feed Service
├─ Timeline Service (mandatory)
│   ├─ Cassandra (99.99% uptime SLA)
│   └─ Cache (99.9% uptime SLA)
├─ User Service (optional)
│   └─ Used for filtering: if fails, skip filters
├─ Ranking Service (optional)
│   └─ Used for ML ranking: fallback to recency
└─ Engagement Service (optional)
    └─ Used to show like counts: show "???" if fails

Bulkhead Implementation:
├─ Thread pool per service (prevents starvation)
│   └─ If User Service slow: only 10 threads stall, others continue
├─ Timeout: 100ms max per dependency
├─ Circuit Breaker: disable after 10% failure rate
└─ Fallback: serve degraded response
```

---

## 8. Scaling Challenges & Solutions

### 8.1 Write Amplification

**Problem**: 1 tweet write → millions of follower timeline writes

```
Metrics:
├─ 1.5B tweets/day = 17K tweets/sec average
├─ High-profile accounts: 100 tweets/sec × 1M followers = 100M writes/sec
└─ Timeline storage: 100M writes × 7-day retention = massive

Solution: Buffering + Async Processing

Write Path:
Tweet → Kafka (in-memory queue) → Async Processor
├─ Kafka Topic: "tweets.created"
├─ Processor: consumes in batches (1000 tweets/sec)
├─ Writes timeline rows in bulk (more efficient)
└─ Cassandra: write throughput: 1M writes/sec per cluster

Capacity Planning:
├─ 1 Cassandra cluster: 1M writes/sec
├─ For 100M writes/sec: need 100 clusters (impossible!)
└─ Solution: Use PULL model for high-profile users
    ├─ Followers fetch on-demand
    ├─ Reduces writes from 100M to 10K
    └─ Read cost is acceptable
```

### 8.2 Hot Keys Problem

**Problem**: Celebrity tweets receive 10M likes in seconds

```
Key: "tweet:{tweet_id}:likes" in Redis (Set)
└─ All 10M requests hit same Redis node
    ├─ Network saturated on that shard
    ├─ Redis single-threaded, queues requests
    └─ P99 latency: 5+ seconds

Solution: Read/Write Splitting + Local Caches

Architecture:
├─ Client (local cache)
│   └─ Cache likes locally, increment with optimism
│   └─ Send to server in batch every 100ms
├─ Server (Redis)
│   └─ Write: INCRBY likes 100 (batch writes)
│       └─ Reduces writes from 10M to 100K
│   └─ Read: return from cache, refresh every 1s
└─ Duplicate counting: <0.1% acceptable for engagement
```

### 8.3 Spike Handling

**Problem**: Popular tweet mentioned on news → 1M likes/min

```
Scenario: @elonmusk tweets something viral
└─ Initial traffic: 100K QPS → 500K QPS (5x spike)

Solutions:

1. Pre-warming (Reactive)
   ├─ Detect trending spike in first 10 seconds
   ├─ Pre-fetch tweet data to cache
   ├─ Replicate hot tweet to 10 cache instances
   └─ Cost: extra cache memory

2. Rate Limiting (Proactive)
   ├─ Allow 10K likes/sec globally
   ├─ Excess requests: queue with 202 Accepted
   ├─ Process queued requests in the background
   └─ User sees "like sent" optimistically

3. Auto-scaling (Infrastructure)
   ├─ Kafka partition auto-scaling: increase partitions
   ├─ Cache node addition: <30s
   ├─ Database read replica: <5min
   └─ Cost: higher during spike, scale down after
```

---

## 9. Implementation Details

### 9.1 API Contracts

```
GET /feed?limit=200&cursor=xyz
Response:
{
  "tweets": [
    {
      "id": "123456",
      "author": {"id": "456", "handle": "@user", "name": "User", "verified": true},
      "content": "Tweet text",
      "created_at": "2026-04-06T10:30:00Z",
      "engagement": {
        "likes": 1000000,
        "retweets": 500000,
        "replies": 100000
      },
      "user_action": "liked"  // Current user's action
    }
  ],
  "next_cursor": "..."
}

GET /trending?region=US&limit=50
Response:
{
  "trends": [
    {
      "id": "123",
      "name": "#COVID19",
      "tweet_count": 2000000,
      "momentum": 1.5,
      "rank": 1,
      "sample_tweets": [...]
    }
  ]
}

POST /tweet
Request:
{
  "content": "My tweet",
  "media": [{"type": "image", "url": "..."}],
  "reply_to": "tweet_id"  // optional
}

Response:
{
  "id": "new_tweet_id",
  "status": "processing"
}
```

### 9.2 Monitoring & Alerting

```
Key Metrics:

Performance:
├─ Feed latency: P50, P95, P99
├─ API error rate: per service
├─ Cache hit rate: Redis, Elasticsearch
└─ Database query latency: by operation type

Reliability:
├─ Service availability: % uptime per service
├─ Replication lag: <500ms (Cassandra)
├─ Failover time: <30s (database)
└─ Data loss: RPO per storage type

Business:
├─ Tweets created: per second
├─ Likes recorded: per second
├─ Active users: DAU, MAU
└─ Engagement rate: likes/tweets

Alerting:
├─ Feed P99 latency > 500ms: page oncall
├─ Error rate > 1%: page oncall
├─ Cache hit rate < 80%: investigate
├─ Replication lag > 2s: alert, track
└─ Disk usage > 85%: provision more storage
```

---

## 10. Database Sizing

### 10.1 Storage Estimation

```
Data Volume (per day):
├─ Tweets: 1.5B tweets × 500 bytes = 750 GB
├─ Engagement: 100B likes × 50 bytes = 5 TB
├─ Users: 500M users × 1 KB = 500 GB
├─ Images: 50% tweets have images, 2 MB average = 1.5 TB
└─ Total per day: ~7.7 TB

Annual Storage:
├─ Data: 7.7 TB × 365 days = 2.8 PB
├─ Replication Factor 3: 8.4 PB
├─ Indexes & logs: +20%: 10 PB total

Database Split:
├─ SQL (User, Tweet metadata): 2 PB
├─ Cassandra (Timeline, Engagement): 5 PB
├─ Search (Elasticsearch): 1 PB
├─ Cache (Redis, hot data): 500 TB (in-memory)
└─ Archives (Parquet, S3): 3 PB
```

### 10.2 Throughput Sizing

```
Write Throughput:
├─ Tweets: 17K/sec
├─ Likes: 100K/sec
├─ Timeline writes (fanout): 10-50K/sec (user-dependent)
├─ Total: ~150K writes/sec

Cassandra Sizing (1M writes/sec per cluster):
└─ Need 1-2 clusters for all write types

Read Throughput:
├─ Feed reads: 10K QPS
├─ User profile: 5K QPS
├─ Trending: 2K QPS
├─ Search: 3K QPS
└─ Total: ~20K QPS

Database Sizing:
├─ PostgreSQL: 4-way sharding, 5 read replicas per shard = 20 servers
├─ Cassandra: 3 nodes per datacenter × 3 datacenters = 9 nodes (single cluster)
├─ Redis: 256 GB cluster (10-20 nodes with replication)
└─ Elasticsearch: 100 nodes (for search speed)
```

---

## 11. Security & Privacy

### 11.1 Data Protection

```
Encryption:
├─ In-transit: TLS 1.3 for all APIs
├─ At-rest: 
│   ├─ Database: AES-256 encryption
│   ├─ Cache: plaintext (ephemeral data)
│   └─ Backups: AES-256 encryption
├─ Key management: AWS KMS or equivalent
└─ Key rotation: every 90 days

Access Control:
├─ OAuth 2.0 for API authentication
├─ JWT tokens: 15-min expiry, refresh token: 30-day
├─ Rate limiting: 500 API calls/15min per user
└─ IP whitelisting: for internal services
```

### 11.2 Privacy

```
Data Retention:
├─ Tweets: Deleted after 7 years (legal hold)
├─ Engagement: Aggregated after 1 year (remove PII)
├─ Logs: 30 days retention
└─ User data: 180 days after account deletion

GDPR Compliance:
├─ Data export: within 30 days
├─ Right to delete: remove from all systems
├─ Consent: explicit for data processing
└─ Third-party sharing: disabled by default
```

---

## 12. Deployment & Operations

### 12.1 Deployment Strategy

```
Canary Deployment:
├─ Deploy to 5% of users first
├─ Monitor: error rate, latency, CPU usage
├─ If metrics OK: deploy to 100% over 4 hours
├─ If metrics bad: rollback immediately
└─ Rollback time: <5 minutes

Configuration Management:
├─ Feature flags: enable/disable features per region
├─ A/B testing: split traffic between versions
├─ Quick rollback: just disable feature flag
└─ No deployment needed: <1 second effect
```

### 12.2 Disaster Recovery

```
Scenarios & RTO/RPO:

Single Database Failure:
├─ Detection: 10 seconds
├─ RTO: 30 seconds (failover to replica)
├─ RPO: 0 seconds (sync replica)

Entire Datacenter Failure:
├─ Detection: 30 seconds
├─ RTO: 5 minutes (switch region)
├─ RPO: <1 minute (async replicas)

Corruption/Accidental Delete:
├─ Point-in-time recovery: restore from backup
├─ Detection: 1-2 hours (via data validation)
├─ RTO: 2-4 hours
├─ RPO: 24 hours (daily backup)
└─ Safeguard: soft deletes + audit log
```

---

## 12.5 Observability & SLOs (Critical for PE Level)

### 12.5.1 Distributed Tracing

**Purpose**: Track requests across 20+ microservices to find bottlenecks

**Implementation (Jaeger/Zipkin)**:
```
Request Flow Tracing:

User Request (frontend)
    ├─ Trace ID: abc123
    ├─ Span: api-gateway (10ms)
    │   └─ Span: auth-service (2ms)
    ├─ Span: feed-service (180ms)
    │   ├─ Span: cache-lookup (5ms)
    │   ├─ Span: cassandra-query (120ms) ← BOTTLENECK!
    │   ├─ Span: ranking-service (40ms)
    │   └─ Span: filter-service (15ms)
    └─ Total: 195ms

Benefits:
├─ Identify which service is slow for each request
├─ Detect cascading latency (A slow → B slow → C slow)
├─ Debug errors: see exact call path that failed
├─ Optimize: find top 10 slow request patterns

Configuration:
├─ Sample 1% of traffic in production (reduces overhead)
├─ Sample 100% of errors (always trace failures)
├─ Sample 100% of requests >500ms latency
└─ Retention: 7 days (recent issues)
```

### 12.5.2 Service Level Objectives (SLOs)

**Define SLOs per service** (not just uptime):

```
Feed Service SLO:
├─ Availability: 99.95% (1 hour downtime/month)
│   └─ Error rate < 0.05%
├─ Latency: 99th percentile < 300ms
│   ├─ P50: < 100ms
│   ├─ P95: < 200ms
│   └─ P99: < 300ms
├─ Freshness: 95% of feeds < 5 minutes old
└─ SLO budget: 3.6 hours/month to make changes risking availability

Tweet Write Service SLO:
├─ Availability: 99.99% (4 min downtime/month)
├─ Latency: P99 < 500ms
└─ Data loss: 0 (RPO must be 0)

Trending Service SLO:
├─ Availability: 99.9% (43 min downtime/month)
├─ Latency: P99 < 200ms
└─ Freshness: <2 minutes stale

Strategy:
├─ Stricter SLOs for user-facing, critical services
├─ Looser SLOs for batch, non-critical services
├─ Track error budget: if using too fast, stop deployments
└─ Incident post-mortems: focus on SLO violations
```

### 12.5.3 Metrics & Instrumentation

**Key metrics by category**:

```python
# Instrumentation Example

from prometheus_client import Counter, Histogram, Gauge

# Request metrics
request_latency = Histogram(
    'feed_request_latency_ms',
    'Feed request latency',
    buckets=[50, 100, 150, 200, 300, 500]
)

request_errors = Counter(
    'feed_request_errors_total',
    'Total feed request errors',
    labels=['error_type', 'service']
)

cache_hits = Counter(
    'cache_hits_total',
    'Cache hits',
    labels=['cache_type']
)

# Business metrics
tweets_created = Counter('tweets_created_total')
likes_recorded = Counter('likes_recorded_total')
active_users = Gauge('active_users_current')

# Infrastructure metrics
db_connection_pool = Gauge(
    'db_connections_active',
    'Active database connections',
    labels=['database', 'shard']
)

disk_usage = Gauge(
    'disk_usage_percent',
    'Disk usage percentage',
    labels=['mount_point']
)
```

---

## 12.6 Operational Runbooks (3 AM Incident Response)

**What to do when things break at 3 AM**:

### 12.6.1 Feed Service Slow (P99 Latency > 500ms)

```
Detection: Alert triggered
├─ Severity: Page oncall immediately

Step 1: Triage (2 minutes)
├─ Check recent deployments
│   └─ Rolled back bad code? If yes, go to Step 3
├─ Check infrastructure alerts
│   ├─ Cassandra CPU > 90%? → Scale horizontally
│   ├─ Cache eviction rate high? → Increase cache size
│   └─ Network latency high? → Check BGP routes
├─ Check query patterns
│   └─ Unusual queries? (detected via APM logs)

Step 2: Immediate Mitigations (5 minutes)
├─ Enable circuit breaker on Ranking Service (fallback to recency)
│   └─ Latency drops from 200ms to 100ms
├─ Increase cache TTL from 5min to 10min
├─ Enable caching on user following list
├─ Reduce pagination limit from 200 to 100 tweets
└─ Enable read-only mode temporarily (no new likes recorded)

Step 3: Root Cause (10 minutes)
├─ Check Cassandra replication lag
│   └─ If >1s: failover to different DC
├─ Check Redis memory usage
│   └─ If >90%: manually evict old keys
├─ Check database slow query log
│   ├─ Query: SELECT * FROM home_timeline WHERE user_id=123
│   └─ Problem: Full table scan (missing index)
├─ Check application logs for exceptions
│   └─ OOM exception? → Restart service
└─ Check deploy log for recent changes
    └─ Deploy 10 min ago with new ML ranking? → Rollback

Step 4: Long-term Fix (by morning)
├─ If missing index: Create index, optimize query
├─ If poorly tuned: Adjust cache TTL, query limits
├─ If capacity: Add more Cassandra nodes, Redis shards
├─ Post-incident: Update runbook with findings

Automation:
├─ Automated rollback: if error rate > 5% within 5 min of deploy
├─ Automated scaling: if Cassandra CPU > 80%, add node
├─ Automated failover: switch to backup Cassandra cluster
```

### 12.6.2 Database Replication Lag (>5 seconds)

```
Detection: Monitoring alert

Impact:
├─ Users may see stale data (old follower lists, engagement counts)
├─ But they still see tweets (from Cassandra primary)

Diagnosis:
├─ Check Postgres replication status
│   └─ SELECT slot_name, restart_lsn FROM pg_replication_slots
├─ Check network latency between primary and replica
├─ Check replica CPU/disk I/O
├─ Check transaction log write rate (is primary too fast?)

Immediate Action:
├─ If network issue: reroute through backup network
├─ If replica slow: Restart replica process
├─ If primary too fast: Throttle write rate (queue overflow)
│   └─ Accept likes at 90% capacity, queue rest

Don't:
├─ Don't failover to lagging replica (will lose data)
├─ Don't stop primary (will break everything)
├─ Don't increase replication_timeout (hides problem)

Fix:
├─ After lag resolves: perform data validation
│   └─ Check replica has same tweet counts as primary
└─ Incident review: why did this happen?
```

### 12.6.3 Kafka Queue Backing Up (Lag > 100K messages)

```
Meaning: 
├─ Events being published faster than consumed
├─ Notifications might be delayed by hours
├─ Trending might use stale data

Check:
├─ Consumer group status
│   └─ /kafka/bin/kafka-consumer-groups.sh --describe
├─ Which consumer is slow?
│   ├─ Notification Service? (lag: 500K messages)
│   ├─ Trending Service? (lag: 100K messages)
│   └─ Analytics? (lag: 1M messages, but non-critical)

Immediate Actions:
├─ Increase consumer parallelism (more workers)
│   └─ Increase from 10 to 50 Kafka partitions for notifications
├─ If consumer crashed: restart it
├─ If consumer code broken: rollback to previous version
├─ If database too slow: scale read replicas

Do NOT:
├─ Skip messages (delete data)
├─ Restart Kafka cluster (can cause duplication)
└─ Increase message retention (you'll run out of disk)

Prevention:
├─ Alert at lag > 50K (before user impact)
├─ Auto-scale consumers at lag > 50K
└─ Capacity plan: 2x expected peak load
```

### 12.6.4 Redis Cache Failure

```
Scenario: Redis cluster unavailable (all nodes down)

Impact:
├─ Feed requests slow down significantly (no cache)
├─ Database queries spike 10x
├─ Some requests timeout (database can't handle)

Immediate:
├─ Disable cache lookups (fail-open)
│   └─ Feed Service doesn't wait for Redis, goes straight to DB
├─ Enable synthetic cache in-process
│   ├─ Use last 1000 feed items in local memory
│   └─ Better than nothing, stale but fast
├─ Reduce traffic: enable aggressive rate limiting
│   └─ Feed requests: 5K QPS limit (from 10K)

Recovery:
├─ If Redis reboot works: restart cluster
├─ If disk corrupted: restore from backup
│   ├─ Backup is 1 hour old
│   └─ Re-warm cache with hot keys
└─ Populate cache: Batch job queries all active users' feeds, caches them

Don't:
├─ Wait for Redis (feeds will timeout)
├─ Try to rebuild Redis while under load
└─ Use Redis for critical path (lesson: cache is optimization, not requirement)
```

---

## 12.7 Data Quality & Integrity

### 12.7.1 Duplicate Detection & Handling

**Problem**: Network retries → double-counted likes, duplicate tweets in timeline

```
Scenario 1: Like Submitted Twice
├─ User clicks "like" button
├─ Request sent to server: POST /tweet/123/like
├─ Server receives, increments like count, returns success
├─ But response never reaches client (network timeout)
├─ User retries: POST /tweet/123/like again
├─ Result: same user counted twice

Solution:
├─ Idempotency keys (UUID generated by client)
├─ Request: POST /tweet/123/like
│   Header: Idempotency-Key: abc-def-ghi
├─ Server stores: (user_id, tweet_id, idempotency_key) → like_id
├─ Duplicate request with same key: return same like_id, don't double-count
├─ Clean up old keys after 24 hours

Implementation:
├─ Redis cache: idempotency:{key} → like_id
├─ TTL: 24 hours
└─ On retry: return cached result without re-executing
```

### 12.7.2 Data Validation Checks

```python
class DataQualityService:
    
    def validate_likes_count():
        # Check: are likes in Redis consistent with SQL?
        
        for tweet_id in sample(1000):
            redis_count = redis.get(f"tweet:{tweet_id}:likes_count")
            sql_count = db.query(
                "SELECT COUNT(*) FROM likes WHERE tweet_id = ?",
                tweet_id
            )
            
            if abs(redis_count - sql_count) > 100:  # Allow 100 tolerance
                alert(f"Tweet {tweet_id}: Redis={redis_count}, SQL={sql_count}")
                # Reconcile: resync from SQL to Redis
                redis.set(f"tweet:{tweet_id}:likes_count", sql_count)
    
    def validate_timeline_completeness():
        # Check: does user's home_timeline have all tweets from followed users?
        
        for user_id in sample(1000):
            # Get timeline from Cassandra
            timeline_tweets = cassandra.query(
                "SELECT tweet_id FROM home_timeline WHERE user_id=?",
                user_id
            )
            
            # Get expected tweets from SQL
            following = user_service.get_following(user_id)
            expected = db.query(
                f"SELECT tweet_id FROM tweets 
                  WHERE user_id IN ({following}) 
                  AND created_at > now() - interval '1 day'
                  ORDER BY created_at DESC LIMIT 500"
            )
            
            missing = set(expected) - set(timeline_tweets)
            if missing:
                alert(f"User {user_id}: missing {len(missing)} tweets in timeline")
                # Re-fanout missing tweets
                cassandra.insert_batch(
                    [home_timeline_row(user_id, tweet) for tweet in missing]
                )
    
    def validate_engagement_monotonic():
        # Check: engagement counts never decrease (always increase or stay same)
        
        for tweet_id in sample(1000):
            current_likes = redis.get(f"tweet:{tweet_id}:likes")
            prev_likes = db.query(
                "SELECT likes_count FROM tweet_engagement_audit 
                 WHERE tweet_id=? ORDER BY timestamp DESC LIMIT 1",
                tweet_id
            )
            
            if current_likes < prev_likes:
                alert(f"Tweet {tweet_id}: likes decreased from {prev_likes} to {current_likes}")
                # Revert to previous value
                redis.set(f"tweet:{tweet_id}:likes", prev_likes)
```

### 12.7.3 Reconciliation Jobs

```
Batch Reconciliation (run hourly):

1. Tweet Count Reconciliation
   ├─ Aggregate: SUM(likes, retweets, replies) from engagement tables
   ├─ Compare with cached counts in Redis
   ├─ If differ >1%: alert, re-sync
   └─ Update audit table for tracking

2. Timeline Completeness Check
   ├─ For 0.1% of random users: verify all followed tweets are in timeline
   ├─ If missing: re-fanout
   └─ Alert on high missing rate (indicates fanout bug)

3. Soft Delete Validation
   ├─ Check is_deleted=true tweets are not in timelines
   ├─ Remove if found
   └─ Alert if large number found (indicates delete bug)

4. Replication Lag Trending
   ├─ Track max lag over time
   ├─ Alert if lag increasing trend
   └─ Plan capacity before it gets bad
```

---

## 12.8 API Evolution & Backward Compatibility

**Problem**: You have 500M mobile app users. You can't break the API.**

### 12.8.1 Versioning Strategy

```
API Versioning: URL-based (simplest for clients)

GET /v1/feed         ← Old version, 80% of traffic
GET /v2/feed         ← New version, 20% of traffic
GET /v3/feed         ← Latest, canary

Each version is fully independent:
├─ v1: old schema, old ranking algorithm
├─ v2: new engagement schema, new ML ranking
├─ v3: experimental, A/B tested features

Lifecycle:
├─ Deploy v2 → 0% traffic
├─ Canary v2 → 5% of users (new users only)
├─ Ramp v2 → 50% over 1 week
├─ Deprecate v1 → after 6 months
├─ Sunset v1 → 12 months after deprecation
└─ Delete v1 → 18 months after deprecation

Implementation:
├─ Single service handles all versions
├─ Router: if request.version == 1: call_v1_handler()
├─ Shared storage (database is not versioned)
└─ Version-specific business logic (ranking, filtering)
```

### 12.8.2 Schema Evolution Without Downtime

**Problem**: Add new field to Tweet. 500M tweets already exist. Can't lock table for migration.**

```
Strategy: Additive-only changes

Step 1: Deploy Code That Reads New Field
├─ Add field to Tweet schema: "ai_summary: string"
├─ Make it OPTIONAL (nullable, with default)
├─ Deploy to all servers
└─ Old tweets won't have field (NULL), that's okay

Step 2: Backfill Data (Async, Non-blocking)
├─ Background job: for each tweet, generate ai_summary
│   ├─ Batch: process 100K tweets at a time
│   ├─ Throttle: don't overload database
│   └─ Idempotent: safe to re-run
├─ Time: 2-3 weeks for 1.5B tweets
└─ No downtime: users still reading tweets during backfill

Step 3: Deploy Code That Writes New Field
├─ All new tweets now include ai_summary
└─ Old tweets have NULL, still readable

Never:
├─ Don't delete columns (old code might use them)
├─ Don't change field types (breaks old app versions)
├─ Don't rename fields (use aliases instead)
└─ Don't lock tables during migration

Rollback:
├─ If backfill wrong: just set ai_summary back to NULL
├─ If code breaks: disable field in config (feature flag)
└─ No schema rollback needed (additive changes are safe)
```

### 12.8.3 Graceful Degradation for Clients

```json
Response Format (Resilient to API Changes):

Old Client (v1):
{
  "id": "123",
  "content": "My tweet",
  "likes": 100,
  "created_at": "2026-04-06T10:00:00Z"
  // Ignores unknown fields
}

New Client (v2):
{
  "id": "123",
  "content": "My tweet",
  "likes": 100,
  "created_at": "2026-04-06T10:00:00Z",
  "ai_summary": "User sharing their thoughts",
  "media": [
    {
      "type": "image",
      "url": "https://...",
      "alt_text": "Description"  // NEW FIELD
    }
  ]
}

Principle:
├─ Old clients: ignore unknown fields (JSON parsing)
├─ New clients: use new fields if present, fallback if not
├─ API: always return compatible structure
└─ Never remove fields (even if unused)
```

### 12.8.4 Canary Testing for API Changes

```
Workflow: Deploy new /v2/feed endpoint safely

Setup:
├─ Feature flag: enable_v2_feed (default: false)
├─ Internal users: force_v2_feed (test with real users at company)

Week 1: Internal Testing
├─ 100% of employees use v2
├─ Monitor: error rate, latency, engagement
├─ Verify: feature parity with v1
└─ Gather feedback: UI issues, missing data

Week 2: Canary (5% of users)
├─ 5% of new iOS users → v2 automatically
├─ Old iOS users → v1 (backward compatible)
├─ Monitor: error rate, latency, crashes
├─ Metrics dashboard: v1 vs v2 side-by-side
└─ Kill switch: disable v2 with feature flag (instant)

Week 3: Ramp (20% of users)
├─ Increase to 20% new users
├─ A/B test: measure engagement impact
└─ If good: continue ramp

Week 4: Rollout (100%)
├─ All new users on v2
├─ v1 still available for old apps
└─ Deprecation notice: v1 to sunset in 6 months

Continuous Monitoring:
├─ Error budget: allow <0.1% errors before rollback
├─ Latency: if P99 > 500ms, rollback
├─ Data corruption: if >10 reports, rollback immediately
├─ Kill switch: any engineer can disable with Slack command
```

---

## 12.9 Organizational & System Reliability

**PE Level = System Thinking**

### 12.9.1 On-Call Rotation & Escalation

```
On-Call Structure:

Primary On-Call (handles immediate alerts)
├─ Page within 5 minutes of alert
├─ Fix or escalate within 15 minutes
└─ Shift: 1 week (Monday-Monday)

Secondary On-Call (escalation for stuck issues)
├─ Paged if primary can't resolve in 30 minutes
├─ Deeper database/infrastructure knowledge
└─ Shift: 1 week

Manager On-Call (for critical incidents)
├─ Paged if Severity=Critical (multiple services down)
├─ Makes judgment calls: deploy risky fix vs. wait for full team
└─ Shift: rotating among team leads

Critical Incident:
├─ Trigger: >1% error rate OR >10K user impact
├─ Response: all 3 on-call + relevant engineers
├─ War room: real-time sync, shared dashboard
├─ Communication: status updates every 5 minutes
└─ Post-incident: write-up within 24 hours
```

### 12.9.2 Blameless Postmortems

**After every incident (Severity >= High)**:

```
Incident Report Template:

1. Timeline
   ├─ 10:00 AM: Alert triggered (lag > 5s)
   ├─ 10:02 AM: On-call paged
   ├─ 10:05 AM: Root cause identified (bad deploy)
   ├─ 10:10 AM: Service rolled back
   └─ 10:15 AM: Normal operation restored

2. Impact
   ├─ Duration: 15 minutes
   ├─ Users affected: 50K (10% of active users)
   ├─ Data loss: 0
   └─ SLO violated: Feed P99 latency SLO

3. Root Cause (Not "on-call was slow" → True cause)
   ├─ Code deploy: new ranking algorithm had O(n²) complexity
   ├─ Not caught: no perf tests for ranking
   ├─ Why not caught: tests run on sample of 1K tweets, real data 1.5B
   └─ Why deployed: code review focused on logic, not performance

4. Contributing Factors
   ├─ No deployment to canary first (deployed directly to 20%)
   ├─ Monitoring didn't alert on latency early enough (alert threshold too high)
   └─ On-call unfamiliar with ranking service (new person this week)

5. Action Items
   ├─ Immediate (this week):
   │   └─ Code review checklist: must include performance impact
   ├─ Short-term (this month):
   │   ├─ Add perf tests: ranking must run < 50ms on 1M tweets
   │   └─ Lower alert threshold: P99 latency > 300ms (was 500ms)
   └─ Long-term (this quarter):
       ├─ Auto-scaling for ranking service
       └─ Canary deployments mandatory for all services

6. Blameless Lesson
   ├─ NOT: "Engineer should have tested more"
   ├─ BUT: "Process didn't catch performance regression"
   ├─ Action: Fix process, not person
   └─ Follow-up: Check if other services have same risk
```

---

## 12.10 Summary: PE-Level Checklist

```
✅ Architecture: Designed for 500M DAU, clear trade-offs
✅ Scalability: Identified bottlenecks (write amplification, hot keys)
✅ Reliability: Fault tolerance, disaster recovery, RTO/RPO
✅ Operations: Monitoring, alerting, runbooks for 3 AM incidents
✅ Data Quality: Idempotency, validation, reconciliation
✅ API Design: Versioning, backward compatibility, safe evolution
✅ Incident Response: On-call rotation, blameless postmortems
✅ Cost: Realistic infrastructure costs, optimization strategies
✅ Security: Encryption, access control, GDPR compliance
✅ Communication: Clear trade-off decisions explained throughout

This design shows:
├─ Technical depth (every service is thought through)
├─ Operational maturity (how to run this at scale)
├─ Systems thinking (all components work together)
├─ Business acumen (cost, user experience, engagement)
└─ Leadership readiness (can explain and defend decisions)
```

---

## 3.1.2 Follower List Query Performance Comparison

**Real-World Scenario: @elonmusk's Profile Page**

```
Followers: 150 Million
Action: User opens profile, app needs to show "Followed by X, Y, Z"

❌ NAIVE APPROACH (Fails at scale):
└─ Query: SELECT follower_id FROM follows 
   WHERE following_id = @elonmusk LIMIT 5
├─ Execution:
│   └─ Full scan of 150M rows or index scan of sorted list
│   └─ Even with perfect index: 1-5 seconds ❌
│   └─ Mobile app times out after 30s
└─ Why slow?
    └─ Even though index exists on (following_id, follower_id)
    └─ Database has to deserialize 150M entries to find top 5
    └─ Network transfer for result also slow

✅ TIERED APPROACH (Works):
├─ Query: REDIS.SMEMBERS user:@elonmusk:followers_sample
├─ Execution:
│   └─ In-memory lookup: <5ms ✅
│   └─ Returns 10K random followers
│   └─ App displays first 5
└─ Why fast?
    └─ Sample loaded into memory daily
    └─ No database hit needed
    └─ Instant response even for 150M followers
```

**Query Performance Matrix**

| Operation | Regular User (10K) | Popular User (100K) | VIP User (1M+) |
|-----------|-------------------|-------------------|------------------|
| Show "Followed by X" | SQL (20ms) | SQL (150ms) | **Redis (5ms)** |
| Pagination through followers | SQL (50ms) | SQL (200ms) | **SQL + Cursor (300ms)** |
| Who this user follows | **Redis Cache (10ms)** | **Redis Cache (10ms)** | **Redis Cache (10ms)** |
| Get hot followers (active) | N/A | N/A | **Redis (5ms)** |
| Refresh sample (daily job) | N/A | N/A | **SQL (1s) async** |

````

