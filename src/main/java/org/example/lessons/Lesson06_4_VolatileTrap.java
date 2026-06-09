package org.example.lessons;

/**
 * LESSON 6.4 — Fix #4 that ISN'T: the `volatile` trap
 *
 * Beginners hear "volatile makes a variable thread-safe" and slap it on a counter.
 * THIS LESSON PROVES THAT'S WRONG. We mark counter `volatile` and the race is STILL there.
 *
 * WHAT volatile ACTUALLY DOES (two things, neither is mutual exclusion):
 *   1. VISIBILITY: a write by one thread is immediately visible to others. Without
 *      volatile, threads may cache a stale copy and never see updates (see note below).
 *   2. ORDERING: prevents certain instruction reorderings around the variable.
 *
 * WHAT volatile DOES NOT DO:
 *   - It does NOT make counter++ atomic. It's still READ-ADD-WRITE (3 steps).
 *     Two threads can still both read 41 and both write 42. Updates still get lost.
 *
 * RULE OF THUMB:
 *   - volatile  -> correct for a simple FLAG that one thread sets and others READ
 *                  (e.g. a `volatile boolean running` to stop a loop). No read-then-write.
 *   - For READ-MODIFY-WRITE (counter++, check-then-act) -> you need synchronized,
 *     a Lock, or an Atomic. volatile is NOT enough.
 *
 * Run it: still wrong, still random. Compare to 6.1/6.2/6.3 which are correct.
 *
 * (Bonus demo at the bottom: where volatile is the RIGHT tool — a stop flag.)
 */
public class Lesson06_4_VolatileTrap {

    static volatile int counter = 0;  // volatile — but still NOT safe for ++

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 8;
        int incrementsPerThread = 1_000_000;
        int expected = threadCount * incrementsPerThread;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    counter++;   // volatile does NOT fix this read-add-write race
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        System.out.println("=== volatile on a counter (WRONG use) ===");
        System.out.println("Expected total: " + expected);
        System.out.println("Actual total:   " + counter);
        System.out.println("Correct?        " + (counter == expected) + "   <-- still broken!");
        System.out.println();

        // ---- Where volatile IS correct: a flag one thread writes, another reads ----
        stopFlagDemo();
    }

    static volatile boolean running = true; // the RIGHT use of volatile

    static void stopFlagDemo() throws InterruptedException {
        System.out.println("=== volatile on a stop-flag (CORRECT use) ===");
        Thread worker = new Thread(() -> {
            long loops = 0;
            while (running) {   // reads the flag; volatile guarantees it SEES the change
                loops++;
            }
            System.out.println("Worker saw running=false and stopped after " + loops + " loops.");
        });
        worker.start();
        Thread.sleep(200);
        running = false;        // main writes the flag; worker promptly notices
        worker.join();
        System.out.println("Stop-flag worked because there's no read-modify-write — just set & see.");
    }
}
