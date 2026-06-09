package org.example.lessons;

/**
 * LESSON 6.5 — SEEING the visibility problem (why volatile matters)
 *
 * Two runs of the SAME logic:
 *   PASS "broken"   -> flag is a PLAIN boolean. The worker likely NEVER sees the
 *                      update and loops forever (we cut it off with a timeout).
 *   PASS "fixed"    -> flag is VOLATILE. The worker sees the update almost instantly.
 *
 * WHY broken hangs:
 *   - The JIT compiler hoists the plain-boolean read OUT of the tight loop (reads it
 *     once into a register), and/or the worker's CPU core keeps reading a STALE cached
 *     copy. Either way main's write to RAM is invisible to the worker.
 *
 * WHY volatile fixes it:
 *   - volatile forbids caching the value in a register (re-reads memory every time)
 *     and forces cross-core cache coherence, so main's write is seen immediately.
 *     Formally: a volatile write happens-before a subsequent volatile read.
 *
 * The worker threads are DAEMON threads so a stuck one won't keep the JVM alive.
 */
public class Lesson06_5_VisibilityDemo {

    // Two separate flags so the two passes don't interfere.
    static boolean plainRunning = true;        // NOT volatile  -> visibility NOT guaranteed
    static volatile boolean volatileRunning = true; // volatile -> visibility guaranteed

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Cores: " + Runtime.getRuntime().availableProcessors());
        System.out.println();

        runPass("BROKEN  (plain boolean)", () -> { while (plainRunning) { /* spin */ } },
                () -> plainRunning = false);

        runPass("FIXED   (volatile boolean)", () -> { while (volatileRunning) { /* spin */ } },
                () -> volatileRunning = false);

        System.out.println("\nDone. Note: 'BROKEN' behaviour depends on JIT/CPU and may differ");
        System.out.println("between machines or runs — that unpredictability is exactly the danger.");
        System.exit(0); // force-exit in case a daemon worker is still spinning
    }

    /**
     * Starts a worker running 'loop', waits 200ms, then runs 'stop' to flip the flag,
     * and measures how long until the worker actually notices (or times out at 3s).
     */
    static void runPass(String label, Runnable loop, Runnable stop) throws InterruptedException {
        Thread worker = new Thread(loop, "worker");
        worker.setDaemon(true); // so a stuck worker can't block JVM shutdown
        worker.start();

        Thread.sleep(200);              // let the worker get deep into its loop
        long t0 = System.nanoTime();
        stop.run();                     // main flips the flag here
        worker.join(3000);              // wait up to 3 seconds for the worker to stop

        if (worker.isAlive()) {
            System.out.println(label + ": worker STILL RUNNING after 3000 ms  -> never saw the update (HUNG)");
        } else {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.println(label + ": worker stopped ~" + ms + " ms after the flag was set");
        }
    }
}
