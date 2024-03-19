/* Copyright (C) Red Hat 2023-2024 */
package com.redhat.insights.agent;

import com.redhat.insights.config.InsightsConfiguration;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

public final class AgentConfiguration implements InsightsConfiguration {

  static final String TRUE = "true";
  static final String FALSE = "false";

  static final String AGENT_ARG_NAME = "name";
  static final String AGENT_ARG_TOKEN = "token";
  static final String AGENT_ARG_TOKEN_FILE = "token_file";
  static final String AGENT_ARG_BASE_URL = "base_url";
  static final String AGENT_ARG_UPLOAD_URI = "uri";
  static final String AGENT_ARG_PROXY = "proxy";
  static final String AGENT_ARG_PROXY_PORT = "proxy_port";
  static final String AGENT_ARG_OPT_OUT = "opt_out";
  static final String AGENT_ARG_DEBUG = "debug";
  static final String AGENT_ARG_IS_OCP = "is_ocp";
  static final String AGENT_ARG_SHOULD_DEFER = "should_defer";

  private final Map<String, String> args;

  private static final AgentLogger logger = AgentLogger.getLogger();

  public AgentConfiguration(Map<String, String> args) {
    this.args = args;
  }

  public Optional<String> getMaybeAuthToken() {
    String value = args.get(AGENT_ARG_TOKEN);
    if (value != null) {
      return Optional.of(value);
    } else {
      // Try getting it from a token file - this is for dynamic attach (and testing)
      String path = args.get(AGENT_ARG_TOKEN_FILE);
      if (path != null) {
        try {
          byte[] encoded = Files.readAllBytes(Paths.get(path));
          value = new String(encoded, Charset.defaultCharset());
          return Optional.of(value);
        } catch (IOException e) {
          logger.warning(
              "Unable to read specified token file: "
                  + path
                  + " this is probably misconfiguration");
        }
      }
    }
    return Optional.empty();
  }

  @Override
  public String getIdentificationName() {
    return args.get(AGENT_ARG_NAME);
  }

  /**
   * This is not used in the agent, so we return an empty string.
   *
   * @return
   */
  @Override
  public String getCertFilePath() {
    return "";
  }

  /**
   * This is not used in the agent, so we return an empty string.
   *
   * @return
   */
  @Override
  public String getKeyFilePath() {
    return "";
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

  @Override
  public String toString() {
    return "AgentConfiguration{" + "args=" + args + '}';
  }

  public boolean isDebug() {
    return TRUE.equalsIgnoreCase(args.getOrDefault(AGENT_ARG_DEBUG, FALSE));
  }

  // See https://issues.redhat.com/browse/MWTELE-93 for more information
  public boolean isOCP() {
    return TRUE.equalsIgnoreCase(args.getOrDefault(AGENT_ARG_IS_OCP, FALSE));
  }

  // See https://issues.redhat.com/browse/MWTELE-93 for more information
  public boolean shouldDefer() {
    return TRUE.equalsIgnoreCase(args.getOrDefault(AGENT_ARG_SHOULD_DEFER, FALSE));
  }
}
