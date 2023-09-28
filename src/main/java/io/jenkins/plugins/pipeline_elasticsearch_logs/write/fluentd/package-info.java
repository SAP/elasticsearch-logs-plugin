/**
 * An implementation of {@link
 * io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriter} that
 * sends the events via Fluentd Forward Protocol. The destination typically
 * is a Fluentd log forwarder, but may be any other implementation that
 * understands the protocol.
 *
 * Internally the <a href="https://github.com/komamitsu/fluency">Fluency</a>
 * library is used.
 *
 * Log events are not sent immediately but put into an in-memory buffer first.
 * Fluency takes care of flushing the buffer.
 */
package io.jenkins.plugins.pipeline_elasticsearch_logs.write.fluentd;
