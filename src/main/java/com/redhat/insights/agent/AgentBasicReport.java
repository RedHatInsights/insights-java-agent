/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import com.redhat.insights.config.InsightsConfiguration;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.AbstractTopLevelReportBase;
import com.redhat.insights.reports.InsightsSubreport;
import java.util.Collections;
import java.util.Map;

public class AgentBasicReport extends AbstractTopLevelReportBase {
  private AgentBasicReport(
      InsightsLogger logger,
      InsightsConfiguration config,
      Map<String, InsightsSubreport> subReports) {
    super(logger, config, subReports);
  }

  public static AgentBasicReport of(InsightsLogger logger, InsightsConfiguration configuration) {
    return new AgentBasicReport(logger, configuration, Collections.emptyMap());
  }

  public static AgentBasicReport of(
      InsightsLogger logger,
      InsightsConfiguration configuration,
      Map<String, InsightsSubreport> subReports) {
    return new AgentBasicReport(logger, configuration, subReports);
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
