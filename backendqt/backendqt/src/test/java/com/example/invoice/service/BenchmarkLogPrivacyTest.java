package com.example.invoice.service;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkLogPrivacyTest {
    @Test void suppressesHelperDataOnlyOnBenchmarkThread() {
        var privacy=new BenchmarkLogPrivacy();privacy.install();
        Logger logger=(Logger)LoggerFactory.getLogger("benchmark-privacy-test");
        var appender=new ListAppender<ILoggingEvent>();appender.start();logger.addAppender(appender);
        try {
            MDC.put("benchmark.fileOnly","true");logger.warn("private invoice value");
            MDC.remove("benchmark.fileOnly");logger.warn("ordinary business event");
            assertEquals(1,appender.list.size());assertEquals("ordinary business event",appender.list.getFirst().getFormattedMessage());
        }finally{MDC.remove("benchmark.fileOnly");logger.detachAppender(appender);privacy.remove();}
    }
}
