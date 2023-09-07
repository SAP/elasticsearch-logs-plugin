package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.time.format.DateTimeFormatter;

public final class ElasticSearchFieldNames {

  private ElasticSearchFieldNames() {
    // No need to instantiate the class, its constructor can be hidden
  }

  private static final String UID = "uid";
  private static final String RUN_ID = "runId";
  private static final String TIMESTAMP_MILLIS = "timestampMillis";
  private static final String TIMESTAMP = "timestamp";
  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSX");
}
