/**
 * (c) Copyright 2012 WibiData, Inc.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kiji.schema.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kiji.schema.avro.TableLayoutDesc;
import org.kiji.schema.layout.InvalidLayoutException;
import org.kiji.schema.layout.KijiTableLayouts;
import org.kiji.schema.testutil.AbstractKijiIntegrationTest;
import org.kiji.schema.testutil.ToolResult;

/**
 * Integration test for the kiji administrative commands, e.g., create a table, scan it, delete it.
 */
public class IntegrationTestKijiAdminTools extends AbstractKijiIntegrationTest {
  private static final Logger LOG = LoggerFactory.getLogger(IntegrationTestKijiAdminTools.class);

  public static final String SPLIT_KEY_FILE = "org/kiji/schema/tools/split-keys.txt";

  /**
   * A utility function that returns a sorted list of table names in a string. Used to test the
   * output of 'kiji ls' in {@link #testTables() testTables}.
   *
   * @param names A List of table names in arbitrary order. Will be sorted in place.
   * @return A string consisting of the table names sorted case sensitively, each ending in a
   * newline.
   */
  private String sortAndJoinTableNames(List<String> names) {
    Collections.sort(names);
    return StringUtils.join(names, "\n") + "\n";
  }

  public List<String> namesOfDefaultKijiTables() {
    List<String> names = new ArrayList<String>();
    return names;
  }

  /** Removes the first line of a string. */
  public String trimHead(String output) {
    return output.substring(output.indexOf("\n") + 1);
  }

  @Test
  public void testTables() throws Exception {
    // The names of tables in the kiji instance. Updated as we add more.
    List<String> tableNames = namesOfDefaultKijiTables(); //
    // Fresh Kiji, so 'kiji ls' should report the default tables.
    ToolResult lsFreshResult = runTool(new LsTool(), new String[0]);
    assertEquals(0, lsFreshResult.getReturnCode());
    assertTrue(trimHead(lsFreshResult.getStdoutUtf8()).isEmpty());

    // Add a table.
    final File layoutFile =
        KijiTableLayouts.getTempFile(KijiTableLayouts.getLayout(KijiTableLayouts.FOO_TEST));
    ToolResult createFooTableResult = runTool(new CreateTableTool(), new String[] {
      "--table=foo",
      "--layout=" + layoutFile,
    });
    assertEquals(0, createFooTableResult.getReturnCode());
    assertEquals("Parsing table layout: " + layoutFile + "\n"
        + "Creating kiji table: "
        + getKijiURI().toString() + "foo/...\n",
        createFooTableResult.getStdoutUtf8());

    // Now when we 'kiji ls' again, we should see table 'foo'.
    tableNames.add("foo");
    ToolResult lsFooResult = runTool(new LsTool(), new String[0]);
    assertEquals(0, lsFooResult.getReturnCode());
    assertEquals(sortAndJoinTableNames(tableNames), trimHead(lsFooResult.getStdoutUtf8()));

    // Synthesize some user data.
    ToolResult synthesizeResult = runTool(new SynthesizeUserDataTool(), new String[] {
      "--table=foo",
      "--num-users=10",
    });
    assertEquals(0, synthesizeResult.getReturnCode());
    assertEquals("Generating 10 users on kiji table '" + getKijiURI().toString() + "foo/'...\n"
        + "0 rows synthesized...\n"
        + "10 rows synthesized...\n"
        + "Done.\n",
        synthesizeResult.getStdoutUtf8());


    // Make sure there are 10 rows.
    ToolResult ls10RowsResult = runTool(new LsTool(), new String[] {
      "--table=foo",
    });
    assertEquals(0, ls10RowsResult.getReturnCode());
    LOG.debug("Output from 'wibi ls --table=foo' after synthesized users:\n"
        + ls10RowsResult.getStdoutUtf8());
    // 10 rows, each with:
    //   2 columns, each with:
    //     1 line for column name, timestamp.
    //     1 line for column data.
    //   1 blank line.
    // + 1 row for the header
    assertEquals(10 * ((2 * 2) + 1) + 1,
        StringUtils.countMatches(ls10RowsResult.getStdoutUtf8(), "\n"));


    // Look at just the "name" column for 3 rows.
    ToolResult ls3NameResult = runTool(new LsTool(), new String[] {
      "--table=foo",
      "--columns=info:name",
      "--max-rows=3",
    });
    assertEquals(0, ls3NameResult.getReturnCode());
    // 3 rows, each with:
    //   1 column, with:
    //     1 line for column name, timestamp.
    //     1 line for column data.
    //   1 blank line.
    // + 1 row for the header
    assertEquals(3 * ((1 * 2) + 1) + 1,
        StringUtils.countMatches(ls3NameResult.getStdoutUtf8(), "\n"));

    // Delete the foo table.
    ToolResult deleteResult = runTool(new DeleteTableTool(), new String[] {
      "--table=foo",
      "--confirm",
    });
    assertEquals(0, deleteResult.getReturnCode());
    assertEquals("Deleting kiji table: " + getKijiURI().toString() + "foo/\n"
        + "Deleted kiji table: " + getKijiURI().toString() + "foo/\n",
        deleteResult.getStdoutUtf8());


    // Make sure there are only the standard tables left.
    tableNames.remove("foo");
    ToolResult lsCleanedUp = runTool(new LsTool(), new String[0]);
    assertEquals(0, lsCleanedUp.getReturnCode());
    assertTrue(trimHead(lsCleanedUp.getStdoutUtf8()).isEmpty());
  }

