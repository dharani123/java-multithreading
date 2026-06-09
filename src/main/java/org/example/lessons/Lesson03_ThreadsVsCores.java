package org.example.lessons;

/**
 * LESSON 3 — Threads vs CPU cores (answering your main question!)
 *
 * MYTH: "one thread = one CPU core."  -> FALSE.
 *
 * TRUTH:
 *   - A CPU CORE is hardware that can actively execute ONE thread at any given instant.
 *   - A THREAD is a software construct. You can create THOUSANDS of them.
 *   - The OS SCHEDULER rapidly switches cores between threads ("time slicing"),
 *     giving each a tiny slice (a few milliseconds). This switching is so fast it
 *     LOOKS like everything runs at once. That illusion is CONCURRENCY.
 *   - When threads literally run at the SAME instant on DIFFERENT cores, that is
 *     true PARALLELISM. Parallelism is limited by your core count; concurrency is not.
 *
 * So: with 16 cores you can run AT MOST 16 threads truly in parallel at one instant,
 * but you can have 1000 threads "in progress" concurrently.
 *
 * This program creates FAR more threads than you have cores, to prove it works fine.
 */
public class Lesson03_ThreadsVsCores {
    public static void main(String[] args) throws InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("This machine reports " + cores + " available processors (cores/hardware threads).");

        int howMany = 50; // way more threads than cores — totally allowed
        System.out.println("Creating " + howMany + " threads anyway...\n");

        Thread[] threads = new Thread[howMany];
        for (int i = 0; i < howMany; i++) {
            final int id = i;
            threads[i] = new Thread(() -> {
                // Each thread just announces itself and naps briefly.
                System.out.println("Thread #" + id + " running on OS thread '"
                        + Thread.currentThread().getName() + "'");
                sleep(2000);
            }, "t-" + i);
        }

        for (Thread t : threads) t.start(); // launch all 50
        for (Thread t : threads) t.join();  // wait for all 50

        System.out.println("\nAll " + howMany + " threads finished — on just " + cores + " cores.");
        System.out.println("They didn't all run at the SAME instant; the OS scheduler "
                + "interleaved them across the cores. That is CONCURRENCY.");
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
