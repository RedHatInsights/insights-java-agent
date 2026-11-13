/* Copyright (C) Red Hat 2023-2025 */
package com.redhat.insights.agent;

import com.redhat.insights.agent.api.SubreportProvider;
import com.redhat.insights.config.InsightsConfiguration;
import com.redhat.insights.jars.ClasspathJarInfoSubreport;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.AbstractTopLevelReportBase;
import com.redhat.insights.reports.InsightsSubreport;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class AgentBasicReport extends AbstractTopLevelReportBase {
  private static final InsightsLogger logger = AgentLogger.getLogger();

  private AgentBasicReport(
      InsightsConfiguration config, Map<String, InsightsSubreport> subReports) {
    super(logger, config, subReports);
  }

  public static AgentBasicReport of(AgentConfiguration configuration) {
    Map<String, InsightsSubreport> reports = new HashMap<>();
    ClasspathJarInfoSubreport jarsReport = new ClasspathJarInfoSubreport(logger);
    reports.put("jars", jarsReport);
    reports.put("details", AgentSubreport.of(jarsReport, configuration));
    addExtensionReports(reports, configuration);
    return new AgentBasicReport(configuration, reports);
  }

  private static void addExtensionReports(
      Map<String, InsightsSubreport> reports, AgentConfiguration configuration) {
    // Load the agent SubreportProvider if available
    ServiceLoader<SubreportProvider> loader = ServiceLoader.load(SubreportProvider.class);
    for (SubreportProvider provider : loader) {
      Map<String, InsightsSubreport> subs = provider.provideSubreports(configuration, logger);
      // add any provided subreports to main agent report
      // ensure there is no name conflict, fail if there is
      for (String name : subs.keySet()) {
        if (reports.containsKey(name)) {
          throw new RuntimeException(
              "The key provided by the subreport provider ('"
                  + name
                  + "': "
                  + subs.get(name)
                  + ") exists already! ("
                  + reports.get(name)
                  + ")");
        }
        reports.put(name, subs.get(name));
      }
    }
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
