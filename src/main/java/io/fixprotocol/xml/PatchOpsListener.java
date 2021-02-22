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

import static io.fixprotocol.xml.XmlDiffListener.Event.Pos.append;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import io.fixprotocol.orchestra.event.EventListener;

/**
 * Writes XML diffs as patch operations specified by IETF RFC 5261
 *
 * @author Don Mendelson
 * @see <a href="https://tools.ietf.org/html/rfc5261">An Extensible Markup Language (XML) Patch
 *      Operations Framework Utilizing XML Path Language (XPath) Selectors</a>
 */
public class PatchOpsListener implements XmlDiffListener {

  private final OutputStreamWriter writer;
  private final Document diffDocument;
  private final Element rootElement;
  private final AtomicBoolean isClosed = new AtomicBoolean();
  private final EventListener eventLogger;

  /**
   * Constructs a listener with an output stream
   * @param eventListener 
   *
   * @throws IOException if an IO error occurs
   * @throws ParserConfigurationException if a configuration error occurs
   * @throws TransformerConfigurationException if a configuration error occurs
   *
   */
  public PatchOpsListener(OutputStream out, EventListener eventListener)
      throws IOException, ParserConfigurationException, TransformerConfigurationException {
    this.eventLogger = eventListener;
    writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

    final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    dbFactory.setNamespaceAware(true);
    final DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    diffDocument = dBuilder.newDocument();
    rootElement = diffDocument.createElement("diff");
    diffDocument.appendChild(rootElement);
  }

  /*
   * (non-Javadoc)
   *
   * @see java.util.function.Consumer#accept(java.lang.Object)
   */
  @Override
  public void accept(Event t) {

    switch (t.getDifference()) {
      case ADD:
        final Element addElement = diffDocument.createElement("add");
        rootElement.appendChild(addElement);

        if (t.getValue() instanceof Attr) {
          // add attribute
          addElement.setAttribute("sel", t.getXpath());
          addElement.setAttribute("type", "@" + t.getValue().getNodeName());
          final Text textNode = diffDocument.createTextNode(t.getValue().getNodeValue());
          addElement.appendChild(textNode);
        } else if (t.getValue() instanceof Element) {
          // add element
          addElement.setAttribute("sel", t.getXpath());
          if (t.getPos() != append) {
            addElement.setAttribute("pos", t.getPos().toString());
          }
          // will import child text node if it exists (deep copy)
          final Element newValue = (Element) diffDocument.importNode(t.getValue(), true);
          addElement.appendChild(newValue);
        } else if (t.getValue() instanceof Text) {
          addElement.setAttribute("sel", t.getXpath());
          Node text = diffDocument.importNode(t.getValue(), false);
          addElement.appendChild(text);
        } else {
          eventLogger.error("Unhandled node type {0} for add", t.getValue().getNodeType());
        }

        break;
      case REPLACE:
        final Element replaceElement = diffDocument.createElement("replace");
        rootElement.appendChild(replaceElement);

        if (t.getValue() instanceof Attr) {
          // replace attribute
          replaceElement.setAttribute("sel", t.getXpath());
          final Text textNode = diffDocument.createTextNode(t.getValue().getNodeValue());
          replaceElement.appendChild(textNode);
        } else if (t.getValue() instanceof Element || t.getValue() instanceof Text) {
          // replace element
          replaceElement.setAttribute("sel", t.getXpath());
          // will import child text node if it exists
          final Node newValue = diffDocument.importNode(t.getValue(), true);
          replaceElement.appendChild(newValue);
        } else {
          eventLogger.error("Unhandled node type {0} for replace", t.getValue().getNodeType());
        }
        break;
      case REMOVE:
        final Element removeElement = diffDocument.createElement("remove");
        rootElement.appendChild(removeElement);
        removeElement.setAttribute("sel", t.getXpath());
        break;
      default:
        break;
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.AutoCloseable#close()
   */
  @Override
  public void close() throws Exception {
    // Idempotent - only close once
    if (isClosed.compareAndSet(false, true)) {
      final TransformerFactory transformerFactory = TransformerFactory.newInstance();
      final Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      final DOMSource source = new DOMSource(diffDocument);
      final StreamResult result = new StreamResult(writer);
      transformer.transform(source, result);
      writer.close();
    }
  }

}
