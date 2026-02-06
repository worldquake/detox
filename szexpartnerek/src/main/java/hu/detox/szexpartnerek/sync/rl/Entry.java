package hu.detox.szexpartnerek.sync.rl;

import hu.detox.config.ConfigReader;
import hu.detox.szexpartnerek.sync.ITrafoEngine;
import hu.detox.szexpartnerek.sync.Sync;
import lombok.RequiredArgsConstructor;
import org.apache.commons.configuration2.Configuration;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class Entry implements hu.detox.szexpartnerek.sync.Sync.Entry {
    private final List<ITrafoEngine> trafos;
    private final Http client;

    public static Configuration cfg(String name) throws IOException {
        return ConfigReader.INSTANCE.toCfg(toName(name));
    }

    public static String toName(String name) {
        return "sync/rl/" + name;
    }

    public int syncGetSkipped(Collection<String> doOnly, boolean recurse) throws IOException {
        int skp = 0;
        List<ITrafoEngine> trfs = trafos.stream()
                .filter(entry -> doOnly == null || doOnly.remove(entry.getId().toLowerCase(Locale.ROOT)))
                .toList();
        for (ITrafoEngine te : trfs) {
            var sync = new Sync(client, recurse, te);
            System.err.println("Processing " + te.getId());
            skp += sync.dataDl(null);
        }
        return skp;
    }

    @Override
    public void flush() {
        if (trafos != null) for (ITrafoEngine te : trafos) te.flush();
    }

    @Override
    public void close() {
        if (trafos != null) for (ITrafoEngine te : trafos) te.close();
    }
}