  @Test
  public void testKijiLsStartAndLimitRow() throws Exception {
    createAndPopulateFooTable();
    try {
      // There should be 7 rows of input.
      ToolResult lsAllResult = runTool(new LsTool(), new String[] {
        "--table=foo",
        "--columns=info:name",
      });
      assertEquals(0, lsAllResult.getReturnCode());
      // Expect 6 rows of 'kiji ls' output, each with:
      //   1 column, with:
      //     1 line for the column name, timestamp.
      //     1 line for column data.
      //   1 blank line.
      // + 1 row for the header
      assertEquals(6 * ((1 * 2) + 1) + 1,
          StringUtils.countMatches(lsAllResult.getStdoutUtf8(), "\n"));

      // The foo table has row key hashing enabled.  Now let's run another 'kiji ls'
      // command starting from after the second row and before the last row, which
      // should only give us 3 results.  The start-row and limit-row keys here are just
      // numbers that I picked after looking at the results of the 'kiji ls' execution
      // above.
      ToolResult lsLimitResult = runTool(new LsTool(), new String[] {
        "--table=foo",
        "--columns=info:name",
        "--start-row=hex:50000000000000000000000000000000",  // after the second row.
        "--limit-row=hex:e0000000000000000000000000000000",  // before the last row.
      });
      assertEquals(0, lsLimitResult.getReturnCode());
      // Expect 2 rows of 'kiji ls' output, each with:
      //   1 column, with:
      //     1 line for the column name, timestamp.
      //     1 line for column data.
      //   1 blank line.
      // + 1 row for the header
      assertEquals(2 * ((1 * 2) + 1) + 1,
          StringUtils.countMatches(lsLimitResult.getStdoutUtf8(), "\n"));
    } finally {
      deleteFooTable();
    }
  }

  @Test
  public void testChangeRowKeyHashing() throws Exception {
    // Create a table.
    final File layoutFile =
        KijiTableLayouts.getTempFile(KijiTableLayouts.getLayout(KijiTableLayouts.FOO_TEST));
    ToolResult createFooTableResult = runTool(new CreateTableTool(), new String[] {
      "--table=foo",
      "--layout=" + layoutFile,
    });
    assertEquals(0, createFooTableResult.getReturnCode());
    assertEquals("Parsing table layout: " + layoutFile + "\n"
        + "Creating kiji table: " + getKijiURI().toString() + "foo/...\n",
        createFooTableResult.getStdoutUtf8());

    // Attempt to change hashRowKeys (should not be possible).
    final File changedLayoutFile =
        KijiTableLayouts.getTempFile(KijiTableLayouts.getFooChangeHashingTestLayout());
    try {
      ToolResult setHashRowKeyResult = runTool(new LayoutTool(), new String[] {
        "--do=set",
        "--table=foo",
        "--layout=" + changedLayoutFile,
      });
      assertEquals(3, setHashRowKeyResult.getReturnCode());
    } catch (InvalidLayoutException e) {
      LOG.debug("Exception message: " + e.getMessage());
      assertTrue(e.getMessage().contains("Invalid layout update from reference row keys format"));
    } finally {
      // Delete the table.
      ToolResult deleteResult = runTool(new DeleteTableTool(), new String[] {
            "--table=foo",
            "--confirm",
          });
      assertEquals(0, deleteResult.getReturnCode());
      assertEquals("Deleting kiji table: " + getKijiURI().toString() + "foo/\n"
          + "Deleted kiji table: " + getKijiURI().toString() + "foo/\n",
          deleteResult.getStdoutUtf8());
    }

  }

