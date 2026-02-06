package hu.detox.szexpartnerek.sync;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.Agent;
import hu.detox.ifaces.ID;
import hu.detox.io.CharIOHelper;

import java.io.File;
import java.io.Flushable;
import java.io.IOException;
import java.util.Iterator;
import java.util.function.Function;

public interface ITrafoEngine extends ID<String>, Function<String, Object>, Flushable, AutoCloseable {
    interface Filters {
        boolean skips(String in);
    }

    void reStart(boolean force);

    Function<String, String> url();

    Iterator<?> input(JsonNode parent);

    default ITrafoEngine[] subTrafos() {
        return null;
    }

    default ITrafoEngine[] preTrafos() {
        return null;
    }

    IPersister persister();

    default boolean post() {
        return false;
    }

    default String[] in() {
        return new String[]{getId() + ".txt", getId() + ".ser", getId() + ".jsonl"};
    }

    default CharIOHelper findIn() throws IOException {
        CharIOHelper cio = null;
        for (Object fp : in()) {
            if (fp instanceof String) {
                fp = hu.detox.szexpartnerek.sync.rl.Entry.toName((String) fp);
                fp = Agent.resource((String) fp);
            }
            cio = CharIOHelper.attempt(fp);
            if (cio != null) break;
        }
        return cio;
    }


    default File out() {
        return new File("target/gen-" + getId() + ".jsonl");
    }

    default Iterator<String> pager() {
        return null;
    }

    default void flush() {
        ITrafoEngine[] trs = preTrafos();
        if (trs != null) for (ITrafoEngine tr : trs) tr.flush();
        IPersister p = persister();
        if (p != null) p.flush();
        trs = subTrafos();
        if (trs != null) for (ITrafoEngine tr : trs) tr.flush();
    }

    @Override
    default void close() {
        ITrafoEngine[] trs = preTrafos();
        if (trs != null) for (ITrafoEngine tr : trs) tr.close();
        IPersister p = persister();
        if (p != null) p.close();
        trs = subTrafos();
        if (trs != null) for (ITrafoEngine tr : trs) tr.close();
    }
}
