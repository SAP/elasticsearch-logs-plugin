package io.jenkins.plugins.pipeline_elasticsearch_logs;

import static java.lang.String.format;

import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Utils {

    public static void logThrowableHideDetails(Logger logger, Level level, String message, Throwable error) throws IOException {
        String errorId = logThrowableWithID(logger, level, message, error);
        throw new IOException(format("%s - Search Jenkins log for ErrorID '%s'", message, errorId));
    }
    
    public static String logThrowableWithID(Logger logger, Level level, String message, Throwable error) {
        String errorId = UUID.randomUUID().toString();
        logger.log(level, format("%s - ErrorID: '%s'", message, errorId), error);
        return errorId;
    }
    
}
