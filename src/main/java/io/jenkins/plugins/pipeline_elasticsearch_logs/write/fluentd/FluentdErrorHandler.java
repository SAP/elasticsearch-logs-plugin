package io.jenkins.plugins.pipeline_elasticsearch_logs.write.fluentd;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.fluentd.logger.errorhandler.ErrorHandler;

public class FluentdErrorHandler extends ErrorHandler {

    private List<Error> errors = new ArrayList<Error>();
    
    @Override
    public void handleNetworkError(IOException ex) {
        errors.add(new Error(ex));
    }

    private static class Error {
        private final IOException exception;
        private final long timemillis;
        public Error(IOException ex) {
            this.exception = ex;
            this.timemillis = System.currentTimeMillis();
        }
        
    }

    public int getErrorCount() {
        return errors.size();
    }
    
    public Exception getLastError(long afterTimestampMillis) {
        int size = errors.size();
        if(size <= 0) return null;
        Error error = errors.get(size-1);
        if(afterTimestampMillis > 0 && error.timemillis <= afterTimestampMillis) {
            return null;
        }
        return error.exception;
    }

}
