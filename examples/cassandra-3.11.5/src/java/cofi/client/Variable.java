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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class models the interesting variables. It also implements methods for
 * collecting interesting variables.
 */
public class Variable {
  // An GUID for this variable. Its format is:
  // nd<num>_RootClass(.field)*N
  public String accessPath;
  public String type;
  public String value;

  // A mapping from classes to a 0 hash code. These classes are the ones we
  // tried to get a hash code but failed. This map is used to save some
  // computation that is doom to fail.
  private static ConcurrentHashMap<Class, Boolean> badClassHashCodes =
          new ConcurrentHashMap<>();

  // An up-to-date snapshot of the local variables.
  private static HashMap<String, String> localState = new HashMap<>();

  // Checkpoints for each thread.
  private static ConcurrentHashMap<Thread, HashMap<String, String>> checkpoints
          = new ConcurrentHashMap<>();

  private static Config.Logger logger = new Config.Logger("YCZ_VARIABLE");

  public Variable(String accessPath, String type, String value) {
    this.accessPath = accessPath;
    this.type = type;
    this.value = value;
  }

  /**
   * Try to zoom in according to the given access path. Record the variable when
   * it reaches the end of the access path or the given object is null.
   * @param curObj The given object, it will either be added to the collected
   *               variables, or be zoomed in.
   * @param accessors The field names in the access path.
   * @param curIndex The index in the accessors for the curObject.
   * @param curPath The prefix of the final access ID.
   * @param variables The map from access IDs to interesting variables.
   */
  static void zoomInAndGetVars(
          Object curObj,
          ArrayList<String> accessors,
          int curIndex,
          String curPath,
          HashSet<Variable> variables) {
    // If the current variable is null, record it. All the non-primitive
    // variables will be casted into strings, so their types are all "string".
    if (curObj == null) {
      variables.add(new Variable(curPath, "string", "null"));
      return;
    }

    // If we have reached the end of the access path, record the current var.
    if (curIndex == accessors.size() - 1) {
      recordVariable(curPath, curObj, variables);
      return;
    }

    // Otherwise, let's zoom in according to the accessors.
    try {
      String accessor = accessors.get(curIndex + 1);
      Class objClass = curObj.getClass();

      // get() is usually used to access the referred object of an Atomic* obj.
      if (accessor.equals("get()")) {
        Object nextObj = objClass.getMethod("get").invoke(curObj);
        String newPath = curPath + ".get()";
        zoomInAndGetVars(nextObj, accessors, curIndex + 1, newPath, variables);
        return;
      }

      // get(*) is used to get all the elements inside a collection or a map.
      if (accessor.equals("get(*)")) {
        zoomInAllElements(curPath, curObj, curIndex, accessors, variables);
        return;
      }

      // get(<number>) is used to get an element from a collection or a
      // map. The element's index or key hash equals to the <number>.
      if (accessor.startsWith("get(") && accessor.endsWith(")")) {
        String num = accessor.substring(4, accessor.length() - 1);
        zoomInOneElement(curPath, curObj, num, curIndex, accessors, variables);
        return;
      }

      // [*] is used to get all the elements inside an array.
      if (accessor.equals("[*]")) {
        zoomInArray(curObj, accessors, curIndex, curPath, variables);
        return;
      }

      // [<index>] is used to get the element at the index from an array.
      if (accessor.startsWith("[") && accessor.endsWith("]")) {
        String idx = accessor.substring(1, accessor.length() - 1);
        zoomInArrayElmnt(curPath, curObj, idx, curIndex, accessors, variables);
        return;
      }

      // Otherwise, the accessor is a field name. Access it.
      zoomInField(curPath, curObj, accessor, curIndex, accessors, variables);
    } catch (Throwable t) {
      logger.error("Fail to zoom in from " + curPath, t);
      Runtime.getRuntime().halt(1);
    }
  }

