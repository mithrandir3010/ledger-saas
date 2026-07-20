package com.ledgersaas.backend.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Spring @Async, MDC context'ini yeni thread'e otomatik taşımaz.
 * Bu decorator, görevi kuyruğa koyan thread'in MDC map'ini (requestId dahil)
 * kopyalayıp görevi çalıştıracak executor thread'ine aktarır.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                } else {
                    MDC.clear();
                }
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
