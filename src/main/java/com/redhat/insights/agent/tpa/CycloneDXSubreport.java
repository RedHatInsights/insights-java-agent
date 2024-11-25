/* Copyright (C) Red Hat 2024 */
package com.redhat.insights.agent.tpa;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.InsightsSubreport;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLDecoder;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public final class CycloneDXSubreport implements InsightsSubreport {

  private final InsightsLogger logger;
  private final Instrumentation inst;
  private CycloneDXModel sbom = null;

  private static final String CLASSPATH_ENV = "java.class.path";

  public CycloneDXSubreport(InsightsLogger logger, Instrumentation inst) {
    this.logger = logger;
    this.sbom = new CycloneDXModel(logger);
    this.inst = inst;
  }

  @Override
  public synchronized void generateReport() {
    Libraries libs = new Libraries(logger);
    if (inst == null) {
      // FIXME
      return;
    }

    Class[] classes = inst.getAllLoadedClasses();
    for (Class clazz : classes) {
      try {
        if (!isSkippable(clazz)) {
          ProtectionDomain pd = clazz.getProtectionDomain();
          if (pd != null) {
            CodeSource cs = pd.getCodeSource();
            if (cs != null) {
              URL url = cs.getLocation();
              if (url != null) {
                String codesource = url.toString();
                String decoded = URLDecoder.decode(codesource, "UTF-8");
                libs.addAllLibraries(clazz, decoded);
              }
            }
          }
        }
      } catch (Exception e) {
        logger.info("Error processing class: " + clazz.getName());
      }
    }

    CycloneDXModel sbom = new CycloneDXModel(logger);
    sbom.setComponents(libs.getLibraries());
    sbom.setDependencies(libs.getDependencies());
    //    sbom.save( filename );
  }

  public static boolean isSkippable(Class clazz) {
    if (clazz == null || clazz.isArray() || clazz.isPrimitive() || clazz.isInterface()) {
      return true;
    }

    // skip primordial classloader
    return clazz.getClassLoader() == null;
  }

  @Override
  public String getVersion() {
    return "" + sbom.getVersion();
  }

  @Override
  public JsonSerializer<InsightsSubreport> getSerializer() {
    return new CycloneDXSubreportSerializer();
  }
}
