package io.jenkins.plugins.pipeline_elasticsearch_logs.write.index_api;

import java.util.Collections;
import java.util.List;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;

import hudson.security.ACL;
import jenkins.model.Jenkins;

final class CredentialsHelper {

    private CredentialsHelper() {}

    @CheckForNull
    static <T extends Credentials> T findCredentials(Class<T> clazz, @Nonnull String id) {
        List<T> credentials = CredentialsProvider.lookupCredentials(
            clazz,
            Jenkins.get(),
            ACL.SYSTEM,
            Collections.emptyList()
        );

        IdMatcher matcher = new IdMatcher(id);
        for (T cred : credentials) {
            if (matcher.matches(cred)) {
                return cred;
            }
        }
        return null;
    }
}
