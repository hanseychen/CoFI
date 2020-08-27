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
package cofi.instrumentation;

import javassist.*;
import cofi.util.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Add getters to the target program.
 */
public class GetterAdder {
  /**
   * Add field getters for the classes in the given file.
   * @param args Not used.
   */
  public static void main(String[] args) {
    // Add getters to each class in the current directory.
    for (String className : loadClasses()) {
      // TODO: Can we handle classes whose name has the "$"?
      addGettersToClass(className);
    }
  }

  /**
   * Load classes from the specified class root.
   * @return The set of classes that locate in the specified class root.
   */
  private static HashSet<String> loadClasses() {
    HashSet<String> classes = new HashSet<>();
    Path curDir = Paths.get("");
    try {
      Files.walk(curDir)
              .filter(new Predicate<Path>() {
                @Override
                public boolean test(Path path) {
                  return path.toString().endsWith(".class");
                }
              })
              .forEach(new Consumer<Path>() {
                @Override
                public void accept(Path path) {
                  String pathName = path.toString();
                  String className = pathName
                          .replace("/", ".")
                          .substring(0, pathName.length() - 6);
                  classes.add(className);
                }
              });
    } catch (IOException e) {
      Logger.fatal("Failed to load classes from " + curDir.toAbsolutePath(), e);
      System.exit(1);
    }

    return classes;
  }

  /**
   * Add getters for the fields of the specified class.
   */
  private static void addGettersToClass(String className) {
    ClassPool pool = ClassPool.getDefault();

    // Load the class.
    CtClass cc;
    try {
      cc = pool.get(className);
    } catch (NotFoundException nfe) {
      Logger.warn("Not adding getters for class " + className + " " +
              "because we fail to load it.", nfe);
      return;
    }

    // Hack for Java 1.7
    // If this class is an interface, skip adding getters to it.
    // TODO: Can we add getters in interfaces?
    if (cc.isInterface()) {
      cc.detach();
      return;
    }

    // Get its fields.
    CtField[] fields = cc.getDeclaredFields();
    // If this class doesn't have fields, no need to proceed.
    if (fields.length == 0) return;

    // Add getters for the fields
    boolean addedGetter = false;
    for (CtField field : fields) {
      // Skip creating the getter for the field if we cannot load its type.
      try {
        field.getType();
      } catch (NotFoundException nfe) {
        Logger.debug("Cannot load the type of field " + field.toString() +
                ". Skipping since we can't create a getter for it anyway.");
        continue;
      }

      // Create a getter for the field.
      try {
        CtMethod getter = CtNewMethod.getter("yGet_" + field.getName(), field);
        cc.addMethod(getter);
        if (Modifier.isStatic(field.getModifiers())) {
          getter.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
        }
        addedGetter = true;
      } catch (CannotCompileException cce) {
        Logger.fatal("Fail to add getter for field " + field +
                " in class " + className + " because of unexpected reason.");
        cce.printStackTrace(System.err);
        Runtime.getRuntime().halt(1);
      }
    }

    // If we have added a getter to the given class, update the class file.
    try {
      if (addedGetter) {
        cc.writeFile();
      }
    } catch (NotFoundException nfe) {
      Logger.fatal("Cannot find the class file of " + className + ".");
      nfe.printStackTrace(System.err);
      Runtime.getRuntime().halt(1);
    } catch (CannotCompileException cce) {
      Logger.fatal("Cannot compile the class file of " + className + ".");
      cce.printStackTrace(System.err);
      Runtime.getRuntime().halt(1);
    } catch (IOException ioe) {
      Logger.fatal("Failed to write the class file of " + className + ".");
      ioe.printStackTrace(System.err);
      Runtime.getRuntime().halt(1);
    }
    cc.detach();
  }
}
