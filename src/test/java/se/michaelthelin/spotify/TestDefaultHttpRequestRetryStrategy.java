/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package se.michaelthelin.spotify;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class TestDefaultHttpRequestRetryStrategy {

  private SpotifyHttpRequestRetryStrategy retryStrategy;

  @BeforeEach
  public void setup() {
    this.retryStrategy = new SpotifyHttpRequestRetryStrategy(3, TimeValue.ofMilliseconds(1234L));
  }

  @Test
  public void testBasics() {
    final HttpResponse response1 = new BasicHttpResponse(503, "Oopsie");
    Assertions.assertTrue(this.retryStrategy.retryRequest(response1, 1, null));
    Assertions.assertTrue(this.retryStrategy.retryRequest(response1, 2, null));
    Assertions.assertTrue(this.retryStrategy.retryRequest(response1, 3, null));
    Assertions.assertFalse(this.retryStrategy.retryRequest(response1, 4, null));
    final HttpResponse response2 = new BasicHttpResponse(500, "Big Time Oopsie");
    Assertions.assertFalse(this.retryStrategy.retryRequest(response2, 1, null));
    final HttpResponse response3 = new BasicHttpResponse(429, "Oopsie");
    Assertions.assertFalse(this.retryStrategy.retryRequest(response3, 1, null));
    Assertions.assertFalse(this.retryStrategy.retryRequest(response3, 2, null));
    Assertions.assertFalse(this.retryStrategy.retryRequest(response3, 3, null));
    Assertions.assertFalse(this.retryStrategy.retryRequest(response3, 4, null));

    Assertions.assertEquals(TimeValue.ofMilliseconds(1234L), this.retryStrategy.getRetryInterval(response1, 1, null));
  }

  @Test
  public void testRetryAfterHeaderAsLong() {
    final HttpResponse response = new BasicHttpResponse(503, "Oopsie");
    response.setHeader(HttpHeaders.RETRY_AFTER, "321");

    Assertions.assertEquals(TimeValue.ofMilliseconds(1234L), this.retryStrategy.getRetryInterval(response, 3, null));
  }

  @Test
  public void testRetryAfterHeaderAsDate() {
    this.retryStrategy = new SpotifyHttpRequestRetryStrategy(3, TimeValue.ZERO_MILLISECONDS);
    final HttpResponse response = new BasicHttpResponse(503, "Oopsie");
    response.setHeader(HttpHeaders.RETRY_AFTER, DateUtils.formatStandardDate(Instant.now().plus(100, ChronoUnit.SECONDS)));

    Assertions.assertTrue(this.retryStrategy.getRetryInterval(response, 3, null).compareTo(TimeValue.ZERO_MILLISECONDS) == 0);
  }

  @Test
  public void testRetryAfterHeaderAsPastDate() {
    final HttpResponse response = new BasicHttpResponse(503, "Oopsie");
    response.setHeader(HttpHeaders.RETRY_AFTER, DateUtils.formatStandardDate(Instant.now().minus(100, ChronoUnit.SECONDS)));

    Assertions.assertEquals(TimeValue.ofMilliseconds(1234L), this.retryStrategy.getRetryInterval(response, 3, null));
  }

  @Test
  public void testInvalidRetryAfterHeader() {
    final HttpResponse response = new BasicHttpResponse(503, "Oopsie");
    response.setHeader(HttpHeaders.RETRY_AFTER, "Stuff");

    Assertions.assertEquals(TimeValue.ofMilliseconds(1234L), retryStrategy.getRetryInterval(response, 3, null));
  }

  @Test
  public void noRetryOnConnectTimeout() {
    final HttpGet request = new HttpGet("/");

    Assertions.assertFalse(retryStrategy.retryRequest(request, new SocketTimeoutException(), 1, null));
  }

  @Test
  public void noRetryOnConnect() {
    final HttpGet request = new HttpGet("/");

    Assertions.assertFalse(retryStrategy.retryRequest(request, new ConnectException(), 1, null));
  }

  @Test
  public void noRetryOnConnectionClosed() {
    final HttpGet request = new HttpGet("/");

    Assertions.assertFalse(retryStrategy.retryRequest(request, new ConnectionClosedException(), 1, null));
  }

  @Test
  public void noRetryForNoRouteToHostException() {
    final HttpGet request = new HttpGet("/");

    Assertions.assertFalse(retryStrategy.retryRequest(request, new NoRouteToHostException(), 1, null));
  }

  @Test
  public void noRetryOnSSLFailure() {
    final HttpGet request = new HttpGet("/");

    Assertions.assertFalse(retryStrategy.retryRequest(request, new SSLException("encryption failed"), 1, null));
  }

  @Test
  public void noRetryOnUnknownHost() {
    final HttpGet request = new HttpGet("/");

    Assertions.assertFalse(retryStrategy.retryRequest(request, new UnknownHostException(), 1, null));
  }

  @Test
  public void noRetryOnAbortedRequests() {
    final HttpGet request = new HttpGet("/");
    request.cancel();

    Assertions.assertFalse(retryStrategy.retryRequest(request, new IOException(), 1, null));
  }

  @Test
  public void retryOnNonAbortedRequests() {
    final HttpGet request = new HttpGet("/");

    Assertions.assertTrue(retryStrategy.retryRequest(request, new IOException(), 1, null));
  }

}
