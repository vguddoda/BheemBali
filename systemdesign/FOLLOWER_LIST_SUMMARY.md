# Follower List Handling at Scale - Interview Summary

## The Question You Asked
**"If a user has 1M followers, where do we store the follower list? How do we handle it differently?"**

---

## Quick Answer (30 seconds)

We use a **tiered approach**:
- **Tier 1 (Redis)**: Hot/sample followers (10K cached for instant display)
- **Tier 2 (PostgreSQL)**: Full follower list (source of truth)
- **Tier 3 (Batch)**: Refresh sample daily, paginate from SQL when needed

This lets us show "Followed by @user1, @user2, @user3" in **<5ms** even for 150M followers.

---

## The Problem We're Solving

### Naive Approach (FAILS ❌)
```sql
SELECT follower_id FROM follows 
WHERE following_id = @elonmusk  -- He has 150M followers!
LIMIT 5

-- Performance: 1-5+ seconds even with perfect index
-- Why? Database must scan/deserialize 150M rows
```

### Real-World Impact
- Mobile app has 30 second timeout
- User's profile page takes 5+ seconds to load
- Cannot paginate through followers list
- Unacceptable for VIP accounts

---

## The Solution: Three-Tier Architecture

### Tier 1: Hot Followers (Redis) ⚡
```
Data Structure: user:{user_id}:followers_hot → Sorted Set
Content: 10K most-active followers
Score: Engagement metric (who liked/retweeted recently)
Latency: <5ms (all in-memory)
TTL: Refresh weekly

Use Case: Show "Followed by @top_1, @top_2, @top_3" on profile
```

### Tier 2: Sample Followers (Redis) ⚡
```
Data Structure: user:{user_id}:followers_sample → Set
Content: Random 10K followers (for variety)
Latency: <5ms
TTL: Refresh daily with batch job

Use Case: "Who to follow" suggestions, random sample display
```

### Tier 3: Full List (PostgreSQL) 💾
```
Table: follows (follower_id, following_id, created_at, is_active)
Sharding: By following_id (who has followers)
Index: (following_id, created_at DESC)
Latency: 100-500ms
Use Case: Pagination, export, analytics

Query Pattern:
SELECT follower_id FROM follows 
WHERE following_id = @user_id 
  AND created_at < @cursor  -- Cursor pagination
ORDER BY created_at DESC 
LIMIT 20
```

---

## Performance Comparison

| Operation | Regular (10K) | Popular (100K) | VIP (1M+) |
|-----------|--------------|----------------|-----------|
| Show "Followed by X" | **SQL 20ms** | SQL 150ms | **Redis 5ms** ✅ |
| Pagination | SQL 50ms | SQL 200ms | **SQL+Cursor 300ms** ✅ |
| Get following list | Redis 10ms | Redis 10ms | **Redis 10ms** ✅ |

---

## Implementation Strategy

### When User Opens Profile

```python
def get_followers_for_profile(user_id, limit=20, cursor=None):
    # Check if VIP
    follower_count = cache.get(f"user:{user_id}:follower_count")
    
    if follower_count < 1_000_000:
        # Regular user: direct SQL
        return db.query(
            f"SELECT follower_id FROM follows 
              WHERE following_id = {user_id} 
              ORDER BY created_at DESC LIMIT {limit}"
        )
    
    # VIP user: use cached sample
    if not cursor:
        # First request: return hot followers (most active)
        return redis.zrange(f"user:{user_id}:followers_hot", 0, limit-1)
    
    # Pagination: cursor-based query (slower but acceptable)
    cursor_decoded = decode_cursor(cursor)
    return db.query(
        f"SELECT follower_id FROM follows 
          WHERE following_id = {user_id} 
          AND created_at < {cursor_decoded['timestamp']}
          ORDER BY created_at DESC LIMIT {limit}"
    )
```

### When Someone Follows (@newperson follows @elonmusk)

```python
def on_new_follower(follower_id, following_id):
    # 1. Write to SQL (source of truth)
    db.insert("INSERT INTO follows (follower_id, following_id, created_at)")
    
    # 2. Update follower count cache
    cache.incr(f"user:{following_id}:follower_count")
    
    # 3. If VIP: add to hot followers (sorted by engagement)
    if is_vip(following_id):
        engagement = calculate_engagement_score(follower_id)
        redis.zadd(f"user:{following_id}:followers_hot", 
                   {follower_id: engagement})
        
        # Keep only top 10K
        redis.zremrangebyrank(f"user:{following_id}:followers_hot", 
                              10000, -1)
```

### Daily Batch Job

