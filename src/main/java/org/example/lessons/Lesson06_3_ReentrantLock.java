package org.example.lessons;

import java.util.concurrent.locks.ReentrantLock;

/**
 * LESSON 6.3 — Fix #3: ReentrantLock (an explicit, more powerful lock)
 *
 * `synchronized` (6.1) is simple but rigid: the lock is acquired and released
 * automatically at the block's braces. You can't ask "is it locked?", you can't
 * give up after waiting too long, and you can't lock in one method / unlock in another.
 *
 * ReentrantLock is the same idea (mutual exclusion) but as an OBJECT you control:
 *   - lock.lock()            -> acquire (waits if needed)
 *   - lock.unlock()          -> release  (YOU must call it — hence try/finally!)
 *   - lock.tryLock()         -> acquire only if free right now; returns true/false
 *   - lock.tryLock(2, SECONDS) -> wait up to a timeout, then give up
 *   - fairness option, lockInterruptibly(), condition variables, etc.
 *
 * "Reentrant" = the SAME thread can lock() again while already holding it (a count
 * is kept). synchronized is reentrant too. This matters when locked method A calls
 * locked method B on the same lock — without reentrancy it would deadlock itself.
 *
 * THE GOLDEN RULE: always unlock in a `finally` block. If the protected code throws,
 * finally still runs and releases the lock — otherwise every other thread waits forever.
 */
public class Lesson06_3_ReentrantLock {

    static int counter = 0;
    static final ReentrantLock lock = new ReentrantLock();

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 8;
        int incrementsPerThread = 1_000_000;
        int expected = threadCount * incrementsPerThread;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    lock.lock();            // acquire the lock (others wait)
                    try {
                        counter++;          // critical section: one thread at a time
                    } finally {
                        lock.unlock();      // ALWAYS release, even if body throws
                    }
                }
            });
        }

        long start = System.currentTimeMillis();
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        long ms = System.currentTimeMillis() - start;

        System.out.println("Expected total: " + expected);
        System.out.println("Actual total:   " + counter);
        System.out.println("Correct?        " + (counter == expected));
        System.out.println("Time:           " + ms + " ms");
    }
}
