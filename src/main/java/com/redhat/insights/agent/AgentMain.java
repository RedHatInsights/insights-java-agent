/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import com.redhat.insights.InsightsReportController;
import com.redhat.insights.http.InsightsFileWritingClient;
import com.redhat.insights.http.InsightsHttpClient;
import com.redhat.insights.jars.JarInfo;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.logging.JulLogger;
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

public final class AgentMain {
  private final InsightsLogger logger;
  private final AgentConfiguration configuration;
  private final BlockingQueue<JarInfo> waitingJars;

  private AgentMain(
      InsightsLogger logger, AgentConfiguration configuration, BlockingQueue<JarInfo> jarsToSend) {
    this.logger = logger;
    this.configuration = configuration;
    this.waitingJars = jarsToSend;
  }

  public static void premain(String agentArgs, Instrumentation instrumentation) {
    startAgent(agentArgs, instrumentation);
  }

  public static void agentmain(String agentArgs, Instrumentation instrumentation) {
    startAgent(agentArgs, instrumentation);
  }

  public static void startAgent(String agentArgs, Instrumentation instrumentation) {
    InsightsLogger logger = new JulLogger("AgentMain");
    if (agentArgs == null || "".equals(agentArgs)) {
      logger.error("Unable to start Red Hat Insights client: Need config arguments");
      return;
    }
    Optional<AgentConfiguration> oArgs = parseArgs(logger, agentArgs);
    if (!oArgs.isPresent()) {
      return;
    }
    AgentConfiguration args = oArgs.get();

    BlockingQueue<JarInfo> jarsToSend = new LinkedBlockingQueue<>();
    try {
      logger.info("Starting Red Hat Insights client");
      new AgentMain(logger, args, jarsToSend).start();
      ClassNoticer noticer = new ClassNoticer(logger, jarsToSend);
      instrumentation.addTransformer(noticer);
    } catch (Throwable t) {
      logger.error("Unable to start Red Hat Insights client", t);
      return;
    }
  }

  static Optional<AgentConfiguration> parseArgs(InsightsLogger logger, String agentArgs) {
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

    if (!config.getMaybeAuthToken().isPresent()) {
      Path certPath = Paths.get(config.getCertFilePath());
      Path keyPath = Paths.get(config.getKeyFilePath());
      if (!Files.exists(certPath) || !Files.exists(keyPath)) {
        if (!out.containsKey("debug") || !"true".equals(out.get("debug"))) {
          logger.error("Unable to start Red Hat Insights client: Missing certificate or key files");
          return Optional.empty();
        }
      }
    }

    return Optional.of(config);
  }

  private void start() {
    final InsightsReport simpleReport = AgentBasicReport.of(logger, configuration);
    final PEMSupport pem = new PEMSupport(logger, configuration);

    Supplier<InsightsHttpClient> httpClientSupplier;
    if (configuration.isDebug()) {
      httpClientSupplier = () -> new InsightsFileWritingClient(logger, configuration);
    } else {
      httpClientSupplier =
          () -> new InsightsAgentHttpClient(logger, configuration, () -> pem.createTLSContext());
    }
    final InsightsReportController controller =
        InsightsReportController.of(
            logger, configuration, simpleReport, httpClientSupplier, waitingJars);
    controller.generate();
  }
}
