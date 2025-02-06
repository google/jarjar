/*
 * Copyright 2024 Google Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import junit.framework.TestCase;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

@SuppressWarnings("JdkImmutableCollections")
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
            createEntry("qux/", "qux"),
            createEntry("qux/bar/", "qux"),
            createEntry("qux/bar/A.class", "Hello"),
            createEntry("qux/bar/B.class", "Hello")));
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
            createEntry("qux/", ""),
            createEntry("qux/bar/", ""),
            createEntry("qux/bar/B.class", "Hello"),
            createEntry("zaf/", ""),
            createEntry("zaf/bar/", ""),
            createEntry("zaf/bar/A.class", "Hello")));
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
            createEntry("qux/", ""),
            createEntry("qux/bar/", ""),
            createEntry("qux/bar/B.class", "Hello"),
            createEntry("zaf/bar/A.class", "Hello")));
  }

  public void testProcessor_multiReleaseEntriesArePreserved() throws Exception {
    assertJarTransformation(
        List.of(
            createEntry("foo/bar/A.class", createClass("foo/bar/A")),
            createEntry("META-INF/versions/11/foo/bar/A.class", createClass("foo/bar/A")),
            createEntry("META-INF/versions/21/foo/bar/A.class", createClass("foo/bar/A"))),
        new JarTransformerChain(
            new RemappingClassTransformer[] {
              new RemappingClassTransformer(
                  new Remapper() {
                    @Override
                    public String map(final String internalName) {
                      if (internalName.equals("foo/bar/A")) {
                        return "foo/baz/B";
                      }
                      return internalName;
                    }
                  })
            }),
        List.of(
            createEntry("META-INF/versions/11/foo/baz/B.class", createClass("foo/baz/B")),
            createEntry("META-INF/versions/21/foo/baz/B.class", createClass("foo/baz/B")),
            createEntry("foo/baz/B.class", createClass("foo/baz/B"))));
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
      assertEquals(printData(expectedEntry.data), printData(actualEntry.data));
    }
  }

  private String printData(byte[] data) {
    Printer textifier = new Textifier();
    StringWriter sw = new StringWriter();
    try {
      new ClassReader(data)
          .accept(
              new TraceClassVisitor(null, textifier, new PrintWriter(sw, true)),
              ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
    } catch (IndexOutOfBoundsException e) {
      return new String(data, UTF_8);
    }
    return sw.toString();
  }

  private EntryStruct createEntry(String name, String data) {
    return createEntry(name, data.getBytes(UTF_8));
  }

  private EntryStruct createEntry(String name, byte[] data) {
    EntryStruct entry = new EntryStruct();
    entry.name = name;
    entry.time = ARBITRARY_INSTANT.toEpochMilli();
    entry.data = data;
    return entry;
  }

  private File writeJar(List<EntryStruct> entryStructs) throws Exception {
    File result = File.createTempFile("test", "jar");
    try (ZipOutputStream outZip = IoUtil.bufferedZipOutput(result)) {
      for (EntryStruct entryStruct : entryStructs) {
        ZipEntry outEntry = new ZipEntry(entryStruct.name);
        outEntry.setTime(entryStruct.time);
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
        EntryStruct entryStruct = new EntryStruct();
        entryStruct.name = inEntry.getName();
        entryStruct.time = inEntry.getTime();
        entryStruct.data = inZip.getInputStream(inEntry).readAllBytes();
        result.add(entryStruct);
      }
    }
    return result;
  }

  private byte[] createClass(String name) {
    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    classWriter.visit(Opcodes.V1_8, Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);

    MethodVisitor methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
    methodVisitor.visitCode();
    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
    methodVisitor.visitMethodInsn(
        Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    methodVisitor.visitInsn(Opcodes.RETURN);
    methodVisitor.visitEnd();

    return classWriter.toByteArray();
  }

  private static final Instant ARBITRARY_INSTANT = Instant.parse("2024-02-27T10:15:30.00Z");

  public StandaloneJarProcessorTest(String name) {
    super(name);
  }
}
