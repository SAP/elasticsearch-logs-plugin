package io.jenkins.plugins.pipeline_elasticsearch_logs.testutils;

import java.io.IOException;
import java.io.StringWriter;

import hudson.console.AnnotatedLargeText;

public class LogUtils {

    /**
     * Removes annotations in the log and returns the plaintext log.
     *
     * @param logText
     * @return
     * @throws IOException
     */
    public static String removeAnnotations(AnnotatedLargeText<?> logText) throws IOException {
        StringWriter stringWriter = new StringWriter();
        logText.writeLogTo(0, stringWriter);
        stringWriter.flush();
        return stringWriter.getBuffer().toString();
    }

}
