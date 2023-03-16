/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import com.redhat.insights.config.InsightsConfiguration;
import java.util.Map;
import java.util.Optional;

public final class AgentConfiguration implements InsightsConfiguration {

  static final String AGENT_ARG_NAME = "name";
  static final String AGENT_ARG_CERT = "cert";
  static final String AGENT_ARG_KEY = "key";
  static final String AGENT_ARG_TOKEN = "token";
  static final String AGENT_ARG_BASE_URL = "base_url";
  static final String AGENT_ARG_UPLOAD_URI = "uri";
  static final String AGENT_ARG_PROXY = "proxy";
  static final String AGENT_ARG_PROXY_PORT = "proxy_port";
  static final String AGENT_ARG_OPT_OUT = "optOut";

  private final Map<String, String> args;

  public AgentConfiguration(Map<String, String> args) {
    this.args = args;
  }

  public Optional<String> getMaybeAuthToken() {
    String value = args.get(AGENT_ARG_TOKEN);
    if (value != null) {
      return Optional.of(value);
    }
    return Optional.empty();
  }

  @Override
  public String getIdentificationName() {
    return args.get(AGENT_ARG_NAME);
  }

  @Override
  public String getCertFilePath() {
    if (args.containsKey(AGENT_ARG_CERT)) {
      return args.get(AGENT_ARG_CERT);
    }
    return InsightsConfiguration.DEFAULT_RHEL_CERT_FILE_PATH;
  }

  @Override
  public String getKeyFilePath() {
    if (args.containsKey(AGENT_ARG_KEY)) {
      return args.get(AGENT_ARG_KEY);
    }
    return InsightsConfiguration.DEFAULT_RHEL_KEY_FILE_PATH;
  }

  @Override
  public String getUploadBaseURL() {
    if (args.containsKey(AGENT_ARG_BASE_URL)) {
      return args.get(AGENT_ARG_BASE_URL);
    }
    return InsightsConfiguration.DEFAULT_UPLOAD_BASE_URL;
  }

  @Override
  public String getUploadUri() {
    if (args.containsKey(AGENT_ARG_UPLOAD_URI)) {
      return args.get(AGENT_ARG_UPLOAD_URI);
    }
    return InsightsConfiguration.DEFAULT_UPLOAD_URI;
  }

  @Override
  public Optional<ProxyConfiguration> getProxyConfiguration() {
    if (args.containsKey(AGENT_ARG_PROXY) && args.containsKey(AGENT_ARG_PROXY_PORT)) {
      return Optional.of(
          new ProxyConfiguration(
              args.get(AGENT_ARG_PROXY), Integer.parseUnsignedInt(args.get(AGENT_ARG_PROXY_PORT))));
    }
    return Optional.empty();
  }

  @Override
  public boolean isOptingOut() {
    if (args.containsKey(AGENT_ARG_OPT_OUT)) {
      return "true".equalsIgnoreCase(args.get(AGENT_ARG_OPT_OUT));
    }
    return false;
  }
}
