
/*
 * The MIT License
 *
 * Copyright 2014 Barnes and Noble College
 *
 * Source: https://github.com/jenkinsci/logstash-plugin/blob/f3eea8d405d8c4e5df228b61e06efcfb173c5be9/src/main/java/jenkins/plugins/logstash/utils/SSLHelper.java
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;

public class SSLHelper {

    public static void setClientBuilderSSLContext(HttpClientBuilder clientBuilder, KeyStore customKeyStore)
            throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException, KeyManagementException {
        if (customKeyStore == null) return;
        String alias = customKeyStore.aliases().nextElement();
        X509Certificate certificate = (X509Certificate) customKeyStore.getCertificate(alias);
        if (certificate != null) clientBuilder.setSSLContext(createSSLContext(customKeyStore));
    }

    private static SSLContext createSSLContext(KeyStore truststore)
            throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException, KeyManagementException {

        SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
        sslContextBuilder.loadTrustMaterial(truststore, null);
        
        return sslContextBuilder.build();
    }
}
