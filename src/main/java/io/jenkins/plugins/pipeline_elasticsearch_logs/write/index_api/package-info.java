/**
 * An implementation of {@link
 * io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriter} that
 * uses the Elasticsearch Index API to store log events.
 *
 * This is a simple but less performant implementation. For each log event
 * a single API request is sent. No local buffers are used.
 */
package io.jenkins.plugins.pipeline_elasticsearch_logs.write.index_api;
