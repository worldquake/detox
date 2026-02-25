package hu.detox.szexpartnerek.sync;

import hu.detox.ifaces.ID;
import hu.detox.utils.TimeUtils;
import hu.detox.utils.strings.Naming;
import hu.detox.utils.strings.PasswordBuilder;
import org.springframework.dao.DataAccessException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static hu.detox.szexpartnerek.spring.SzexConfig.jdbc;

public abstract class AbstractPersister implements IPersister, ID<String> {
    private int batch;

    @Override
    public void incBatch() {
        if (++batch >= hu.detox.szexpartnerek.sync.Main.args().getMaxBatch()) {
            flush();
        }
    }

    public Set<Integer> getProcessableIds(Set<Integer> untouchable, int acceptable) throws DataAccessException {
        HashSet<Integer> canProcess = new HashSet<>();
        List<Integer> eligible = new ArrayList<>();
        String untouchedSql = "SELECT id, ts FROM " + getId() + " WHERE del = false ORDER BY ts ASC";
        Instant oneDayAgo = TimeUtils.instant().minus(Duration.ofDays(1));

        jdbc().query(untouchedSql, rs -> {
            while (rs.next()) {
                int id = rs.getInt("id");
                Timestamp ts = rs.getTimestamp("ts");
                if (ts == null || ts.toInstant().isBefore(oneDayAgo)) {
                    eligible.add(id);
                } else {
                    untouchable.add(id);
                }
            }
            return null;
        });

        if (eligible.size() > acceptable) {
            int percent = 2 + PasswordBuilder.RANDOM.nextInt(6);
            int count = Math.max(1, eligible.size() * percent / 100);

            Collections.shuffle(eligible, PasswordBuilder.RANDOM);
            List<Integer> selected = eligible.subList(0, count);

            canProcess.addAll(selected);
        } else {
            canProcess.addAll(eligible);
        }

        return canProcess;
    }

    @Override
    public String getId() {
        return new Naming(getClass().getSimpleName().replace("Persister", ""))
                .toFormat(Naming.Format.CONSTANT).getText().toLowerCase(Locale.ROOT);
    }

    protected boolean notBigEnoughBatch() {
        return batch < hu.detox.szexpartnerek.sync.Main.args().getMaxBatch();
    }

    @Override
    public void flush() {
        System.err.println("Flushed " + (batch == Integer.MAX_VALUE ? "remaining" : batch) + " " + getId());
        batch = 0;
    }

    @Override
    public void close() {
        if (batch > 0) batch = Integer.MAX_VALUE;
        flush();
    }
}
