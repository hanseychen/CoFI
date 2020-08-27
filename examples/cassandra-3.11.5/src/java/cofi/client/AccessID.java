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
package cofi.client;

import java.lang.reflect.Method;
import java.util.*;

public class AccessID {
  String nodeID = null;
  Class<?> rootClass = null;
  String staticField = null;
  ArrayList<String> accessors = new ArrayList<>();

  private static Config.Logger logger = new Config.Logger("ycz.AccessID");

  /**
   * Construct an AccessID object using the given access ID string. An access ID
   * is of the following form:
   * nodeID-rootClass.staticField(.accessor)*N
   * @param idString The string representation of an access ID.
   * @return The constructed access ID object.
   */
  static AccessID parse(String idString) {
    AccessID id = new AccessID();

    // Get the node ID. Find the '-' separator starting from index 3 to handle
    // the case that the node ID consists of a negative number.
    int head = 0, tail = idString.indexOf('-', 3);
    id.nodeID = idString.substring(head, tail);

    // Get the root class. Here, we assume that the package name is in lowercase
    // letters, so the root class will be the first word starting with a capital
    // letter.
    head = tail + 1;
    for (int i = head; i < idString.length(); ++i) {
      if (Character.isUpperCase(idString.charAt(i))) {
        tail = idString.indexOf('.', i + 1);
        try {
          id.rootClass = Class.forName(idString.substring(head, tail));
        } catch (ClassNotFoundException cnfe) {
        }
        break;
      }
    }
    if (id.rootClass == null) {
      logger.error("Can't load the root class from " + idString,
              new IllegalArgumentException());
      Runtime.getRuntime().halt(1);
    }

    // Get the static field. The static field is the one right after the root
    // class.
    head = tail + 1;
    tail = idString.indexOf('.', head + 1);
    if (tail == -1) {
      id.staticField = idString.substring(head);
      return id;
    } else {
      id.staticField = idString.substring(head, tail);
    }

    // Get the accessors. Each accessor specifies how to further zoom in to get
    // the interesting variable.
    while (tail != -1 && tail < idString.length()) {
      head = tail + 1;
      tail = idString.indexOf('.', head + 1);
      if (tail == -1) {
        id.accessors.add(idString.substring(head));
      } else {
        id.accessors.add(idString.substring(head, tail));
      }
    }

    return id;
  }

  /**
   * Return a map containing a pair of name and value of a variable. The
   * variable is retrieved based on this access ID.
   * @return The map from variable name to variable value.
   */
  HashMap<String, String> getNameAndValue() {
    HashMap<String, String> result = new HashMap<>();
    try {
      // Get a reference of the static field.
      Object curValue = rootClass.getMethod("yGet_" + staticField).invoke(null);
      String curName = nodeID + "-" + rootClass.getName() + "." + staticField;

      // Zoom in according to the access ID.
      for (String accessor : accessors) {
        if (curValue == null) {
          // We won't be able to zoom in a null.
          break;
        } else if (accessor.equals("get()")) {
          // "get()" means we need to zoom in an Atomic* object.
          curValue = curValue.getClass().getMethod("get").invoke(curValue);
        } else if (accessor.startsWith("get(")) {
          // "get(###)" means get an entry from a map.
          curValue = zoomIntoMap((Map) curValue, accessor);
        } else if (accessor.startsWith("[") && accessor.endsWith("]")) {
          // "[###]" means get an element from a collection. In this case, the
          // current value can either be an array, or a Collection object.
          curValue = zoomIntoArray(curValue, accessor);
        } else {
          // Otherwise, it is a field name.
          Method getter = curValue.getClass().getMethod("yGet_" + accessor);
          curValue = getter.invoke(curValue);
        }
        curName += "." + accessor;
      }

      result.put(curName, StrUtils.sanitizeStringValue(Objects.toString(curValue)));
    } catch (Throwable t) {
      logger.error("Can't get the variable and value for: " + this, t);
      Runtime.getRuntime().halt(1);
    }
    return result;
  }

  /**
   * Zoom into the given map object based on the given accessor.
   * @param map The map object to zoom in.
   * @param accessor The accessor contains the hash of the key.
   * @return The corresponding value object.
   */
  private Object zoomIntoMap(Map map, String accessor) {
    int hash = Integer.parseInt(accessor.substring(4, accessor.length() - 1));
    for (Object key : map.keySet()) {
      if (Variable.getHash(key) == hash) {
        return map.get(key);
      }
    }
    return null;
  }

  /**
   * Zoom into the given array/collection based on the given accessor.
   * @param array The array/collection object to zoom in.
   * @param accessor The accessor containing the index.
   * @return The corresponding element.
   */
  private Object zoomIntoArray(Object array, String accessor) {
    int index = Integer.parseInt(accessor.substring(1, accessor.length() - 1));
    if (array instanceof Collection) {
			Iterator it = ((Collection) array).iterator();
			Object element = null;
			while (index-- >= 0) {
				if (!it.hasNext()) return null;
				element = it.next();
			}
			return element;
    } else {
			List list = Arrays.asList(array);
			if (list.size() > index) {
				return list.get(index);
			} else {
				return null;
			}
		}
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(nodeID)
            .append("-")
            .append(rootClass.getName())
            .append(".")
            .append(staticField);

    for (String accessor : accessors) {
      sb.append(".").append(accessor);
    }

    return sb.toString();
  }
}
