package com.marketplace.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Console logging that always shows which thread printed the line and its state. */
public final class ThreadLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private ThreadLog() {
    }

    /** synchronized so lines from different threads never get mixed together. */
    public static synchronized void log(String message) {
        Thread t = Thread.currentThread();
        System.out.printf("[%s] [%-22s %-13s] %s%n",
                LocalTime.now().format(TIME), t.getName(), t.getState(), message);
    }

    /** Lets one thread report another thread's state (e.g. WAITING, TERMINATED). */
    public static synchronized void logState(Thread other) {
        System.out.printf("[%s]   state of %-22s = %s%n",
                LocalTime.now().format(TIME), other.getName(), other.getState());
    }
}
