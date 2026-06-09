package org.example.lessons;

/**
 * LESSON 5 — Race conditions (the #1 danger of multithreading)
 *
 * THE SETUP: many threads share ONE variable and all add to it.
 * We launch threads that each do counter++ a fixed number of times.
 * The MATH says the final total must be: threadCount * incrementsPerThread.
 * But run it... and the total comes out TOO LOW, and DIFFERENT every run.
 *
 * WHY? Because "counter++" is NOT one single step. The CPU does it in 3 steps:
 *     1. READ  counter from memory into the core           (e.g. read 41)
 *     2. ADD   1 to that value                             (compute 42)
 *     3. WRITE the result back to memory                   (store 42)
 *
 * Now imagine two threads interleave (remember Lesson 3: order is unpredictable):
 *     Thread A reads 41 ......................... (A has 41)
 *     Thread B reads 41 ......................... (B also has 41!)
 *     Thread A writes 42
 *     Thread B writes 42   <-- should have been 43! One increment was LOST.
 *
 * That lost-update is a RACE CONDITION: the result depends on the exact, random
 * timing of threads racing over shared data. This is the bug that ruins beginners.
 */
public class Lesson05_RaceCondition {

    // Shared mutable state — the source of all the trouble.
    static int counter = 0;

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 8;
        int incrementsPerThread = 1_000_000;
        int expected = threadCount * incrementsPerThread;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    counter++;   // <-- LOOKS atomic, is actually read-add-write (3 steps)
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        System.out.println("Expected total: " + expected);
        System.out.println("Actual total:   " + counter);
        System.out.println("Lost updates:   " + (expected - counter));
        System.out.println();
        if (counter == expected) {
            System.out.println("(Got lucky this run — RUN IT AGAIN, it will usually be wrong.)");
        } else {
            System.out.println("^ The 'Actual' is LESS than 'Expected'. That gap is the race condition.");
            System.out.println("  Run it several times: the number changes. That randomness IS the bug.");
        }
    }
}
