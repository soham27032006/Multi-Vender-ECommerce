package com.marketplace.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;

/**
 * Gives pool threads readable names ("buyer-1") instead of "pool-1-thread-1",
 * and remembers them so we can print their states later.
 */
public class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final boolean daemon;
    private final List<Thread> created = new ArrayList<>();
    private int count = 0;

    public NamedThreadFactory(String prefix, boolean daemon) {
        this.prefix = prefix;
        this.daemon = daemon;
    }

    /** synchronized: the pool may create threads from more than one thread. */
    @Override
    public synchronized Thread newThread(Runnable task) {
        count++;
        Thread t = new Thread(task, prefix + "-" + count);
        // Daemon threads don't keep the JVM alive, so a forgotten pool can't block exit.
        t.setDaemon(daemon);
        created.add(t);
        return t;
    }

    public synchronized List<Thread> getThreads() {
        return new ArrayList<>(created);
    }
}
