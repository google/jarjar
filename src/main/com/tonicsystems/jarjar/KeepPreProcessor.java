/*
 * Copyright 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tonicsystems.jarjar;

import com.tonicsystems.jarjar.util.EntryStruct;
import com.tonicsystems.jarjar.util.JarProcessor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * First stage of "keep" directive processing.
 *
 * <p>This processor doesn't modify JAR entries in any way. Instead, it accumulates a dependency
 * graph of the observed classes. {@link KeepPostProcessor} uses this graph to determine which
 * classes can be deleted.
 */
final class KeepPreProcessor extends Remapper implements JarProcessor {
  private final ClassVisitor cv = new ClassRemapper(new EmptyClassVisitor(), this);
  private final List<Wildcard> wildcards;

  private final LinkedHashSet<String> matchedClasses = new LinkedHashSet<>();
  private final LinkedHashMap<String, LinkedHashSet<String>> classToDeps = new LinkedHashMap<>();
  private LinkedHashSet<String> activeClassDeps = null;

  public KeepPreProcessor(List<Keep> patterns) {
    this.wildcards = PatternElement.createWildcards(patterns);
  }

  public Set<String> getExcludes() {
    LinkedHashSet<String> keepClasses = new LinkedHashSet<>();
    collectTransitiveDeps(keepClasses, this.matchedClasses);

    LinkedHashSet<String> excludeClasses = new LinkedHashSet<>(classToDeps.keySet());
    excludeClasses.removeAll(keepClasses);
    return excludeClasses;
  }

  private void collectTransitiveDeps(LinkedHashSet<String> result, Collection<String> roots) {
    if (roots == null) {
      return;
    }

    for (String name : roots) {
      if (result.add(name)) {
        collectTransitiveDeps(result, classToDeps.get(name));
      }
    }
  }

  @Override
  public boolean process(EntryStruct struct) throws IOException {
    if (this.activeClassDeps != null) {
      throw new IllegalStateException("KeepProcessor::process is not reentrant");
    }

    if (!struct.isClass()) {
      return true;
    }

    String activeClass = struct.name.substring(0, struct.name.length() - 6);

    for (Wildcard wildcard : wildcards) {
      if (wildcard.matches(activeClass)) {
        this.matchedClasses.add(activeClass);
      }
    }

    try {
      this.activeClassDeps = new LinkedHashSet<>();
      new ClassReader(new ByteArrayInputStream(struct.data)).accept(cv, ClassReader.EXPAND_FRAMES);
      this.activeClassDeps.remove(activeClass);
      this.classToDeps.put(activeClass, this.activeClassDeps);
    } catch (Exception e) {
      System.err.println("Error reading " + struct.name + ": " + e.getMessage());
    } finally {
      this.activeClassDeps = null;
    }

    return true;
  }

  @Override
  public String map(String key) {
    if (key.startsWith("java/") || key.startsWith("javax/")) {
      return null;
    }

    this.activeClassDeps.add(key);
    return null;
  }

  @Override
  public Object mapValue(Object value) {
    if (value instanceof String) {
      String s = (String) value;
      if (PackageRemapper.isArrayForName(s)) {
        mapDesc(s.replace('.', '/'));
      } else if (isForName(s)) {
        map(s.replace('.', '/'));
      }
      return value;
    } else {
      return super.mapValue(value);
    }
  }

  // TODO: use this for package remapping too?
  private static boolean isForName(String value) {
    if (value.isEmpty()) {
      return false;
    }

    for (int i = 0, len = value.length(); i < len; i++) {
      char c = value.charAt(i);
      if (c != '.' && !Character.isJavaIdentifierPart(c)) {
        return false;
      }
    }
    return true;
  }
}
