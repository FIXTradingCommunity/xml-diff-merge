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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;
import io.fixprotocol.orchestra.event.EventListener;
import io.fixprotocol.orchestra.event.EventListenerFactory;
import io.fixprotocol.orchestra.event.TeeEventListener;

/**
 * Merges a difference file created by {@link XmlDiff} into a baseline XML file to create a new XML
 * file
 *
 * Reads XML diffs as patch operations specified by IETF RFC 5261
 *
 * @author Don Mendelson
 *
 * @see <a href="https://tools.ietf.org/html/rfc5261">An Extensible Markup Language (XML) Patch
 *      Operations Framework Utilizing XML Path Language (XPath) Selectors</a>
 *
 */
public class XmlMerge {
  private static final Logger logger = LogManager.getLogger();
  private int errors = 0;

  /**
   * Merges a baseline XML file with a differences file to produce a second XML file
   *
   * @param args three file names: baseline XML file, diff file, name of second XML to produce
   * Optionally, argument '-e <event-filename>' can direct errors to a JSON file suitable for UI rendering.
   *
   */
  public static void main(String[] args) {
    if (args.length < 3) {
      usage();
      System.exit(1);
    } else {
      try {
        String eventFilename = null;
        final XmlMerge tool = new XmlMerge();
        for (int i = 3; i < args.length; i++) {
          switch (args[i]) {
            case "-e":
              eventFilename = args[i + 1];
              i++;
              break;
          }
        }
        tool.addEventLogger(eventFilename != null ? new FileOutputStream(eventFilename) : null);

        boolean successful = tool.merge(new FileInputStream(args[0]), new FileInputStream(args[1]),
            new FileOutputStream(args[2]));
        System.exit(successful ? 0 : 1);
      } catch (final Exception e) {
        logger.fatal("XmlMerge failed", e);
        System.exit(1);
      }
    }
  }

  /**
   * Prints application usage
   */
  public static void usage() {
    System.out.println("Usage: XmlMerge <xml-file1> <diff-file> <xml-file2>");
  }

  private final TeeEventListener eventLogger;
  private final EventListenerFactory factory;

