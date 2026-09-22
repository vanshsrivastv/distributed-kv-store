# Distributed KV Store — Learning Log

Tracks what's been learned, built, and verified at each stage. Interview Checkpoint
answers here are the *refined* correct versions after discussion, not first attempts.

---

## Stage 1: Concurrency Foundations

**Status:** ✅ Complete

### Concepts covered
- Threads vs. `Runnable` (a `Runnable` is just a job description; a `Thread` is what
  actually executes it concurrently — calling `.run()` directly does not create concurrency)
- Race conditions: why `counter++` is really 3 steps (read, add, write-back) and why
  interleaving those steps across threads loses updates
- Why the compiler cannot catch race conditions (valid single-threaded syntax; the bug
  only exists across timing/interleaving, which is a runtime scheduling concern)
- `synchronized` / mutual exclusion and critically, that it only works if all threads
  lock on the **same shared object** (`synchronized(new Object())` fails because a new
  object is created per call, so no thread ever contends with another)
- `.join()` — why omitting it lets `main` print a stale/partial value before worker
  threads finish
- Measured cost of locking: `synchronized` was ~10-20x slower than no locking at all
- `AtomicInteger` / compare-and-swap (CAS) as a lock-free alternative — correct AND
  ~5-9x faster than `synchronized` (32ms vs. 150-290ms), because CAS never blocks a
  thread (a failed compare just retries), whereas `synchronized` involves JVM/OS-level
  monitor machinery

### What I built
- `RaceConditionDemo.java` (throwaway, not part of final project):
  1. Two threads incrementing a shared `int` 1,000,000 times each, unsynchronized →
     wrong result (~1,913,483), different every run
  2. Fixed with `synchronized(RaceConditionDemo.class)` → reliably 2,000,000, ~150-290ms
  3. Confirmed `synchronized(new Object())` does NOT fix it (new lock object per call)
  4. Fixed with `AtomicInteger.incrementAndGet()` → reliably 2,000,000, ~33ms

### Interview Checkpoint — Q&A

**Q1: What is a race condition, and why doesn't the compiler catch it?**
A: A race condition occurs when two or more threads read and write shared memory
concurrently, and the final result depends on the unpredictable order/timing of those
operations. The compiler can't catch it because the code is syntactically valid,
ordinary single-threaded code — the bug only manifests from *runtime* thread
interleaving, which is a scheduling concern, not a syntax/type concern.

**Q2: Why did two threads each incrementing a shared counter 1,000,000 times not
produce 2,000,000?**
A: `counter++` is really three steps: read the value, add 1, write it back. If Thread A
reads the value (say 7) before Thread B writes its own increment back, both threads
compute 8 from the same starting point and one increment is silently lost. This
happens repeatedly across a million iterations, so the final count comes in lower than
expected, and differs on every run.

**Q3: What does `synchronized` actually do, and why must two threads share the same
lock object for it to work?**
A: `synchronized(obj) { ... }` forces any thread entering that block to first acquire
`obj`'s lock; only one thread may hold a given lock at a time, so any other thread
trying to enter a block synchronized on the *same* object must wait until the lock is
released. It only provides mutual exclusion if threads are contending for the *same*
object — locking on different objects provides no exclusion between them at all.

**Q4: Why did `synchronized(new Object())` fail to fix the race condition?**
A: Because `new Object()` creates a brand-new, distinct object every time that line
executes. Thread A and Thread B end up locking on different objects almost every time,
so they never actually contend with each other — each gets an uncontested private lock,
which provides zero real mutual exclusion.

**Q5: What's the practical cost of locking, and why does that matter for a database
handling thousands of concurrent writers?**
A: Locking serializes access — every thread must wait its turn to acquire the lock, so
throughput degrades as contention increases. Measured cost here was ~10-20x slower than
no locking. For a database with thousands of concurrent writers, one big lock around
the whole data structure would become a severe bottleneck, serializing what should be
parallel work — this is why the MemTable needs a lock-free design.

**Q6: What is compare-and-swap (CAS), and how does a lock-free approach differ from a
locked one?**
A: CAS is a hardware-level atomic instruction: "update this memory location to a new
value, but only if it still holds the value I last read; otherwise fail." A lock-free
approach never blocks a thread waiting for a key — if two threads' CAS attempts
collide, the loser's update simply fails and it retries immediately with the fresh
value, rather than being paused/scheduled out by the OS the way a lock-holding thread
would be.

**Q7: Why is `AtomicInteger` faster than `synchronized` but not as fast as doing no
coordination at all?**
A: It's faster than `synchronized` because CAS never blocks a thread or involves
JVM/OS-level monitor machinery — it's a single hardware instruction with a retry loop.
It's still slower than no coordination because CAS requires real cross-core
coordination (cache-coherency traffic when one core updates a value that other cores
have cached) — the uncoordinated version is only "fast" because it does no correctness
work at all, which is exactly why it's wrong.

---

## Stage 2: Basic Single-Node KV Store

**Status:** Complete

### Concepts covered
- Instance-based design instead of static state, so multiple independent stores can exist
- Encapsulation: `private` field so all changes go through `put`/`get`/`delete`
- `final` on a field: the reference can't be reassigned, the contents can still change
- Why `main` must be static (JVM entry point) without everything else being static
- Always use braces on if/else, to avoid the dangling-else bug
- Input validation: check `parts.length` before indexing, normalize whitespace
  with `trim()` and `split("\\s+")`
- `HashMap` is not thread-safe (fine here because the program is single-threaded)

### What I built
- `src/KVStore.java`: a `HashMap`-backed store with `put`, `get` (returns `null`
  if missing) and `delete`, plus a console loop (`put k v`, `get k`, `delete k`, `exit`)
