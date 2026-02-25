package hu.detox.szexpartnerek.sync;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static hu.detox.szexpartnerek.spring.SzexConfig.exec;
import static hu.detox.szexpartnerek.spring.SzexConfig.query;

public class FKOff implements AutoCloseable {
    public FKOff() {
        exec("PRAGMA foreign_keys = OFF");
    }

    public Map<String, List<String>> getFKFaults() {
        return query("SELECT \"table\", rowid FROM pragma_foreign_key_check()", rs -> {
            Map<String, List<String>> itables = new HashMap<>();
            while (rs.next()) {
                rs.getString(1);
                List<String> cnt = itables.computeIfAbsent(rs.getString(1), k -> new LinkedList<>());
                cnt.add(rs.getString(2));
            }
            return itables;
        });
    }

    public void deleteFKs() {
        var faults = getFKFaults();
        if (faults.isEmpty()) return;
        for (Map.Entry<String, List<String>> entry : faults.entrySet()) {
            for (String rid : entry.getValue())
                exec("DELETE FROM " + entry.getKey() + " WHERE rowid = " + rid);
            ((Map) faults).put(entry.getKey(), entry.getValue().size());
        }
        System.err.println("** Foreign keys from " + faults + " deleted");
    }

    @Override
    public void close() throws Exception {
        try {
            deleteFKs();
        } finally {
            exec("PRAGMA foreign_keys = ON");
        }
    }
}
