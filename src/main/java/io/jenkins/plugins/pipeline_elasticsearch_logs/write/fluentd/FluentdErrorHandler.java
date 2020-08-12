package io.jenkins.plugins.pipeline_elasticsearch_logs.write.fluentd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.komamitsu.fluency.RetryableException;
import org.komamitsu.fluency.ingester.sender.ErrorHandler;


public class FluentdErrorHandler implements ErrorHandler {

    private List<Error> errors = Collections.synchronizedList(new ArrayList<>());


    private static class Error {
        private final Throwable throwable;
        public Error(Throwable ex) {
            this.throwable = ex;
        }
    }

    public int getErrorCount() {
        return errors.size();
    }

    public RetryableException findRetryError()
    {
        for (Error error: errors) {
            if (error.throwable instanceof RetryableException) {
                return (RetryableException) error.throwable;
            }
        }
        return null;
    }

    @Override
    public void handle(Throwable e) {
        errors.add(new Error(e));
    }
}