  /**
   * Zoom into one specified element in the given object. The given object can
   * be a collection or a map.
   * @param curPath The access path of the current object.
   * @param curObj The current object.
   * @param numStr The index or the key hash of the specified element.
   * @param curIndex The accessor index when getting the current object.
   * @param accessors The set of accessors.
   * @param variables The set of variables.
   */
  private static void zoomInOneElement(
          String curPath,
          Object curObj,
          String numStr,
          int curIndex,
          ArrayList<String> accessors,
          HashSet<Variable> variables) {
    int nextIndex = curIndex + 1, num = Integer.parseInt(numStr);
    Object nextObj = null;
    String nextPath = curPath + ".get(" + numStr + ")";

    if (curObj instanceof Collection) {
      int index = 0;
      for (Object element : (Collection) curObj) {
        if (index == num) {
          nextObj = element;
          break;
        } else {
          index++;
        }
      }
    } else if (curObj instanceof Map) {
      for (Object key : ((Map) curObj).keySet()) {
        if (getHash(key) == num) {
          nextObj = ((Map) curObj).get(key);
          break;
        }
      }
    } else {
      throw new IllegalArgumentException("The specified object is neither a " +
              "collection nor a map. Path=" + curPath + "; Value=" + curObj);
    }

    zoomInAndGetVars(nextObj, accessors, nextIndex, nextPath, variables);
  }

  /**
   * Zoom into every element in the given object. The given object can be a
   * collection or a map.
   * @param curPath The access path of the current object.
   * @param curObj The current object.
   * @param curIndex The accessor index when getting the current object.
   * @param accessors The set of accessors.
   * @param variables The set of variables.
   */
  private static void zoomInAllElements(
          String curPath,
          Object curObj,
          int curIndex,
          ArrayList<String> accessors,
          HashSet<Variable> variables) {
    int nextIndex = curIndex + 1;
    if (curObj instanceof Collection) {
      int index = 0;
      for (Object nextObj : (Collection) curObj) {
        zoomInAndGetVars(
                nextObj,
                accessors,
                nextIndex,
                curPath + ".get(" + index++ + ")",
                variables);
      }
    } else if (curObj instanceof Map) {
      for (Object key : ((Map) curObj).keySet()) {
        Object value = ((Map) curObj).get(key);
        zoomInAndGetVars(
                value,
                accessors,
                nextIndex,
                curPath + ".get(" + getHash(key) + ")",
                variables);
      }
    } else {
      throw new IllegalArgumentException("The specified object is neither a " +
              "collection nor a map. Path=" + curPath + "; Value=" + curObj);
    }
  }

  /**
   * Record the variable.
   * @param path The access path of the given variable.
   * @param obj The object of the given variable.
   * @param vars The set of variables to record into.
   */
  private static void recordVariable(
          String path, Object obj, HashSet<Variable> vars) {
    // Get the type of the object.
    String objType;
    if (obj instanceof Boolean) {
      objType = "boolean";
    } else if (obj instanceof Byte
            || obj instanceof Short
            || obj instanceof Integer
            || obj instanceof Long) {
      objType = "int";
    } else if (obj instanceof Float || obj instanceof Double) {
      objType = "float";
    } else {
      objType = "string";
    }

    // Record the object.
    vars.add(new Variable(path, objType, Objects.toString(obj)));
  }

  /**
   * Add the given object, which instantiates a JVM class, into the given
   * variable set.
   * @param obj The given object.
   * @param id The access ID of the given object.
   * @param variables a set of variables.
   */
  private static void getJVMVar(
          Object obj,
          String id,
          HashSet<Variable> variables) {
    switch (obj.getClass().getName()) {
      case "java.lang.Boolean":
        variables.add(new Variable(id, "boolean", obj.toString()));
        break;
      case "java.lang.Byte":
      case "java.lang.Short":
      case "java.lang.Integer":
      case "java.lang.Long":
        variables.add(new Variable(id, "int", obj.toString()));
        break;
      case "java.lang.Float":
      case "java.lang.Double":
        variables.add(new Variable(id, "float", obj.toString()));
        break;
      case "java.lang.Character":
      default:
        String strVal = StrUtils.sanitizeStringValue(obj.toString());
        variables.add(new Variable(id, "string", strVal));
    }
  }

