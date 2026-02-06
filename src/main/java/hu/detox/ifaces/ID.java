package hu.detox.ifaces;

public interface ID<T> {
    default T getId() {
        return (T) getClass().getSimpleName();
    }
}
