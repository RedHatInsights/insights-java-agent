/* Copyright (C) Red Hat 2023-2024 */
package com.redhat.insights.agent; /* Copyright (C) Red Hat 2023 */

import static org.junit.jupiter.api.Assertions.*;

import com.redhat.insights.InsightsCustomScheduledExecutor;
import com.redhat.insights.InsightsReportController;
import com.redhat.insights.InsightsScheduler;
import com.redhat.insights.agent.doubles.MockInsightsConfiguration;
import com.redhat.insights.agent.doubles.NoopInsightsLogger;
import com.redhat.insights.config.InsightsConfiguration;
import com.redhat.insights.http.InsightsHttpClient;
import com.redhat.insights.jars.JarInfo;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.InsightsReport;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.jar.JarFile;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class ClassNoticerTest {

  private static final String WAR_PATH = "src/test/resources/spring-boot-2-app-war-1.0.0.war";

  @Test
  @Disabled
  public void testNoticer() throws InterruptedException {
    // Setup Byte Buddy agent
    Instrumentation instrumentation = ByteBuddyAgent.install();
    Assertions.assertInstanceOf(Instrumentation.class, instrumentation);

    InsightsLogger logger = new NoopInsightsLogger();
    BlockingQueue<JarInfo> jarsToSend = new LinkedBlockingQueue<>();
    ClassNoticer noticer = new ClassNoticer(jarsToSend);
    instrumentation.addTransformer(noticer);

    InsightsConfiguration mockConfig =
        MockInsightsConfiguration.of("test_app", false, Duration.ofDays(1), Duration.ofSeconds(5));
    InsightsHttpClient mockHttpClient = Mockito.mock(InsightsHttpClient.class);
    Mockito.when(mockHttpClient.isReadyToSend()).thenReturn(true);
    InsightsReport report = AgentBasicReport.of(mockConfig);
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

  @Test
  public void testWar_mwtele_230() throws IOException, ClassNotFoundException {
    // Setup Byte Buddy agent
    Instrumentation instrumentation = ByteBuddyAgent.install();
    JarFile jarFile = new JarFile(WAR_PATH);
    instrumentation.appendToSystemClassLoaderSearch(jarFile);

    BlockingQueue<JarInfo> jarsToSend = new LinkedBlockingQueue<>();
    ClassNoticer noticer = new ClassNoticer(jarsToSend);
    instrumentation.addTransformer(noticer);

    // OK, agent is setup, now do the test
    Class<?> clz = Class.forName("org.springframework.boot.loader.JarLauncher");
    assertNotNull(clz);
  }
}
