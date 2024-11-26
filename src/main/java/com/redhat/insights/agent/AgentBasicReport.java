/* Copyright (C) Red Hat 2023-2024 */
package com.redhat.insights.agent;

import com.redhat.insights.agent.tpa.CycloneDXSubreport;
import com.redhat.insights.config.InsightsConfiguration;
import com.redhat.insights.jars.ClasspathJarInfoSubreport;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.AbstractTopLevelReportBase;
import com.redhat.insights.reports.InsightsSubreport;
import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

public class AgentBasicReport extends AbstractTopLevelReportBase {
  private static final InsightsLogger logger = AgentLogger.getLogger();

  private AgentBasicReport(
      InsightsConfiguration config, Map<String, InsightsSubreport> subReports) {
    super(logger, config, subReports);
  }

  public static AgentBasicReport of(
      AgentConfiguration configuration, Instrumentation instrumentation) {
    Map<String, InsightsSubreport> reports = new HashMap<>();
    ClasspathJarInfoSubreport jarsReport = new ClasspathJarInfoSubreport(logger);
    reports.put("jars", jarsReport);
    if (configuration.shouldSendSbom()) {
      CycloneDXSubreport sbomReport = new CycloneDXSubreport(logger, instrumentation);
      reports.put("cycloneDX", sbomReport);
    }
    reports.put("details", AgentSubreport.of(jarsReport, configuration));
    return new AgentBasicReport(configuration, reports);
  }

  @Override
  protected long getProcessPID() {
    return Long.parseLong(
        java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
  }

  @Override
  protected Package[] getPackages() {
    return Package.getPackages();
  }
}
