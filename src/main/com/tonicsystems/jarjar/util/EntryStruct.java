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

package com.tonicsystems.jarjar.util;

/** A mutable representation of a JAR entry. */
public final class EntryStruct {
  private static final String VERSIONS_PREFIX = "META-INF/versions/";
  private static final String CLASS_SUFFIX = ".class";

  public byte[] data;
  public String name;
  public long time;

  /** Returns true if the entry is a class file. */
  public boolean isClass() {
    if (!name.endsWith(CLASS_SUFFIX)) {
      return false;
    }
    return true;
  }

  /**
   * If this entry is under {@code META-INF/versions/<N>/} where {@code <N>} is a positive integer,
   * returns the index immediately after {@code META-INF/versions/<N>/}; otherwise returns {@code
   * 0}.
   */
  int versionPrefixEnd() {
    if (!name.startsWith(VERSIONS_PREFIX)) {
      return 0;
    }
    int slash = name.indexOf('/', VERSIONS_PREFIX.length());
    if (slash <= VERSIONS_PREFIX.length() || name.charAt(VERSIONS_PREFIX.length()) == '0') {
      return 0;
    }
    for (int i = VERSIONS_PREFIX.length(); i < slash; i++) {
      char c = name.charAt(i);
      if (c < '0' || c > '9') {
        return 0;
      }
    }
    return slash + 1;
  }

  /**
   * Returns the internal class name (for example {@code "foo/Bar"}) for a class entry, stripping
   * any {@code META-INF/versions/<N>/} multi-release prefix and the {@code .class} suffix.
   */
  public String className() {
    if (!isClass()) {
      throw new IllegalStateException("Not a class entry: " + name);
    }
    return name.substring(versionPrefixEnd(), name.length() - CLASS_SUFFIX.length());
  }

  public boolean isDir() {
    return name.endsWith("/");
  }
}
