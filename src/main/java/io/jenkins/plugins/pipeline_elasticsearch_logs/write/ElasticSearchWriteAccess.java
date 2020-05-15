package io.jenkins.plugins.pipeline_elasticsearch_logs.write;

import java.io.IOException;
import java.util.function.Supplier;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

public abstract class ElasticSearchWriteAccess extends AbstractDescribableImpl<ElasticSearchWriteAccess> implements ExtensionPoint {

    public abstract void push(String data) throws IOException;

    public static abstract class ElasticSearchWriteAccessDescriptor extends Descriptor<ElasticSearchWriteAccess> {
        protected ElasticSearchWriteAccessDescriptor() {
        }
    }

    /**
     * For serialization return a supplier providing a deserialized instance
     * of this object at a later point in time.
     * @return
     */
    public abstract Supplier<ElasticSearchWriteAccess> getSupplier() throws IOException;

}
