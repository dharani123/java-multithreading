package org.example.lessons;

/**
 * LESSON 1 — What is a thread? (The baseline)
 *
 * A PROCESS is your running program (one JVM = one process).
 * A THREAD is a single sequence of instructions executing INSIDE that process.
 *
 * Every Java program already has ONE thread before you write any thread code:
 * the "main" thread. main() runs on it. So you have always been doing
 * "threading" — just with a single thread.
 *
 * Run this and notice:
 *   1) Everything happens strictly top-to-bottom (sequential).
 *   2) Thread.currentThread().getName() tells you WHICH thread is running a line.
 */
public class Lesson01_WhatIsAThread {
    public static void main(String[] args) {
        // Which thread is executing main()? It's the one the JVM created for us.
        String me = Thread.currentThread().getName();
        System.out.println("Hello! I am running on thread: " + me);

        // A "task": boil water, then make tea. Done one after another.
        boilWater();
        makeTea();

        System.out.println("Done. All of this ran on the single '" + me + "' thread.");
    }

    static void boilWater() {
        System.out.println("[" + Thread.currentThread().getName() + "] boiling water...");
        sleep(5000); // pretend this takes 500ms of real work/waiting
        System.out.println("[" + Thread.currentThread().getName() + "] water is boiled.");
    }

    static void makeTea() {
        System.out.println("[" + Thread.currentThread().getName() + "] making tea...");
        sleep(5000);
        System.out.println("[" + Thread.currentThread().getName() + "] tea is ready.");
    }

    // Helper: pause this thread for the given milliseconds.
    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // good practice: restore the flag
        }
    }
}
