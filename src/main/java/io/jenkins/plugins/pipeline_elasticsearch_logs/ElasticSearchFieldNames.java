package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.time.format.DateTimeFormatter;

public final class ElasticSearchFieldNames {

  private ElasticSearchFieldNames() {
    // No need to instantiate the class, its constructor can be hidden
  }

  public static final String UID = "uid";
  public static final String RUN_ID = "runId";
  public static final String TIMESTAMP_MILLIS = "timestampMillis";
  public static final String TIMESTAMP = "timestamp";
  public static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSX");
}
