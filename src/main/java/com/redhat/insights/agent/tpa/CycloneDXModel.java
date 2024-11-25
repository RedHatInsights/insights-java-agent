/* Copyright (C) Red Hat 2024 */
package com.redhat.insights.agent.tpa;

import com.redhat.insights.logging.InsightsLogger;
import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.cyclonedx.BomGeneratorFactory;
import org.cyclonedx.CycloneDxSchema;
import org.cyclonedx.generators.json.BomJsonGenerator;
import org.cyclonedx.model.*;

public class CycloneDXModel extends Bom {

  private final InsightsLogger logger;

  public CycloneDXModel(InsightsLogger logger) {
    this.logger = logger;
    setVersion(1);
    setMetadata(makeMetadata());
    setSerialNumber("urn:uuid:" + UUID.randomUUID());
  }

  public static Metadata makeMetadata() {
    Metadata meta = new Metadata();
    meta.setTimestamp(new Date());
    Tool jbom = new Tool();
    String jbomVersion = getJbomVersion();
    jbom.setName("jbom");
    jbom.setVendor("Eclipse Foundation - https://projects.eclipse.org/projects/technology.jbom");
    jbom.setVersion(jbomVersion);
    meta.setTools(new ArrayList<>(Arrays.asList(jbom)));

    String description = "Java";
    String hostname = "unknown";
    try {
      hostname =
          InetAddress.getLocalHost().getHostAddress()
              + " ("
              + InetAddress.getLocalHost().getHostName()
              + ")";
    } catch (Exception e) {
      // continue
    }

    Library appNode = new Library(hostname);
    appNode.setType(Component.Type.APPLICATION);
    appNode.setDescription(description);
    appNode.setVersion(jbomVersion);
    meta.setComponent(appNode);

    OrganizationalEntity manufacturer = new OrganizationalEntity();
    manufacturer.setName("Unknown");
    meta.setManufacture(manufacturer);

    return meta;
  }

  private static String getJbomVersion() {
    String version = "unknown";
    final Properties properties = new Properties();
    try {
      InputStream is = CycloneDXModel.class.getResourceAsStream("/jdom.properties");
      properties.load(is);
      version = properties.getProperty("version");
    } catch (Exception e) {
      // continue
    }
    return version;
  }

  public void save(String filename) {
    try {
      List<Component> components = getComponents();
      int size = 0;
      if (components != null) {
        size = components.size();
      }
      logger.debug("Saving SBOM with " + size + " components to " + filename);
      BomJsonGenerator bomGenerator =
          BomGeneratorFactory.createJson(CycloneDxSchema.VERSION_LATEST, this);
      String bomString = bomGenerator.toJsonString();
      FileUtils.write(new File(filename), bomString, Charset.forName("UTF-8"), false);
    } catch (Exception e) {
      logger.info("Couldn't save SBOM to " + filename);
      e.printStackTrace();
    }
  }
}
