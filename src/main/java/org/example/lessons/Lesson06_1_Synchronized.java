package org.example.lessons;

/**
 * LESSON 6.1 — Fix #1: the `synchronized` keyword (a LOCK)
 *
 * Recap of the bug (Lesson 5): counter++ is read-add-write (3 steps), and threads
 * interleave those steps, losing updates.
 *
 * THE IDEA: make the 3 steps UNINTERRUPTIBLE as a group — a "critical section"
 * that only ONE thread may execute at a time. Java's built-in tool is `synchronized`.
 *
 * HOW IT WORKS:
 *   - Every Java object has an invisible "lock" (also called a "monitor").
 *   - synchronized(someObject) { ... } means: "to enter this block you must HOLD
 *     someObject's lock. Only one thread can hold it at a time. Others WAIT here."
 *   - So the read-add-write can't be split by another thread mid-way. The lost-update
 *     interleaving from Lesson 5 becomes impossible.
 *
 * COST: threads now take turns at the counter instead of truly running in parallel
 * over it. Correctness beats speed here — a fast wrong answer is worthless.
 */
public class Lesson06_1_Synchronized {

    static int counter = 0;
    static final Object lock = new Object(); // a dedicated object whose lock we use

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 8;
        int incrementsPerThread = 1_000_000;
        int expected = threadCount * incrementsPerThread;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    synchronized (lock) {   // <-- only ONE thread inside at a time
                        counter++;          //     now safely atomic as a unit
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
