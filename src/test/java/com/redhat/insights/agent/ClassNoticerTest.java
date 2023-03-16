/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent; /* Copyright (C) Red Hat 2023 */

import com.redhat.insights.InsightsCustomScheduledExecutor;
import com.redhat.insights.InsightsReport;
import com.redhat.insights.InsightsReportController;
import com.redhat.insights.InsightsScheduler;
import com.redhat.insights.config.InsightsConfiguration;
import com.redhat.insights.doubles.MockInsightsConfiguration;
import com.redhat.insights.doubles.NoopInsightsLogger;
import com.redhat.insights.http.InsightsHttpClient;
import com.redhat.insights.jars.JarInfo;
import com.redhat.insights.logging.InsightsLogger;
import java.lang.instrument.Instrumentation;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class ClassNoticerTest {

  @Test
  @Disabled
  public void testNoticer() throws InterruptedException {
    // Setup Byte Buddy agent
    Instrumentation instrumentation = ByteBuddyAgent.install();
    Assertions.assertInstanceOf(Instrumentation.class, instrumentation);

    InsightsLogger logger = new NoopInsightsLogger();
    BlockingQueue<JarInfo> jarsToSend = new LinkedBlockingQueue<>();
    ClassNoticer noticer = new ClassNoticer(logger, jarsToSend);
    instrumentation.addTransformer(noticer);

    InsightsConfiguration mockConfig =
        MockInsightsConfiguration.of("test_app", false, Duration.ofDays(1), Duration.ofSeconds(5));
    InsightsHttpClient mockHttpClient = Mockito.mock(InsightsHttpClient.class);
    Mockito.when(mockHttpClient.isReadyToSend()).thenReturn(true);
    InsightsReport report = AgentBasicReport.of(logger, mockConfig);
    InsightsScheduler scheduler = InsightsCustomScheduledExecutor.of(logger, mockConfig);

    InsightsReportController controller =
        InsightsReportController.of(
            logger, mockConfig, report, () -> mockHttpClient, scheduler, jarsToSend);
    controller.generate();

    Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                Mockito.verify(mockHttpClient, Mockito.times(2))
                    .sendInsightsReport(
                        ArgumentMatchers.any(), (InsightsReport) ArgumentMatchers.any()));
  }
}
