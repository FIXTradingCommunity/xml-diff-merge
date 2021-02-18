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

import static io.fixprotocol.xml.XmlDiffListener.Event.Difference.ADD;
import static io.fixprotocol.xml.XmlDiffListener.Event.Difference.REMOVE;
import static io.fixprotocol.xml.XmlDiffListener.Event.Difference.REPLACE;
import static io.fixprotocol.xml.XmlDiffListener.Event.Pos.append;
import static io.fixprotocol.xml.XmlDiffListener.Event.Pos.before;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import io.fixprotocol.orchestra.event.EventListener;
import io.fixprotocol.orchestra.event.EventListenerFactory;
import io.fixprotocol.orchestra.event.TeeEventListener;
import io.fixprotocol.xml.XmlDiffListener.Event;
import io.fixprotocol.xml.XmlDiffListener.Event.Difference;
import io.fixprotocol.xml.XmlDiffListener.Event.Pos;

/**
 * Utility to report differences between two XML files
 * <p>
 * It does not require XML schemas. Only one assumption is made: the key identifying attribute of an
 * XML element is named either "id" or "name". This should work for the majority of XML files since
 * that is common practice.
 *
 * @author Don Mendelson
 *
 */
public class XmlDiff {

  private static final class ElementComparator implements Comparator<Element> {
    @Override
    public int compare(Element n1, Element n2) {
      int retv = n1.getNodeName().compareTo(n2.getNodeName());
      if (retv == 0) {
        final String id1 = XpathUtil.getAttributeCaseInsensitiveValue(n1, "id");
        final String id2 = XpathUtil.getAttributeCaseInsensitiveValue(n2, "id");
        if (id1.length() > 0 && id2.length() > 0) {
          retv = id1.compareTo(id2);
        }
        if (retv == 0) {
          final String name1 = XpathUtil.getAttributeCaseInsensitiveValue(n1, "name");
          final String name2 = XpathUtil.getAttributeCaseInsensitiveValue(n2, "name");
          if (name1.length() > 0 && name2.length() > 0) {
            retv = name1.compareTo(name2);
          }
        }
      }
      return retv;
    }
  }

  private static final Logger logger = LogManager.getLogger();

  /**
   * Compares two XML files. By default, report is sent to console.
   *
   * @param args file names of two XML files to compare and optional name of difference file. If
   *        diff file is not provided, then output goes to console.
   *        
   *        Optionally, argument '-u' treats XML nodes as unordered so moves among children are not considered significant. Otherwise,
   *        a move will result in an add and a remove operation.
   *        
   *        Optionally, argument '-e <event-filename>' can direct errors to a JSON file suitable for UI rendering.
   */
  public static void main(String[] args) {
    if (args.length < 2) {
      usage();
      System.exit(1);
    } else {
      final XmlDiff tool = new XmlDiff();
      OutputStream out = System.out;
      String eventFilename = null;
      try (InputStream is1 = new FileInputStream(args[0]);
          InputStream is2 = new FileInputStream(args[1])) {
        for (int i = 2; i < args.length; i++) {
          switch (args[i]) {
            case "-u":
              tool.setAreElementsOrdered(false);
              break;
            case "-o":
              tool.setAreElementsOrdered(true);
              break;
            case "-e":
              eventFilename = args[i + 1];
              i++;
              break;
            default:
              out = new FileOutputStream(args[i]);
          }
        }
        final PatchOpsListener aListener = new PatchOpsListener(out);
        tool.setListener(aListener);
        tool.addEventLogger(eventFilename != null ? new FileOutputStream(eventFilename) : null);
        tool.diff(is1, is2);
        System.exit(0);
      } catch (final Exception e) {
        logger.fatal("XmlDiff failed", e);
        System.exit(1);
      }
    }
  }

  /**
   * Prints application usage
   */
  public static void usage() {
    System.out
        .println("Usage: XmlDiff <xml-file1> <xml-file2> [diff-file] [-e event-file] [-u|-o]\n"
            + "diff-file defaults to console\t-u=unordered elements\t-o=ordered elements");
  }

