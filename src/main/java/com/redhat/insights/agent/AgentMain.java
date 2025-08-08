/* Copyright (C) Red Hat 2023-2025 */
package com.redhat.insights.agent;

import com.redhat.insights.InsightsException;
import com.redhat.insights.InsightsReportController;
import com.redhat.insights.http.InsightsFileWritingClient;
import com.redhat.insights.http.InsightsHttpClient;
import com.redhat.insights.jars.JarInfo;
import com.redhat.insights.reports.InsightsReport;
import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

/** Main class for the agent. */
public final class AgentMain {
  private static final AgentLogger logger = AgentLogger.getLogger();

  private final AgentConfiguration configuration;
  private final BlockingQueue<JarInfo> waitingJars;

  private static boolean loaded = false;

  private AgentMain(AgentConfiguration configuration, BlockingQueue<JarInfo> jarsToSend) {
    this.configuration = configuration;
    this.waitingJars = jarsToSend;
  }

  public static void premain(String agentArgs, Instrumentation instrumentation) {
    startAgent(agentArgs, instrumentation);
  }

  public static void agentmain(String agentArgs, Instrumentation instrumentation) {
    startAgent(agentArgs, instrumentation);
  }

  /**
   * Real entry point for the agent.
   *
   * @param agentArgs the agent argument string
   * @param instrumentation the instrumentation object, only used for class notification
   */
  public static void startAgent(String agentArgs, Instrumentation instrumentation) {
    synchronized (AgentMain.class) {
      if (loaded) {
        logger.warning("Insights agent already loaded, skipping");
        return;
      }
      loaded = true;
    }

    Optional<AgentConfiguration> oArgs = parseArgs(agentArgs);
    if (!oArgs.isPresent()) {
      return;
    }
    AgentConfiguration config = oArgs.get();

    if (!shouldContinue(config)) {
      logger.info(
          "Config indicates Red Hat Insights agent is not to be run. Not starting agent capability.");
      return;
    }
    if (config.isDebug()) {
      logger.setDebugDelegate();
      logger.debug("Running in debug mode");
    }

    final BlockingQueue<JarInfo> jarsToSend = new LinkedBlockingQueue<>();
    try {
      logger.info("Starting Red Hat Insights agent");
      new AgentMain(config, jarsToSend).start();
      ClassNoticer noticer = new ClassNoticer(jarsToSend);
      instrumentation.addTransformer(noticer);
    } catch (Throwable t) {
      logger.error("Unable to start Red Hat Insights client", t);
    }
  }

  // Now we have the config, we need to check for the existence of the unshaded client, not just
  // the agent. This is belt-and-braces in case this agent is ever loaded into an
  // app that has built-in Insights support.
  //
  // If we detect the unshaded client, we should defer to that. If we don't, we should continue.

  // See https://issues.redhat.com/browse/MWTELE-93 for more information
  static boolean shouldContinue(AgentConfiguration config) {
    if (config.isOptingOut()) {
      return false;
    }

    try {
      // This obfuscation is necessary to work around the shader plugin which will try to helpfully
      // rename the class name when we don't want it to.
      String obfuscatedStem = "com.redhat";
      String obfuscatedSubPackageAndClass = ".insights.InsightsReportController";

      Class.forName(obfuscatedStem + obfuscatedSubPackageAndClass);
      if (config.isOCP()) {
        if (config.shouldDefer()) {
          logger.warning("Insights builtin support is available, deferring to that");
          return false;
        } else {
          logger.warning(
              "Starting Red Hat Insights client: Builtin support for OpenShift is available, but"
                  + " the agent is configured to run anyway. Ensure that this configuration is correct.");
        }
      } else {
        // Always defer if we're on RHEL
        logger.warning("Insights builtin support is available, deferring to that");
        return false;
      }
    } catch (ClassNotFoundException __) {
      // Builtin support not found, continue
    }
    return true;
  }

  /**
   * Parse the agent arguments from the form "key1=value1;key2=value2;..."
   *
   * @param agentArgs
   * @return
   */
  static Optional<AgentConfiguration> parseArgs(String agentArgs) {
    Map<String, String> out = new HashMap<>();
    if (agentArgs != null && !agentArgs.isEmpty()) {
      for (String pair : agentArgs.split(";")) {
        String[] kv = pair.split("=");
        if (kv.length != 2) {
          logger.error(
              "Unable to start Red Hat Insights agent: Malformed config arguments (should be"
                  + " key-value pairs)");
          return Optional.empty();
        }
        out.put(kv[0], kv[1]);
      }
    }
    AgentConfiguration config = new AgentConfiguration(out);

    if (config.getIdentificationName() == null || "".equals(config.getIdentificationName())) {
      logger.error(
          "Unable to start Red Hat Insights agent: App requires a name for identification");
      return Optional.empty();
    }
    logger.debug(config.toString());

    return Optional.of(config);
  }

  private void start() {
    final InsightsReport report = AgentBasicReport.of(configuration);

    final Supplier<InsightsHttpClient> clientSupplier = getInsightsClientSupplier();
    try {
      final InsightsReportController controller =
          InsightsReportController.of(logger, configuration, report, clientSupplier, waitingJars);
      controller.generate();
    } catch (InsightsException e) {
      logger.info("Unable to start Red Hat Insights agent: " + e.getMessage());
    }
  }

  /*
   * There are only two possibilities - either we're running in OCP or we're not. If we are, we
   * need an HTTP client that can talk through the proxy to the Insights service. If we're not, we
   * are running on RHEL and need to put a report somewhere the RHEL Insights client can pick it up.
   */
  private Supplier<InsightsHttpClient> getInsightsClientSupplier() {
    if (configuration.isOCP()) {
      return () -> new InsightsAgentHttpClient(configuration);
    } else {
      return () -> new InsightsFileWritingClient(logger, configuration);
    }
  }
}
