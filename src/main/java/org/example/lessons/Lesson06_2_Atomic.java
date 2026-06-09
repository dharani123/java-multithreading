package org.example.lessons;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * LESSON 6.2 — Fix #2: AtomicInteger (lock-free, hardware-backed)
 *
 * In 6.1 we used a lock so threads take turns. That works, but locking has overhead:
 * threads block and wait. For a simple counter we can do better.
 *
 * THE IDEA: modern CPUs offer a single hardware instruction called
 * Compare-And-Swap (CAS) that does read-and-update as ONE indivisible step.
 * AtomicInteger wraps that. No lock, no blocking — the hardware guarantees atomicity.
 *
 * HOW CAS WORKS (incrementAndGet, conceptually):
 *   loop:
 *     current = read value                 (say 41)
 *     next    = current + 1                (42)
 *     if value is STILL current -> set it to next, done.   // succeeded
 *     else -> someone changed it; loop and retry.          // try again
 * The "if still current" check is the atomic hardware step, so no update is ever lost.
 *
 * RESULT: correct AND typically faster than synchronized for counters, because
 * threads never block — at worst they retry the tiny CAS loop.
 *
 * USE WHEN: a single number/reference. For Atomic types there's also AtomicLong,
 * AtomicBoolean, AtomicReference, and LongAdder (even faster under heavy contention).
 */
public class Lesson06_2_Atomic {

    // Not a plain int — an object whose increment is guaranteed atomic.
    static AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 8;
        int incrementsPerThread = 1_000_000;
        int expected = threadCount * incrementsPerThread;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    counter.incrementAndGet();  // <-- atomic ++ in ONE safe step (CAS)
                }
            });
        }

        long start = System.currentTimeMillis();
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        long ms = System.currentTimeMillis() - start;

        System.out.println("Expected total: " + expected);
        System.out.println("Actual total:   " + counter.get()); // .get() reads the value
        System.out.println("Correct?        " + (counter.get() == expected));
        System.out.println("Time:           " + ms + " ms");
    }
}