  private boolean areElementsOrdered = true;
  private final ArrayList<Attr> attributesArray1 = new ArrayList<>(64);
  private final ArrayList<Attr> attributesArray2 = new ArrayList<>(64);
  private final ElementComparator elementComparator = new ElementComparator();
  private final TeeEventListener eventLogger;
  private final EventListenerFactory factory;
  private XmlDiffListener listener;

  public XmlDiff() {
    eventLogger = new TeeEventListener();
    factory = new EventListenerFactory();
    final EventListener logEventLogger = factory.getInstance("LOG4J");
    try {
      logEventLogger.setResource(logger);
    } catch (final Exception e) {
      eventLogger.addEventListener(logEventLogger);
      e.printStackTrace();
    }
  }

  public boolean areElementsOrdered() {
    return areElementsOrdered;
  }

  /**
   * Generates differences between two XML documents
   *
   * @param is1 first XML input stream
   * @param is2 second XML input stream
   * @throws Exception if an IO or parsing error occurs
   */
  public void diff(InputStream is1, InputStream is2) throws Exception {
    Objects.requireNonNull(is1, "First input stream cannot be null");
    Objects.requireNonNull(is2, "Second input stream cannot be null");

    try (is1; is2) {
      final Document doc1 = parse(is1);
      final Element root1 = doc1.getDocumentElement();
      final Document doc2 = parse(is2);
      final Element root2 = doc2.getDocumentElement();

      if (!diffElements(root1, root2)) {
        eventLogger.fatal("XmlDiff failed; not comparing same root nodes; {0} {1}",
            XpathUtil.getFullXPath(root1), XpathUtil.getFullXPath(root2));
        System.exit(1);
      }
      listener.close();
      eventLogger.info("XmlDiff complete");
    }
  }

  /**
   * Generates differences between two XML node trees
   *
   * @param is1 first XML input stream
   * @param xpathString1 XPath expression that evaluates to an Element in the first stream
   * @param is2 second XML input stream
   * @param xpathString2 XPath expression that evaluates to an Element in the second stream
   * @throws Exception if an IO or parsing error occurs
   * @throws IllegalArgumentException if XPath expressions do not both evaluate to Elements
   */
  public void diff(InputStream is1, String xpathString1, InputStream is2, String xpathString2)
      throws Exception {
    Objects.requireNonNull(is1, "First input stream cannot be null");
    Objects.requireNonNull(xpathString1, "First expression cannot be null");
    Objects.requireNonNull(is2, "Second input stream cannot be null");
    Objects.requireNonNull(xpathString2, "Second expression cannot be null");

    try (is1; is2) {
      final XPathFactory factory = XPathFactory.newInstance();
      final XPath xpath1 = factory.newXPath();
      final Object node1 = xpath1.evaluate(xpathString1, new InputSource(is1), XPathConstants.NODE);
      final XPath xpath2 = factory.newXPath();
      final Object node2 = xpath2.evaluate(xpathString2, new InputSource(is2), XPathConstants.NODE);

      if (node1 instanceof Element && node2 instanceof Element) {
        diffElements((Element) node1, (Element) node2);
      } else {
        throw new IllegalArgumentException("Nodes to compare are not both Elements");
      }
    } finally {
      listener.close();
    }
  }

  /**
   * Compare XML elements with regard to order of children.
   *
   * @param areElementsOrdered if {@code true} order is significant for comparison and add
   *        instructions are to insert in a specific position. Otherwise, order is insignificant for
   *        comparison, and adds are appended to existing children.
   */
  public void setAreElementsOrdered(boolean areElementsOrdered) {
    this.areElementsOrdered = areElementsOrdered;
  }

  /**
   * Registers a listener for ontology differences. If one is not registered, a default listener
   * sends reports to the console.
   *
   * @param listener a listener
   */
  public void setListener(XmlDiffListener listener) {
    this.listener = listener;
  }

