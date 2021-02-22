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

import java.util.Objects;
import java.util.function.Consumer;
import org.w3c.dom.Node;

/**
 * Event handler for {@link XmlDiff}
 *
 * @author Don Mendelson
 *
 */
public interface XmlDiffListener extends Consumer<XmlDiffListener.Event>, AutoCloseable {

  /**
   * XML difference event
   *
   */
  class Event {
    /**
     * Type of XML difference
     */
    enum Difference {
      ADD, EQUAL, REMOVE, REPLACE
    }

    /**
     * Position of ADD
     */
    enum Pos {
      after, append, before, prepend
    }

    /**
     * Return an Event to ADD
     *
     * @param xpath parent of the added node; must not be null
     * @param value node value of element or attribute; must not be null
     * @param pos position to add or insert new Node
     */
    static Event add(String xpath, Node value, Pos pos) {
      return new Event(Difference.ADD,
          Objects.requireNonNull(xpath, "XPath target for add missing"),
          Objects.requireNonNull(value, "Node to add missing"), null, pos);
    }

    /**
     * Return an Event to REMOVE
     *
     * @param xpath node to remove; must not be null
     */
    static Event remove(String xpath) {
      return new Event(Difference.REMOVE, xpath, null, null, null);
    }

    /**
     * Return an Event to REPLACE
     *
     * @param xpath node target of change; must not be null
     * @param value node new value of element or attribute
     * @param oldValue previous node value
     */
    static Event replace(String xpath, Node value, Node oldValue) {
      return new Event(Difference.REPLACE, xpath, value,
          Objects.requireNonNull(oldValue, "Old node missing"), null);
    }

    private final Difference difference;
    private final Node oldValue;
    private final Pos pos;
    private final Node value;
    private final String xpath;

    private Event(Difference difference, String xpath, Node value, Node oldValue, Pos pos) {
      this.difference = Objects.requireNonNull(difference, "Difference type missing");
      this.xpath = Objects.requireNonNull(xpath, "Xpath missing");
      this.value = value;
      this.oldValue = oldValue;
      this.pos = pos;
    }

    public Pos getPos() {
      return pos;
    }

    Difference getDifference() {
      return difference;
    }

    Node getOldValue() {
      return oldValue;
    }

    Node getValue() {
      return value;
    }

    String getXpath() {
      return xpath;
    }

  }

}
