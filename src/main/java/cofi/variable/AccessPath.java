/*
 * Copyright 2020 Haicheng Chen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cofi.variable;

import cofi.util.StrOps;

import java.util.ArrayList;

public class AccessPath {
  private String rootClass;
  private ArrayList<String> accessors = new ArrayList<>();

  /**
   * Create an AccessPath object by parsing the specified string.
   * @param pathStr A string representation of an access path.
   */
  public AccessPath(String pathStr) {
    int tail = -1;

    // Get the root class name.
    while (true) {
      // Find the next delimiter.
      tail = pathStr.indexOf('.', tail + 1);
      if (tail == -1 || tail == pathStr.length() - 1) {
        // If we reach the end of the string when parsing the root class, the
        // String is not an access path.
        throw new IllegalArgumentException("The specified string does not " +
                "have a class: " + pathStr);
      }

      // If the next character is a capital letter, we have found the root class
      if (Character.isUpperCase(pathStr.charAt(tail + 1))) {
        tail = pathStr.indexOf('.', tail + 1);
        rootClass = pathStr.substring(0, tail);
        break;
      }
    }

    // Get the name of each field.
    int head = tail + 1;
    while (true) {
      tail = pathStr.indexOf('.', head);
      if (tail == -1) {
        // This is the last field.
        accessors.add(pathStr.substring(head));
        break;
      } else if (tail == pathStr.length() - 1) {
        // The path string is mal-formed.
        throw new IllegalArgumentException("The specified string has invalid " +
                "field: " + pathStr);
      } else {
        // We have found one more field.
        accessors.add(pathStr.substring(head, tail));
        head = tail + 1;
      }
    }
  }

  private AccessPath(String rootClass, ArrayList<String> accessors) {
    this.rootClass = rootClass;
    this.accessors = accessors;
  }

  public String getRootClass() { return rootClass; }

  public ArrayList<String> getAccessors() { return accessors; }

  /**
   * Checks if this access path includes the other path.
   * @param another The other path to check.
   * @return Whether this access path includes the other path.
   */
  public boolean includes(String another) {
    // Get rid of the possible node ID
    another = StrOps.rmNodeID(another);

    // Get the parts that must be matched.
    String curPathStr = toString();
    String[] parts = curPathStr.split("\\*");

    // Go over the string to see check that all parts are matched.
    for (String part : parts) {
      // If any part cannot be matched, the path does not include the other.
      if (!another.startsWith(part)) {
        return false;
      }

      // remove the current part from the other path.
      another = another.substring(part.length());

      // Skip possible numbers.
      if (part.endsWith("(")) {
        int endIndex = another.indexOf(")");
        another = another.substring(endIndex);
      } else if (part.endsWith("[")) {
        int endIndex = another.indexOf("]");
        another = another.substring(endIndex);
      }
    }

    return another.length() == 0;
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer(rootClass);
    for (String accessor : accessors) {
      sb.append(".").append(accessor);
    }
    return sb.toString();
  }
}
