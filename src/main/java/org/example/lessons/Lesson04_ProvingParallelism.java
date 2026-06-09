package org.example.lessons;

/**
 * LESSON 4 — Proving cores give REAL speedup (parallelism you can measure)
 *
 * In Lesson 3 the threads mostly slept, so extra cores didn't help much.
 * Here we give each thread HEAVY CPU WORK (no sleeping). Now cores matter:
 * splitting the work across cores finishes faster because the cores genuinely
 * compute at the same instant.
 *
 * We do the SAME total amount of work twice:
 *   1) Single-threaded  -> one core does everything.
 *   2) Multi-threaded   -> work split across N threads, OS spreads them over cores.
 * ...and we time both. On a multi-core machine, run 2 should be clearly faster.
 *
 * NOTE: speedup is never perfectly Nx (overhead, memory bandwidth, scheduling),
 * and it only helps for CPU-BOUND work. We'll cover I/O-bound work later.
 */
public class Lesson04_ProvingParallelism {

    // A deliberately CPU-heavy function: sum of sqrt over a big range.
    static double crunch(long from, long to) {
        double acc = 0;
        for (long i = from; i < to; i++) {
            acc += Math.sqrt(i) * Math.sin(i);
        }
        return acc;
    }

    public static void main(String[] args) throws InterruptedException {
        int cores = Runtime.getRuntime().availableProcessors();
        long total = 400_000_000L; // total iterations of work
        System.out.println("Cores available: " + cores);
        System.out.println("Total work units: " + total + "\n");

        // ---------- Run 1: single thread ----------
        long t0 = System.currentTimeMillis();
        double r1 = crunch(0, total);
        long single = System.currentTimeMillis() - t0;
        System.out.println("Single-threaded:  " + single + " ms  (result=" + (long) r1 + ")");

        // ---------- Run 2: split across N threads ----------
        int n = cores;                 // use as many threads as cores
        long chunk = total / n;        // each thread handles an equal slice
        Thread[] threads = new Thread[n];
        double[] partials = new double[n]; // each thread writes to ITS OWN slot (no clashing)

        long t1 = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final long from = i * chunk;
            final long to = (i == n - 1) ? total : from + chunk; // last thread mops up remainder
            threads[i] = new Thread(() -> partials[idx] = crunch(from, to));
            threads[i].start();
        }
        for (Thread t : threads) t.join(); // wait for all slices to finish

        double r2 = 0;
        for (double p : partials) r2 += p;  // combine partial results
        long multi = System.currentTimeMillis() - t1;
        System.out.println("Multi-threaded:   " + multi + " ms  (result=" + (long) r2 + ", threads=" + n + ")");

        // ---------- Verdict ----------
        System.out.printf("%nSpeedup: %.2fx faster with %d threads.%n",
                (double) single / Math.max(multi, 1), n);
        System.out.println("Same result both ways — we just used more cores to get there sooner.");
    }
}
