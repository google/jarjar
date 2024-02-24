/*
 * Copyright 2008 Google Inc.
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

class IoUtil {
  private IoUtil() {}

  public static void pipe(InputStream is, OutputStream out, byte[] buf) throws IOException {
    for (; ; ) {
      int amt = is.read(buf);
      if (amt < 0) {
        break;
      }
      out.write(buf, 0, amt);
    }
  }

  public static void copy(File from, File to, byte[] buf) throws IOException {
    try (InputStream in = new FileInputStream(from);
        OutputStream out = new FileOutputStream(to)) {
      pipe(in, out, buf);
    }
  }

  /**
   * Create a copy of an zip file without its empty directories.
   *
   * @param inputFile the input file
   * @param outputFile the output file
   * @throws IOException
   */
  public static void copyZipWithoutEmptyDirectories(final File inputFile, final File outputFile)
      throws IOException {
    try (ZipFile inputZip = new ZipFile(inputFile);
        ZipOutputStream outputStream = bufferedZipOutput(outputFile)) {
      ArrayList<? extends ZipEntry> sortedList =
          inputZip.stream()
              .sorted(Comparator.comparing(ZipEntry::getName))
              .collect(Collectors.toCollection(ArrayList::new));

      // treat them again and write them in output, wenn they not are empty directories
      for (int i = sortedList.size() - 1; i >= 0; i--) {
        final ZipEntry inputEntry = sortedList.get(i);
        final String name = inputEntry.getName();
        final boolean isEmptyDirectory;
        if (inputEntry.isDirectory()) {
          if (i == sortedList.size() - 1) {
            // no item afterwards; it was an empty directory
            isEmptyDirectory = true;
          } else {
            final String nextName = sortedList.get(i + 1).getName();
            isEmptyDirectory = !nextName.startsWith(name);
          }
        } else {
          isEmptyDirectory = false;
        }

        // write the entry
        if (isEmptyDirectory) {
          sortedList.remove(inputEntry);
        } else {
          outputStream.putNextEntry(inputEntry);
          try (final InputStream is = inputZip.getInputStream(inputEntry)) {
            // 2024-02-23: Using a shared buffer had no measurable perf improvement
            is.transferTo(outputStream);
          }
        }
      }
    }
  }

  /**
   * Create a ZipOutputStream with buffering.
   *
   * <p>Buffering is critical to performance because the ZIP format has a lot of small header
   * segments, which each trigger an OS write call otherwise.
   */
  public static ZipOutputStream bufferedZipOutput(File file) throws IOException {
    return new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
  }
}
