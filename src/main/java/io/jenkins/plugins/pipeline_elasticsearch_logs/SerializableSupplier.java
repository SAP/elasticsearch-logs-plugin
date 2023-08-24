package io.jenkins.plugins.pipeline_elasticsearch_logs;


import java.io.Serializable;
import java.util.function.Supplier;

public interface SerializableSupplier<T> extends Supplier<T>, Serializable {
    // Only method inherited from Supplier
}
