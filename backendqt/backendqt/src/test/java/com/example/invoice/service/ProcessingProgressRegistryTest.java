package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ProcessingProgressRegistryTest {
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-01T00:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(long seconds) { now = now.plusSeconds(seconds); }
    }

    @Test void tracksStepsWithDurationsAndFinalState() {
        var clock = new MutableClock(); var registry = new ProcessingProgressRegistry(clock);
        assertEquals(ProcessingProgressRegistry.State.IDLE, registry.snapshot(7L).state());
        registry.start(7L);
        registry.step(7L, "OCR", "Tesseract");
        clock.advance(3);
        registry.step(7L, "AI_EXTRACTION", null);
        registry.step(7L, "AI_EXTRACTION", "Gửi văn bản OCR cho qwen3-vl:8b");
        var running = registry.snapshot(7L);
        assertEquals(ProcessingProgressRegistry.State.RUNNING, running.state());
        assertEquals("AI_EXTRACTION", running.currentStep());
        assertEquals("Gửi văn bản OCR cho qwen3-vl:8b", running.currentDetail());
        assertEquals(2, running.steps().size());
        assertEquals(Duration.ofSeconds(3), Duration.between(running.steps().get(0).startedAt(), running.steps().get(0).endedAt()));
        clock.advance(20);
        registry.finish(7L, false);
        var failed = registry.snapshot(7L);
        assertEquals(ProcessingProgressRegistry.State.FAILED, failed.state());
        assertNull(failed.currentStep());
        assertNotNull(failed.steps().get(1).endedAt());
        registry.step(7L, "PERSISTENCE", null);
        assertEquals(2, registry.snapshot(7L).steps().size()); // finished runs are frozen
    }

    @Test void finishedEntriesExpireButRunningOnesStay() {
        var clock = new MutableClock(); var registry = new ProcessingProgressRegistry(clock);
        registry.start(1L); registry.finish(1L, true);
        registry.start(2L);
        clock.advance(ProcessingProgressRegistry.RETENTION.toSeconds() + 1);
        registry.start(3L); // purge happens on start
        assertEquals(ProcessingProgressRegistry.State.IDLE, registry.snapshot(1L).state());
        assertEquals(ProcessingProgressRegistry.State.RUNNING, registry.snapshot(2L).state());
    }
}
