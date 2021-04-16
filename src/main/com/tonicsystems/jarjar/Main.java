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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.tonicsystems.jarjar.util.RuntimeIOException;
import com.tonicsystems.jarjar.util.StandaloneJarProcessor;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;

public class Main {

  private static final String LINE_SEPARATOR = System.getProperty("line.separator");
  private static final String HELP;

  static {
    try {
      HELP = readIntoString(Main.class.getResourceAsStream("help.txt"));
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  private static String readIntoString(InputStream in) throws IOException {
    StringBuilder sb = new StringBuilder();
    BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
    String line = null;
    while ((line = r.readLine()) != null) {
      sb.append(line).append(LINE_SEPARATOR);
    }
    return sb.toString();
  }


  public static void main(String[] args) throws Exception {
    MainUtil.runMain(new Main(), args, "help");
  }

  public void help() {
    System.err.print(HELP);
  }

  public void strings(String cp) throws IOException {
    if (cp == null) {
      throw new IllegalArgumentException("cp is required");
    }
    new StringDumper()
        .run(cp, new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));
  }

  // TODO: make level an enum
  public void find(String level, String cp1, String cp2) throws IOException {
    if (level == null || cp1 == null) {
      throw new IllegalArgumentException("level and cp1 are required");
    }
    if (cp2 == null) {
      cp2 = cp1;
    }
    int levelFlag;
    if ("class".equals(level)) {
      levelFlag = DepHandler.LEVEL_CLASS;
    } else if ("jar".equals(level)) {
      levelFlag = DepHandler.LEVEL_JAR;
    } else {
      throw new IllegalArgumentException("unknown level " + level);
    }
    PrintWriter w = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8)));
    DepHandler handler = new TextDepHandler(w, levelFlag);
    new DepFind().run(cp1, cp2, handler);
    w.flush();
  }

  public void process(File rulesFile, File inJar, File outJar) throws IOException {
    if (rulesFile == null || inJar == null || outJar == null) {
      throw new IllegalArgumentException("rulesFile, inJar, and outJar are required");
    }
    List<PatternElement> rules = RulesFileParser.parse(rulesFile);
    boolean verbose = Boolean.getBoolean("verbose");
    boolean skipManifest = Boolean.getBoolean("skipManifest");
    MainProcessor proc = new MainProcessor(rules, verbose, skipManifest);
    StandaloneJarProcessor.run(inJar, outJar, proc);
    proc.strip(outJar);
  }
}
