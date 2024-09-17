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
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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

  private static DocumentBuilder docBuilder;
  private XmlMerge xmlMerge;

  @BeforeAll
  public static void setupOnce() throws Exception {
    new File("target/test").mkdirs();
    LogManager.setFactory(new CustomLogFactory());
    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    docBuilder = docFactory.newDocumentBuilder();
  }

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  public void setUp() throws Exception {
     xmlMerge = new XmlMerge();
  }

  @Test
  public void unordered() throws Exception {

    final String mergedFilename = "target/test/unorderedmerged.xml";
    final String diffFilename = "target/test/unordereddiff.xml";
    XmlDiff.main(new String[] {"src/test/resources/DiffTest1.xml", "src/test/resources/DiffTest2.xml", diffFilename, "-u"});

    Document doc = docBuilder.parse(diffFilename);

//     Expectation:
//     replace @version="1" with version="2"
//     add  <eee attr="z"/>
//     replace <ccc>Some test</ccc> with <ccc>Some other test</ccc>
//     remove <ddd att="y"/>
//     remove <fff id="1"/>
//     add <fff id="3"/>
//     
    assertEquals(3, doc.getElementsByTagName("add").getLength());
    assertEquals(1, doc.getElementsByTagName("replace").getLength());
    assertEquals(3, doc.getElementsByTagName("remove").getLength());

    try (
        final FileInputStream is1Baseline = new FileInputStream(
            Thread.currentThread().getContextClassLoader().getResource("DiffTest1.xml").getFile());
        final FileInputStream isDiff = new FileInputStream(diffFilename);
        final FileOutputStream osMerge = new FileOutputStream(mergedFilename)) {
      xmlMerge.merge(is1Baseline, isDiff, osMerge);
    }
  }

  @Test
  public void ordered() throws Exception {
    final String mergedFilename = "target/test/orderedmerged.xml";
    final String diffFilename = "target/test/ordereddiff.xml";
    XmlDiff.main(new String[] {"src/test/resources/DiffTest1.xml", "src/test/resources/DiffTest2.xml", diffFilename});

    Document doc = docBuilder.parse(diffFilename);

    assertEquals(3, doc.getElementsByTagName("add").getLength());
    assertEquals(1, doc.getElementsByTagName("replace").getLength());
    assertEquals(3, doc.getElementsByTagName("remove").getLength());
    
    try (
        final FileInputStream is1Baseline = new FileInputStream(Thread.currentThread()
            .getContextClassLoader().getResource("DiffTest1.xml").getFile());
        final FileInputStream isDiff = new FileInputStream(diffFilename);
        final FileOutputStream osMerge = new FileOutputStream(mergedFilename)) {
      xmlMerge.merge(is1Baseline, isDiff, osMerge);
    }
  }

  @Test
  public void epDiff() throws Exception {
    final String mergedFilename = "target/test/roundtripmerged.xml";
    final String diffFilename = "target/test/roundtripdiff.xml";    
    XmlDiff.main(new String[] {"src/test/resources/FixRepository2016EP215.xml", "src/test/resources/FixRepository2016EP216.xml", diffFilename});

    try (
        final FileInputStream is1Baseline = new FileInputStream(Thread.currentThread()
            .getContextClassLoader().getResource("FixRepository2016EP215.xml").getFile());
        final FileInputStream isDiff = new FileInputStream(diffFilename);
        final FileOutputStream osMerge = new FileOutputStream(mergedFilename)) {
      xmlMerge.merge(is1Baseline, isDiff, osMerge);
    }
  }

  @Test
  public void xsdDiff() throws Exception {
    final String diffFilename = "target/test/xsddiff.xml";    
    XmlDiff.main(new String[] {"src/test/resources/Enums-new.xsd", "src/test/resources/Enums-old.xsd", diffFilename, "-u"});
  }

  @Test
  public void removeAttribute() throws Exception {
    final String mergedFilename = "target/test/Instrument-merged.xml";
    final String diffFilename = "src/test/resources/Instrument-diff.xml";
    final String baseFilename = "src/test/resources/Instrument-base.xml";

    try (
        final FileInputStream is1Baseline = new FileInputStream(baseFilename);
        final FileInputStream isDiff = new FileInputStream(diffFilename);
        final FileOutputStream osMerge = new FileOutputStream(mergedFilename)) {
      xmlMerge.merge(is1Baseline, isDiff, osMerge);

      Document doc = docBuilder.parse(mergedFilename);
      NodeList elements = doc.getElementsByTagName("fixr:component");
      Element element = (Element) elements.item(0);
      assertEquals(0, element.getAttribute("added").length());
    }
  }

}
