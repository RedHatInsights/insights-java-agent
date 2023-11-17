/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.redhat.insights.jars.ClasspathJarInfoSubreport;
import com.redhat.insights.jars.JarInfo;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.InsightsSubreport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AgentSubreport implements InsightsSubreport {

  private final InsightsLogger logger;
  private final ClasspathJarInfoSubreport jarsReport;

  private String guessedWorkload = "Unidentified";

  private static final Map<String, String> guesses = new HashMap<>();

  static {
    guesses.put("org/springframework/boot/SpringApplication", "Spring Boot");
    guesses.put("io/quarkus/bootstrap/QuarkusBootstrap", "Quarkus");
  }

  private AgentSubreport(InsightsLogger logger, ClasspathJarInfoSubreport jarsReport) {
    this.logger = logger;
    this.jarsReport = jarsReport;
  }

  public static InsightsSubreport of(InsightsLogger logger, ClasspathJarInfoSubreport jarsReport) {
    return new AgentSubreport(logger, jarsReport);
  }

  @Override
  public void generateReport() {
    if (jarsReport.getJarInfos().isEmpty()) {
      // Jar subreport has not been generated yet, generate it now
      // This could cause unnecessary dual-generation (the correct thing is probably to fix the
      // ClasspathJarInfoSubreport, so it checks and doesn't dual-generate)
      jarsReport.generateReport();
    }
    Collection<JarInfo> jarInfos = jarsReport.getJarInfos();
    if (jarInfos.isEmpty()) {
      logger.warning("No JARs found in AgentSubreport");
    } else if (jarInfos.size() == 1) {
      // Blob / uberjar - use class based fingerprinting
      fingerprintByClass(jarInfos.toArray(new JarInfo[0])[0]);
    } else {
      // JAR based fingerprinting
      fingerprintByJar(jarInfos);
    }
  }

  void fingerprintByJar(Collection<JarInfo> jarInfos) {}

  void fingerprintByClass(JarInfo jarInfo) {
    // Deal with the special cases first
    if (jarInfo.name().contains("jboss-modules")) {
      guessedWorkload = "EAP / Wildfly";
      return;
    }
    // Try to find Quarkus
    try {
      Class.forName("io.quarkus.bootstrap.QuarkusBootstrap");
      guessedWorkload = "Quarkus";
      return;
    } catch (ClassNotFoundException __) {
      // not found
    }

    // Open the JAR and look for classes
    String classLocation = String.valueOf(System.getProperties().get("java.class.path"));
    try (ZipInputStream zipIn =
        new ZipInputStream(Files.newInputStream(Paths.get(classLocation)))) {
      ZipEntry entry = zipIn.getNextEntry();
      String workload = "";
      while (entry != null) {
        if (entry.getName().endsWith(".class")) {
          String className = entry.getName();
          logger.info("Found class: " + className);
          for (Map.Entry<String, String> guess : guesses.entrySet()) {
            if (className.contains(guess.getKey())) {
              // FIXME Handle multiple matches "Possibly: X or Y"
              workload = guess.getValue();
              break;
            }
          }
        }
        entry = zipIn.getNextEntry();
      }
      if (!workload.isEmpty()) {
        guessedWorkload = workload;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public JsonSerializer<InsightsSubreport> getSerializer() {
    return new AgentSubreportSerializer();
  }

  public String getGuessedWorkload() {
    return guessedWorkload;
  }
}