  public XmlMerge() {
    eventLogger = new TeeEventListener();
    factory = new EventListenerFactory();
    final EventListener logEventLogger = factory.getInstance("LOG4J");
    try {
      logEventLogger.setResource(logger);
      eventLogger.addEventListener(logEventLogger);
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Merges differences into an XML file to produce a new XML file
   *
   * @param baseline XML input stream
   * @param diff reads difference stream produced by {@link XmlDiff}
   * @param out XML output
   * @return returns {@code true} if merge completed successfully without errors
   * @throws Exception if an IO or parser error occurs
   */
  public boolean merge(InputStream baseline, InputStream diff, OutputStream out) throws Exception {
    Objects.requireNonNull(baseline, "Baseline stream cannot be null");
    Objects.requireNonNull(diff, "Difference stream cannot be null");
    Objects.requireNonNull(out, "Output stream cannot be null");

    final Document baselineDoc = parse(baseline);

    // XPath implementation supplied with Java 8 fails so using Saxon
    final XPathFactory factory = new net.sf.saxon.xpath.XPathFactoryImpl();
    final XPath xpathEvaluator = factory.newXPath();
    final CustomNamespaceContext nsContext = new CustomNamespaceContext();
    nsContext.populate(baselineDoc);
    xpathEvaluator.setNamespaceContext(nsContext);

    final Document diffDoc = parse(diff);
    final Element diffRoot = diffDoc.getDocumentElement();
    final NodeList children = diffRoot.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      final Node child = children.item(index);
      final short type = child.getNodeType();
      if (type != Node.ELEMENT_NODE) {
        continue;
      }
      final Element patchOpElement = (Element) child;
      final String tag = patchOpElement.getNodeName();

      switch (tag) {
        case "add":
          add(baselineDoc, xpathEvaluator, patchOpElement);
          break;
        case "remove":
          remove(baselineDoc, xpathEvaluator, patchOpElement);
          break;
        case "replace":
          replace(baselineDoc, xpathEvaluator, patchOpElement);
          break;
        default:
          errors++;
          throw new IllegalArgumentException(String.format("Invalid merge operation %s", tag));
      }
    }

    write(baselineDoc, out);
    eventLogger.info("XmlMerge completed with {0} errors", getErrors());
    return getErrors() == 0;
  }

  private void add(Document doc, XPath xpathEvaluator, Element patchOpElement) {
    final String xpathExpression = patchOpElement.getAttribute("sel");
    final String attribute = patchOpElement.getAttribute("type");
    final String pos = patchOpElement.getAttribute("pos");

    XPathExpression compiled;
    try {
      compiled = xpathEvaluator.compile(xpathExpression);

      final Node siteNode = (Node) compiled.evaluate(doc, XPathConstants.NODE);
      if (siteNode == null) {
        errors++;
        eventLogger.error("Target not found for add; {0}", xpathExpression);
      } else {
        if (attribute.length() > 0) {
          String value = null;
          final NodeList children = patchOpElement.getChildNodes();
          for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (Node.TEXT_NODE == child.getNodeType()) {
              value = patchOpElement.getTextContent();
              break;
            }
          }
          ((Element) siteNode).setAttribute(attribute.substring(1), value);
        } else {
          Element value = null;
          final NodeList children = patchOpElement.getChildNodes();
          for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (Node.ELEMENT_NODE == child.getNodeType()) {
              value = (Element) child;
              break;
            }
          }
          final Node imported = doc.importNode(value, true);
          switch (pos) {
            case "prepend":
              // siteNode is parent - make first child
              siteNode.insertBefore(imported, siteNode.getFirstChild());
              break;
            case "before":
              // insert as sibling before siteNode
              siteNode.getParentNode().insertBefore(imported, siteNode);
              break;
            case "after":
              // insert as sibling after siteNode
              final Node nextSibling = siteNode.getNextSibling();
              if (nextSibling != null) {
                siteNode.getParentNode().insertBefore(imported, nextSibling);
              } else {
                siteNode.getParentNode().appendChild(imported);
              }
              break;
            default:
              // siteNode is parent - make last child
              siteNode.appendChild(imported);
          }
        }
      }
    } catch (final XPathExpressionException e) {
      errors++;
      eventLogger.error("Invalid XPath expression for add; {1}", xpathExpression);
    }
  }

  public void addEventLogger(OutputStream jsonOutputStream) throws Exception {
    if (jsonOutputStream != null) {
      final EventListener jsonEventLogger = factory.getInstance("JSON");
      jsonEventLogger.setResource(jsonOutputStream);
      eventLogger.addEventListener(jsonEventLogger);
    }
  }

  private Document parse(InputStream is)
      throws ParserConfigurationException, SAXException, IOException {
    final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    final DocumentBuilder db = dbf.newDocumentBuilder();
    return db.parse(is);
  }

  private void remove(final Document doc, XPath xpathEvaluator, Element patchOpElement) {
    final String xpathExpression = patchOpElement.getAttribute("sel");
    try {
      final Node node =
          (Node) xpathEvaluator.compile(xpathExpression).evaluate(doc, XPathConstants.NODE);
      if (node != null) {
        final Node parent = node.getParentNode();
        if (parent != null) {
          final NodeList children = parent.getChildNodes();
          for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) == node) {
              parent.removeChild(node);
              break;
            }
          }
        }
      }
    } catch (final XPathExpressionException e) {
      errors++;
      eventLogger.error("Invalid XPath expression for remove; {0}", xpathExpression);
    }
  }

  private void replace(final Document doc, XPath xpathEvaluator, Element patchOpElement) {
    final String xpathExpression = patchOpElement.getAttribute("sel");
    try {
      final String value = patchOpElement.getFirstChild().getNodeValue();

      final Node siteNode =
          (Node) xpathEvaluator.compile(xpathExpression).evaluate(doc, XPathConstants.NODE);
      if (siteNode == null) {
        errors++;
        eventLogger.error("Target not found for replace; {0}", xpathExpression);
      } else {
        switch (siteNode.getNodeType()) {
          case Node.ELEMENT_NODE:
            final NodeList children = siteNode.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
              if (children.item(i).getNodeType() == Node.TEXT_NODE) {
                children.item(i).setNodeValue(value);
                return;
              }
            }

            final Text text = doc.createTextNode(value);
            text.setNodeValue(value);
            siteNode.appendChild(text);
            break;
          case Node.ATTRIBUTE_NODE:
            siteNode.setNodeValue(value);
            break;
        }
      }
    } catch (final XPathExpressionException e) {
      errors++;
      eventLogger.error("Invalid XPath expression for remove; {0}", xpathExpression);
    }
  }

  public int getErrors() {
    return errors;
  }

  private void write(Document document, OutputStream outputStream) throws TransformerException {
    final DOMSource source = new DOMSource(document);
    final StreamResult result = new StreamResult(outputStream);
    final TransformerFactory transformerFactory = TransformerFactory.newInstance();
    final Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty("indent", "yes");
    transformer.transform(source, result);
  }
}