- Bug found by testing: `put   a   b` (extra spaces) silently stored an empty key
  because `split(" ")` produced empty strings. Fixed with `trim()` + `split("\\s+")`
- Known small gap: `exit` is checked before `trim()`, so `exit ` with a trailing
  space does not quit

### Interview Checkpoint: Q&A

**Q1: Why is `KVStore` instance-based instead of static?**
A: Static state gives one shared copy for the whole program, so only one store could
ever exist. Instance-based lets you create many independent stores. This matters for
simulating several Raft nodes in one process for testing, and for unit tests that each
need a fresh store.

**Q2: Why is `data` private, and what breaks if it is public?**
A: Private forces every change to go through `put`/`delete`. From Stage 4, `put` will
write to the WAL before updating the map. If outside code could modify the map
directly, it would skip the WAL and silently break crash recovery.

**Q3: What does `final` on `data` prevent, and what does it not prevent?**
A: It prevents reassigning `data` to a different map. It does not prevent changing
the map's contents, so `data.put(...)` is fine.

**Q4: Is `HashMap` safe if two threads call `put` at once?**
A: No. Concurrent puts can lose updates (like the counter in Stage 1) and can corrupt
the map's internal structure during a resize. Use `ConcurrentHashMap` or a lock.

**Q5: What did the `put   a   b` bug teach about input validation?**
A: The bug was silent: bad input was accepted and stored as wrong data, with no error.
Input should be normalized or rejected explicitly at the boundary, never silently
accepted. A loud failure is better than quietly corrupt data.

---

## Stage 3: MemTable and SkipList

**Status:** Complete (single-threaded)

### Concepts covered
- Why the MemTable must be sorted by key (SSTable = *Sorted* String Table; a sorted
  MemTable flushes to disk with a simple in-order walk)
- Why not `TreeMap`: it's sorted but rebalances on insert, which makes concurrent
  access hard. A skip list only touches a few local pointers per insert
- Skip list structure: level 0 holds every key; higher levels are sparser "express
  lanes" built by giving each node a random height (repeated coin flips, so about
  half of nodes reach the next level up, a quarter the one after that, geometric
  distribution)
- Verified the distribution empirically: 10,000 calls to the random-height function
  produced counts that roughly halved at each level (4952, 2513, 1283, 629, ...),
  matching the theoretical geometric distribution
- Search/insert both walk from a `head` sentinel node, moving right while possible
  and dropping a level when blocked; the stopping point at each level (the
  predecessor) is exactly what an insert needs to splice pointers around
- Tombstones: a delete cannot simply remove a key, because a MemTable has no
  visibility into whether that key already exists in an older, immutable SSTable on
  disk. Deleting must record a marker (a `tombstone` flag) so a later read stops and
  reports "deleted" instead of falling through to stale data on disk. This applies
  even when the key isn't currently in the MemTable at all
- Refactored `put`/`delete`'s near-identical logic into one shared `upsert` helper,
  parameterized by value and the tombstone flag, to remove duplication

### What I built
- `src/SkipList.java`: `Node` (key, value, tombstone flag, per-node `next` array
  sized to that node's own random height), `randomHeight()`, `findPredecessors()`
  (the core traversal, reused by every operation), `put`, `get`, `delete`, all
  routed through a shared `upsert` helper
- Verified with a manual test in `main`: insert three keys, read them back, overwrite
  an existing key, delete it (confirmed `get` returns `null` afterward), then
  re-insert the same key (confirmed it comes back correctly, tombstone cleared)

### Known deferred work
- Not thread-safe yet. Concurrency is deliberately deferred to Stage 8-9, once the
  gRPC server introduces real concurrent access, so it can be tackled against an
  actual concurrent workload rather than a hypothetical one

### Interview Checkpoint: Q&A

**Q1: Why does the MemTable need to be sorted, and why a skip list instead of a
`TreeMap`?**
A: SSTables are written in sorted order, so a sorted MemTable can be flushed with a
simple in-order walk. A `TreeMap` is sorted but rebalances on insert, which can touch
nodes far from the insertion point, making it hard for many threads to modify safely
at once. A skip list's insert only touches a small number of local pointers, which
makes it far more concurrency-friendly, even though this version is single-threaded
for now.

**Q2: How does a skip list get roughly O(log n) search without any rebalancing?**
A: Each node is given a random height via repeated coin flips: roughly half the nodes
reach height 2, a quarter reach height 3, and so on. This creates sparser "express
lane" levels above the full bottom level, so a search can skip large chunks of the
list at high levels and only walk carefully once it drops to the bottom. It's called
probabilistic because the structure's shape depends on randomness, not on rebalancing
logic reacting to inserts.

**Q3: What does `findPredecessors` actually return, and why do both `get` and `put`
need it?**
A: For each level, the last node whose key is smaller than the target key (the
`head` sentinel if none). `get` only needs level 0's predecessor to check the next
node for a match. `put`/`delete` need the full array, because those are exactly the
pointers that must be rewired to splice a new node into every level it participates
in.

**Q4: Why can't `delete` just remove the node from the MemTable?**
A: The MemTable can't see whether that key already exists in an older SSTable on
disk. If delete just removed the in-memory node, a later `get` would find nothing in
the MemTable and fall through to the stale value on disk. Delete has to leave a
tombstone marker behind, even for a key not currently in the MemTable, so a read
knows to stop and report "deleted" rather than searching further down.

**Q5: Why was extracting `upsert` a real improvement, not just cosmetic?**
A: `put` and `delete` had identical predecessor-lookup and pointer-splicing logic,
differing only in the value and tombstone flag. Duplicated logic means a bug fix
(e.g. in the splicing order) has to be made in two places, and it's easy to fix one
and forget the other. Parameterizing the shared logic into one method removes that
risk entirely.
