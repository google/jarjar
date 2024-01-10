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
import java.util.Arrays;
import junit.framework.TestCase;

public class ServiceProcessorTest extends TestCase {
  public void testProcess() throws Exception {
    Rule rule = new Rule();
    rule.setPattern("foo.**");
    rule.setResult("bar.@1");
    PackageRemapper remapper = new PackageRemapper(Arrays.asList(rule), false);
    ServiceProcessor serviceProcessor = new ServiceProcessor(remapper);

    EntryStruct struct = new EntryStruct();
    struct.name = "META-INF/services/foo.Service";
    struct.data = "foo.baz.ServiceImplementation\n".getBytes(UTF_8);

    assertTrue(serviceProcessor.process(struct));
    assertEquals("META-INF/services/bar.Service", struct.name);
    assertEquals("bar.baz.ServiceImplementation\n", new String(struct.data, UTF_8));
  }

  public ServiceProcessorTest(String name) {
    super(name);
  }
}
