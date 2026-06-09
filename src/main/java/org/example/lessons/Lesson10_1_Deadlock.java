package org.example.lessons;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * LESSON 10.1 — Deadlock: two threads that freeze each other forever
 *
 * A DEADLOCK is when two (or more) threads each hold a lock the OTHER needs, so neither
 * can ever proceed. Nothing crashes, no error prints — the program just HANGS. This is
 * one of the nastiest concurrency bugs because it's silent and often intermittent.
 *
 * THE CLASSIC RECIPE (we build it on purpose):
 *   Thread A: lock(lock1) ... then tries to lock(lock2)
 *   Thread B: lock(lock2) ... then tries to lock(lock1)
 *   If A grabs lock1 and B grabs lock2 at the same time, then:
 *     - A waits forever for lock2 (B holds it)
 *     - B waits forever for lock1 (A holds it)
 *   => frozen. This is a "deadly embrace."
 *
 * THE FOUR CONDITIONS (all must hold for deadlock — break ANY one to prevent it):
 *   1. Mutual exclusion   - locks are exclusive
 *   2. Hold and wait      - you hold one lock while waiting for another
 *   3. No preemption      - a lock can't be force-taken from a thread
 *   4. Circular wait       - A waits on B waits on A  <-- easiest one to break
 *
 * THE FIX (next file behavior, shown here too): impose a GLOBAL LOCK ORDERING — every
 * thread always acquires locks in the SAME order (lock1 then lock2, never the reverse).
 * That breaks "circular wait": no cycle can form.
 *
 * A WATCHDOG thread uses the JVM's built-in ThreadMXBean.findDeadlockedThreads() to
 * detect the freeze, print the culprits, and exit — otherwise this program runs forever.
 */
public class Lesson10_1_Deadlock {

    static final Object lock1 = new Object();
    static final Object lock2 = new Object();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Part A: BROKEN — opposite lock order causes deadlock ===");
        startWatchdog();           // detects the freeze and exits the JVM
        runBroken();               // this will deadlock; watchdog reports it and exits
        // (we never reach here — the watchdog calls System.exit on detection)
    }

    /** Two threads acquire the two locks in OPPOSITE orders -> circular wait -> deadlock. */
    static void runBroken() throws InterruptedException {
        Thread a = new Thread(() -> {
            synchronized (lock1) {
                System.out.println("  [A] holds lock1, wants lock2...");
                sleep(100);                       // give B time to grab lock2 first
                synchronized (lock2) {            // <-- blocks forever (B holds lock2)
                    System.out.println("  [A] got both (never prints)");
                }
            }
        }, "Thread-A");

        Thread b = new Thread(() -> {
            synchronized (lock2) {                // opposite order!
                System.out.println("  [B] holds lock2, wants lock1...");
                sleep(100);
                synchronized (lock1) {            // <-- blocks forever (A holds lock1)
                    System.out.println("  [B] got both (never prints)");
                }
            }
        }, "Thread-B");

        a.start();
        b.start();
        a.join();
        b.join();
    }

    /**
     * Watchdog: every 500ms ask the JVM if any threads are deadlocked. If so, print them
     * and exit. This is also how you'd diagnose a hung production app (jstack does the same).
     */
    static void startWatchdog() {
        Thread watchdog = new Thread(() -> {
            ThreadMXBean mx = ManagementFactory.getThreadMXBean();
            while (true) {
                sleep(500);
                long[] deadlocked = mx.findDeadlockedThreads(); // null if none
                if (deadlocked != null) {
                    System.out.println("\n  !! DEADLOCK DETECTED by watchdog !!");
                    ThreadInfo[] infos = mx.getThreadInfo(deadlocked, true, true);
                    for (ThreadInfo info : infos) {
                        System.out.println("    - " + info.getThreadName()
                                + " is BLOCKED waiting on lock held by "
                                + info.getLockOwnerName());
                    }
                    System.out.println("\n  The two threads are frozen forever. Exiting.\n");
                    showTheFix();
                    System.exit(0);
                }
            }
        }, "watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    /**
     * Demonstrates the cure: both threads lock in the SAME order, so no cycle forms.
     *
     * IMPORTANT: we must use FRESH lock objects here. The original Thread-A/Thread-B are
     * still frozen forever HOLDING lock1 and lock2, so reusing them would just block again
     * (that very mistake was a bug in the first version of this lesson!).
     */
    static void showTheFix() {
        System.out.println("=== Part B: FIXED — same lock order, no deadlock ===");
        final Object fixLock1 = new Object();   // fresh locks, not the frozen ones
        final Object fixLock2 = new Object();
        Runnable bothInOrder = () -> {
            // EVERY thread: fixLock1 first, THEN fixLock2. Always the same order.
            synchronized (fixLock1) {
                synchronized (fixLock2) {
                    System.out.println("  [" + Thread.currentThread().getName()
                            + "] acquired both in order, did its work.");
                }
            }
        };
        Thread a = new Thread(bothInOrder, "Fixed-A");
        Thread b = new Thread(bothInOrder, "Fixed-B");
        a.start();
        b.start();
        try { a.join(); b.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        System.out.println("  Both finished. Consistent lock ordering breaks 'circular wait'.");
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
