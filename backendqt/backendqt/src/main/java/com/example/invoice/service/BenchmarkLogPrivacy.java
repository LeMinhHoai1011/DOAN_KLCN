package com.example.invoice.service;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Suppress original helper/provider logs only on the benchmark worker; structured errors go to output files. */
@Component
@ConditionalOnProperty(name="benchmark.enabled",havingValue="true")
public class BenchmarkLogPrivacy {
    private TurboFilter filter;
    @jakarta.annotation.PostConstruct public void install() {
        if(LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            filter=new TurboFilter() {
                @Override public FilterReply decide(Marker marker,ch.qos.logback.classic.Logger logger,Level level,
                        String format,Object[] params,Throwable throwable) {
                    return "true".equals(MDC.get("benchmark.fileOnly")) ? FilterReply.DENY : FilterReply.NEUTRAL;
                }
            };
            filter.start();context.addTurboFilter(filter);
        }
    }
    @jakarta.annotation.PreDestroy public void remove() {
        if(filter!=null && LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.getTurboFilterList().remove(filter);filter.stop();
        }
    }
}
