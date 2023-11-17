/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import com.redhat.insights.config.InsightsConfiguration;
import com.redhat.insights.jars.ClasspathJarInfoSubreport;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.AbstractTopLevelReportBase;
import com.redhat.insights.reports.InsightsSubreport;
import java.util.HashMap;
import java.util.Map;

public class AgentBasicReport extends AbstractTopLevelReportBase {
  private AgentBasicReport(
      InsightsLogger logger,
      InsightsConfiguration config,
      Map<String, InsightsSubreport> subReports) {
    super(logger, config, subReports);
  }

  public static AgentBasicReport of(InsightsLogger logger, InsightsConfiguration configuration) {
    Map<String, InsightsSubreport> reports = new HashMap<>();
    ClasspathJarInfoSubreport jarsReport = new ClasspathJarInfoSubreport(logger);
    reports.put("jars", jarsReport);
    reports.put("details", AgentSubreport.of(logger, jarsReport));
    return new AgentBasicReport(logger, configuration, reports);
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
