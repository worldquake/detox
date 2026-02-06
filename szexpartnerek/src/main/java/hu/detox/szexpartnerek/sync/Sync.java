package hu.detox.szexpartnerek.sync;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.ifaces.ID;
import hu.detox.io.CharIOHelper;
import hu.detox.io.IOHelper;
import hu.detox.utils.Http;
import hu.detox.utils.ReflectionUtils;
import hu.detox.utils.Serde;

import java.io.File;
import java.io.Flushable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

public class Sync implements AutoCloseable {
    @Override
    public void close() {
        IOHelper.closeSilently(engine);
    }

    public interface Entry extends ID<String>, Flushable, AutoCloseable {
        int syncGetSkipped(Collection<String> doOnly, boolean recurse) throws IOException;

        @Override
        default String getId() {
            String pkg = this.getClass().getPackage().getName();
            return pkg.substring(pkg.lastIndexOf('.') + 1);
        }

        void flush();

        default void close() {
            flush();
        }
    }

    private final Http client;
    private boolean recurse;
    private ITrafoEngine engine;

    public Sync(Http client, boolean recurse, ITrafoEngine engine) {
        this.client = client;
        this.recurse = recurse;
        this.engine = engine;
        if (engine != null) engine.reStart(true);
    }

    public int transformAll(Serde serde, String... urls) throws IOException {
        IPersister p = engine.persister();
        int skp = 0;
        try {
            boolean first = true, cont;
            int ln = 1;
            for (String url : urls) {
                if (url == null) continue;
                Iterator<String> pager = engine.pager();
                cont = true;
                while (cont) {
                    try {
                        StringBuilder sb = new StringBuilder(engine.getId());
                        JsonNode bodyNode = null;
                        if (serde.inMode() == null || serde.inMode().equals(Serde.Mode.TXT)) {
                            String curl = url;
                            if (pager != null) {
                                if (!pager.hasNext()) break;
                                String nextPage = pager.next();
                                curl += "&" + nextPage;
                                sb.append("[").append(nextPage).append("]");
                            }
                            Object body = null;
                            if (pager instanceof IPager pg) {
                                body = pg.req();
                            }
                            var resp = engine.post() ? client.post(curl, body) : client.get(curl);
                            bodyNode = serde.serialize(resp, curl, engine);
                            if (bodyNode != null && pager instanceof IPager pg) {
                                if (first) {
                                    if (!pg.first(bodyNode)) {
                                        System.err.println(sb.append(" - skip"));
                                        break;
                                    }
                                    sb.append(" start ").append(curl);
                                    first = false;
                                }
                                cont = pg.current(bodyNode) >= 0;
                                sb.append(cont ? " ..." : " last");
                            }
                        } else if (serde.inMode().equals(Serde.Mode.JSONL)) {
                            bodyNode = Serde.OM.readTree(url);
                        }
                        if (bodyNode == null) {
                            System.err.println(sb.append(" end"));
                            break;
                        }
                        skp += save(sb, bodyNode);
                        System.err.println(sb);
                    } catch (RuntimeException re) {
                        throw ReflectionUtils.extendMessage(re, "url=" + url + ", ln=" + ln);
                    }
                    if (pager == null) break;
                }
                ln++;
            }
        } finally {
            serde.flush();
            if (p != null) p.flush();
            engine.flush();
        }
        return skp;
    }

    private int save(StringBuilder sb, JsonNode bodyNode) throws IOException {
        int skp = 0;
        if (recurse) {
            boolean rec = recurse;
            try {
                recurse = false;
                skp += doInnerTransforms(sb, engine.preTrafos(), bodyNode);
            } finally {
                recurse = rec;
            }
        }
        IPersister p = engine.persister();
        if (p != null) {
            p.save(sb, bodyNode);
            p.incBatch();
        }
        if (recurse) {
            skp += doInnerTransforms(sb, engine.subTrafos(), bodyNode);
        }
        return skp;
    }

    private int doInnerTransforms(StringBuilder sb, ITrafoEngine[] tes, JsonNode bodyNode) throws IOException {
        if (tes == null) return 0;
        var orig = engine;
        int skp, askp = 0;
        try {
            for (ITrafoEngine ste : tes) {
                engine = ste;
                ste.reStart(false);
                skp = dataDl(bodyNode);
                if (skp > 0) sb.append(", skp[").append(ste.getId()).append("]=").append(skp);
                askp += skp;
            }
        } finally {
            engine = orig;
        }
        return askp;
    }

    public int dataDl(JsonNode parent) throws IOException {
        boolean cont = true;
        String[] arr = new String[10];
        String ln;
        File out = engine.out();
        Serde serde = null;
        int skip = 0;
        try {
            CharIOHelper in = null;
            if (parent == null) in = engine.findIn();
            Iterator<?> ini = parent == null ? null : engine.input(parent);
            boolean defaultIn = in == null && ini == null;
            serde = new Serde(out, in);
            while (cont) {
                Arrays.fill(arr, null);
                if (defaultIn) {
                    arr[0] = engine.url().apply(null);
                    cont = false;
                } else {
                    for (int i = 0; i < 10; i++) {
                        ln = null;
                        if (ini != null) {
                            if (ini.hasNext()) {
                                Object o = ini.next();
                                ln = o instanceof JsonNode jn ? jn.asText() : o.toString();
                            }
                        } else {
                            ln = serde.nextStr();
                        }
                        if (!hu.detox.szexpartnerek.sync.Main.ARGS.get().isFull() && ln != null &&
                                engine instanceof ITrafoEngine.Filters tf && tf.skips(ln)) {
                            skip++;
                            continue;
                        }
                        if (ln == null) {
                            cont = false;
                            break;
                        }
                        arr[i] = engine.url().apply(ln);
                    }
                }
                skip += transformAll(serde, arr);
            }
        } finally {
            if (serde != null) serde.close();
            engine.flush();
        }
        return skip;
    }
}
