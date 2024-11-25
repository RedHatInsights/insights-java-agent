/* Copyright (C) Red Hat 2024 */
package com.redhat.insights.agent.tpa;

import com.github.packageurl.PackageURL;
import com.redhat.insights.logging.InsightsLogger;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.*;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Component.Scope;
import org.cyclonedx.model.Hash;

public class Libraries {

  private final InsightsLogger logger;

  // FIXME: set Scope.EXCLUDED for non-invoked libraries - private Set<Component> invoked = new
  // HashSet<>();
  private Set<String> codesourceExamined = new HashSet<>();
  private Set<Component> libraries = new HashSet<>();
  private Set<org.cyclonedx.model.Dependency> dependencies = new HashSet<>();
  private Hash rootSHA1;
  private Hash rootMD5;

  public Libraries(InsightsLogger logger) {
    this.logger = logger;
  }

  public void runScan(File jarPath) throws Exception {
    addAllLibraries(null, jarPath.getAbsolutePath());
  }

  //    public void save( String outputPath ) {
  //        CycloneDXModel sbom = new CycloneDXModel(logger);
  //        for ( Component component: libraries ) {
  //            sbom.addComponent(component);
  //        }
  //        for ( org.cyclonedx.model.Dependency dep : getDependencies() ) {
  //            sbom.addDependency( dep );
  //        }
  //        sbom.save( outputPath );
  //    }

  // find containing jar file and include ALL libraries
  public void addAllLibraries(Class clazz, String codesource) {

    // FIXME - change codesourceExamined to a Map<codesource, Library>
    // increment library.classesUsed;

    if (!isArchive(codesource)) {
      return;
    }

    try {
      String filepath = codesource.substring(codesource.lastIndexOf(":") + 1);
      String parts[] = filepath.split("!/");
      String path = parts[0];
      if (File.separator.equals("\\")) {
        path = path.replace("\\", "/");
      }
      if (codesourceExamined.contains(path)) {
        return;
      }
      codesourceExamined.add(path);

      File f = new File(path);

      String sha1 = hash(new FileInputStream(f), MessageDigest.getInstance("SHA1"));
      rootSHA1 = new Hash(Hash.Algorithm.SHA1, sha1);

      String md5 = hash(new FileInputStream(f), MessageDigest.getInstance("MD5"));
      rootMD5 = new Hash(Hash.Algorithm.MD5, md5);

      // scan for nested libraries
      JarInputStream jis3 = new JarInputStream(new FileInputStream(f));
      JarFile jarfile = new JarFile(f);
      scan(jarfile, jis3, f.getAbsolutePath());
      addRootHashesToRootJar();
    } catch (Exception e) {
      logger.info("Unusual CodeSource detected: " + codesource);
    }
  }

  private void addRootHashesToRootJar() {
    for (Component lib :
        libraries.stream()
            .filter(lib -> lib.getHashes() == null || lib.getHashes().isEmpty())
            .collect(Collectors.toList())) {
      lib.addHash(rootSHA1);
      lib.addHash(rootMD5);
    }
  }

  public void scan(JarFile jarFile, JarInputStream jis, String codesource) throws Exception {
    JarEntry entry = null;
    while ((entry = jis.getNextJarEntry()) != null) {
      if (isArchive(entry.getName())) {
        try {
          scanInner(codesource, jarFile, jis, entry);
        } catch (Exception e) {
          logger.debug(
              "Problem extracting metadata from "
                  + entry.getName()
                  + " based on "
                  + codesource
                  + ". Continuing.");
        }
      } else if (isPom(entry)) {
        try {
          Library innerlib = new Library();
          // FIXME: set Scope.EXCLUDED for non-invoked libraries
          innerlib.setScope(Scope.REQUIRED);
          innerlib.parsePath(entry.getName());
          innerlib.addProperty("codesource", jarFile.getName() + "!/" + entry.getName());
          libraries.add(innerlib);
          innerlib.setType(Library.Type.LIBRARY);
          parsePom(jis, innerlib);
          try {
            if (innerlib.getGroup() != null && innerlib.getName() != null) {
              innerlib.setPurl(
                  new PackageURL(
                      PackageURL.StandardTypes.MAVEN,
                      innerlib.getGroup(),
                      innerlib.getName(),
                      innerlib.getVersion(),
                      null,
                      null));
            }
          } catch (Exception e) {
            // continue
          }
        } catch (Exception e) {
          // Logger.log( "Problem parsing POM from " + nestedName + " based on " + codesource + ".
          // Continuing." );
        }
      }
    }
  }