  /**
   * Check if the given object instantiates one of the Atomic* classes, i.e.,
   * AtomicReference, AtomicBoolean, AtomicInteger, and AtomicLong.
   * @param o The object to check.
   * @return Returns true if the object instantiates one of the Atomic* classes.
   */
  private static boolean isAtomic(Object o) {
    return o instanceof AtomicReference
            || o instanceof AtomicBoolean
            || o instanceof AtomicInteger
            || o instanceof AtomicLong;
  }

  /**
   * Zoom into the field of the current object.
   * @param curPath The access path of the current object.
   * @param curObj The current object.
   * @param fieldName The field to get to.
   * @param curIndex The accessor index when getting the current object.
   * @param accessors The list of accessors.
   * @param variables The set of variables.
   */
  private static void zoomInField(
          String curPath,
          Object curObj,
          String fieldName,
          int curIndex,
          ArrayList<String> accessors,
          HashSet<Variable> variables) {
    try {
      // Get the class of the given object.
      Class objClass = curObj.getClass();
      // Get the getter method.
      Method getter = objClass.getMethod("yGet_" + fieldName);
      // Get the field.
      Object nextObj = getter.invoke(curObj);

      // Zoom in
      int nextIndex = curIndex + 1;
      String nextPath = curPath + "." + fieldName;
      zoomInAndGetVars(nextObj, accessors, nextIndex, nextPath, variables);
    } catch (Throwable t) {
      throw new RuntimeException("Fail to zoom in to field " + fieldName +
              " from " + curPath, t);
    }
  }

  /**
   * Zoom in one element of the given array object.
   * TODO: Can we simplify this method?
   * @param curPath The access path of the current object.
   * @param curObj The given array object to zoom in.
   * @param idxStr The index to zoom in.
   * @param curIndex The accessor index when getting the current object.
   * @param accessors The set of accessors.
   * @param variables The collection of interesting variables.
   */
  private static void zoomInArrayElmnt(
          String curPath,
          Object curObj,
          String idxStr,
          int curIndex,
          ArrayList<String> accessors,
          HashSet<Variable> variables) {
    int nextIndex = curIndex + 1;
    int elementIndex = 0;
    int target = Integer.parseInt(idxStr);
    String nextPath = curPath + ".[" + idxStr + "]";
    Object nextObj = null;

    String typeString = curObj.getClass().getComponentType().toString();
    switch (typeString) {
      case "boolean":
        for (boolean element : (boolean[]) curObj) {
          if (elementIndex == target) {
            nextObj = element;
            break;
          } else {
            elementIndex++;
          }
        }
        break;
      case "byte":
        for (byte element : (byte[]) curObj) {
          if (elementIndex == target) {
            nextObj = element;
            break;
          } else {
            elementIndex++;
          }
        }
        break;
      case "short":
        for (short element : (short[]) curObj) {
          if (elementIndex == target) {
            nextObj = element;
            break;
          } else {
            elementIndex++;
          }
        }
        break;
      case "int":
        for (int element : (int[]) curObj) {
          if (elementIndex == target) {
            nextObj = element;
            break;
          } else {
            elementIndex++;
          }
        }
        break;
      case "long":
        for (long element : (long[]) curObj) {
          if (elementIndex == target) {
            nextObj = element;
            break;
          } else {
            elementIndex++;
          }
        }
        break;
      case "char":
        for (char element : (char[]) curObj) {
          if (elementIndex == target) {
            nextObj = element;
            break;
          } else {
            elementIndex++;
          }
        }
        break;
      case "float":
        for (float element : (float[]) curObj) {
          if (elementIndex == target) {
            nextObj = element;
            break;
          } else {
            elementIndex++;
          }
        }
        break;
      case "double":
        for (double element : (double[]) curObj) {
          if (elementIndex == target) {
            nextObj = element;
            break;
          } else {
            elementIndex++;
          }
        }
        break;
      default:
        for (Object element : (Object[]) curObj) {
          if (elementIndex == target) {
            nextObj = element;
            break;
          } else {
            elementIndex++;
          }
        }
    }
    zoomInAndGetVars(nextObj, accessors, nextIndex, nextPath, variables);
  }

