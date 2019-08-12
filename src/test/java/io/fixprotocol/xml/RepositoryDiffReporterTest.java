/*
 * Copyright 2017-2019 FIX Protocol Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package io.fixprotocol.xml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RepositoryDiffReporterTest {

  private static final String DIFF_FILENAME = "target/test/testdiff.html";
  private RepositoryDiffReporter tool;

  @BeforeAll
  public static void setupOnce() throws Exception {
    new File("target/test").mkdirs();
  }

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  public void setUp() throws Exception {
    tool = new RepositoryDiffReporter();
    RepositoryDiffReporter.HtmlDiffListener aListener = tool.new HtmlDiffListener(new FileOutputStream(DIFF_FILENAME));
    tool.setListener(aListener);
  }


  @Test
  public void report() throws Exception {
    tool.diff(
        new FileInputStream(Thread.currentThread().getContextClassLoader()
            .getResource("FixRepository2016EP215.xml").getFile()),
        new FileInputStream(Thread.currentThread().getContextClassLoader()
            .getResource("FixRepository2016EP216.xml").getFile()));
   }

}
