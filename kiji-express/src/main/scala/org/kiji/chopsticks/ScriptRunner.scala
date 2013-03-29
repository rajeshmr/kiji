/**
 * (c) Copyright 2013 WibiData, Inc.
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

package org.kiji.chopsticks

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLClassLoader
import java.util.UUID
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

import scala.io.Source

import com.google.common.io.Files
import com.twitter.scalding.Args
import com.twitter.scalding.Job
import com.twitter.scalding.Tool
import com.twitter.util.Eval
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.util.ToolRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.kiji.chopsticks.Resources.doAndClose

/**
 * ScriptRunner provides the machinery necessary to be able to run uncompiled KijiChopsticks
 * scripts.
 *
 * An uncompiled KijiChopsticks should be written as if it were a script inside of a Scalding Job
 * class. Scripts can access command line arguments by using the Scalding Args object with the name
 * args. Scripts compiled by ScriptRunner get wrapped in a Scalding Job constructor:
 * {{{
 *   // Added by ScriptRunner:
 *   { args: com.twitter.scalding.Args =&gt;
 *     new com.twitter.scalding.Job(args) {
 *
 *       // User code here.
 *
 *     }
 *   }
 * }}}
 *
 * To run a script using ScriptRunner call the chop jar command with the following syntax.
 * {{{
 *   chop jar &lt;/path/to/chopsticks/jar&gt; org.kiji.chopsticks.ScriptRunner \
 *       &lt;/path/to/script&gt; [other options here]
 * }}}
 */
object ScriptRunner {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Builds a jar entry and writes it to the specified jar output stream.
   *
   * @param source directory or file to write to the specified JarOutputStream.
   * @param entryPath that the new jar entry will be added under.
   * @param target stream to write to.
   */
  private[chopsticks] def addToJar(source: File, entryPath: String, target: JarOutputStream) {
    source
        .listFiles
        .foreach {
          case file if file.isFile() && file.getName().endsWith(".class") => {
            val entry: JarEntry = new JarEntry(entryPath + file.getName())
            entry.setTime(file.lastModified())
            target.putNextEntry(entry)

            doAndClose(new BufferedInputStream(new FileInputStream(file))) { inputStream =>
              val buffer = new Array[Byte](inputStream.available())
              inputStream.read(buffer)
              target.write(buffer)
            }
            target.closeEntry()
          }
          case dir if dir.isDirectory() => {
            // Make sure the path for this directory ends with a '/'.
            val path: String = entryPath + dir.getName() + "/"

            // Add a new jar entry for the directory.
            val entry: JarEntry = new JarEntry(path)
            entry.setTime(dir.lastModified())
            target.putNextEntry(entry)
            target.closeEntry()

            // Recursively process nested files/directories.
            dir.listFiles().foreach { nested: File => addToJar(nested, path, target) }
          }
          case other => logger.debug("ignoring %s".format(other.getPath()))
        }
  }

  /**
   * Builds a jar containing the .class files in the specified folder and all subfolders.
   *
   * @param source directory to include in jar.
   * @param target path to desired jar.
   */
  private[chopsticks] def buildJar(source: File, target: File) {
    require(source.isDirectory(), "Source file %s is not a directory".format(source.getPath()))

    doAndClose(new JarOutputStream(new FileOutputStream(target))) { jarStream =>
      addToJar(source, "", jarStream)
    }
  }

  /**
   * Compiles a script to the specified folder. Before compilation occurs, the code contained within
   * the specified script will be wrapped in a scaling job constructor. After compilation, this job
   * constructor, in the form of a function, will be returned.
   *
   * @param script to compile.
   * @param outputDirectory that compiled classes will be placed in.
   * @return a function that will construct a KijiChopsticks job given command line arguments.
   */
  private[chopsticks] def compileScript(script: String, outputDirectory: File): (Args) => Job = {
    // Create an evaluator that will compile the script to a temporary directory.
    logger.info("compiling classes to %s".format(outputDirectory))
    val compiler = new Eval(Some(outputDirectory))

    // Modify the script so that it returns a job constructor.
    val preparedScript: String = {
      val before = "{ args: com.twitter.scalding.Args => new com.twitter.scalding.Job(args) {"
      val after = "} }"
      "%s\n%s\n%s".format(before, script, after)
    }

    // Check and compile the code.
    compiler.check(preparedScript)
    compiler.apply(preparedScript)
  }

  /**
   * Compiles and runs the provided KijiChopsticks script.
   *
   * @param args Command line arguments for this script runner. The first argument to ScriptRunner
   *     is expected to be a path to the script to run. Any remaining flags get passed to the
   *     script itself.
   */
  def main(args: Array[String]) {
    val scriptFile: File = new File(args.head)
    val jobArgs: Array[String] = args.tail
    require(scriptFile.exists(), "%s does not exist".format(scriptFile.getPath()))
    require(scriptFile.isFile(), "%s is not a file".format(scriptFile.getPath()))

    // Find a temporary folder to store compiled classes in.
    val tempDir: File = Files.createTempDir()
    val compileFolder: File = new File("%s/classes".format(tempDir.getPath()))
    val compileJar: File = new File("%s/%s.jar".format(tempDir.getPath(), scriptFile.getName()))

    // Compile the script.
    compileFolder.mkdir()
    val script: String = doAndClose(Source.fromFile(scriptFile)) { source: Source =>
      source.mkString
    }
    val jobc: (Args) => Job = compileScript(script, compileFolder)

    // Build a jar.
    logger.info("building %s".format(compileJar))
    buildJar(compileFolder, compileJar)
    assume(
        compileJar.isFile(),
        "%s is not a file.".format(compileJar.getPath()))
    assume(
        compileJar.getPath().endsWith(".jar"),
        "%s should end with '.jar'.".format(compileJar.getPath()))
    assume(
        compileJar.exists(),
        "%s does not exist.".format(compileJar.getPath()))

    // Create a new configuration and store the jar location in 'tmpjars'.
    val conf = {
      val hbaseConf = HBaseConfiguration.create()
      hbaseConf.set("tmpjars", "file:" + compileJar.getPath())
      hbaseConf
    }

    // Create a tool with the compiled job jar.
    val tool: Tool = {
      val scaldingTool: Tool = new Tool
      scaldingTool.setJobConstructor(jobc)
      scaldingTool
    }

    // Run the job.
    ToolRunner.run(conf, tool, jobArgs)
  }
}
