/*
 * Copyright 2024 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tonicsystems.jarjar;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.tonicsystems.jarjar.util.EntryStruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.jar.Manifest;
import junit.framework.TestCase;

public class ManifestProcessorTest extends TestCase {

  public void testProcess_nonManifestFile_keptWithNoRemapping() throws Exception {
    ManifestProcessor processor = new ManifestProcessor(null, true);

    EntryStruct struct = new EntryStruct();
    struct.name = "META-INF/MANIFEST.NOT";

    assertTrue(processor.process(struct));
  }

  public void testProcess_skipManifestTrue_skippedWithNoRemapping() throws Exception {
    ManifestProcessor processor = new ManifestProcessor(null, true);

    EntryStruct struct = new EntryStruct();
    struct.name = "META-INF/MANIFEST.MF";

    assertFalse(processor.process(struct));
  }

  public void testProcess_remapMainClass() throws Exception {
    // Given
    Rule rule = new Rule();
    rule.setPattern("foo.**");
    rule.setResult("bar.@1");
    PackageRemapper remapper = new PackageRemapper(Arrays.asList(rule), false);
    ManifestProcessor processor = new ManifestProcessor(remapper, false);

    EntryStruct struct = new EntryStruct();
    struct.name = "META-INF/MANIFEST.MF";
    struct.data =
        serializeManifest(
            createManifest(
                "Manifest-Version", "1.0",
                "Main-Class", "foo.Main",
                "Other-Class", "foo.Other"));

    // When
    assertTrue(processor.process(struct));

    // Then
    assertEquals(
        createManifest(
            "Manifest-Version", "1.0",
            "Main-Class", "bar.Main",
            "Other-Class", "foo.Other"),
        new Manifest(new ByteArrayInputStream(struct.data)));
  }

  public void testProcess_noMainClass_preservesBytesVerbatim() throws Exception {
    Rule rule = new Rule();
    rule.setPattern("foo.**");
    rule.setResult("bar.@1");
    PackageRemapper remapper = new PackageRemapper(Arrays.asList(rule), false);
    ManifestProcessor processor = new ManifestProcessor(remapper, false);

    byte[] original = "Created-By: custom-tool\n\n".getBytes(UTF_8);
    EntryStruct struct = new EntryStruct();
    struct.name = "META-INF/MANIFEST.MF";
    struct.data = original;

    assertTrue(processor.process(struct));
    assertSame(original, struct.data);
  }

  public void testProcess_unmappedMainClass_preservesBytesVerbatim() throws Exception {
    Rule rule = new Rule();
    rule.setPattern("foo.**");
    rule.setResult("bar.@1");
    PackageRemapper remapper = new PackageRemapper(Arrays.asList(rule), false);
    ManifestProcessor processor = new ManifestProcessor(remapper, false);

    byte[] original =
        serializeManifest(
            createManifest(
                "Manifest-Version", "1.0",
                "Main-Class", "other.Main"));
    EntryStruct struct = new EntryStruct();
    struct.name = "META-INF/MANIFEST.MF";
    struct.data = original;

    assertTrue(processor.process(struct));
    assertSame(original, struct.data);
  }

  public void testProcess_remapMainClass_withoutManifestVersion_retainsAttributes()
      throws Exception {
    Rule rule = new Rule();
    rule.setPattern("foo.**");
    rule.setResult("bar.@1");
    PackageRemapper remapper = new PackageRemapper(Arrays.asList(rule), false);
    ManifestProcessor processor = new ManifestProcessor(remapper, false);

    EntryStruct struct = new EntryStruct();
    struct.name = "META-INF/MANIFEST.MF";
    struct.data = "Main-Class: foo.Main\nCreated-By: custom-tool\n\n".getBytes(UTF_8);

    assertTrue(processor.process(struct));
    assertEquals(
        createManifest(
            "Manifest-Version", "1.0",
            "Main-Class", "bar.Main",
            "Created-By", "custom-tool"),
        new Manifest(new ByteArrayInputStream(struct.data)));
  }

  public void testMainProcessor_noRuleDirectives_ignoresNonStandardManifest() throws Exception {
    Zap zap = new Zap();
    zap.setPattern("foo.**");
    MainProcessor processor =
        new MainProcessor(Arrays.asList(zap), /* verbose= */ false, /* skipManifest= */ false);

    byte[] nonStandard = "# comment line not accepted by java.util.jar.Manifest\n".getBytes(UTF_8);
    EntryStruct struct = new EntryStruct();
    struct.name = "META-INF/MANIFEST.MF";
    struct.data = nonStandard;

    assertTrue(processor.process(struct));
    assertSame(nonStandard, struct.data);
  }

  private Manifest createManifest(String... keyThenValue) {
    Manifest manifest = new Manifest();
    for (int i = 0; i < keyThenValue.length; i += 2) {
      manifest.getMainAttributes().putValue(keyThenValue[i], keyThenValue[i + 1]);
    }
    return manifest;
  }

  private byte[] serializeManifest(Manifest manifest) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    manifest.write(baos);
    return baos.toByteArray();
  }

  public ManifestProcessorTest(String name) {
    super(name);
  }
}