  /**
   * Zoom in the given array object.
   * TODO: Can we simplify this method?
   * @param curObj The given array object to collect or zoom in.
   * @param acs The set of accessors.
   * @param curIndex The index of the given object in the names.
   * @param cPath The prefix of the access ID.
   * @param vars The collection of interesting variables.
   */
  private static void zoomInArray(
          Object curObj,
          ArrayList<String> acs,
          int curIndex,
          String cPath,
          HashSet<Variable> vars) {
    int nIdx = curIndex + 1;
    int eIdx = 0;

    String typeString = curObj.getClass().getComponentType().toString();
    switch (typeString) {
      case "boolean":
        for (boolean next : (boolean[]) curObj) {
          zoomInAndGetVars(next, acs, nIdx, cPath + ".[" + eIdx++ + "]", vars);
        }
        break;
      case "byte":
        for (byte next : (byte[]) curObj) {
          zoomInAndGetVars(next, acs, nIdx, cPath + ".[" + eIdx++ + "]", vars);
        }
        break;
      case "short":
        for (short next : (short[]) curObj) {
          zoomInAndGetVars(next, acs, nIdx, cPath + ".[" + eIdx++ + "]", vars);
        }
        break;
      case "int":
        for (int next : (int[]) curObj) {
          zoomInAndGetVars(next, acs, nIdx, cPath + ".[" + eIdx++ + "]", vars);
        }
        break;
      case "long":
        for (long next : (long[]) curObj) {
          zoomInAndGetVars(next, acs, nIdx, cPath + ".[" + eIdx++ + "]", vars);
        }
        break;
      case "char":
        for (char next : (char[]) curObj) {
          zoomInAndGetVars(next, acs, nIdx, cPath + ".[" + eIdx++ + "]", vars);
        }
        break;
      case "float":
        for (float next : (float[]) curObj) {
          zoomInAndGetVars(next, acs, nIdx, cPath + ".[" + eIdx++ + "]", vars);
        }
        break;
      case "double":
        for (double next : (double[]) curObj) {
          zoomInAndGetVars(next, acs, nIdx, cPath + ".[" + eIdx++ + "]", vars);
        }
        break;
      default:
        for (Object next : (Object[]) curObj) {
          zoomInAndGetVars(next, acs, nIdx, cPath + ".[" + eIdx++ + "]", vars);
        }
    }
  }

  /**
   * Generate a hash code for the given object. The hash code of a given object
   * is the XOR of the hash codes of its fields (recursively).
   * @param obj The object whose hash code is of interest.
   * @return The hash code of the object.
   */
  static int getHash(Object obj) {
    return getHash(new HashSet<Class>(), obj, 0);
  }

  /**
   * Generate a hash code for the given object. The hash code of a given object
   * is the XOR of the hash codes of its fields (recursively).
   * @param obj The object whose hash code is of interest.
   * @param prevDepth The depth we have zoomed.
   * @return The hash code of the object.
   */
  private static int getHash(HashSet<Class> pre, Object obj, int prevDepth) {
    // For the following scenarios, we will quickly return a hash code of 0:
    // 1. The given object is null.
    // 2. We have zoomed too deep.
    // 3. We have zoomed recursively.
    // 4. We failed to get a hash code for the given object's class.
    if (obj == null
            || prevDepth == Config.zoomDepth
            || pre.contains(obj.getClass())
            || badClassHashCodes.containsKey(obj.getClass())) {
      return 0;
    }

    try {
      Class objClass = obj.getClass();

      // An Atomic* object's hash code is the same as the referred object.
      if (isAtomic(obj)) {
        Object referredObj = objClass.getMethod("get").invoke(obj);
        return getHash(pre, referredObj, prevDepth);
      }

      // An Enum object's hash code will be the hash code of its name.
      if (objClass.isEnum()) {
        return obj.toString().hashCode();
      }

      // An array object's hash code will be calculated similarly as in
      // List.hashCode().
      if (objClass.isArray()) {
        return getHashOfArray(pre, obj, prevDepth);
      }

      // A Collection object's hash code will be the same as the underlying
      // array's.
      // TODO: Should we handle Sets differently since they don't care about
      //       the order of the elements?
      if (obj instanceof Collection) {
        return getHashOfArray(pre, ((Collection) obj).toArray(), prevDepth);
      }

      // A Map object's hash code will be the sum of its entries'.
      if (obj instanceof Map) {
        return getHashOfMap(pre, obj, prevDepth);
      }

      // The hash code of an object instantiating other JVM class will be the
      // hash code of its string representation.
      String packageName = objClass.getPackage().getName();
      if (packageName.startsWith("java.") || packageName.startsWith("sun.")) {
        return obj.toString().hashCode();
      }

      // If the given object instantiates a non-JVM class, try to zoom in.
      return getHashOfAppClass(pre, obj, prevDepth);
    } catch (Throwable t) {
      logger.info("Fail to get hash code for class " + obj.getClass(), t);
      badClassHashCodes.put(obj.getClass(), true);
    }
    return 0;
  }

