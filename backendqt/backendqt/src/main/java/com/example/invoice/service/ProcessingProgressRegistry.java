package com.example.invoice.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Live view of which pipeline step a document is in, for upload progress screens.
 * In-memory on purpose: one backend instance, and progress is disposable once the job ends
 * (the persisted outcome lives in processing history). Finished entries are kept briefly so the
 * last poll can still see DONE/FAILED.
 */
@Component
public class ProcessingProgressRegistry {
    static final Duration RETENTION = Duration.ofMinutes(10);
    static final int MAX_STEPS = 60;

    public enum State { IDLE, RUNNING, DONE, FAILED }
    public record Step(String code, String detail, Instant startedAt, Instant endedAt) {}
    public record Snapshot(Long documentId, State state, String currentStep, String currentDetail,
            Instant startedAt, Instant finishedAt, List<Step> steps) {}

    private static final class Entry {
        final Instant startedAt;
        final List<Step> steps = new ArrayList<>();
        State state = State.RUNNING;
        Instant finishedAt;
        Entry(Instant startedAt) { this.startedAt = startedAt; }
    }

    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public ProcessingProgressRegistry() { this(Clock.systemUTC()); }
    ProcessingProgressRegistry(Clock clock) { this.clock = clock; }

    public void start(Long documentId) {
        purge();
        entries.put(documentId, new Entry(clock.instant()));
    }

    /** Enters a step; re-entering the current step only updates its detail. */
    public void step(Long documentId, String code, String detail) {
        Entry entry = entries.get(documentId);
        if (entry == null) return;
        synchronized (entry) {
            if (entry.state != State.RUNNING) return;
            Instant now = clock.instant();
            int last = entry.steps.size() - 1;
            if (last >= 0 && entry.steps.get(last).code().equals(code)) {
                Step current = entry.steps.get(last);
                entry.steps.set(last, new Step(code, detail == null ? current.detail() : detail, current.startedAt(), null));
                return;
            }
            close(entry, now);
            if (entry.steps.size() >= MAX_STEPS) entry.steps.removeFirst();
            entry.steps.add(new Step(code, detail, now, null));
        }
    }

    public void finish(Long documentId, boolean succeeded) {
        Entry entry = entries.get(documentId);
        if (entry == null) return;
        synchronized (entry) {
            Instant now = clock.instant();
            close(entry, now);
            entry.state = succeeded ? State.DONE : State.FAILED;
            entry.finishedAt = now;
        }
    }

    public Snapshot snapshot(Long documentId) {
        Entry entry = entries.get(documentId);
        if (entry == null) return new Snapshot(documentId, State.IDLE, null, null, null, null, List.of());
        synchronized (entry) {
            Step current = entry.state == State.RUNNING && !entry.steps.isEmpty() ? entry.steps.getLast() : null;
            return new Snapshot(documentId, entry.state, current == null ? null : current.code(),
                    current == null ? null : current.detail(), entry.startedAt, entry.finishedAt, List.copyOf(entry.steps));
        }
    }

    private static void close(Entry entry, Instant now) {
        int last = entry.steps.size() - 1;
        if (last < 0 || entry.steps.get(last).endedAt() != null) return;
        Step open = entry.steps.get(last);
        entry.steps.set(last, new Step(open.code(), open.detail(), open.startedAt(), now));
    }

    private void purge() {
        Instant cutoff = clock.instant().minus(RETENTION);
        entries.values().removeIf(entry -> entry.finishedAt != null && entry.finishedAt.isBefore(cutoff));
    }
}
