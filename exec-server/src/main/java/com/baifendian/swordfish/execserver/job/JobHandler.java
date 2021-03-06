/*
 * Copyright (C) 2017 Baifendian Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.baifendian.swordfish.execserver.job;

import com.baifendian.swordfish.common.job.Job;
import com.baifendian.swordfish.common.job.JobProps;
import com.baifendian.swordfish.common.job.exception.ExecException;
import com.baifendian.swordfish.common.job.logger.JobLogger;
import com.baifendian.swordfish.common.utils.DateUtils;
import com.baifendian.swordfish.dao.FlowDao;
import com.baifendian.swordfish.common.config.BaseConfig;
import com.baifendian.swordfish.dao.enums.FlowStatus;
import com.baifendian.swordfish.dao.model.ExecutionFlow;
import com.baifendian.swordfish.dao.model.ExecutionNode;
import com.baifendian.swordfish.dao.model.FlowNode;
import com.baifendian.swordfish.execserver.Constants;
import com.baifendian.swordfish.execserver.exception.ExecTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class JobHandler {

  private final Logger logger = LoggerFactory.getLogger(JobHandler.class);

  private FlowNode node;

  private ExecutionFlow executionFlow;

  private ExecutionNode executionNode;

  private FlowDao flowDao;

  private String jobIdLog;

  private Job job;

  private ExecutorService executorService;

  private int timeout;

  private final long startTime;

  private Map<String, String> systemParamMap;

  private Map<String, String> customParamMap;

  private Map<String, String> allParamMap;

  public JobHandler(FlowDao flowDao, ExecutionFlow executionFlow, ExecutionNode executionNode, FlowNode node, ExecutorService executorService, int timeout,
                    Map<String, String> systemParamMap, Map<String, String> customParamMap) {
    this.flowDao = flowDao;
    this.executionFlow = executionFlow;
    this.executionNode = executionNode;
    this.node = node;
    this.executorService = executorService;
    this.timeout = timeout;
    this.systemParamMap = systemParamMap;
    this.customParamMap = customParamMap;
    this.startTime = System.currentTimeMillis();
    this.jobIdLog = String.format("%s_%s", executionNode.getJobId(), DateUtils.now(Constants.DATETIME_FORMAT));
    // custom参数会覆盖system参数
    allParamMap = new HashMap<>();
    allParamMap.putAll(systemParamMap);
    allParamMap.putAll(customParamMap);

  }

  public FlowStatus handle() throws IOException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, InterruptedException {
    String flowLocalPath = BaseConfig.getFlowExecDir(executionFlow.getProjectId(), executionFlow.getFlowId(), executionFlow.getId());
    String jobScriptPath = flowLocalPath;
    //FileUtils.forceMkdir(new File(jobScriptPath));
    logger.info("job:{} script path:{}", jobIdLog, jobScriptPath);

    // 作业参数配置
    JobProps props = new JobProps();
    props.setJobParams(node.getParameter());
    props.setWorkDir(jobScriptPath);
    props.setProxyUser(executionFlow.getProxyUser());
    props.setDefinedParams(allParamMap);
    props.setProjectId(executionFlow.getProjectId());
    props.setWorkflowId(executionFlow.getFlowId());
    props.setNodeName(node.getName());
    props.setExecId(executionFlow.getId());
    props.setEnvFile(BaseConfig.getSystemEnvPath());
    props.setQueue(executionFlow.getQueue());

    JobLogger jobLogger = new JobLogger(executionNode.getJobId(), logger);
    //logger.info("props:{}", props);
    job = JobTypeManager.newJob(jobIdLog, node.getType(), props, jobLogger);
    Boolean result;
    try {
      result = submitJob(job);
    } catch (Exception e) {
      result = false;
      logger.error("run job error, job:" + jobIdLog, e);
    }
    FlowStatus status;
    if (result) {
      status = FlowStatus.SUCCESS;
    } else {
      status = FlowStatus.FAILED;
    }
    return status;
  }

  /**
   * 运行一个 job <p>
   *
   * @return 成功或失败
   */
  protected boolean submitJob(Job job) {
    // 异步提交 job
    Future<Boolean> future = executorService.submit(new Callable<Boolean>() {

      @Override
      public Boolean call() throws Exception {
        boolean isSuccess = true;
        try {
          job.before();
          job.process();
          job.after();
          if (job.getExitCode() != 0) {
            isSuccess = false;
          }
        } finally {
          // insertLogToDb(job.getContext().getExecLogger()); //
          // 插入日志到数据库中
        }
        return isSuccess;
      }
    });

    boolean isSuccess = false;

    // 短任务，需要设置超时时间
    if (!JobTypeManager.isLongJob(node.getType())) {
      try {
        isSuccess = future.get(calcNodeTimeout(), TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        throw new ExecTimeoutException("execute task time out", e);
      } catch (InterruptedException | ExecutionException e) {
        throw new ExecException("execute task get error", e);
      }
    } else { // 长任务定时检查作业是否有报错，如果报错就返回
      while(true){
        try {
          future.get(60, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
          isSuccess = false;
          throw new ExecException("execute task get error", e);
        } catch (TimeoutException e) {

        }
      }
    }

    return isSuccess;
  }

  /**
   * 计算节点的超时时间（s）， <p>
   *
   * @return 超时时间
   */
  private int calcNodeTimeout() {
    int usedTime = (int) ((System.currentTimeMillis() - startTime) / 1000);
    if (timeout <= usedTime) {
      throw new ExecTimeoutException("current workflow execution fetch time out");
    }
    return timeout - usedTime;
  }

  public Job getJob() {
    return job;
  }

  public String getJobIdLog() {
    return jobIdLog;
  }
}