  /**
   * Get the hash code of an array. The hash code of an array is calculated
   * similarly to what List.hashCode() does.
   * @param pre The classes we have zoomed in.
   * @param arrObj The given array object to get the hash code.
   * @param prevDepth The depth we have zoomed in.
   * @return The hash code for the given array.
   */
  private static int getHashOfArray(
          HashSet<Class> pre, Object arrObj, int prevDepth) {
    Class objCls = arrObj.getClass();
    if (!objCls.isArray()) {
      logger.error("The given object (" + arrObj + ") of class " + objCls +
              " is not an array.", new IllegalStateException());
      Runtime.getRuntime().halt(1);
    }

    switch (objCls.getComponentType().toString()) {
      case "boolean": return Arrays.hashCode((boolean[]) arrObj);
      case "byte":    return Arrays.hashCode((byte[]) arrObj);
      case "short":   return Arrays.hashCode((short[]) arrObj);
      case "int":     return Arrays.hashCode((int[]) arrObj);
      case "long":    return Arrays.hashCode((long[]) arrObj);
      case "char":    return Arrays.hashCode((char[]) arrObj);
      case "float":   return Arrays.hashCode((float[]) arrObj);
      case "double":  return Arrays.hashCode((double[]) arrObj);
      default: // Object[]
        int cnt = 0, hash = 1;
        for (Object e : (Object[]) arrObj) {
          if (cnt++ >= Config.fanoutFactor) {
            break;
          }
          hash = 31 * hash + getHash(pre, e, prevDepth);
        }
        return hash;
    }
  }

  /**
   * Get the hash code of the given map. The hash code of the given map is
   * calculated as the sum of its entries. The hash code of an entry is
   * calculated as the hash code of the following string:
   * hash_of_key->hash_of_value.
   * The substrings hash_of_key and hash_of_value are calculated as regular
   * objects.
   * @param pre The classes we have zoomed in.
   * @param mapObj The map object.
   * @param prevDepth The depth we have zoomed in.
   * @return The hash code of the map object.
   */
  private static int getHashOfMap(
          HashSet<Class> pre, Object mapObj, int prevDepth) {
    if (!(mapObj instanceof Map)) {
      logger.error("The given object (" + mapObj + ") of class " +
              mapObj.getClass() + " is not a map.",
              new IllegalStateException());
      Runtime.getRuntime().halt(1);
    }

    int cnt = 0, hash = 0;
    for (Object key : ((Map) mapObj).keySet()) {
      if (cnt++ >= Config.fanoutFactor) {
        break;
      }
      int keyHash = getHash(pre, key, prevDepth);
      int valHash = getHash(pre, ((Map) mapObj).get(key), prevDepth);
      String entryString = keyHash + "->" + valHash;
      hash += entryString.hashCode();
    }
    return hash;
  }

