/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.redhat.insights.jars.ClasspathJarInfoSubreport;
import com.redhat.insights.jars.JarInfo;
import com.redhat.insights.logging.InsightsLogger;
import com.redhat.insights.reports.InsightsSubreport;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AgentSubreport implements InsightsSubreport {
  private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class[0];
  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

  private final InsightsLogger logger;
  private final ClasspathJarInfoSubreport jarsReport;

  private String guessedWorkload = "Unidentified";

  private static final Map<String, String> classGuesses = new HashMap<>();
  private static final Map<String, String> jarGuesses = new HashMap<>();
  // tomcat-catalina

  static {
    classGuesses.put("org/springframework/boot/SpringApplication", "Spring Boot");
    classGuesses.put("io/quarkus/bootstrap/QuarkusBootstrap", "Quarkus");

    jarGuesses.put("tomcat-catalina", "Tomcat / JWS");
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

  void fingerprintByJar(Collection<JarInfo> jarInfos) {
    String workload = "";
    for (JarInfo jar : jarInfos) {
      for (Map.Entry<String, String> guess : jarGuesses.entrySet()) {
        if (jar.name().contains(guess.getKey())) {
          // FIXME Handle multiple matches "Possibly: X or Y"
          workload = guess.getValue();
          break;
        }
      }
    }
    if (!workload.isEmpty()) {
      guessedWorkload = workload;
    }
  }

  void fingerprintByClass(JarInfo jarInfo) {
    // Deal with the special cases first
    if (jarInfo.name().contains("jboss-modules")) {
      try {
        Class<?> moduleClass =
            this.getClass().getClassLoader().loadClass("org.jboss.modules.Module");
        guessedWorkload = fingerprintJBoss(moduleClass);
        return;
      } catch (ClassNotFoundException __) {
        // not found
      }
      guessedWorkload = "Unknown EAP / Wildfly";
      return;
    }
    // Try to find Quarkus
    try {
      Class<?> qClass = Class.forName("io.quarkus.bootstrap.runner.QuarkusEntryPoint");
      String quarkusVersion = qClass.getPackage().getImplementationVersion();
      guessedWorkload = "Quarkus " + quarkusVersion;
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
          for (Map.Entry<String, String> guess : classGuesses.entrySet()) {
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

  public Class<?> getModuleClass() throws ClassNotFoundException {
    return this.getClass().getClassLoader().loadClass("org.jboss.modules.Module");
  }

  public ClassLoader getModuleClassLoader(Object moduleLoader, String moduleName)
      throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
          IllegalArgumentException, InvocationTargetException {
    Method getClassLoaderMethod =
        getModuleClass().getDeclaredMethod("getClassLoader", EMPTY_CLASS_ARRAY);
    return (ClassLoader)
        getClassLoaderMethod.invoke(loadModule(moduleLoader, moduleName), EMPTY_OBJECT_ARRAY);
  }

  public Object loadModule(Object moduleLoader, String moduleName)
      throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
          IllegalArgumentException, InvocationTargetException {
    Method loadModuleMethod =
        moduleLoader.getClass().getMethod("loadModule", new Class[] {String.class});
    return loadModuleMethod.invoke(moduleLoader, moduleName);
  }

  String fingerprintJBoss(Class<?> moduleClass) {
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
      logger.error(ex.getMessage(), ex);
    }
    return "Unknown EAP / Wildfly - possibly misconfigured";
  }

  private Class<?> getJBossModuleLoaderClass(Class<?> subModuleLoaderClass) {
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
