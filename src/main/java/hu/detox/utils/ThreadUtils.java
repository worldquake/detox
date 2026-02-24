package hu.detox.utils;

import hu.detox.Agent;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.measure.quantity.Duration;
import javax.measure.unit.SI;
import java.io.InterruptedIOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ThreadUtils implements UncaughtExceptionHandler {
    private static final String BLOCK = "[block]";
    private static final String SENSITIVE = "[sens]";
    private static final Logger logger = LoggerFactory.getLogger(ThreadUtils.class);

    private static final ThreadUtils INSTANCE = new ThreadUtils();

    public static void checkInterruptionIo() throws InterruptedIOException {
        ThreadUtils.checkInterruptionIo(null);
    }

    public static void checkInterruptionIo(final String msg) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Thread " + Thread.currentThread() + " interrupted" + (msg == null ? "" : ": " + msg));
        }
    }

    public static StackTraceElement[] getStacktrace() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .filter(se -> !(
                        se.getClassName().startsWith("com.intellij.rt.debugger") ||
                                se.getModuleName() != null || se.getClassName().contains("CGLIB$")))
                .toArray(StackTraceElement[]::new);
    }

    public static Exceptions exception(final Throwable e) {
        return new Exceptions(e);
    }

    public static boolean isBlock() {
        return ThreadUtils.isBlock(null);
    }

    public static boolean isBlock(Thread t) {
        if (t == null) {
            t = Thread.currentThread();
        }
        return t.getName().contains(ThreadUtils.BLOCK);
    }

    public static boolean isDaemon() {
        return ThreadUtils.isDaemon(null);
    }

    public static boolean isDaemon(Thread t) {
        if (t == null) {
            t = Thread.currentThread();
        }
        return t.isDaemon() && !t.getName().contains(ThreadUtils.SENSITIVE) && !t.getName().contains(ThreadUtils.BLOCK);
    }

    public static void setBlock(final boolean b) {
        final Thread t = Thread.currentThread();
        final boolean nb = ThreadUtils.isBlock(t);
        if (b && !nb) {
            t.setName(ThreadUtils.BLOCK + t.getName());
        } else if (!b && nb) {
            t.setName(t.getName().replace(ThreadUtils.BLOCK, org.apache.commons.lang3.StringUtils.EMPTY));
        }
    }

    public static void setDaemon(final boolean d) {
        final Thread t = Thread.currentThread();
        if (!t.isAlive()) {
            t.setDaemon(d);
        }
        final boolean nd = ThreadUtils.isDaemon(t);
        if (d && !nd) {
            t.setName(t.getName().replace(ThreadUtils.SENSITIVE, org.apache.commons.lang3.StringUtils.EMPTY));
        } else if (!d && nd) {
            t.setName(ThreadUtils.SENSITIVE + t.getName());
        }
    }

    public static void setName(final String name) {
        ThreadUtils.setName(null, name);
    }

    public static void setName(Thread t, String name) {
        if (name == null) {
            return;
        }
        if (t == null) {
            t = Thread.currentThread();
        }
        if (t.getName().contains(ThreadUtils.SENSITIVE) && !name.contains(ThreadUtils.SENSITIVE)) {
            name = ThreadUtils.SENSITIVE + name;
        }
        t.setName(name);
    }

    public static void sleep(final Amount<Duration> amount) throws InterruptedIOException {
        ThreadUtils.sleep(amount.to(SI.SECOND).getExactValue(), TimeUnit.SECONDS);
    }

    public static void sleep(final long amount, final TimeUnit unit) throws InterruptedIOException {
        final long millis = unit.toMillis(amount);
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            throw ThreadUtils.exception(e).ret(InterruptedIOException.class);
        }
    }

    public static <T extends Throwable> T uncaught(T e) {
        return ThreadUtils.uncaught(e, Thread.currentThread());
    }

    public static <T extends Throwable> T uncaught(T e, final Thread t) {
        return ThreadUtils.uncaught(e, t, null);
    }

    public static <T extends Throwable> T uncaught(T e, final Thread t, final Object msg) {
        ThreadUtils.INSTANCE.uncaughtException(e, t, msg);
        return e;
    }

    public static void waitForAll(final List<Object> wes) throws InterruptedException {
        Object wer;
        for (final Object we : wes) {
            if (we instanceof Thread) {
                ((Thread) we).join();
            } else if (we instanceof Future<?>) {
                try {
                    wer = ((Future) we).get();
                    ThreadUtils.logger.info("Returned " + we + " with " + wer);
                } catch (final ExecutionException e) {
                    ThreadUtils.logger.error("Failed " + we, e);
                }
            }
        }
    }

    public static void waitIO(final Object lock) throws InterruptedIOException {
        try {
            lock.wait();
        } catch (final InterruptedException ex) {
            throw new InterruptedIOException(ex.toString());
        }
    }

    public static void waitThreads(final boolean killThreads) {
        final Thread t = Thread.currentThread();
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        outer:
        while (!threads.isEmpty()) {
            threads = Thread.getAllStackTraces().keySet();
            if (!ThreadUtils.isBlock(t)) {
                threads.remove(t);
            }
            for (final Iterator<Thread> ith = threads.iterator(); ith.hasNext(); ) {
                final Thread th = ith.next();
                if (!ThreadUtils.isDaemon(th)) {
                    try {
                        th.join();
                    } catch (final InterruptedException e) {
                        break outer;
                    }
                }
                ith.remove();
            }
        }
        if (Agent.debug) {
            System.err.println("Exited all threads");
        }
    }

    private boolean uncaught;

    private ThreadUtils() {
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(final Thread t, final Throwable e) {
        this.uncaughtException(e, t, null);
    }

    private synchronized void uncaughtException(final Throwable e, final Thread t, Object msg) {
        if (this.uncaught) {
            return;
        }
        this.uncaught = true;
        try {
            String smsg = "Uncaught exception in thread " + t;
            SystemUtils.returnCode++;
            smsg += ", ret=" + SystemUtils.returnCode;
            final UncaughtExceptionHandler ueh = Thread.currentThread().getUncaughtExceptionHandler();
            System.err.println(smsg);
            ueh.uncaughtException(t, e);
        } finally {
            this.uncaught = false;
        }
    }
}