  @Test
  public void testCreateHashedTableWithNumRegions() throws Exception {
    // Create a table.
    final File layoutFile =
        KijiTableLayouts.getTempFile(KijiTableLayouts.getLayout(KijiTableLayouts.FOO_TEST));
    ToolResult createFooTableResult = runTool(new CreateTableTool(), new String[] {
      "--table=foo",
      "--layout=" + layoutFile,
      "--num-regions=" + 2,
    });
    assertEquals(0, createFooTableResult.getReturnCode());
    assertEquals("Parsing table layout: " + layoutFile + "\n"
        + "Creating kiji table: " + getKijiURI().toString() + "foo/...\n",
        createFooTableResult.getStdoutUtf8());

    // Delete the table.
    ToolResult deleteResult = runTool(new DeleteTableTool(), new String[] {
          "--table=foo",
          "--confirm",
        });
    assertEquals(0, deleteResult.getReturnCode());
    assertEquals("Deleting kiji table: " + getKijiURI().toString() + "foo/\n"
        + "Deleted kiji table: " + getKijiURI().toString() + "foo/\n",
        deleteResult.getStdoutUtf8());
  }

  @Test(expected=RuntimeException.class)
  public void testCreateHashedTableWithSplitKeys() throws Exception {
    // Create a table.
    final File layoutFile =
        KijiTableLayouts.getTempFile(KijiTableLayouts.getLayout(KijiTableLayouts.FOO_TEST));
    String splitKeyPath = getClass().getClassLoader().getResource(SPLIT_KEY_FILE).getPath();
    @SuppressWarnings("unused")
    ToolResult createFooTableResult = runTool(new CreateTableTool(), new String[] {
      "--table=foo",
      "--layout=" + layoutFile,
      "--split-key-file=file://" + splitKeyPath,
    });
  }

  @Test(expected=RuntimeException.class)
  public void testCreateUnhashedTableWithNumRegions() throws Exception {
    // Create a table.
    final File layoutFile =
        KijiTableLayouts.getTempFile(KijiTableLayouts.getFooChangeHashingTestLayout());
    ToolResult createUnhashedTable = runTool(new CreateTableTool(), new String[]{
        "--table=foo",
        "--layout=" + layoutFile,
        "--num-regions=4",
    });
  }

  @Test
  public void testCreateUnhashedTableWithSplitKeys() throws Exception {
    // Create a table.
    final TableLayoutDesc desc = KijiTableLayouts.getFooUnhashedTestLayout();
    final String tableName = desc.getName();
    final File layoutFile = KijiTableLayouts.getTempFile(desc);
    String splitKeyPath = getClass().getClassLoader().getResource(SPLIT_KEY_FILE).getPath();
    ToolResult createFooTableResult = runTool(new CreateTableTool(), new String[] {
      "--table=" + tableName,
      "--layout=" + layoutFile,
      "--split-key-file=file://" + splitKeyPath,
    });
    assertEquals(0, createFooTableResult.getReturnCode());
    assertEquals("Parsing table layout: " + layoutFile + "\n"
        + "Creating kiji table: " + getKijiURI().toString() + tableName + "/...\n",
        createFooTableResult.getStdoutUtf8());

    // Delete the table.
    ToolResult deleteResult = runTool(new DeleteTableTool(), new String[] {
          "--table=" + tableName,
          "--confirm",
        });
    assertEquals(0, deleteResult.getReturnCode());
    assertEquals("Deleting kiji table: " + getKijiURI().toString() + desc.getName() + "/\n"
        + "Deleted kiji table: " + getKijiURI().toString() + desc.getName() + "/\n",
        deleteResult.getStdoutUtf8());
  }

  @Test(expected=InvalidLayoutException.class)
  public void testCreateTableWithInvalidSchemaClassInLayout() throws Exception {
    final File layoutFile =
        KijiTableLayouts.getTempFile(KijiTableLayouts.getLayout(KijiTableLayouts.INVALID_SCHEMA));
    ToolResult createFailResult = runTool(new CreateTableTool(), new String[]{
        "--table=foo",
        "--layout=" + layoutFile,
    });
  }
}
