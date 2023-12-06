/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.redhat.insights.jars.ClasspathJarInfoSubreport;
import com.redhat.insights.jars.JarInfo;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.InsightsSubreport;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class AgentSubreport implements InsightsSubreport {
  private static final InsightsLogger logger = new SLF4JLogger(AgentSubreport.class);

  private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class[0];
  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

  private final ClasspathJarInfoSubreport jarsReport;

  private String guessedWorkload = "Unidentified";

  private static final Map<String, Function<Class<?>, String>> activeGuesses = new HashMap<>();

  static {
    activeGuesses.put("org.springframework.boot.SpringApplication", __ -> "Spring Boot");
    activeGuesses.put("org.springframework.boot.loader.Launcher", __ -> "Spring Boot");
    activeGuesses.put("org.jboss.modules.Module", AgentSubreport::fingerprintJBoss);
    activeGuesses.put(
        "io.quarkus.bootstrap.runner.QuarkusEntryPoint", AgentSubreport::fingerprintQuarkus);
    activeGuesses.put("org.apache.catalina.Server", AgentSubreport::fingerprintTomcat);
  }

  private AgentSubreport(ClasspathJarInfoSubreport jarsReport) {
    this.jarsReport = jarsReport;
  }

  public static InsightsSubreport of(ClasspathJarInfoSubreport jarsReport) {
    return new AgentSubreport(jarsReport);
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
    } else {
      fingerprintReflectively(jarInfos);
    }
  }

  private void fingerprintReflectively(Collection<JarInfo> jarInfos) {
    String workload = "";
    for (Map.Entry<String, Function<Class<?>, String>> guess : activeGuesses.entrySet()) {
      try {
        Class<?> clazz = Class.forName(guess.getKey());
        // FIXME Handle multiple matches "Possibly: X or Y"
        workload = guess.getValue().apply(clazz);
        break;
      } catch (ClassNotFoundException __) {
        // not found - ignore
      }
    }
    if (!workload.isEmpty()) {
      guessedWorkload = workload;
    }
  }

  static String fingerprintTomcat(Class<?> qClazz) {
    try {
      Class.forName("org.apache.tomcat.vault.VaultInteraction");
    } catch (ClassNotFoundException __) {
      return "Tomcat";
    }
    return "JWS";
  }

  static String fingerprintQuarkus(Class<?> qClazz) {
    String quarkusVersion = qClazz.getPackage().getImplementationVersion();
    return "Quarkus " + quarkusVersion;
  }

  static Class<?> getModuleClass() throws ClassNotFoundException {
    return AgentSubreport.class.getClassLoader().loadClass("org.jboss.modules.Module");
  }

  static ClassLoader getModuleClassLoader(Object moduleLoader, String moduleName)
      throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
          IllegalArgumentException, InvocationTargetException {
    Method getClassLoaderMethod =
        getModuleClass().getDeclaredMethod("getClassLoader", EMPTY_CLASS_ARRAY);
    return (ClassLoader)
        getClassLoaderMethod.invoke(loadModule(moduleLoader, moduleName), EMPTY_OBJECT_ARRAY);
  }

  static Object loadModule(Object moduleLoader, String moduleName)
      throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
          IllegalArgumentException, InvocationTargetException {
    Method loadModuleMethod = moduleLoader.getClass().getMethod("loadModule", String.class);
    return loadModuleMethod.invoke(moduleLoader, moduleName);
  }

  static String fingerprintJBoss(Class<?> moduleClass) {
    try {
      Method getBootModuleLoaderMethod =
          getModuleClass().getDeclaredMethod("getBootModuleLoader", EMPTY_CLASS_ARRAY);
      Object moduleLoader = getBootModuleLoaderMethod.invoke(null, EMPTY_OBJECT_ARRAY);
      ClassLoader versionModuleClassLoader =
          getModuleClassLoader(moduleLoader, "org.jboss.as.version");
      String home = System.getProperty("jboss.home.dir", null);
      if (home != null) {
        Class<?> moduleLoaderClass = getJBossModuleLoaderClass(moduleLoader.getClass());
        Class<?> productConfigClass =
            versionModuleClassLoader.loadClass("org.jboss.as.version.ProductConfig");
        Method fromFilesystemSlotMethod =
            productConfigClass.getDeclaredMethod(
                "fromFilesystemSlot", moduleLoaderClass, String.class, Map.class);
        Object productConfig =
            fromFilesystemSlotMethod.invoke(
                null, moduleLoaderClass.cast(moduleLoader), home, Collections.emptyMap());
        //        this.productName =
        //                (String)
        //                        productConfigClass
        //                                .getDeclaredMethod("getProductName", EMPTY_CLASS_ARRAY)
        //                                .invoke(productConfig, EMPTY_OBJECT_ARRAY);
        //        this.productVersion =
        //                (String)
        //                        productConfigClass
        //                                .getDeclaredMethod("getProductVersion", EMPTY_CLASS_ARRAY)
        //                                .invoke(productConfig, EMPTY_OBJECT_ARRAY);
        //        this.isProduct =
        //                (Boolean)
        //                        productConfigClass
        //                                .getDeclaredMethod("isProduct", EMPTY_CLASS_ARRAY)
        //                                .invoke(productConfig, EMPTY_OBJECT_ARRAY);
        return (String)
            productConfigClass
                .getDeclaredMethod("getPrettyVersionString", EMPTY_CLASS_ARRAY)
                .invoke(productConfig, EMPTY_OBJECT_ARRAY);
      }
    } catch (NoSuchMethodException
        | SecurityException
        | ClassNotFoundException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException ex) {
      // ignore
    }
    return "Unknown EAP / Wildfly - possibly misconfigured";
  }

  static Class<?> getJBossModuleLoaderClass(Class<?> subModuleLoaderClass) {
    if ("org.jboss.modules.ModuleLoader".equals(subModuleLoaderClass.getName())) {
      return subModuleLoaderClass;
    }
    if ("java.lang.Object".equals(subModuleLoaderClass.getName())) {
      throw new IllegalArgumentException(
          subModuleLoaderClass + " is not a subclass of org.jboss.modules.ModuleLoader");
    }
    return getJBossModuleLoaderClass(subModuleLoaderClass.getSuperclass());
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
