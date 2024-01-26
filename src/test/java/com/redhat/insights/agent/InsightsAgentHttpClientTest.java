/* Copyright (C) Red Hat 2024 */
package com.redhat.insights.agent;

import static com.redhat.insights.InsightsErrorCode.ERROR_SSL_CREATING_CONTEXT;
import static com.redhat.insights.agent.AgentMain.parseArgs;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.insights.InsightsException;
import java.util.*;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpPost;
import org.junit.jupiter.api.Test;

public class InsightsAgentHttpClientTest {

  @Test
  void testBearerToken() {
    Optional<AgentConfiguration> oConfig = parseArgs("name=foo;token=bar;is_ocp=true");
    InsightsAgentHttpClient client =
        new InsightsAgentHttpClient(
            oConfig.get(),
            () -> {
              throw new InsightsException(
                  ERROR_SSL_CREATING_CONTEXT,
                  "Illegal attempt to create SSLContext for token auth");
            });
    HttpPost post = client.createAuthTokenPost();
    List<Header> headers = Arrays.asList(post.getAllHeaders());
    assertTrue(
        headers.stream()
            .anyMatch(
                h -> h.getName().equals("Authorization") && h.getValue().equals("Bearer bar")));
  }
}