  private void addEventLogger(OutputStream jsonOutputStream) throws Exception {
    if (jsonOutputStream != null) {
      final EventListener jsonEventLogger = factory.getInstance("JSON");
      jsonEventLogger.setResource(jsonOutputStream);
      eventLogger.addEventListener(jsonEventLogger);
    }
  }

  // append or prepend
  private void appendElement(Element elementToAdd, Pos pos) {
    final String xpath = XpathUtil.getFullXPath(elementToAdd.getParentNode());
    // Copies an element with its attributes (deep copy)
    final Node elementCopy = elementToAdd.cloneNode(true);
    listener.accept(Event.add(xpath, elementCopy, pos));
  }

  private boolean diffAttributes(NamedNodeMap attributes1, NamedNodeMap attributes2) {
    boolean isEqual = true;
    sortAttributes(attributes1, attributesArray1);
    sortAttributes(attributes2, attributesArray2);

    int index1 = 0;
    int index2 = 0;

    while (index1 < attributesArray1.size() || index2 < attributesArray2.size()) {
      Difference difference = Difference.EQUAL;
      if (index1 == attributesArray1.size()) {
        difference = ADD;
      } else if (index2 == attributesArray2.size()) {
        difference = REMOVE;
      } else {
        final int nameCompare = attributesArray1.get(index1).getNodeName()
            .compareTo(attributesArray2.get(index2).getNodeName());
        if (nameCompare == 0) {
          final int valueCompare = attributesArray1.get(index1).getNodeValue()
              .compareTo(attributesArray2.get(index2).getNodeValue());
          if (valueCompare != 0) {
            difference = REPLACE;
          }
        } else if (nameCompare > 0) {
          difference = ADD;
        } else {
          difference = REMOVE;
        }
      }

      switch (difference) {
        case ADD:
          listener.accept(
              Event.add(XpathUtil.getFullXPath(attributesArray2.get(index2).getOwnerElement()),
                  attributesArray2.get(index2), append));
          index2 = Math.min(index2 + 1, attributesArray2.size());
          isEqual = false;
          break;
        case REPLACE:
          listener.accept(Event.replace(XpathUtil.getFullXPath(attributesArray1.get(index1)),
              attributesArray2.get(index2), attributesArray1.get(index1)));
          index1 = Math.min(index1 + 1, attributesArray1.size());
          index2 = Math.min(index2 + 1, attributesArray2.size());
          isEqual = false;
          break;
        case EQUAL:
          index1 = Math.min(index1 + 1, attributesArray1.size());
          index2 = Math.min(index2 + 1, attributesArray2.size());
          break;
        case REMOVE:
          listener.accept(Event.remove(XpathUtil.getFullXPath(attributesArray1.get(index1))));
          index1 = Math.min(index1 + 1, attributesArray1.size());
          isEqual = false;
          break;
      }
    }
    return isEqual;
  }

