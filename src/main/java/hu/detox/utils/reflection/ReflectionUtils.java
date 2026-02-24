package hu.detox.utils.reflection;

import hu.detox.parsers.XmlUtils;
import hu.detox.utils.ThreadUtils;
import hu.detox.utils.strings.StringUtils;
import kotlin.Pair;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.text.similarity.LevenshteinDetailedDistance;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.*;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

//CHOFF TODO simplify this later
public final class ReflectionUtils {
    private static final Map<Class<?>, Reflector> CACHE = new HashMap<Class<?>, Reflector>();

    public static <T> T bulk(final Collection<?> coll, final Class<?>... clz) {
        return (T) ReflectionUtils.bulk((Iterable<?>) coll, clz);
    }

    public static <T> T bulk(final Iterable<?> coll, final Class<?>... clz) {
        return (T) ReflectionUtils.bulk(coll.iterator(), clz);
    }

    public static <T> T bulk(final Iterator<?> coll, final Class<?>... clz) {
        return (T) Proxy.newProxyInstance(XmlUtils.class.getClassLoader(), clz, (proxy, method, args) -> {
            Object ret = null;
            while (coll.hasNext()) {
                ret = method.invoke(coll.next(), args);
            }
            return ret;
        });
    }

    public static <T> T bulk(final Object[] coll, final Class<?>... clz) {
        return (T) ReflectionUtils.bulk((List<?>) Arrays.asList(coll), clz);
    }

    public static <R> R convertClassTypeTo(Class<?> parClz, final Object parObj) {
        if (parObj == null || parClz.isAssignableFrom(parObj.getClass())) {
            return (R) parObj;
        }
        parClz = ReflectionUtils.toWrapper(parClz);
        R ret;
        try {
            final Method m = ReflectionUtils.getMethod(parClz, "valueOf", parObj.getClass());
            ret = (R) m.invoke(null, parObj);
        } catch (final NoSuchMethodException nsme) {
            ret = (R) ReflectionUtils.getInstance(parClz, parObj);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException("The " + parClz + ".valueOf method failed to called for " + parObj, e);
        } catch (final InvocationTargetException e) {
            throw new IllegalArgumentException("The " + parClz + ".valueOf method for '" + parObj + "' thrown an exception", e.getCause()); //NOCH Unwrapped
        }
        return ret;
    }