  /**
   * Get the hash code of an object instantiating a non-JVM class. The hash code
   * of such an object will be the XOR of its instance fields.
   * @param pre The classes we have zoomed in.
   * @param obj The object to get the hash code.
   * @param prevDepth The depth we have zoomed in.
   * @return The hash code of the object.
   */
  private static int getHashOfAppClass(
          HashSet<Class> pre, Object obj, int prevDepth) {
    int hash = 0;
    for (Field f : getAllFields(obj.getClass())) {
      if (Modifier.isStatic(f.getModifiers())) {
        continue;
      }

      try {
        String fieldName = f.getName();
        // TODO: Handle special characters. What do they mean?
        if (fieldName.contains("$") || fieldName.contains("#")) {
          continue;
        }

        // Get a reference of the field, and get its hash code.
        Class objCls = obj.getClass();
        Method fieldGetter = objCls.getMethod("yGet_" + fieldName);
        Object fieldAsRef = fieldGetter.invoke(obj);
        pre.add(objCls);
        hash ^= getHash(pre, fieldAsRef, prevDepth + 1);
        pre.remove(objCls);
      } catch (Throwable t) {
        logger.info("Error when hashing field " + f, t);
      }
    }
    return hash;
  }

  /**
   * Get all the fields of the given class, including the fields in itself and
   * its super classes (both direct and indirect).
   * @param cls The class whose fields are to of interest.
   * @return The set of fields of this class.
   */
  private static HashSet<Field> getAllFields(Class<?> cls) {
    // First, let's get all the super classes.
    HashSet<Class<?>> ancestors = getInstrumentedAncestors(cls);

    // Then, let's get all the fields of these classes.
    HashSet<Field> allFields = new HashSet<>();
    for (Class<?> ancestor : ancestors) {
      Collections.addAll(allFields, ancestor.getDeclaredFields());
    }
    return allFields;
  }

  /**
   * Get all the super classes of the given class that are likely instrumented
   * by yCozy.
   * @param cls The class whose super classes are of interest.
   * @return The set of instrumented super classes as well as the given class.
   */
  private static HashSet<Class<?>> getInstrumentedAncestors(Class<?> cls) {
    HashSet<Class<?>> ancestors = new HashSet<>();

    for (Method m : cls.getDeclaredMethods()) {
      if (m.getName().startsWith("yGet_")) {
        ancestors.add(cls);
        // If the current class is instrumented, check for its super classes.
        ancestors.addAll(getInstrumentedAncestors(cls.getSuperclass()));
        return ancestors;
      }
    }

    return ancestors;
  }

  /**
   * Update the name and value of a variable in the local state.
   * @param newVars New variable names and values.
   */
  static synchronized void updateVariables(HashMap<String, String> newVars) {
    // Remove the old one.
    for (String newName : newVars.keySet()) {
      for (String oldName : localState.keySet()) {
        if (newName.startsWith(oldName) || oldName.startsWith(newName)) {
          localState.remove(oldName);
        }
      }
    }
    // Add the new one.
    localState.putAll(newVars);
  }

  /**
   * Make a new checkpoint using the latest local state. A checkpoint is
   * associated with the thread that makes the checkpoint.
   */
  static synchronized void makeNewCheckpoint() {
    HashMap<String, String> newCheckpoint = new HashMap<>(localState);
    checkpoints.put(Thread.currentThread(), newCheckpoint);
  }

  /**
   * Remove the latest checkpoint made by the current thread, and return a
   * string representation of the removed checkpoint.
   * FIXME: What's the format of the string? Should the leading number followed
   *        by "#####" instead of " "?
   * @return The string representation of the removed checkpoint.
   */
  static String getLastCheckpointAsString() {
    HashMap<String, String> checkpoint = getLastCheckpoint();
    if (checkpoint == null) {
      return "0 ";
    } else {
      StringBuilder sb = new StringBuilder();
      sb.append(checkpoint.size()).append(" ");
      for (String name : checkpoint.keySet()) {
        sb.append(name).append("#####");
        sb.append(checkpoint.get(name)).append("#####");
      }
      return sb.toString();
    }
  }

  /**
   * Remove and return the latest checkpoint made by the current thread.
   * @return The removed checkpoint.
   */
  private static HashMap<String, String> getLastCheckpoint() {
    return checkpoints.remove(Thread.currentThread());
  }
}