  private boolean diffChildElements(Element element1, Element element2) {
    final NodeList nodeList1 = element1.getChildNodes();
    final NodeList nodeList2 = element2.getChildNodes();

    final ArrayList<Element> elementsArray1 = new ArrayList<>(64);
    final ArrayList<Element> elementsArray2 = new ArrayList<>(64);
    if (areElementsOrdered()) {
      selectElements(nodeList1, elementsArray1);
      selectElements(nodeList2, elementsArray2);
    } else {
      selectElements(nodeList1, elementsArray1).sort(elementComparator);
      selectElements(nodeList2, elementsArray2).sort(elementComparator);
    }

    int index1 = 0;
    int index2 = 0;
    boolean isEqual = true;
    while (index1 < elementsArray1.size() || index2 < elementsArray2.size()) {

      Difference difference = Difference.EQUAL;
      if (index1 == elementsArray1.size()) {
        difference = ADD;
      } else if (index2 == elementsArray2.size()) {
        difference = REMOVE;
      } else {
        final Element child1 = elementsArray1.get(index1);
        final Element child2 = elementsArray2.get(index2);
        final int elementCompare = elementComparator.compare(child1, child2);
        if (elementCompare == 0) {
          diffElements(child1, child2);
        } else if (elementCompare > 0) {
          difference = ADD;
        } else {
          difference = REMOVE;
        }
      }
      switch (difference) {
        case ADD:
          if (areElementsOrdered && index1 < elementsArray1.size()) {
            insertElement(elementsArray2.get(index2), elementsArray1.get(index1), before);
          } else {
            appendElement(elementsArray2.get(index2), append);
          }
          index2 = Math.min(index2 + 1, elementsArray2.size());
          isEqual = false;
          break;
        case REPLACE:
          listener.accept(Event.replace(XpathUtil.getFullXPath(elementsArray1.get(index1)),
              elementsArray2.get(index2), elementsArray1.get(index1)));
          index1 = Math.min(index1 + 1, elementsArray1.size());
          index2 = Math.min(index2 + 1, elementsArray2.size());
          isEqual = false;
          break;
        case EQUAL:
          index1 = Math.min(index1 + 1, elementsArray1.size());
          index2 = Math.min(index2 + 1, elementsArray2.size());
          break;
        case REMOVE:
          listener.accept(Event.remove(XpathUtil.getFullXPath(elementsArray1.get(index1))));
          index1 = Math.min(index1 + 1, elementsArray1.size());
          isEqual = false;
          break;
      }
    }

    return isEqual;
  }

  private boolean diffElements(final Element child1, final Element child2) {
    if (!child1.getNodeName().equals(child2.getNodeName())) {
      return false;
    }
    diffText(child1, child2);
    final NamedNodeMap attributes1 = child1.getAttributes();
    final NamedNodeMap attributes2 = child2.getAttributes();
    diffAttributes(attributes1, attributes2);
    diffChildElements(child1, child2);
    return true;
  }

  private boolean diffText(Element element1, Element element2) {
    final Node child1 = element1.getFirstChild();
    final Node child2 = element2.getFirstChild();

    if (child1 != null && Node.TEXT_NODE == child1.getNodeType()) {
      if (child2 == null || Node.TEXT_NODE != child2.getNodeType()) {
        listener.accept(Event.remove(XpathUtil.getFullXPath(child1)));
      } else {
        final int valueCompare =
            child1.getNodeValue().trim().compareTo(child2.getNodeValue().trim());

        if (valueCompare != 0) {
          listener.accept(Event.replace(XpathUtil.getFullXPath(element1), child2, child1));
        } else {
          return true;
        }
      }
    } else if (child2 != null && Node.TEXT_NODE == child2.getNodeType()) {
      listener.accept(Event.add(XpathUtil.getFullXPath(element2), child2, append));
    }
    return false;
  }

  // insert before or after
  private void insertElement(Element elementToAdd, Node location, Pos pos) {
    final String xpath = XpathUtil.getFullXPath(location);
    // Copies an element with its attributes (deep copy)
    final Node elementCopy = elementToAdd.cloneNode(true);
    listener.accept(Event.add(xpath, elementCopy, pos));
  }

  private Document parse(InputStream is)
      throws ParserConfigurationException, SAXException, IOException {
    final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    final DocumentBuilder db = dbf.newDocumentBuilder();
    return db.parse(is);
  }

  private ArrayList<Element> selectElements(NodeList nodeList, ArrayList<Element> nodeArray) {
    nodeArray.clear();

    for (int i = 0; i < nodeList.getLength(); i++) {
      final Node node = nodeList.item(i);
      if (Node.ELEMENT_NODE == node.getNodeType()) {
        nodeArray.add((Element) node);
      }
    }
    return nodeArray;
  }

  private ArrayList<Attr> sortAttributes(NamedNodeMap attributes, ArrayList<Attr> nodeArray) {
    nodeArray.clear();

    for (int i = 0; i < attributes.getLength(); i++) {
      nodeArray.add((Attr) attributes.item(i));
    }
    nodeArray.sort(Comparator.comparing(Node::getNodeName));
    return nodeArray;
  }

}
