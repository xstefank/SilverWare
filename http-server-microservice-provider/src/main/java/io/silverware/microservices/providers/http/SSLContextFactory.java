/*
 * -----------------------------------------------------------------------\
 * SilverWare
 *  
 * Copyright (C) 2010 - 2013 the original author or authors.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -----------------------------------------------------------------------/
 */
package io.silverware.microservices.providers.http;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * Class for creating {@link SSLContext}.
 *
 * @author Radek Koubsky (radek.koubsky@gmail.com)
 */
public class SSLContextFactory {
   private final String keystore;
   private final char[] keystorePwd;
   private final String truststore;
   private final char[] truststorePwd;

   /**
    * Ctor.
    *
    * @param keystore keystore name
    * @param keystorePwd keystore password
    * @param truststore truststore name
    * @param truststorePwd truststore password
    */
   public SSLContextFactory(final String keystore, final String keystorePwd, final String truststore,
         final String truststorePwd) {
      this.keystore = keystore;
      this.keystorePwd = keystore != null ? keystorePwd.toCharArray() : "".toCharArray();
      this.truststore = truststore;
      this.truststorePwd = truststorePwd != null ? truststorePwd.toCharArray() : "".toCharArray();
   }

   /**
    * Creates and initializes {@link SSLContext}.
    *
    * @return initialized instance of ssl context
    */
   public SSLContext createSSLContext() throws IOException {
      SSLContext sslContext;
      try {
         sslContext = SSLContext.getInstance("TLS");
         sslContext.init(keyManagers(), trustManagers(), null);
      } catch (final NoSuchAlgorithmException | KeyManagementException e) {
         throw new IOException("Unable to initialize SSLContext", e);
      }
      return sslContext;
   }

   private KeyManager[] keyManagers() throws IOException {
      if (this.keystore == null || this.keystore.isEmpty()) {
         return null;
      }
      KeyManagerFactory keyManagerFactory;
      try {
         keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
         keyManagerFactory.init(keyStore(this.keystore, this.keystorePwd), this.keystorePwd);
         return keyManagerFactory.getKeyManagers();
      } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | IOException e) {
         throw new IOException("Unable to initialize KeyManagerFactory", e);
      }
   }

   private TrustManager[] trustManagers() throws IOException {
      if (this.truststore == null || this.truststore.isEmpty()) {
         return null;
      }
      TrustManagerFactory trustManagerFactory;
      try {
         trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
         trustManagerFactory.init(keyStore(this.truststore, this.truststorePwd));
         return trustManagerFactory.getTrustManagers();
      } catch (final KeyStoreException | NoSuchAlgorithmException e) {
         throw new IOException("Unable to initialize TrustManagerFactory", e);
      }
   }

   private KeyStore keyStore(final String name, final char[] password) throws IOException {
      try (InputStream ksStream = getClass().getClassLoader().getResourceAsStream(name);) {
         final KeyStore keyStore = KeyStore.getInstance("JKS");
         keyStore.load(ksStream, password);
         return keyStore;
      } catch (NoSuchAlgorithmException | CertificateException | IOException | KeyStoreException e) {
         throw new IOException(String.format("Unable to load KeyStore %s", name), e);
      }
   }
}