  public void scanInner(String codesource, JarFile jarFile, JarInputStream jis, JarEntry entry)
      throws Exception {

    Library innerlib = new Library();
    // FIXME: set Scope.EXCLUDED for non-invoked libraries
    innerlib.setScope(Scope.REQUIRED);
    innerlib.parsePath(entry.getName());
    innerlib.addProperty("codesource", jarFile.getName() + "!/" + entry.getName());
    logger.debug("   INNER " + entry.getName());

    libraries.add(innerlib);
    innerlib.setType(Library.Type.LIBRARY);

    InputStream nis1 = jarFile.getInputStream(entry);
    String md5 = hash(nis1, MessageDigest.getInstance("MD5"));
    innerlib.addHash(new Hash(Hash.Algorithm.MD5, md5));

    InputStream nis2 = jarFile.getInputStream(entry);
    String sha1 = hash(nis2, MessageDigest.getInstance("SHA1"));
    innerlib.addHash(new Hash(Hash.Algorithm.SHA1, sha1));

    innerlib.addProperty("maven", "https://search.maven.org/search?q=1:" + sha1);

    InputStream nis3 = jarFile.getInputStream(entry);
    JarInputStream innerJis = new JarInputStream(nis3);

    Manifest mf = innerJis.getManifest();
    if (mf != null) {
      Attributes attr = mf.getMainAttributes();
      String group = attr.getValue("Implementation-Vendor-Id");
      String artifact = attr.getValue("Implementation-Title");
      if (group != null) innerlib.setGroup(group);
      if (artifact != null) innerlib.setName(artifact);
    }

    // scan through this jar to find any pom files
    InputStream nis4 = jarFile.getInputStream(entry);
    JarInputStream innerJis4 = new JarInputStream(nis4);
    while ((entry = innerJis4.getNextJarEntry()) != null) {
      if (isPom(entry)) {
        try {
          parsePom(innerJis4, innerlib);
        } catch (Exception e) {
          // Logger.log( "Problem parsing POM from " + nestedName + " based on " + codesource + ".
          // Continuing." );
        }
      }
    }

    try {
      if (innerlib.getGroup() != null && innerlib.getName() != null) {
        innerlib.setPurl(
            new PackageURL(
                PackageURL.StandardTypes.MAVEN,
                innerlib.getGroup(),
                innerlib.getName(),
                innerlib.getVersion(),
                null,
                null));
      }
    } catch (Exception e) {
      // continue
    }
  }

  private boolean isPom(JarEntry entry) {
    return !entry.isDirectory() && entry.getName().endsWith("/pom.xml");
  }

  public boolean isArchive(String filename) {
    if (filename.endsWith("!/")) {
      filename = filename.substring(0, filename.length() - 2);
    }
    boolean isArchive =
        filename.endsWith(".jar")
            || filename.endsWith(".war")
            || filename.endsWith(".ear")
            || filename.endsWith(".zip");
    return isArchive;
  }

  private String getUniqueRef(String group, String artifact, String version) {
    return group + ":" + artifact + ":" + version;
  }

  private void parsePom(JarInputStream is, Library lib) throws Exception {
    // String pom = getPOM( is );
    // System.out.println( pom );

    MavenXpp3Reader reader = new MavenXpp3Reader();
    Model model = reader.read(is);
    // Model model = reader.read(new StringReader( pom ));
    String g = model.getGroupId();
    String a = model.getArtifactId();
    String v = model.getVersion();
    if (g != null) lib.setGroup(g);
    if (a != null) lib.setName(a);
    if (v != null) lib.setVersion(v);
    lib.setBomRef(getUniqueRef(lib.getGroup(), lib.getName(), lib.getVersion()));
    org.cyclonedx.model.Dependency cycloneDep =
        new org.cyclonedx.model.Dependency(
            getUniqueRef(lib.getGroup(), lib.getName(), lib.getVersion()));
    for (Dependency dep : model.getDependencies()) {
      org.cyclonedx.model.Dependency subDep =
          new org.cyclonedx.model.Dependency(
              getUniqueRef(dep.getGroupId(), dep.getArtifactId(), dep.getVersion()));
      cycloneDep.addDependency(subDep);
    }
    dependencies.add(cycloneDep);
  }

  public List<Component> getLibraries() {
    return new ArrayList<>(libraries);
  }

  public List<org.cyclonedx.model.Dependency> getDependencies() {
    return new ArrayList<>(dependencies);
  }

  public void dump() {
    logger.debug("Found " + getLibraries().size() + " libraries");
    for (Component lib : getLibraries()) {
      logger.debug(lib.toString());
    }
  }

  public String getPOM(InputStream is) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int len;
    byte[] buf = new byte[8192];
    while ((len = is.read(buf, 0, buf.length)) != -1) {
      baos.write(buf, 0, len);
    }
    return new String(baos.toByteArray(), "UTF-8");
  }

  // streaming hash, low memory use
  public static String hash(InputStream is, MessageDigest md) throws Exception {
    DigestInputStream dis = new DigestInputStream(is, md);
    byte[] buf = new byte[8192];
    for (int len; (len = dis.read(buf)) != -1; ) {}
    return toHexString(md.digest());
  }

  public static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

  public static String toHexString(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }
}
