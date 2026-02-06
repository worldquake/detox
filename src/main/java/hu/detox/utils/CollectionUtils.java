package hu.detox.utils;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import org.apache.commons.collections.Transformer;

import java.util.*;
import java.util.Map.Entry;

public class CollectionUtils extends org.apache.commons.collections.CollectionUtils {
    private static final Comparator<String> SIMPLE_NAME_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String parS1, String parS2) {
            if (parS1 == null && parS2 == null) {
                return 0;
            }
            if (parS1 == null) {
                return -1;
            }
            if (parS2 == null) {
                return 1;
            }
            final int i1 = parS1.lastIndexOf('.');
            if (i1 >= 0) {
                parS1 = parS1.substring(i1 + 1);
            }
            final int i2 = parS2.lastIndexOf('.');
            if (i2 >= 0) {
                parS2 = parS2.substring(i2 + 1);
            }
            return parS1.compareToIgnoreCase(parS2);
        }
    };

    public static List<Object> flatten(final Object... anys) {
        final ArrayList<Object> ret = new ArrayList<>(anys.length);
        CollectionUtils.flattenTo(ret, anys);
        return ret;
    }

    private static void flattenInternal(final List<Object> lst, final Collection<?> anys) {
        for (final Object o : anys) {
            CollectionUtils.flattenTo(lst, o);
        }
    }

    private static void flattenInternal(final List<Object> lst, final Object[] anys) {
        for (final Object o : anys) {
            CollectionUtils.flattenTo(lst, o);
        }
    }

    public static void flattenTo(final List<Object> lst, final Object... anys) {
        for (final Object a : anys) {
            if (a instanceof Collection<?>) {
                CollectionUtils.flattenInternal(lst, (Collection) a);
            } else if (a instanceof Object[]) {
                CollectionUtils.flattenInternal(lst, (Object[]) a);
            } else {
                lst.add(a);
            }
        }
    }

    public static <T, E> Set<T> getKeysByValue(final Map<T, E> map, final E value) {
        final Set<T> keys = new HashSet<T>();
        for (final Entry<T, E> entry : map.entrySet()) {
            if (Objects.equals(value, entry.getValue())) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    public static List<String> join(final Map<String, String> parMap, final String parSep) {
        if (parMap == null) {
            return null;
        }
        final List<String> list = new ArrayList<String>();
        if (parMap.size() == 0) {
            return list;
        }
        for (final Entry<String, String> entry : parMap.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            if (value == null || value.length() == 0) {
                list.add(key);
            } else {
                list.add(key + parSep + value);
            }
        }
        return list;
    }

    public static Map<String, List<String>> joinAll(final Map<String, Map<String, String>> parMap, final String parSep) {
        if (parMap == null) {
            return null;
        }
        final Map<String, List<String>> result = new HashMap<String, List<String>>();
        for (final Entry<String, Map<String, String>> entry : parMap.entrySet()) {
            result.put(entry.getKey(), CollectionUtils.join(entry.getValue(), parSep));
        }
        return result;
    }

    public static boolean mapEquals(final Map<?, ?> parMap1, final Map<?, ?> parMap2) {
        if (parMap1 == null && parMap2 == null) {
            return true;
        }
        if (parMap1 == null || parMap2 == null) {
            return false;
        }
        if (parMap1.size() != parMap2.size()) {
            return false;
        }
        for (final Entry<?, ?> entry : parMap1.entrySet()) {
            final Object key = entry.getKey();
            final Object value1 = entry.getValue();
            final Object value2 = parMap2.get(key);
            if (!CollectionUtils.objectEquals(value1, value2)) {
                return false;
            }
        }
        return true;
    }

    private static boolean objectEquals(final Object parObj1, final Object parObj2) {
        if (parObj1 == null && parObj2 == null) {
            return true;
        }
        if (parObj1 == null || parObj2 == null) {
            return false;
        }
        return parObj1.equals(parObj2);
    }

    public static void removeAll(final List<?> c, final int... idxs) {
        if (org.apache.commons.collections.CollectionUtils.isEmpty(c) || org.apache.commons.lang3.ArrayUtils.isEmpty(idxs)) {
            return;
        }
        Arrays.sort(idxs);
        CollectionUtils.removeAllSorted(c, idxs);
    }

    public static void removeAllFrom(final List lst, final int from) {
        final List<Object> l1 = new LinkedList<>(lst.subList(0, from));
        lst.clear();
        lst.addAll(l1);
    }

    public static void removeAllSorted(final List<?> c, final int... idxs) {
        if (org.apache.commons.collections.CollectionUtils.isEmpty(c) || org.apache.commons.lang3.ArrayUtils.isEmpty(idxs)) {
            return;
        }
        for (int j = idxs.length - 1; j >= 0; j--) {
            if (idxs[j] < 0) {
                continue;
            }
            c.remove(idxs[j]);
        }
    }

    public static void reorder(final ArrayList arr, final int[] index) {
        // Fix all elements one by one
        for (int i = 0; i < arr.size(); i++) {
            // While index[i] and arr[i] are not fixed
            while (index[i] != i) {
                // Store values of the target (or correct)
                // position before placing arr[i] there
                final int oldTargetI = index[index[i]];
                final char oldTargetE = (char) arr.get(index[i]);

                // Place arr[i] at its target (or correct)
                // position. Also copy corrected index for
                // new position
                arr.set(index[i], arr.get(i));
                index[index[i]] = index[i];

                // Copy old target values to arr[i] and
                // index[i]
                index[i] = oldTargetI;
                arr.set(i, oldTargetE);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> List<T> sort(final List<T> parList) {
        if (parList != null && parList.size() > 0) {
            Collections.sort((List) parList);
        }
        return parList;
    }

    public static <K, V> Map<K, V> sortByValue(final Map<K, V> map, final Comparator<Object> cmp) {
        final List<Entry<K, V>> list = new LinkedList<Entry<K, V>>(map.entrySet());
        Collections.sort(list, new Comparator<Entry<K, V>>() {
            @Override
            public int compare(final Entry<K, V> o1, final Entry<K, V> o2) {
                final Object vo1 = o1.getValue();
                final Object vo2 = o2.getValue();
                if (cmp == null) {
                    return ((Comparable) vo1).compareTo(vo2);
                } else {
                    return cmp.compare(vo1, vo2);
                }
            }
        });
        final Map<K, V> result = new LinkedHashMap<K, V>();
        for (final Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static List<String> sortSimpleName(final List<String> parList) {
        if (parList != null && parList.size() > 0) {
            Collections.sort(parList, CollectionUtils.SIMPLE_NAME_COMPARATOR);
        }
        return parList;
    }

    public static Map<String, String> split(final List<String> parList, final String parSep) {
        if (parList == null) {
            return null;
        }
        final Map<String, String> map = new HashMap<String, String>();
        if (parList.size() == 0) {
            return map;
        }
        for (final String item : parList) {
            final int index = item.indexOf(parSep);
            if (index == -1) {
                map.put(item, "");
            } else {
                map.put(item.substring(0, index), item.substring(index + 1));
            }
        }
        return map;
    }

    public static Map<String, Map<String, String>> splitAll(final Map<String, List<String>> parList, final String parSep) {
        if (parList == null) {
            return null;
        }
        final Map<String, Map<String, String>> result = new HashMap<String, Map<String, String>>();
        for (final Entry<String, List<String>> entry : parList.entrySet()) {
            result.put(entry.getKey(), CollectionUtils.split(entry.getValue(), parSep));
        }
        return result;
    }

    public static <T> List<T> subList(final List<T> parList, final String parIval) {
        int from = 0;
        int to = parList.size();
        if (parIval != null) {
            final int idx = parIval.indexOf("-");
            if (idx > 0) {
                from = Integer.parseInt(parIval.substring(0, idx));
                to = Integer.parseInt(parIval.substring(idx + 1));
            } else if (idx == 0) {
                to = Integer.parseInt(parIval);
            }
            if (to < 0) {
                to = parList.size() + to;
            }
        }
        return parList.subList(from, to);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> toMap(final Object... parPairs) {
        final Map<K, V> ret = new LinkedHashMap<K, V>();
        if (parPairs == null || parPairs.length == 0) {
            return ret;
        }

        if (parPairs.length % 2 != 0) {
            throw new IllegalArgumentException("Map pairs can not be odd number.");
        }
        final int len = parPairs.length / 2;
        for (int i = 0; i < len; i++) {
            ret.put((K) parPairs[2 * i], (V) parPairs[2 * i + 1]);
        }
        return ret;
    }

    public static Map<String, String> toStringMap(final String... parPairs) {
        final Map<String, String> parameters = new HashMap<String, String>();
        if (parPairs.length > 0) {
            if (parPairs.length % 2 != 0) {
                throw new IllegalArgumentException("pairs must be even.");
            }
            for (int i = 0; i < parPairs.length; i = i + 2) {
                parameters.put(parPairs[i], parPairs[i + 1]);
            }
        }
        return parameters;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> Collection<T> trafo(final Collection c, final Transformer tr) {
        org.apache.commons.collections.CollectionUtils.transform(c, tr);
        return c;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> List<T> trafo(final Object[] arr, final Transformer tr) {
        final LinkedList ll = new LinkedList(Arrays.asList(arr));
        CollectionUtils.trafo(ll, tr);
        return ll;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> Collection<T> trafoNoNulls(Collection c, final Transformer tr) {
        c = CollectionUtils.trafo(c, tr);
        Iterables.removeIf(c, Predicates.isNull());
        return c;
    }

    public static <T> void removeAllInplaceSorted(final List<T> c, final int... idxs) {
        CollectionUtils.removeAllSorted(c, idxs);
    }

    private CollectionUtils() {
        // Util class
    }
}
