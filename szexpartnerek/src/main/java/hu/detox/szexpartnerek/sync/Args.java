package hu.detox.szexpartnerek.sync;

import lombok.Value;

import java.util.List;

@Value
public class Args {
    boolean full;
    int maxBatch;
    List<String> ids;
}
