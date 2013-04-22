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

package org.kiji.mapreduce.platform;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.TaskType;

import org.kiji.annotations.ApiAudience;
import org.kiji.delegation.Lookups;

/**
 * Abstract representation of an underlying platform for KijiMR. This interface
 * is fulfilled by specific implementation providers that are dynamically chosen
 * at runtime based on the Hadoop &amp; HBase jars available on the classpath.
 */
@ApiAudience.Framework
public abstract class KijiMRPlatformBridge {
  /**
   * This API should only be implemented by other modules within KijiMR;
   * to discourage external users from extending this class, keep the c'tor
   * package-private.
   */
  KijiMRPlatformBridge() {
  }

  /**
   * Create and return a new TaskAttemptContext implementation parameterized by the specified
   * configuration and task attempt ID objects.
   *
   * @param conf the Configuration to use for the task attempt.
   * @param id the TaskAttemptID of the task attempt.
   * @return a new TaskAttemptContext.
   */
  public abstract TaskAttemptContext newTaskAttemptContext(Configuration conf, TaskAttemptID id);

  /**
   * Create and return a new TaskAttemptID object.
   *
   * @param jtIdentifier the jobtracker id.
   * @param jobId the job id number.
   * @param type the type of the task being created.
   * @param taskId the task id number within this job.
   * @param id the id number of the attempt within this task.
   * @return a newly-constructed TaskAttemptID.
   */
  public abstract TaskAttemptID newTaskAttemptID(String jtIdentifier, int jobId, TaskType type,
      int taskId, int id);

  private static KijiMRPlatformBridge mBridge;

  /**
   * @return the KijiMRPlatformBridge implementation appropriate to the current runtime
   * conditions.
   */
  public static final synchronized KijiMRPlatformBridge get() {
    if (null != mBridge) {
      return mBridge;
    }
    mBridge = Lookups.getPriority(KijiMRPlatformBridgeFactory.class).lookup().getBridge();
    return mBridge;
  }
}

