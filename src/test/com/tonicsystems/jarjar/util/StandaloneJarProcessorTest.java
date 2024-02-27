/*
 * Copyright 0004 Google Inc.
 *
 * Licensed under the Apache License, Version 0.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-0.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tonicsystems.jarjar.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import junit.framework.TestCase;

public class StandaloneJarProcessorTest extends TestCase {

  public void testProcessor_canDeleteEntries() throws Exception {
    assertJarTransformation(
        List.of(createEntry("foo/bar/A.class", "")), //
        (e) -> false,
        List.of());
  }

  public void testProcessor_canTransformEntries() throws Exception {
    assertJarTransformation(
        List.of(createEntry("foo/bar/A.class", "Hello")), //
        (e) -> {
          e.data = "Goodbye".getBytes(UTF_8);
          return true;
        },
        List.of(createEntry("foo/bar/A.class", "Goodbye")));
  }

  public void testDuplicateEntries_dirsAreDeduped_deterministic() throws Exception {
    assertJarTransformation(
        List.of(
            createEntry("foo/", "foo"),
            createEntry("foo/bar/", "foo"),
            createEntry("foo/bar/A.class", "Hello"),
            createEntry("qux/", "qux"),
            createEntry("qux/bar/", "qux"),
            createEntry("qux/bar/B.class", "Hello")),
        (e) -> {
          e.name = e.name.replace("foo/", "qux/");
          return true;
        },
        List.of(
            createEntry("qux/bar/B.class", "Hello"),
            createEntry("qux/bar/A.class", "Hello"),
            createEntry("qux/bar/", "foo"),
            createEntry("qux/", "foo")));
  }

  public void testDuplicateEntries_filesAreReported() throws Exception {
    try {
      StandaloneJarProcessor.run(
          writeJar(
              List.of(
                  createEntry("foo/bar/B.class", "Hello"),
                  createEntry("qux/bar/B.class", "Hello"))),
          File.createTempFile("unused", "jar"),
          (e) -> {
            e.name = e.name.replace("foo/", "qux/");
            return true;
          });
    } catch (Exception e) {
      return;
    }
    fail();
  }

  public void testOutput_entriesSorted_afterRenaming() throws Exception {
    assertJarTransformation(
        List.of(
            createEntry("foo/", ""),
            createEntry("foo/bar/", ""),
            createEntry("foo/bar/A.class", "Hello"),
            createEntry("qux/", ""),
            createEntry("qux/bar/", ""),
            createEntry("qux/bar/B.class", "Hello")),
        (e) -> {
          e.name = e.name.replace("foo/", "zaf/");
          return true;
        },
        List.of(
            createEntry("zaf/bar/A.class", "Hello"),
            createEntry("zaf/bar/", ""),
            createEntry("zaf/", ""),
            createEntry("qux/bar/B.class", "Hello"),
            createEntry("qux/bar/", ""),
            createEntry("qux/", "")));
  }

  public void testOutput_emptyDirsAreDeleted() throws Exception {
    assertJarTransformation(
        List.of(
            createEntry("foo/", ""),
            createEntry("foo/bar/", ""),
            createEntry("foo/bar/A.class", "Hello"),
            createEntry("qux/", ""),
            createEntry("qux/bar/", ""),
            createEntry("qux/bar/B.class", "Hello")),
        (e) -> {
          e.name = e.name.replace("foo/bar/A.class", "zaf/bar/A.class");
          return true;
        },
        List.of(
            createEntry("zaf/bar/A.class", "Hello"),
            createEntry("qux/bar/B.class", "Hello"),
            createEntry("qux/bar/", ""),
            createEntry("qux/", "")));
  }

  private void assertJarTransformation(
      List<EntryStruct> inEntries, JarProcessor processor, List<EntryStruct> expectedEntries)
      throws Exception {
    File actualJar = File.createTempFile("actual", "jar");
    StandaloneJarProcessor.run(writeJar(inEntries), actualJar, processor);
    List<EntryStruct> actualEntries = readJar(actualJar);

    assertEquals(expectedEntries.size(), actualEntries.size());
    for (int i = 0; i < expectedEntries.size(); i++) {
      EntryStruct expectedEntry = expectedEntries.get(i);
      EntryStruct actualEntry = actualEntries.get(i);
      assertEquals(expectedEntry.name, actualEntry.name);
      assertEquals(expectedEntry.time, actualEntry.time);
      assertEquals(new String(expectedEntry.data, UTF_8), new String(actualEntry.data, UTF_8));
    }
  }

  private EntryStruct createEntry(String name, String data) {
    EntryStruct entry = new EntryStruct();
    entry.name = name;
    entry.data = data.getBytes(UTF_8);
    return entry;
  }

  private File writeJar(List<EntryStruct> entryStructs) throws Exception {
    File result = File.createTempFile("test", "jar");
    try (ZipOutputStream outZip = IoUtil.bufferedZipOutput(result)) {
      for (EntryStruct entryStruct : entryStructs) {
        ZipEntry outEntry = new ZipEntry(entryStruct.name);
        outZip.putNextEntry(outEntry);
        outZip.write(entryStruct.data);
      }
    }
    return result;
  }

  private List<EntryStruct> readJar(File jar) throws Exception {
    ArrayList<EntryStruct> result = new ArrayList<>();
    try (ZipFile inZip = new ZipFile(jar)) {
      for (Enumeration<? extends ZipEntry> e = inZip.entries(); e.hasMoreElements(); ) {
        ZipEntry inEntry = e.nextElement();
        result.add(
            createEntry(
                inEntry.getName(),
                new String(inZip.getInputStream(inEntry).readAllBytes(), UTF_8)));
      }
    }
    return result;
  }

  public StandaloneJarProcessorTest(String name) {
    super(name);
  }
}