```python
def refresh_followers_sample(user_id):
    """Run every 24 hours for VIP users"""
    
    # Get random 10K followers from SQL
    sample = db.query(
        f"SELECT follower_id FROM follows 
          WHERE following_id = {user_id} 
          ORDER BY RANDOM() 
          LIMIT 10000"
    )
    
    # Update cache
    redis.delete(f"user:{user_id}:followers_sample")
    redis.sadd(f"user:{user_id}:followers_sample", *sample)
```

---

## Dual Sharding: Why It Matters

### The Problem
We need to answer TWO queries efficiently:
1. **"Who follows @elonmusk?"** (for his profile page)
2. **"Who does @alice follow?"** (for her feed generation)

### The Solution: Two Sharded Tables

```
Primary Shard (by following_id):
├─ For: "Who follows this user?"
├─ Index: (following_id, created_at DESC)
└─ Used by: Profile pages, recommendations

Secondary Shard (by follower_id):
├─ For: "Who does this user follow?"
├─ Index: (follower_id, created_at DESC)
└─ Used by: Feed generation

Write Path:
1. Write to primary shard first
2. Publish to Kafka: "follows.created"
3. Secondary shard consumes and replicates
4. Async replication lag: <1 second (acceptable)
```

Why dual shard?
- Single shard + complex queries = slow
- Two shards = both queries fast in <100ms
- Cost: 2x storage, but worth it for scale

---

## Hot Key Problem (Celebrity Tweets)

**Scenario**: @elonmusk's followers list gets hammered

```
100 users/second want to see "Followed by" on his profile
└─ All 100 hit the same Redis key: followers_hot:{elon}

Solution:
├─ Serve from cache (TTL 24 hours)
├─ Don't fetch from DB on every request
├─ Result: <5ms response, no database load
```

---

## Interview Talking Points

### What Would You Say?

1. **"For regular users with <100K followers, direct SQL is fine.**
   - Query time: <50ms
   - Index (following_id, created_at) makes it fast
   - Network transfer is acceptable

2. **For VIP users with 1M+ followers, we cache samples in Redis.**
   - Can't query 150M rows every time someone opens profile
   - Cache 10K hot followers (most active): <5ms response
   - Cache 10K sample followers (random): <5ms response
   - SQL is source of truth, but not in critical path

3. **If user wants to paginate, use cursor-based pagination on SQL.**
   - SELECT ... WHERE following_id = ? AND created_at < ?
   - Use index to skip to the right place
   - Acceptable latency: 100-500ms for pagination (not critical path)

4. **Dual sharding handles both "who follows" and "who they follow".**
   - Can't shard both by same key efficiently
   - Two sharded tables, async replication between them
   - Keeps both queries fast

5. **Daily batch job refreshes samples.**
   - Distributed query: SELECT follower_id ORDER BY RANDOM() LIMIT 10K
   - Update Redis cache overnight
   - No real-time consistency issues (everyone doesn't need exact same sample)
```

---

## Key Trade-offs

| Decision | Rationale |
|----------|-----------|
| **Cache 10K hot, not all followers** | Can't store 150M in memory per user |
| **Sample in Redis, source in SQL** | Users don't need every follower, just a sample |
| **Dual shard by follower_id AND following_id** | Both queries are critical; can't optimize for one |
| **Async replication lag <1s** | Eventual consistency acceptable for relationships |
| **Refresh sample daily** | Followers list doesn't change that fast |
| **Cursor pagination only for page 2+** | First page from cache (hot/sample) |

---

## Real Example Timeline

```
@newperson follows @elonmusk (150M followers)

T+0ms:      INSERT INTO follows (follower_id=@newperson, following_id=@elonmusk)
T+1ms:      cache.incr(followers_count)
T+2ms:      redis.zadd(followers_hot, new follower if active)
T+3ms:      Kafka publish: "follows.created"
T+100ms:    Secondary shard (follower_id) replicated
T+500ms:    Others can see new follower in sample

When @elonmusk opens profile:
T+0ms:      redis.zrange(followers_hot) → <5ms
T+5ms:      Display "Followed by @active_1, @active_2, @active_3"

If someone scrolls (wants to paginate):
T+0ms:      SQL query with cursor → 200-300ms
T+300ms:    Display next 20 followers
```

---

## Summary

✅ **Tier 1**: Redis hot followers (10K, <5ms) - for instant display
✅ **Tier 2**: Redis sample followers (10K, <5ms) - for variety
✅ **Tier 3**: PostgreSQL full list (source of truth) - for pagination & exports
✅ **Dual Sharding**: One by following_id, one by follower_id (both queries fast)
✅ **Batch Refresh**: Daily job updates sample (async, non-blocking)

**Result**: Users with 1M+ followers get instant profile loads (<5ms) while maintaining data consistency.

