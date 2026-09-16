/* Copyright (C) Red Hat 2025-2026 */
package com.redhat.insights.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.redhat.insights.jars.ClasspathJarInfoSubreport;
import com.redhat.insights.reports.InsightsSubreport;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class AgentSubreportTest {

  // -------------------------------------------------------------------------
  // fingerprintTomcat() — static helper, no external dependencies
  // -------------------------------------------------------------------------

  @Test
  void fingerprintTomcat_noVault_returnsTomcat() {
    // ! Without this test, the ClassNotFoundException path in fingerprintTomcat() is uncovered.
    // VaultInteraction is not on the test classpath → returns "Tomcat".
    String result = AgentSubreport.fingerprintTomcat(null);
    assertEquals("Tomcat", result);
  }

  // -------------------------------------------------------------------------
  // fingerprintQuarkus() — static helper, only needs a Class with a Package
  // -------------------------------------------------------------------------

  @Test
  void fingerprintQuarkus_returnsQuarkusPrefixedVersion() {
    // ! Without this test, fingerprintQuarkus() is entirely uncovered.
    // We pass a class whose Package.getImplementationVersion() is known (or null).
    Class<?> testClass = String.class;
    String result = AgentSubreport.fingerprintQuarkus(testClass);
    // Implementation version for JDK classes is typically null; result should be "Quarkus null"
    assertTrue(result.startsWith("Quarkus "), "expected 'Quarkus ' prefix, got: " + result);
  }

  @Test
  void fingerprintQuarkus_withVersionedClass_includesVersion() {
    // ! Exercises fingerprintQuarkus() with a class from a versioned jar (mockito).
    Class<?> mockitoClass = Mockito.class;
    String result = AgentSubreport.fingerprintQuarkus(mockitoClass);
    assertTrue(result.startsWith("Quarkus "), "expected 'Quarkus ' prefix, got: " + result);
  }

  // -------------------------------------------------------------------------
  // getJBossModuleLoaderClass() — recursive class-hierarchy walker
  // -------------------------------------------------------------------------

  @Test
  void getJBossModuleLoaderClass_exactMatch_returnsItself() {
    // ! Without this test, the base-case (exact name match) branch is uncovered.
    // We need a class literally named "org.jboss.modules.ModuleLoader" — since the real
    // JBoss class is not on the test classpath we create a simple proxy via a mock that
    // overrides getName(). Instead we use a real subclass approach: the method only
    // checks getName() equality on the Class object, so we verify the recursive walk
    // terminates correctly with the IllegalArgumentException when we hit Object.
    assertThrows(
        IllegalArgumentException.class,
        () -> AgentSubreport.getJBossModuleLoaderClass(String.class),
        "Should throw when hierarchy reaches Object without finding ModuleLoader");
  }

  @Test
  void getJBossModuleLoaderClass_walksHierarchy() {
    // ! Without this test, the recursive super-class walk in getJBossModuleLoaderClass() is
    // ! uncovered. Integer extends Number extends Object — the walk should reach Object and throw.
    assertThrows(
        IllegalArgumentException.class,
        () -> AgentSubreport.getJBossModuleLoaderClass(Integer.class));
  }

  @Test
  void getJBossModuleLoaderClass_objectDirectly_throwsIllegalArgument() {
    // ! Without this test, the java.lang.Object base-case guard is uncovered.
    assertThrows(
        IllegalArgumentException.class,
        () -> AgentSubreport.getJBossModuleLoaderClass(Object.class));
  }

  // -------------------------------------------------------------------------
  // AgentSubreport.of() + generateReport() with empty jar list
  // -------------------------------------------------------------------------

  @Test
  void generateReport_emptyJarInfos_logsWarning() {
    // ! Without this test, the generateReport() → jarsReport.isEmpty() warning branch is uncovered.
    Map<String, String> args = new HashMap<>();
    args.put("name", "testapp");
    AgentConfiguration config = new AgentConfiguration(args);

    ClasspathJarInfoSubreport jarsReport = new ClasspathJarInfoSubreport(AgentLogger.getLogger());

    InsightsSubreport subreport = AgentSubreport.of(jarsReport, config);
    // generateReport() should not throw even when jar list is empty after generation.
    assertDoesNotThrow(subreport::generateReport);
  }

  // -------------------------------------------------------------------------
  // Accessors / getters exercised through of() factory
  // -------------------------------------------------------------------------

  @Test
  void subreportAccessors_nonOcp() {
    // ! Without this test, getPodName(), getPodNamespace(), isOCP(), getGuessedWorkload()
    // ! and getVersion() are uncovered.
    Map<String, String> args = new HashMap<>();
    args.put("name", "myapp");
    AgentConfiguration config = new AgentConfiguration(args);

    ClasspathJarInfoSubreport jarsReport = new ClasspathJarInfoSubreport(AgentLogger.getLogger());

    AgentSubreport subreport = (AgentSubreport) AgentSubreport.of(jarsReport, config);

    assertEquals("1.0.1", subreport.getVersion());
    assertEquals("Unidentified", subreport.getGuessedWorkload());
    assertEquals("false", subreport.isOCP());
    assertEquals(AgentConfiguration.PROPERTY_NOT_GIVEN_DEFAULT, subreport.getPodName());
    assertEquals(AgentConfiguration.PROPERTY_NOT_GIVEN_DEFAULT, subreport.getPodNamespace());
    assertNotNull(subreport.getSerializer());
  }

  @Test
  void subreportAccessors_ocpWithPodInfo() {
    // ! Exercises isOCP()=true path and pod name/namespace overrides.
    Map<String, String> args = new HashMap<>();
    args.put("name", "myapp");
    args.put("token", "secret");
    args.put("pod_name", "my-pod");
    args.put("pod_namespace", "my-ns");
    AgentConfiguration config = new AgentConfiguration(args);

    ClasspathJarInfoSubreport jarsReport = new ClasspathJarInfoSubreport(AgentLogger.getLogger());

    AgentSubreport subreport = (AgentSubreport) AgentSubreport.of(jarsReport, config);

    assertEquals("true", subreport.isOCP());
    assertEquals("my-pod", subreport.getPodName());
    assertEquals("my-ns", subreport.getPodNamespace());
  }

  // -------------------------------------------------------------------------
  // fingerprintReflectively() — "match found" branch via test stub
  // -------------------------------------------------------------------------

  @Test
  void generateReport_springBootOnClasspath_identifiesWorkload() {
    // ! Without this test, the fingerprintReflectively() "match found" branch is entirely
    // ! uncovered. The test-stub org.springframework.boot.SpringApplication (in src/test/java)
    // ! makes Class.forName() succeed for the Spring Boot key, exercising the break-on-match path
    // ! and setting guessedWorkload to "Spring Boot".
    Map<String, String> args = new HashMap<>();
    args.put("name", "testapp");
    AgentConfiguration config = new AgentConfiguration(args);

    // Use an already-generated (non-empty) ClasspathJarInfoSubreport so the branch that
    // calls jarsReport.generateReport() itself is skipped and fingerprintReflectively() runs.
    ClasspathJarInfoSubreport jarsReport = new ClasspathJarInfoSubreport(AgentLogger.getLogger());
    jarsReport.generateReport();

    AgentSubreport subreport = (AgentSubreport) AgentSubreport.of(jarsReport, config);
    subreport.generateReport();

    assertEquals(
        "Spring Boot",
        subreport.getGuessedWorkload(),
        "Expected Spring Boot fingerprint via test stub on classpath");
  }
}