    public static final <T extends Throwable> T extendMessage(final T me, String msg) {
        if (msg == null) {
            return me;
        }
        final Field f = ReflectionUtils.getCaller(me).getAccessibleField("detailMessage");
        try {
            final String detailMsg = StringUtils.trimToNull((String) f.get(me));
            if (me instanceof SQLException) {
                msg += ", errc=" + ((SQLException) me).getErrorCode() + ", state=" + ((SQLException) me).getSQLState();
            }
            if (detailMsg == null) {
                f.set(me, msg);
            } else {
                f.set(me, detailMsg + ", " + msg);
            }
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return me;
    }

    private static Class<?> get(final Object me) {
        if (me == null || me.equals(Class.class)) {
            return null;
        }
        if (me instanceof Class) {
            return (Class) me;
        }
        return me.getClass();
    }

    public static <T extends Annotation> T getAnnotation(final Class<?> parClz, final Class<T> parAnno) {
        T ret = null;
        Class<?> clz = parClz;
        do {
            ret = clz.getAnnotation(parAnno);
            clz = clz.getSuperclass();
        } while (ret == null && clz != null && clz != Object.class);
        return ret;
    }

    public static <T extends Annotation> T getAnnotation(final Class<T> clz, final Annotation[] parAnnos) {
        T ret = null;
        for (final Annotation a : parAnnos) {
            if (clz.isAssignableFrom(a.getClass())) {
                ret = (T) a;
                break;
            }
        }
        return ret;
    }

    public static <T extends Annotation> T getAnnotation(final Field parField, final Class<T> parAnno) {
        T ret = parField.getAnnotation(parAnno);
        final Target t = parAnno.getAnnotation(Target.class);
        if (ret == null && (t == null || ArrayUtils.contains(t.value(), ElementType.TYPE))) {
            ret = ReflectionUtils.getAnnotation(parField.getDeclaringClass(), parAnno);
            if (ret == null) {
                try {
                    final Field f = getAccessibleField(false, parField.getDeclaringClass().getSuperclass(), parField.getName());
                    ret = ReflectionUtils.getAnnotation(f, parAnno);
                } catch (final IllegalArgumentException ia) {
                    ret = null;
                }
            }
        }
        return ret;
    }

    public static Reflector getCaller(final Object me) {
        return ReflectionUtils.getCaller(me, els -> els[4]);
    }

    public static Reflector getCaller(final Object me, final Function<StackTraceElement[], StackTraceElement> stack) {
        try {
            Class<?> key = ReflectionUtils.get(me);
            if (key == null) {
                key = Class.forName(stack.apply(ThreadUtils.getStacktrace()).getClassName());
            }
            //System.out.println(ReflectionUtils.class.getClassLoader() + " - " + Reflector.class.getClassLoader());
            Reflector refutil = ReflectionUtils.CACHE.get(key);
            if (refutil == null) {
                refutil = new Reflector(key);
                ReflectionUtils.CACHE.put(key, refutil);
            }
            if (me != null && !(me instanceof Class)) {
                refutil.setCurrentInstance(me);
            } else {
                refutil.setCurrentInstance(null);
            }
            return refutil;
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException("Invalid call", e);
        }
    }

    public static <T> T getConventionalInstance(final Class<T> parPackageClazz, final String parName, final Object... parNonNullArgs) {
        try {
            return ReflectionUtils.getConventionalInstanceOf(parPackageClazz, parName, parNonNullArgs);
        } catch (final Exception ex) { // NOCS: We re-throw unconditionally
            throw new IllegalArgumentException(parPackageClazz + "+" + parName + " has no constuction option for " + Arrays.toString(parNonNullArgs), ex);
        }
    }

    public static <T> T getConventionalInstance(final Class<T> parPackageClazz, final String[] parName, final Object... parNonNullArgs) {
        try {
            return ReflectionUtils.getConventionalInstanceOf(parPackageClazz, parName, parNonNullArgs);
        } catch (final Exception ex) { // NOCS: We re-throw unconditionally
            throw new IllegalArgumentException(
                    parPackageClazz + "+" + Arrays.toString(parName) + " has no constuction option for " + Arrays.toString(parNonNullArgs), ex);
        }
    }

    public static <T> T getConventionalInstanceOf(final Class<T> parPackageClass, final String parName, final Object... parNonNullArgs)
            throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        final String[] args = new String[]{parName};
        return ReflectionUtils.getConventionalInstanceOf(parPackageClass, args, parNonNullArgs);
    }

