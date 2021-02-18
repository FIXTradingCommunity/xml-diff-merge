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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

class CustomNamespaceContext implements NamespaceContext {
  private final Map<String, String> namespaces = new HashMap<>();

  public CustomNamespaceContext() {
    namespaces.put("xmlns", XMLConstants.XMLNS_ATTRIBUTE_NS_URI);
    namespaces.put("xml", XMLConstants.XML_NS_URI);
    namespaces.put("xsi", XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
  }

  @Override
  public String getNamespaceURI(String prefix) {
    if (prefix == null) {
      throw new NullPointerException("Null prefix");
    }
    final String uri = namespaces.get(prefix);
    if (uri != null) {
      return uri;
    } else {
      return XMLConstants.NULL_NS_URI;
    }
  }

  // This method isn't necessary for XPath processing.
  @Override
  public String getPrefix(String uri) {
    throw new UnsupportedOperationException();
  }

  // This method isn't necessary for XPath processing either.
  @Override
  public Iterator<String> getPrefixes(String uri) {
    throw new UnsupportedOperationException();
  }

  public void populate(Document doc) {
    final Element baselineRoot = doc.getDocumentElement();
    final NamedNodeMap rootAttributes = baselineRoot.getAttributes();

    for (int i = 0; i < rootAttributes.getLength(); i++) {
      final Attr attr = (Attr) rootAttributes.item(i);
      final String prefix = attr.getPrefix();
      if ("xmlns".equals(prefix)) {
        register(attr.getLocalName(), attr.getValue());
      } else if ("xmlns".equals(attr.getLocalName())) {
        // default namespace
        register(XMLConstants.DEFAULT_NS_PREFIX, attr.getValue());
      }
    }
  }

  public void register(String prefix, String uri) {
    namespaces.put(prefix, uri);
  }

}
