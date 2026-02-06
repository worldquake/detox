package hu.detox.utils;

import kotlin.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class Reflector {
    public static final Logger logger = LoggerFactory.getLogger(Reflector.class);
    private final Class<?> on;
    private final InheritableThreadLocal<Object> instance = new InheritableThreadLocal<>();

    Reflector(final Class<?> on) {
        this.on = on;
    }

    public <T> T call(final String meth, final Object... args) {
        return (T) ReflectionUtils.invokeMethod(this.instance.get(), meth, args);
    }

    public <T> T get(final String prop) {
        try {
            return (T) this.getAccessibleField(prop).get(this.instance.get());
        } catch (final IllegalArgumentException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalArgumentException("Can not get " + prop, e);
        }
    }

    public Field getAccessibleField(final String name) {
        Class<?> clz = this.on;
        while (clz != Object.class) {
            try {
                final Field ret = clz.getDeclaredField(name);
                ret.setAccessible(true);
                return ret;
            } catch (final NoSuchFieldException nsf) {
                clz = clz.getSuperclass();
            }
        }
        throw new IllegalArgumentException(name + " is not a field in " + this.on + " hierarchy");
    }

    public Method getAccessibleMethod(final String name, final Class<?>... hints) {
        Class<?> clz = this.on;
        while (clz != Object.class) {
            final Method[] ret = clz.getDeclaredMethods();
            final Method m = this.matches(ret, name, hints);
            if (m == null) {
                clz = clz.getSuperclass();
                continue;
            }
            m.setAccessible(true);
            return m;
        }
        throw new IllegalArgumentException(name + " is not a method in " + this.on + " hierarchy");
    }

    protected Object getCurrentInstance() {
        return this.instance.get();
    }

    public List<Method> getDeclaredMethods(final String name, final Class<?>... hints) {
        final Class<?> clz = this.on;
        final List<Method> ret = new LinkedList<>();
        final Method[] ms = clz.getDeclaredMethods();
        for (final Method m : ms) {
            if (m.getName().matches(name) && this.matches(m, hints)) {
                m.setAccessible(true);
                ret.add(m);
            }
        }
        return ret;
    }

    public Set<Field> getFields(final Class<?> parCls) {
        if (parCls == null) {
            return Collections.emptySet();
        }
        final Field[] flds = parCls.getDeclaredFields();
        final Set<Field> ret = new LinkedHashSet<Field>(flds.length);
        final Class<?> sup = parCls.getSuperclass();
        final Set<Field> supMap = this.getFields(sup);
        ret.addAll(supMap);
        for (final Field f : flds) {
            ret.add(f);
        }
        return ret;
    }

    public final <T> List<Pair<Field, T>> getFieldsAndValuesOfType(final Class<T> any) {
        final Set<Field> fs = this.getFields(this.on);
        final List<Pair<Field, T>> ret = new LinkedList<>();
        for (final Field f : fs) {
            try {
                final T cur = this.getValue(f);
                if (any == null || any.isAssignableFrom(f.getType()) || cur != null && any.isAssignableFrom(cur.getClass())) {
                    ret.add(new Pair<>(f, cur));
                }
            } catch (final Exception e) {
                throw new IllegalStateException("Failed to get member " + f, e);
            }
        }
        return ret;
    }

    public final <T> List<T> getFieldValuesOfType(final Class<T> any) {
        final Set<Field> fs = this.getFields(this.on);
        final List<T> ret = new LinkedList<T>();
        for (final Field f : fs) {
            try {
                final T cur = this.getValue(f);
                if (any == null || any.isAssignableFrom(f.getType()) || cur != null && any.isAssignableFrom(cur.getClass())) {
                    ret.add(cur);
                }
            } catch (final Exception e) {
                throw new IllegalStateException("Failed to get member " + f, e);
            }
        }
        return ret;
    }

    public Class<?> getOn() {
        return this.on;
    }

    private <T> T getValue(final Field f) throws IllegalArgumentException, IllegalAccessException {
        T cur = null;
        f.setAccessible(true);
        final Object ci = this.getCurrentInstance();
        if ((f.getModifiers() & Modifier.STATIC) != 0 && ci == null) {
            cur = (T) f.get(null);
        } else if (ci != null) {
            cur = (T) f.get(ci);
        }
        return cur;
    }

    private boolean matches(final Method m, final Class<?>... hints) {
        boolean match = true;
        int i = 0;
        for (final Class<?> p : m.getParameterTypes()) {
            if (i == hints.length) {
                break;
            }
            if (hints[i] == null) {
                match = false;
                break;
            } else if (hints[i].equals(Void.class)) {
                continue;
            } else if (!p.isAssignableFrom(hints[i])) {
                match = false;
                break;
            }
            i++;
        }
        return match;
    }

    private Method matches(final Method[] ms, final String name, final Class<?>... hints) {
        for (final Method m : ms) {
            if (m.getName().matches(name) && this.matches(m, hints)) {
                return m;
            }
        }
        return null;
    }

    public void set(final String prop, final Object val) {
        try {
            this.getAccessibleField(prop).set(this.instance.get(), val);
        } catch (final IllegalArgumentException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalArgumentException("Can not set " + prop + " to " + val, e);
        }
    }

    void setCurrentInstance(final Object obj) {
        if (obj != null && !this.on.isAssignableFrom(obj.getClass())) {
            throw new IllegalArgumentException(obj + " is not " + this.on);
        }
        this.instance.set(obj);
    }

    @Override
    public String toString() {
        return String.valueOf(this.instance.get());
    }
}
