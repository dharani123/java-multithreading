package org.example.lessons;

/**
 * LESSON 2 — Creating your own thread
 *
 * There are two classic ways to define the work a thread should run:
 *
 *   (A) Implement Runnable  -> preferred. Runnable is just "a task": one run() method.
 *   (B) Extend Thread       -> works, but ties your task to the Thread class. Avoid normally.
 *
 * KEY IDEA: creating a Thread object does NOTHING yet. You must call start().
 *   - start() asks the OS to create a NEW thread and run run() ON THAT THREAD.
 *   - calling run() directly would just run it on the CURRENT thread (no new thread!).
 *
 * Watch the output: the main thread and the worker thread print INTERLEAVED,
 * because they run independently at the same time.
 */
public class Lesson02_YourFirstThread {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("[" + name() + "] start of main");

        // (A) Runnable = the task. A lambda is the cleanest way to write one.
        Runnable task = () -> {
            for (int i = 1; i <= 5; i++) {
                System.out.println("[" + name() + "] worker counting: " + i);
                sleep(3000);
            }
        };

        // Wrap the task in a Thread and give it a readable name.
        Thread worker = new Thread(task, "worker-1");

        worker.start();   // <-- THIS spawns a new thread. run() now executes elsewhere.
        // worker.run();  // <-- (don't) this would run on main, no concurrency at all.

        // Meanwhile, main keeps going independently:
        for (int i = 1; i <= 5; i++) {
            System.out.println("[" + name() + "] main doing its own thing: " + i);
            sleep(300);
        }

        // join() = "wait here until 'worker' has finished." Without it, main might
        // reach the last line before the worker is done.

        System.out.println("[" + name() + "] both threads finished. main ends.");
    }

    static String name() { return Thread.currentThread().getName(); }

    static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
