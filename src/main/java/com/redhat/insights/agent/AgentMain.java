/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import com.redhat.insights.InsightsReportController;
import com.redhat.insights.http.InsightsFileWritingClient;
import com.redhat.insights.http.InsightsHttpClient;
import com.redhat.insights.jars.JarInfo;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.InsightsReport;
import com.redhat.insights.tls.PEMSupport;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

/** Main class for the agent. */
public final class AgentMain {
  private static final InsightsLogger logger = new SLF4JLogger(AgentMain.class);

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

    if (agentArgs == null || "".equals(agentArgs)) {
      logger.error("Unable to start Red Hat Insights client: Need config arguments");
      return;
    }
    Optional<AgentConfiguration> oArgs = parseArgs(agentArgs);
    if (!oArgs.isPresent()) {
      return;
    }
    AgentConfiguration config = oArgs.get();

    if (!shouldContinue(config)) {
      return;
    }

    BlockingQueue<JarInfo> jarsToSend = new LinkedBlockingQueue<>();
    try {
      logger.info("Starting Red Hat Insights client");
      new AgentMain(config, jarsToSend).start();
      ClassNoticer noticer = new ClassNoticer(jarsToSend);
      instrumentation.addTransformer(noticer);
    } catch (Throwable t) {
      logger.error("Unable to start Red Hat Insights client", t);
    }
  }

  // Now we have the config, we need to check for the existence of the unshaded client, not just
  // the agent.
  // This is belt-and-braces in case this agent is ever loaded into an
  // app that has built-in Insights support.

  // See https://issues.redhat.com/browse/MWTELE-93 for more information
  static boolean shouldContinue(AgentConfiguration config) {
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
   * @param logger
   * @param agentArgs
   * @return
   */
  static Optional<AgentConfiguration> parseArgs(String agentArgs) {
    Map<String, String> out = new HashMap<>();
    for (String pair : agentArgs.split(";")) {
      String[] kv = pair.split("=");
      if (kv.length != 2) {
        logger.error(
            "Unable to start Red Hat Insights client: Malformed config arguments (should be"
                + " key-value pairs)");
        return Optional.empty();
      }
      out.put(kv[0], kv[1]);
    }
    AgentConfiguration config = new AgentConfiguration(out);

    if (config.getIdentificationName() == null || "".equals(config.getIdentificationName())) {
      logger.error(
          "Unable to start Red Hat Insights client: App requires a name for identification");
      return Optional.empty();
    }
    logger.debug(config.toString());

    if (shouldLookForCerts(config)) {
      Path certPath = Paths.get(config.getCertFilePath());
      Path keyPath = Paths.get(config.getKeyFilePath());
      if (!Files.exists(certPath) || !Files.exists(keyPath)) {
        logger.error("Unable to start Red Hat Insights client: Missing certificate or key files");
        return Optional.empty();
      }
    }

    return Optional.of(config);
  }

  private static boolean shouldLookForCerts(AgentConfiguration config) {
    boolean hasToken = !config.getMaybeAuthToken().isPresent();
    return !hasToken && !config.isDebug() && !config.isFileOnly();
  }

  private void start() {
    final InsightsReport report = AgentBasicReport.of(configuration);
    final PEMSupport pem = new PEMSupport(logger, configuration);

    Supplier<InsightsHttpClient> httpClientSupplier;
    if (configuration.isDebug() || configuration.isFileOnly()) {
      httpClientSupplier = () -> new InsightsFileWritingClient(logger, configuration);
    } else {
      httpClientSupplier =
          () -> new InsightsAgentHttpClient(configuration, () -> pem.createTLSContext());
    }
    final InsightsReportController controller =
        InsightsReportController.of(logger, configuration, report, httpClientSupplier, waitingJars);
    controller.generate();
  }
}
