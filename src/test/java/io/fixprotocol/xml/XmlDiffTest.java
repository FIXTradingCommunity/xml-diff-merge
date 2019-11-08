/*
 * Copyright 2017-2019 FIX Protocol Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package io.fixprotocol.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class CustomLogFactory implements LoggerContextFactory {
  private final org.apache.logging.log4j.spi.LoggerContext ctx;

  CustomLogFactory() {
    final ConfigurationBuilder<BuiltConfiguration> builder =
        ConfigurationBuilderFactory.newConfigurationBuilder();
    builder.setStatusLevel(Level.WARN);
    final AppenderComponentBuilder appenderBuilder = builder.newAppender("Stdout", "CONSOLE")
        .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
        .add(builder.newLayout("PatternLayout").addAttribute("pattern",
            "%date %-5level: %msg%n%throwable"));
    builder.add(appenderBuilder);
    builder.add(builder.newRootLogger(Level.INFO).add(builder.newAppenderRef("Stdout")));
    ctx = Configurator.initialize(builder.build());
  }

  @Override
  public org.apache.logging.log4j.spi.LoggerContext getContext(String fqcn, ClassLoader loader,
      Object externalContext, boolean currentContext) {
    return ctx;
  }

  @Override
  public org.apache.logging.log4j.spi.LoggerContext getContext(String fqcn, ClassLoader loader,
      Object externalContext, boolean currentContext, URI configLocation, String name) {
    return ctx;
  }

  @Override
  public void removeContext(org.apache.logging.log4j.spi.LoggerContext context) {

  }
}


public class XmlDiffTest {

  private static final String MERGED_FILENAME = "target/test/testmerged.xml";
  private static final String DIFF_FILENAME = "target/test/testdiff.xml";
  private XmlDiff xmlDiff;
  private XmlMerge xmlMerge;

  @BeforeAll
  public static void setupOnce() throws Exception {
    new File("target/test").mkdirs();
    LogManager.setFactory(new CustomLogFactory());
  }

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  public void setUp() throws Exception {
    xmlDiff = new XmlDiff();
    xmlDiff.setListener(new PatchOpsListener(new FileOutputStream(DIFF_FILENAME)));
    xmlMerge = new XmlMerge();
  }

  @Test
  public void simpleDiffUnordered() throws Exception {
    try (
        final FileInputStream is1 = new FileInputStream(
            Thread.currentThread().getContextClassLoader().getResource("DiffTest1.xml").getFile());
        final FileInputStream is2 = new FileInputStream(Thread.currentThread()
            .getContextClassLoader().getResource("DiffTest2.xml").getFile())) {
      xmlDiff.setAreElementsOrdered(false);
      xmlDiff.diff(is1, is2);
    }

    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    Document doc = docBuilder.parse(DIFF_FILENAME);

    assertEquals(2, doc.getElementsByTagName("add").getLength());
    assertEquals(2, doc.getElementsByTagName("replace").getLength());
    assertEquals(2, doc.getElementsByTagName("remove").getLength());

    try (
        final FileInputStream is1Baseline = new FileInputStream(
            Thread.currentThread().getContextClassLoader().getResource("DiffTest1.xml").getFile());
        final FileInputStream isDiff = new FileInputStream(DIFF_FILENAME);
        final FileOutputStream osMerge = new FileOutputStream(MERGED_FILENAME)) {
      xmlMerge.merge(is1Baseline, isDiff, osMerge);
    }
  }

  @Test
  public void simpleDiffOrdered() throws Exception {
    try (
        final FileInputStream is1 = new FileInputStream(
            Thread.currentThread().getContextClassLoader().getResource("DiffTest1.xml").getFile());
        final FileInputStream is2 = new FileInputStream(Thread.currentThread()
            .getContextClassLoader().getResource("DiffTest2.xml").getFile())) {
      xmlDiff.setAreElementsOrdered(true);
      xmlDiff.diff(is1, is2);
    }

    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    Document doc = docBuilder.parse(DIFF_FILENAME);

    assertEquals(3, doc.getElementsByTagName("add").getLength());
    assertEquals(1, doc.getElementsByTagName("replace").getLength());
    assertEquals(3, doc.getElementsByTagName("remove").getLength());
  }

  @Test
  public void diffAndMerge() throws Exception {
    try (
        final FileInputStream is1 = new FileInputStream(Thread.currentThread()
            .getContextClassLoader().getResource("FixRepository2016EP215.xml").getFile());
        final FileInputStream is2 = new FileInputStream(Thread.currentThread()
            .getContextClassLoader().getResource("FixRepository2016EP216.xml").getFile())) {
      xmlDiff.diff(is1, is2);
    }

    try (
        final FileInputStream is1Baseline = new FileInputStream(Thread.currentThread()
            .getContextClassLoader().getResource("FixRepository2016EP215.xml").getFile());
        final FileInputStream isDiff = new FileInputStream(DIFF_FILENAME);
        final FileOutputStream osMerge = new FileOutputStream(MERGED_FILENAME)) {
      xmlMerge.merge(is1Baseline, isDiff, osMerge);
    }
  }

}
