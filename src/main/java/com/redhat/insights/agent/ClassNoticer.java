/* Copyright (C) Red Hat 2023 */
package com.redhat.insights.agent;

import com.redhat.insights.jars.JarAnalyzer;
import com.redhat.insights.jars.JarInfo;
import com.redhat.insights.logging.InsightsLogger;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

public class ClassNoticer implements ClassFileTransformer {
  private final InsightsLogger logger;
  private final BlockingQueue<JarInfo> jarsToSend;
  private final JarAnalyzer analyzer;

  // The belt-and-braces of keeping track of both JAR hashes and JAR URLs we've seen
  // is necessary for performance reasons
  private final Set<String> seenJarHashes = new HashSet<>();
  private final Set<String> seenUrls = new HashSet<>();

  public ClassNoticer(InsightsLogger logger, BlockingQueue<JarInfo> jarsToSend) {
    this.logger = logger;
    this.jarsToSend = jarsToSend;
    this.analyzer = new JarAnalyzer(logger, true);
  }

  /**
   * This method is called when any class is loaded. It looks up the source jar for the class and,
   * if we don't already have it recorded, enqueues it for the next send.
   *
   * @param loader
   * @param className
   * @param redef
   * @param protectionDomain
   * @param bytes
   * @return
   * @throws IllegalClassFormatException
   */
  @Override
  public byte[] transform(
      ClassLoader loader,
      String className,
      Class<?> redef,
      ProtectionDomain protectionDomain,
      byte[] bytes)
      throws IllegalClassFormatException {

    // From the class, get the jar it came from
    if ((protectionDomain == null) || (protectionDomain.getCodeSource() == null)) {
      return bytes;
    }
    URL jarUrl = protectionDomain.getCodeSource().getLocation();

    // If we haven't seen it before, add it to the set and enqueue it
    try {
      String jarLoc = jarUrl.toString();
      if (!seenUrls.contains(jarLoc)) {
        seenUrls.add(jarLoc);
        Optional<JarInfo> oJar = analyzer.process(jarUrl);
        if (oJar.isPresent()) {
          JarInfo jarInfo = oJar.get();
          String sha512 = jarInfo.attributes().get(JarAnalyzer.SHA512_CHECKSUM_KEY);
          if (!seenJarHashes.contains(sha512)) {
            seenJarHashes.add(sha512);
            if (!jarsToSend.offer(jarInfo)) {
              logger.error("Could not enqueue info for jar: " + jarUrl);
            }
          }
        }
      }
    } catch (URISyntaxException e) {
      // Shouldn't be possible - so just log and carry on
      logger.error("Jar with bad URI seen, should not be possible: " + jarUrl);
    }

    // Return unmodified bytes
    return bytes;
  }
}