    public static <T> T getConventionalInstanceOf(final Class<T> parPackageClass, final String[] parNames, final Object... parNonNullArgs)
            throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        final Class<?> fClz = ReflectionUtils.loadConventional(parPackageClass, parNames);
        return (T) ReflectionUtils.getInstanceOf(fClz, parNonNullArgs);
    }

    /**
     * Tries to instantiate a non-default packaged class with the given constructor parameters. Continously tries to eliminate the tailing package names to find
     * an instance, and if no instance found it will report the failure as exception.
     *
     * @param parFullClazz   The class name (with package), that can contain one '/' that is used as '.', but will not try to remove tailing packages if the package before
     *                       '/' is was tried.
     * @param parNonNullArgs The arguments for constructor.
     * @return The class instance.
     * @throws ClassNotFoundException Thrown if no class found.
     */
    public static Object getFallbackInstance(final String parFullClazz, final Object... parNonNullArgs) throws ClassNotFoundException {
        int indOfDot = parFullClazz.lastIndexOf('.');
        if (indOfDot == -1) {
            throw new IllegalStateException(parFullClazz + " must be a full classname with package");
        }
        final String cn = parFullClazz.replace('/', '.').substring(indOfDot + 1);
        String pack = parFullClazz.substring(0, indOfDot);
        final List<Class<?>> contArgs = new LinkedList<Class<?>>();
        for (final Object o : parNonNullArgs) {
            contArgs.add(o.getClass());
        }
        int per = pack.indexOf('/');
        ClassNotFoundException cnf = null;
        do {
            try {
                final String tryClazz = pack.replace('/', '.') + '.' + cn;
                return ReflectionUtils.getInstanceOf(tryClazz, parNonNullArgs);
            } catch (final ClassNotFoundException e) {
                if (cnf == null) {
                    cnf = e;
                }
                if (per == -2) {
                    throw cnf;
                }
                indOfDot = pack.lastIndexOf('.');
                if (indOfDot < per) {
                    indOfDot = per;
                    per = -2;
                } else if (indOfDot == -1) {
                    per = -2;
                }
                if (indOfDot >= 0) {
                    pack = pack.substring(0, indOfDot);
                }
            } catch (final
            Exception e) { // NOCS: We re-throw, but this is just to handle several well known problem in one place
                throw new IllegalArgumentException(parFullClazz + " is not a valid fallback class", e);
            }
        } while (true);
    }

    public static final <T> Set<Field> getFields(final Class<T> any) {
        return ReflectionUtils.getCaller(null).getFields(any);
    }

    public static final <T> List<Pair<Field, T>> getFieldsAndValuesOfType(final Class<T> any) {
        return ReflectionUtils.getCaller(null).getFieldsAndValuesOfType(any);
    }

    public static final <T> List<Pair<Field, T>> getFieldsAndValuesOfType(final Object me, final Class<T> any) {
        return ReflectionUtils.getCaller(me).getFieldsAndValuesOfType(any);
    }

    public static final <T> List<T> getFieldsOfType(final Class<T> any) {
        return ReflectionUtils.getCaller(null).getFieldValuesOfType(any);
    }

    public static final <T> List<T> getFieldsOfType(final Object me, final Class<T> any) {
        return ReflectionUtils.getCaller(me).getFieldValuesOfType(any);
    }

    public static <T> T getInstance(final Object parClazz, final Object... parNonNullArgs) {
        try {
            final Class<T> tc = (Class) ReflectionUtils.toClass(parClazz);
            return ReflectionUtils.getInstanceOf(tc, parNonNullArgs);
        } catch (final Exception ex) { // NOCS: We re-throw unconditionally
            throw new IllegalArgumentException("Constructing " + parClazz + " failed with '" + ex.getMessage() + "' args: " + Arrays.toString(parNonNullArgs), ex);
        }
    }

    public static <T> T getInstanceOf(final Class<T> parClazz, final Object... parNonNullArgs)
            throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        if (parNonNullArgs.length == 1 && parClazz.isAssignableFrom(parNonNullArgs[0].getClass())) {
            return (T) parNonNullArgs[0];
        }
        final Class<?>[] args = ReflectionUtils.toClasses(parNonNullArgs);
        Constructor<T> ret = null;
        try {
            try {
                return (T) setFullyExposedField(false, parClazz, "INSTANCE").get(null);
            } catch (final Exception ia) {
                ret = parClazz.getConstructor(args);
            }
        } catch (final NoSuchMethodException nsm) {
            // This is necessary to try:
            for (final Constructor<?> m : parClazz.getDeclaredConstructors()) {
                if (ReflectionUtils.matchlevel(m.getParameterTypes(), args) > 0) {
                    ret = (Constructor<T>) m;
                    break;
                }
            }
        }
        if (ret == null) {
            throw new NoSuchMethodException(parClazz + "(" + Arrays.toString(args) + ") derived from " + Arrays.toString(parNonNullArgs));
        }
        ret.setAccessible(true);
        try {
            return ret.newInstance(parNonNullArgs);
        } catch (final InvocationTargetException it) {
            final String msg = it.getCause().getMessage();
            if (msg == null) {
                throw it;
            }
            throw ReflectionUtils.extendMessage(it, msg);
        }
    }

    public static Object getInstanceOf(final Object parFullClazz, final Object... parNonNullArgs)
            throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        return ReflectionUtils.getInstanceOf(ReflectionUtils.toClass(parFullClazz), parNonNullArgs);
    }

    public static Method getMethod(final Class<?> parClz, final Object parMeth, final Class<?>... parArgs) throws NoSuchMethodException {
        Method ret = null;
        if (parMeth instanceof Method) {
            ret = (Method) parMeth;
        } else if (parMeth instanceof String && ((String) parMeth).contains(".")) {
            try {
                ret = ReflectionUtils.getMethod((String) parMeth, (Object[]) parArgs);
            } catch (final ClassNotFoundException e) {
                throw new NoSuchMethodException((String) parMeth);
            }
        } else {
            Class<?> clz = parClz;
            int reti = 0;
            while (clz != Object.class && clz != null) {
                for (final Method m : clz.getDeclaredMethods()) {
                    if (parMeth == null || parMeth.equals(m.getName()) // Any method or a given name
                    ) {
                        final int ma = ReflectionUtils.matchlevel(m.getParameterTypes(), parArgs); // Matching the argument list
                        if (ma > reti) {
                            ret = m;
                            reti = ma;
                        }
                    }
                }
                clz = clz.getSuperclass();
            }
            if (ret == null) {
                throw new NoSuchMethodException(parClz + "." + parMeth + "(" + org.apache.commons.lang3.StringUtils.join(parArgs, ',') + ")");
            }
        }
        ret.setAccessible(true);
        return ret;
    }

    public static Method getMethod(final String parFull, final Object... parArgs) throws ClassNotFoundException {
        final int methPos = parFull.lastIndexOf('.');
        final Class<?> clz = Class.forName(parFull.substring(0, methPos));
        final String mname = parFull.substring(methPos + 1, parFull.length());
        final Class<?>[] args = ReflectionUtils.toClasses(parArgs);
        for (final Method m : clz.getDeclaredMethods()) {
            if (m.getName().equals(mname) && (parArgs == null || ReflectionUtils.matchlevel(m.getParameterTypes(), args) > 0)) {
                return m;
            }
        }
        throw new IllegalArgumentException(clz + " does not contain an applicable '" + mname + "' method");
    }

    public static final String getName(final Object any) {
        if (any instanceof Member) {
            return ((Member) any).getName();
        }
        return any.toString();
    }

    public static <T extends Throwable> T initCause(final T t, final Throwable cause) {
        try {
            t.initCause(cause);
        } catch (final IllegalStateException is) {
            setProperty(t, "cause", cause);
        }
        return t;
    }

    public static <T> T invokeMethod(final Object parObj, final Object parMeth, final Object... parNonNullArgs) {
        Method m;
        try {
            m = ReflectionUtils.getMethod(ReflectionUtils.toClass(parObj), parMeth, ReflectionUtils.toClasses(parNonNullArgs));
            return (T) m.invoke(parObj, parNonNullArgs);
        } catch (final Exception e) {
            throw new IllegalArgumentException(parObj + " -> " + parMeth + " with " + Arrays.toString(parNonNullArgs) + " failed", e);
        }
    }

    public static <T> Class<? extends T> loadConventional(final Class<T> parPackageClass, final String... parNames) throws ClassNotFoundException {
        Class<? extends T> fClz = null;
        for (int i = parNames.length; i > 0; i--) {
            String cn = org.apache.commons.lang3.StringUtils.join(parNames, "", 0, i);
            cn = parPackageClass.getPackage().getName() + "." + cn + parPackageClass.getSimpleName();
            try {
                fClz = (Class) ReflectionUtils.toClass(cn);
                break;
            } catch (final ClassNotFoundException cnf) {
                // Nothing to do
            }
        }
        if (fClz == null) {
            throw new ClassNotFoundException("Not found any fallbacked instance of " + Arrays.toString(parNames) + " of " + parPackageClass);
        }
        return fClz;
    }

    private static int matches(final Class<?> parExpected, final Class<?> parGiven) {
        if (parExpected.isPrimitive() && parGiven == null) {
            return 0;
        }
        if (parGiven == null) {
            return 1;
        }
        if (parExpected.equals(parGiven)) {
            return 3;
        }
        if (parExpected.isAssignableFrom(parGiven)) {
            return 2;
        }
        if (ReflectionUtils.toWrapper(parExpected).equals(ReflectionUtils.toWrapper(parGiven))) {
            return 2;
        }
        return 0;
    }

    private static int matchlevel(final Class<?>[] parExpected, final Class<?>[] parGiven) {
        if (parGiven == null || parExpected == null) {
            return 1;
        }
        if (parExpected.length != parGiven.length) {
            return 0;
        }
        if (parGiven.length == 0) {
            return 1;
        }
        int ret = 0, iret;
        for (int i = 0; i < parGiven.length; i++) {
            iret = ReflectionUtils.matches(parExpected[i], parGiven[i]);
            if (iret == 0) {
                ret = iret;
                break;
            } else {
                ret += iret;
            }
        }
        return ret;
    }

    public static LinkedList<Class<?>> resolveClassHierarchyExceptObject(final Object object) {
        Class<?> currentClass;
        try {
            currentClass = ReflectionUtils.toClass(object);
        } catch (final ClassNotFoundException e) {
            throw new IllegalArgumentException(object + " invalid class", e);
        }
        final LinkedList<Class<?>> classHierarchyExceptObject = new LinkedList<>();
        do {
            classHierarchyExceptObject.add(currentClass);
            currentClass = currentClass.getSuperclass();
        } while (!Object.class.equals(currentClass));
        Collections.reverse(classHierarchyExceptObject);
        return classHierarchyExceptObject;
    }

    public static Class<?> toClass(final Object parObj, final Class<?>... classes) throws ClassNotFoundException {
        Class<?> clazz = null;
        if (parObj instanceof CharSequence) {
            String s = String.valueOf(parObj);
            // Try if it is an abbreviated class name:
            for (final Class<?> o : classes) {
                if (o.getName().endsWith(s)) {
                    return o;
                }
            }
            if (!s.contains(".") && !s.startsWith("[")) {
                try {
                    clazz = ReflectionUtils.invokeMethod(Class.class, "getPrimitiveClass", s);
                    return ReflectionUtils.toWrapper(clazz);
                } catch (final IllegalArgumentException ex) {
                    s = "java.lang." + s;
                }
            }
            clazz = Class.forName(s);
        } else if (parObj instanceof Class) {
            clazz = (Class) parObj;
        } else if (parObj != null) {
            clazz = parObj.getClass();
        }
        while (clazz instanceof Class && clazz.isArray()) {
            clazz = clazz.getComponentType();
        }
        return clazz;
    }

    public static Class[] toClasses(final Object[] parGiven) {
        if (parGiven == null) {
            return null;
        }
        final List<Class<?>> contArgs = new LinkedList<Class<?>>();
        for (final Object o : parGiven) {
            if (o != null) {
                contArgs.add(o.getClass());
            }
        }
        return contArgs.toArray(new Class[parGiven.length]);
    }

    public static Class<?> toPrimitive(final Class<?> parClz) {
        Class<?> clz = parClz;
        try {
            clz = getProperty(parClz, "TYPE");
        } catch (final IllegalArgumentException ia) {
            final Class<?> clz2 = ReflectionUtils.toWrapper(clz);
            if (clz2 == clz) {
                throw ia;
            }
        }
        return clz;
    }

    public static Class<?> toWrapper(final Class<?> parClz) {
        Class<?> clz = parClz;
        if (parClz == Integer.TYPE) {
            clz = Integer.class;
        } else if (parClz == Short.TYPE) {
            clz = Short.class;
        } else if (parClz == Byte.TYPE) {
            clz = Byte.class;
        } else if (parClz == Long.TYPE) {
            clz = Long.class;
        } else if (parClz == Float.TYPE) {
            clz = Float.class;
        } else if (parClz == Double.TYPE) {
            clz = Double.class;
        } else if (parClz == Boolean.TYPE) {
            clz = Boolean.class;
        }
        return clz;
    }

    public static <T> T getProperty(final Object parObj, final Object parName) {
        try {
            final Field f = getAccessibleField(false, parObj, parName);
            return (T) f.get(parObj);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException("Should be readable now: " + parName, e);
        }
    }

    /**
     * This is to access a non-static field of a given object in any cost. This means that it will use {@link #setFullyExposedField(boolean, Class, Object)} to set
     * the privileges to the field.
     *
     * @param parObj  The object in which we want to find the mamber by name (the first in hiearchy).
     * @param parName The name of the member to find.
     * @return The field with modified privileges.
     */
    public static Field getAccessibleField(boolean rw, final Object parObj, final Object parName) {
        return getAccessibleField(rw, parObj, parName, false);
    }

    public static Field getAccessibleField(boolean rw, Object parObj, Object parName, final boolean ic) {
        Field ret = null;
        Class<?> clazz;

        if (parObj instanceof Map.Entry) {
            final Map.Entry<Object, Object> e = (Map.Entry) parObj;
            if (parName == null) {
                parName = e.getValue();
            }
            parObj = e.getKey();
        }
        try {
            clazz = ReflectionUtils.toClass(parObj);
        } catch (final ClassNotFoundException e) {
            throw new IllegalArgumentException("Tried to access " + parName + " of an invalid class", e);
        }
        while (clazz != null && clazz != Object.class) {
            try {
                ret = setFullyExposedField(rw, clazz, parName, ic);
                break;
            } catch (final NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }

        if (ret == null) {
            throw new IllegalArgumentException(
                    parName + " in " + (parObj == null ? "<unknown>" : parObj.getClass()) + " or in its superclasses is not accessible");
        }

        return ret;
    }

    public static Field setFullyExposedField(boolean rw, final Class<?> parClazz, final Object parName) throws NoSuchFieldException {
        return setFullyExposedField(rw, parClazz, parName, false);
    }

    public static Field setFullyExposedField(boolean rw, final Class<?> parClazz, final Object parName, final boolean ic) throws NoSuchFieldException {
        Field f = null;
        if (parName instanceof Field) {
            f = (Field) parName;
        } else {
            String sn = String.valueOf(parName);
            if (ic) {
                sn = sn.toLowerCase(Locale.ENGLISH);
                Field closest = null;
                int c, d = Integer.MAX_VALUE;
                for (final Field fi : parClazz.getDeclaredFields()) {
                    if (fi.getName().equalsIgnoreCase(sn)) {
                        f = fi;
                        break;
                    } else {
                        c = LevenshteinDetailedDistance.getDefaultInstance().apply(fi.getName().toLowerCase(Locale.ENGLISH), sn).getDistance();
                        if (d > c) {
                            d = c;
                            closest = fi;
                        }
                    }
                }
                if (f == null) {
                    throw new NoSuchFieldException("No field " + parName + ", closest=" + closest);
                }
            } else {
                f = parClazz.getDeclaredField(sn);
            }
        }
        setFullyExposedField(rw, f);
        return f;
    }

    /**
     * Sets the given field accessible and non-final.
     *
     * @param parField The field to set fully writable from anywhere.
     * @deprecated Do not use unless if you have very specific reason.
     */
    @Deprecated
    public static void setFullyExposedField(boolean rw, final Field parField) {
        if (parField.isAccessible()) return;
        parField.setAccessible(true);
        if (!Modifier.isFinal(parField.getModifiers()) || !rw) return;
        throw new IllegalStateException("Unable to make non-final " + parField);
    }

    public static void setProperty(final Object parObj, final Object parName, Object parValue) {
        NoSuchMethodException nsm = null;
        try {
            final Field f = getAccessibleField(true, parObj, parName);
            if (parValue instanceof String) {
                parValue = StringUtils.to(f.getType(), (String) parValue, null);
            }
            f.set(parObj, parValue);
        } catch (final IllegalAccessException e) {
            throw new IllegalArgumentException("Method fail: " + nsm + " accessing " + parName, e);
        }
    }

    private ReflectionUtils() {
        // Util
    }
}
