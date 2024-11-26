/* Copyright (C) Red Hat 2023-2024 */
package com.redhat.insights.agent;

import com.redhat.insights.config.EnvAndSysPropsInsightsConfiguration;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

public final class AgentConfiguration extends EnvAndSysPropsInsightsConfiguration {

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
  static final String AGENT_ARG_SHOULD_DEFER = "should_defer";
  static final String AGENT_ARG_POD_NAME = "pod_name";
  static final String AGENT_ARG_POD_NAMESPACE = "pod_namespace";
  static final String AGENT_ARG_SEND_SBOM = "sbom";

  static final String PROPERTY_NOT_GIVEN_DEFAULT = "[NONE]";
  private final Map<String, String> args;

  private static final AgentLogger logger = AgentLogger.getLogger();
  private String tokenValue = null;

  public AgentConfiguration(Map<String, String> args) {
    this.args = args;
  }

  public synchronized Optional<String> getMaybeAuthToken() {
    if (tokenValue != null) {
      return Optional.of(tokenValue);
    } else {
      tokenValue = args.get(AGENT_ARG_TOKEN);
      if (tokenValue != null) {
        return Optional.of(tokenValue);
      }
      // Try getting it from a token file - this is for dynamic attach (and testing)
      String path = args.get(AGENT_ARG_TOKEN_FILE);
      if (path != null) {
        try {
          byte[] encoded = Files.readAllBytes(Paths.get(path));
          tokenValue = new String(encoded, Charset.defaultCharset());
          return Optional.of(tokenValue);
        } catch (IOException e) {
          logger.warning(
              "Unable to read specified token file: "
                  + path
                  + " this is probably misconfiguration");
        }
      }
    }
    return super.getMaybeAuthToken();
  }

  @Override
  public String getIdentificationName() {
    String out = args.get(AGENT_ARG_NAME);
    if (out != null) {
      return out;
    }
    return super.getIdentificationName();
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
    return super.getUploadBaseURL();
  }

  @Override
  public String getUploadUri() {
    if (args.containsKey(AGENT_ARG_UPLOAD_URI)) {
      return args.get(AGENT_ARG_UPLOAD_URI);
    }
    return super.getUploadUri();
  }

  @Override
  public Optional<ProxyConfiguration> getProxyConfiguration() {
    if (args.containsKey(AGENT_ARG_PROXY) && args.containsKey(AGENT_ARG_PROXY_PORT)) {
      return Optional.of(
          new ProxyConfiguration(
              args.get(AGENT_ARG_PROXY), Integer.parseUnsignedInt(args.get(AGENT_ARG_PROXY_PORT))));
    }
    return super.getProxyConfiguration();
  }

  @Override
  public boolean isOptingOut() {
    if (args.containsKey(AGENT_ARG_OPT_OUT)) {
      return TRUE.equalsIgnoreCase(args.get(AGENT_ARG_OPT_OUT));
    }
    return super.isOptingOut();
  }

  ///////////////////////////////////////////////////////////////////////////
  // Agent specific configuration

  public boolean isDebug() {
    return TRUE.equalsIgnoreCase(args.getOrDefault(AGENT_ARG_DEBUG, FALSE));
  }

  // See https://issues.redhat.com/browse/MWTELE-93 for more information
  public boolean shouldDefer() {
    return TRUE.equalsIgnoreCase(args.getOrDefault(AGENT_ARG_SHOULD_DEFER, FALSE));
  }

  public boolean shouldSendSbom() {
    return TRUE.equalsIgnoreCase(args.getOrDefault(AGENT_ARG_SEND_SBOM, FALSE));
  }

  @Override
  public String toString() {
    return "AgentConfiguration{" + "args=" + args + '}';
  }

  // Openshift specific report properties
  public boolean isOCP() {
    return getMaybeAuthToken().isPresent();
  }

  public String getPodNamespace() {
    return args.getOrDefault(AGENT_ARG_POD_NAMESPACE, PROPERTY_NOT_GIVEN_DEFAULT);
  }

  public String getPodName() {
    return args.getOrDefault(AGENT_ARG_POD_NAME, PROPERTY_NOT_GIVEN_DEFAULT);
  }
}
