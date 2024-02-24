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

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class StandaloneJarProcessor {
  public static void run(File from, File to, JarProcessor proc) throws IOException {
    File tmpTo = File.createTempFile("jarjar", ".jar");

    Set<String> entries = new HashSet<>();
    try (ZipFile inZip = new ZipFile(from);
        ZipOutputStream outZip = IoUtil.bufferedZipOutput(tmpTo)) {

      for (Enumeration<? extends ZipEntry> e = inZip.entries(); e.hasMoreElements(); ) {
        ZipEntry inEntry = e.nextElement();
        EntryStruct struct = new EntryStruct();
        struct.name = inEntry.getName();
        struct.time = inEntry.getTime();
        struct.data = inZip.getInputStream(inEntry).readAllBytes();
        if (!proc.process(struct)) {
          continue;
        }

        if (entries.add(struct.name)) {
          ZipEntry outEntry = new ZipEntry(struct.name);
          outEntry.setTime(struct.time);
          outEntry.setCompressedSize(-1);
          outZip.putNextEntry(outEntry);
          outZip.write(struct.data);
        } else if (struct.name.endsWith("/")) {
          // TODO(chrisn): log
        } else {
          throw new IllegalArgumentException("Duplicate jar entries: " + struct.name);
        }
      }
    }

    // delete the empty directories
    IoUtil.copyZipWithoutEmptyDirectories(tmpTo, to);
    tmpTo.delete();
  }

  private StandaloneJarProcessor() {}
}
