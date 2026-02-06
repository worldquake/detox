package hu.detox.utils;

import java.io.InterruptedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class Exceptions {
    private Throwable exc;

    public static Exceptions create(final Throwable e) {
        return new Exceptions(e);
    }

    private static boolean is(final Throwable root, final Class<? extends Throwable>... cs) {
        for (final Class<? extends Throwable> c : cs) {
            if (c.isAssignableFrom(root.getClass())) {
                return true;
            }
        }
        return false;
    }

    public static Throwable findCause(Throwable root, final Class<? extends Throwable>... cs) {
        Throwable p;
        do {
            if (is(root, cs)) {
                return root;
            }
            p = root;
            root = root.getCause();
        } while (root != null && root != p);
        return null;
    }

    public Exceptions(final Throwable e) {
        this.exc = e;
    }

    public <T extends Throwable> boolean move(final Class<T> clz) {
        final T c = this.ret(clz);
        if (c != null) {
            this.exc = c;
        }
        return c != null;
    }

    public Throwable ret() {
        return this.exc;
    }

    public <T extends Throwable> T ret(final Class<T> clz) {
        if (clz == InterruptedIOException.class) {
            final Throwable c = findCause(this.exc, clz, InterruptedException.class);
            if (c instanceof InterruptedIOException iio) {
                return (T) iio;
            } else if (c != null) {
                return (T) new InterruptedIOException("Waiting failed: " + c + " on " + Thread.currentThread());
            }
        }
        final T t = (T) findCause(this.exc, clz);
        if (t != null) {
            return t;
        }
        return null;
    }

    public <T extends Throwable> Exceptions swallow(final Class<T> clz, final String reason) {
        final T c = (T) findCause(this.exc, clz);
        if (c != null) {
            System.err.println("Swallowed " + this.exc + ", reason=" + reason);
        }
        return this;
    }

    public Exceptions thr() {
        if (this.exc instanceof Error) {
            throw (Error) this.exc;
        } else if (this.exc instanceof RuntimeException) {
            throw (RuntimeException) this.exc;
        }
        return this;
    }

    public <T extends Throwable> Exceptions thr(final Class<T> clz) throws T {
        return this.thr(null, clz);
    }

    public <T extends Throwable> Exceptions thr(final String reas, final Class<T> clz) throws T {
        Constructor<T> c;
        try {
            c = clz.getConstructor(String.class, Throwable.class);
        } catch (final NoSuchMethodException e) {
            try {
                c = clz.getConstructor(String.class);
            } catch (NoSuchMethodException | SecurityException e1) {
                throw new IllegalStateException("Can't throw " + clz + " for " + this.exc, e1);
            }
        }
        try {
            throw c.getParameterCount() == 2 ? c.newInstance(reas, this.exc) : c.newInstance(reas);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException e) {
            throw new IllegalStateException("Can't throw " + clz + " for " + this.exc, e);
        }
    }

    public void uncaught() {
        ThreadUtils.uncaught(this.exc);
    }

    public <T extends Throwable> Exceptions unwrap(final Class<T> clz) throws T {
        final T t = this.ret(clz);
        if (t != null) {
            throw t;
        }
        return this;
    }
}
