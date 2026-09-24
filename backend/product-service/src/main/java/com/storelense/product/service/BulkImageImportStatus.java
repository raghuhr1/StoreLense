package com.storelense.product.service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mutable progress tracker for one bulk image-import job. Held in-memory only
 * (see ProductService.bulkJobs) — this is a one-off admin tool, not a durable
 * audit trail, so a lost job on restart is an acceptable trade-off against the
 * cost of a dedicated table/migration for it.
 */
public class BulkImageImportStatus {

    public enum State { RUNNING, DONE, FAILED }

    private volatile State state = State.RUNNING;
    private final int totalRows;
    private final AtomicInteger processed        = new AtomicInteger();
    private final AtomicInteger imported         = new AtomicInteger();
    private final AtomicInteger skippedNoProduct = new AtomicInteger();
    private final AtomicInteger failed           = new AtomicInteger();
    private final List<String> errors            = new CopyOnWriteArrayList<>();
    private static final int MAX_ERRORS = 50;

    public BulkImageImportStatus(int totalRows) {
        this.totalRows = totalRows;
    }

    public void onImported()         { processed.incrementAndGet(); imported.incrementAndGet(); }
    public void onSkippedNoProduct() { processed.incrementAndGet(); skippedNoProduct.incrementAndGet(); }
    public void onFailed(String row, String reason) {
        processed.incrementAndGet();
        failed.incrementAndGet();
        if (errors.size() < MAX_ERRORS) errors.add(row + ": " + reason);
    }
    public void markDone()   { state = State.DONE; }
    public void markFailed() { state = State.FAILED; }

    public State getState()             { return state; }
    public int getTotalRows()           { return totalRows; }
    public int getProcessed()           { return processed.get(); }
    public int getImported()            { return imported.get(); }
    public int getSkippedNoProduct()    { return skippedNoProduct.get(); }
    public int getFailed()              { return failed.get(); }
    public List<String> getErrors()     { return Collections.unmodifiableList(errors); }
}
