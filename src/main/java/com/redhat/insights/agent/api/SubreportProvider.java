/* Copyright (C) Red Hat 2025 */
package com.redhat.insights.agent.api;

import com.redhat.insights.agent.AgentConfiguration;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.InsightsSubreport;
import java.util.Map;

public interface SubreportProvider {

  Map<String, InsightsSubreport> provideSubreports(
      AgentConfiguration configuration, InsightsLogger logger);
}
