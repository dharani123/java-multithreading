package org.example.lessons;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LESSON 10.2 — Pitfalls beyond deadlock
 *
 * Three traps that bite people who think "I used a thread-safe class, so I'm safe":
 *
 *   1. CHECK-THEN-ACT on a thread-safe collection is STILL a race.
 *      A ConcurrentHashMap makes each SINGLE operation atomic. But two operations in a
 *      row (containsKey THEN put) are NOT one atomic step — another thread can slip
 *      between them. Thread-safe parts do NOT make a compound action thread-safe.
 *
 *   2. The ATOMIC fix for compound actions: use the collection's built-in atomic
 *      compound methods (putIfAbsent, computeIfAbsent, merge) which do check-and-act
 *      as ONE locked step.
 *
 *   3. LIVELOCK: threads aren't blocked — they're actively running — but they keep
 *      reacting to each other and make no progress. (Two people stepping side-to-side
 *      in a hallway, mirroring each other, never passing.)
 */
public class Lesson10_2_Pitfalls {

    public static void main(String[] args) throws InterruptedException {
        checkThenActRace();
        atomicCompoundFix();
        livelockDemo();
    }

    /**
     * "If key absent, put it" on a ConcurrentHashMap. Looks safe. It is NOT: containsKey
     * and put are two steps, and another thread can slip between them.
     *
     * A single race is timing-dependent and easy to miss, so we run MANY rounds. Each
     * round: a fresh map, several threads racing to be the "first" inserter. We tally
     * every round where MORE THAN ONE thread believed it inserted. Over thousands of
     * rounds the race reliably shows up — proving the compound action isn't atomic.
     */
    static void checkThenActRace() throws InterruptedException {
        System.out.println("=== 1) check-then-act on ConcurrentHashMap is STILL a race ===");

        int rounds = 5000;
        int racyRounds = 0;
        int totalExtraInserts = 0;

        for (int r = 0; r < rounds; r++) {
            ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
            AtomicInteger iThoughtIInserted = new AtomicInteger(0);

            Runnable task = () -> {
                if (!map.containsKey("key")) {   // step 1: check (two threads can both see "absent")
                    Thread.yield();              // widen the window so the race is observable
                    map.put("key", 1);           // step 2: act (both put -> both think they "won")
                    iThoughtIInserted.incrementAndGet();
                }
            };

            Thread[] ts = new Thread[4];
            for (int i = 0; i < ts.length; i++) ts[i] = new Thread(task);
            for (Thread t : ts) t.start();
            for (Thread t : ts) t.join();

            int inserters = iThoughtIInserted.get();
            if (inserters > 1) { racyRounds++; totalExtraInserts += (inserters - 1); }
        }

        System.out.println("  rounds run: " + rounds);
        System.out.println("  rounds where >1 thread thought it inserted: " + racyRounds
                + "  (each should be exactly 1!)");
        System.out.println("  total duplicate 'inserts': " + totalExtraInserts);
        System.out.println("  Any number > 0 PROVES check-then-act is not atomic.\n");
    }

    /** The fix: putIfAbsent does check-and-insert as ONE atomic operation. */
    static void atomicCompoundFix() throws InterruptedException {
        System.out.println("=== 2) Fix: atomic compound op (putIfAbsent) ===");

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        AtomicInteger actualInserts = new AtomicInteger(0);

        Runnable task = () -> {
            // putIfAbsent returns null ONLY for the single thread that actually inserted.
            Integer prev = map.putIfAbsent("key", 1);
            if (prev == null) actualInserts.incrementAndGet();
        };

        Thread[] ts = new Thread[8];
        for (int i = 0; i < ts.length; i++) ts[i] = new Thread(task);
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();

        System.out.println("  threads that actually inserted: " + actualInserts.get()
                + "  (exactly 1, every time)\n");
    }

    /**
     * LIVELOCK (the textbook version): each worker needs BOTH locks. It grabs its first
     * lock, then politely TRIES for the second; if it can't get it, it RELEASES the first
     * and retries. Two workers acquiring in opposite order, moving in lockstep, keep
     * grabbing-failing-releasing in perfect sync — forever busy, never both succeeding.
     *
     * Unlike deadlock (threads BLOCKED, frozen), livelock threads are RUNNING at 100% —
     * they just never make progress. We cap retries so the demo terminates.
     *
     * THE FIX (shown by enabling backoff): random back-off breaks the lockstep so one
     * worker eventually wins. Toggle `useRandomBackoff` to see lockstep vs fixed.
     */
    static void livelockDemo() throws InterruptedException {
        System.out.println("=== 3) Livelock: busy (not blocked) but no progress ===");

        runLivelock("WITHOUT backoff (livelocks)", false);
        runLivelock("WITH random backoff (escapes)", true);

        System.out.println("Done with Lesson 10.");
    }

    static void runLivelock(String label, boolean useRandomBackoff) throws InterruptedException {
        java.util.concurrent.locks.ReentrantLock lockA = new java.util.concurrent.locks.ReentrantLock();
        java.util.concurrent.locks.ReentrantLock lockB = new java.util.concurrent.locks.ReentrantLock();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger collisions = new AtomicInteger(0); // failed grabs = wasted, spinning effort
        int maxRetries = 50;   // each attempt takes a beat, so keep the cap small

        // worker0 wants A then B; worker1 wants B then A (opposite order = the trap).
        Runnable worker0 = makePoliteWorker(lockA, lockB, completed, collisions, maxRetries, useRandomBackoff);
        Runnable worker1 = makePoliteWorker(lockB, lockA, completed, collisions, maxRetries, useRandomBackoff);

        Thread t0 = new Thread(worker0), t1 = new Thread(worker1);
        t0.start(); t1.start();
        t0.join(); t1.join();

        System.out.printf("  [%s] completed: %d/2   |   wasted collisions (back-offs): %d%n",
                label, completed.get(), collisions.get());
    }

    static Runnable makePoliteWorker(java.util.concurrent.locks.ReentrantLock first,
                                     java.util.concurrent.locks.ReentrantLock second,
                                     AtomicInteger completed, AtomicInteger collisions,
                                     int maxRetries, boolean useRandomBackoff) {
        return () -> {
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                first.lock();                          // grab my first lock
                try {
                    sleep(2);                          // hold it a beat so BOTH workers
                                                       // reliably collide each round (forces lockstep)
                    if (second.tryLock()) {            // politely TRY for the second
                        try {
                            completed.incrementAndGet(); // got both -> did the work, done
                            return;
                        } finally { second.unlock(); }
                    }
                    collisions.incrementAndGet();      // couldn't get second -> wasted attempt
                    // step aside (release first) and retry
                } finally {
                    first.unlock();
                }
                if (useRandomBackoff) {
                    sleep((long) (Math.random() * 5)); // random pause breaks the lockstep
                }
                // WITHOUT backoff: both retry immediately, in sync -> they keep colliding
            }
        };
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
