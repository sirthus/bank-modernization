package com.modernize.bankbatch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

@Component
public class ProgressListener implements ChunkListener {

    private static final Logger log = LoggerFactory.getLogger(ProgressListener.class);

    @Override
    public void afterChunk(ChunkContext context) {
        long readCount = context.getStepContext().getStepExecution().getReadCount();
        long writeCount = context.getStepContext().getStepExecution().getWriteCount();
        long skipCount = context.getStepContext().getStepExecution().getSkipCount();
        String stepName = context.getStepContext().getStepName();

        log.info("[{}] processed {} read, {} written, {} skipped",
                stepName, readCount, writeCount, skipCount);
    }
}
