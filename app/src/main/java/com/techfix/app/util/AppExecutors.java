package com.techfix.app.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppExecutors {
    private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor();

    private AppExecutors() { }

    public static void run(Runnable r) {
        DB_EXECUTOR.execute(r);
    }
}
