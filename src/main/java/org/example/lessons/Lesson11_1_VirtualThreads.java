package org.example.lessons;

/**
 * LESSON 11.1 — Virtual threads (Java 21): millions of cheap threads
 *
 * THE PROBLEM they solve (callback to Lesson 7's I/O-bound case):
 *   A classic Java thread ("PLATFORM thread") is a thin wrapper over an OS thread.
 *   It's EXPENSIVE: ~1 MB of stack reserved, and the OS can only juggle a few thousand.
 *   For I/O-bound work that means: while a thread waits on the network, it ties up a
 *   whole OS thread doing NOTHING. Want 100,000 simultaneous connections? You can't
 *   afford 100,000 platform threads. (That's why async/callbacks/reactive exist — to
 *   avoid one-thread-per-request. But async code is hard to read.)
 *
 * THE FIX — VIRTUAL THREADS:
 *   A virtual thread is managed by the JVM, NOT the OS. It's tiny (a few hundred bytes,
 *   growable). You can have MILLIONS. The JVM runs a virtual thread by "mounting" it onto
 *   a small pool of real OS threads called CARRIER threads (about one per CPU core).
 *
 *   THE KEY TRICK: when a virtual thread BLOCKS (Thread.sleep, socket read, etc.), the
 *   JVM UNMOUNTS it from its carrier and parks it cheaply — freeing that carrier to run
 *   a DIFFERENT virtual thread. When the blocking call is ready, the virtual thread is
 *   re-mounted and continues. So a handful of OS threads can run a million virtual ones,
 *   as long as they spend most of their time waiting.
 *
 *   => You write SIMPLE blocking code (no callbacks), and it scales like async.
 *
 * CREATING them (three ways):
 *   Thread.startVirtualThread(runnable);                 // start one immediately
 *   Thread.ofVirtual().name("x").start(runnable);        // builder, more control
 *   Executors.newVirtualThreadPerTaskExecutor();         // a NEW virtual thread per task
 *
 * GOLDEN RULES:
 *   - Use virtual threads for I/O-BOUND work (lots of waiting). Not for CPU-bound —
 *     they don't add compute power; you're still limited by cores (Lesson 7).
 *   - DON'T pool virtual threads. They're cheap — create one per task and let it die.
 *   - A virtual thread is still a java.lang.Thread; same API you already know.
 */
public class Lesson11_1_VirtualThreads {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Cores: " + Runtime.getRuntime().availableProcessors() + "\n");

        // 1) A virtual thread is just a Thread. Look at how it prints itself.
        System.out.println("=== 1) What a virtual thread looks like ===");
        Thread vt = Thread.ofVirtual().name("my-vthread").start(() -> {
            Thread t = Thread.currentThread();
            System.out.println("  running on: " + t);
            System.out.println("  isVirtual()? " + t.isVirtual());
            // The toString shows the VIRTUAL thread and, in brackets, the CARRIER (real
            // OS thread, e.g. ForkJoinPool-1-worker-N) it's currently mounted on.
        });
        vt.join();

        // 2) Compare a platform thread.
        System.out.println("\n=== 2) Platform thread for contrast ===");
        Thread pt = Thread.ofPlatform().name("my-pthread").start(() -> {
            Thread t = Thread.currentThread();
            System.out.println("  running on: " + t);
            System.out.println("  isVirtual()? " + t.isVirtual());
        });
        pt.join();

        // 3) Many virtual threads share a FEW carrier (OS) threads.
        //    Print the carrier each task is mounted on — you'll see only ~#cores distinct
        //    carriers even though there are many virtual threads.
        System.out.println("\n=== 3) Many virtual threads, few carriers ===");
        int n = 8;
        Thread[] threads = new Thread[n];
        for (int i = 0; i < n; i++) {
            final int id = i;
            threads[i] = Thread.ofVirtual().start(() -> {
                String carrier = carrierName();
                System.out.printf("  vthread %d is carried by %s%n", id, carrier);
                sleep(50);
            });
        }
        for (Thread t : threads) t.join();

        System.out.println("\nTakeaway: a virtual thread is a normal Thread you can make by the million;");
        System.out.println("the JVM mounts it on a small set of carrier OS threads only while it runs.");
    }

    /** Extract the carrier (real OS thread) name from a virtual thread's toString(). */
    static String carrierName() {
        // VirtualThread.toString() looks like: "VirtualThread[#23]/runnable@ForkJoinPool-1-worker-3"
        String s = Thread.currentThread().toString();
        int at = s.lastIndexOf('@');
        return at >= 0 ? s.substring(at + 1) : s;
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
