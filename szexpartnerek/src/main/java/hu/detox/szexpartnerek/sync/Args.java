package hu.detox.szexpartnerek.sync;

import hu.detox.utils.CollectionUtils;
import hu.detox.utils.strings.StringUtils;
import lombok.Value;

import java.util.LinkedList;
import java.util.List;

@Value
public class Args {
    private static final int MAX_BATCH_DEFAULT = 100;

    boolean full;
    int maxBatch;
    List<String> ids;

    public int getMaxBatch() {
        return maxBatch == 0 ? MAX_BATCH_DEFAULT : maxBatch;
    }

    public List<String> getIds() {
        if (CollectionUtils.isEmpty(ids)) return null;
        return new LinkedList<>(ids.stream().map(StringUtils::toRootLowerCase).toList());
    }
}
