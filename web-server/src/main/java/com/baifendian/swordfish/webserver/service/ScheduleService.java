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
package com.baifendian.swordfish.webserver.service;

import com.baifendian.swordfish.dao.FlowDao;
import com.baifendian.swordfish.dao.enums.DepPolicyType;
import com.baifendian.swordfish.dao.enums.FailurePolicyType;
import com.baifendian.swordfish.dao.enums.NotifyType;
import com.baifendian.swordfish.dao.enums.ScheduleStatus;
import com.baifendian.swordfish.dao.mapper.MasterServerMapper;
import com.baifendian.swordfish.dao.mapper.ProjectMapper;
import com.baifendian.swordfish.dao.mapper.ScheduleMapper;
import com.baifendian.swordfish.dao.model.*;
import com.baifendian.swordfish.dao.utils.json.JsonUtil;
import com.baifendian.swordfish.rpc.client.MasterClient;
import com.baifendian.swordfish.webserver.dto.ScheduleDto;
import com.baifendian.swordfish.webserver.dto.ScheduleParam;
import com.baifendian.swordfish.webserver.exception.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.baifendian.swordfish.dao.enums.ScheduleStatus.OFFLINE;

@Service
public class ScheduleService {

  private static Logger logger = LoggerFactory.getLogger(ScheduleService.class.getName());

  @Autowired
  private ScheduleMapper scheduleMapper;

  @Autowired
  private ProjectMapper projectMapper;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private FlowDao flowDao;

  @Autowired
  private MasterServerMapper masterServerMapper;

  /**
   * 创建一个调度
   *
   * @param operator
   * @param projectName
   * @param workflowName
   * @param schedule
   * @param notifyType
   * @param maxTryTimes
   * @param failurePolicy
   * @param depWorkflows
   * @param depPolicyType
   * @param timeout
   * @return
   */
  @Transactional(value = "TransactionManager", rollbackFor = Exception.class)
  public Schedule createSchedule(User operator, String projectName, String workflowName, String schedule, NotifyType notifyType, String notifyMails, int maxTryTimes, FailurePolicyType failurePolicy, String depWorkflows, DepPolicyType depPolicyType, int timeout){

    Project project = projectMapper.queryByName(projectName);

    if (project == null) {
      throw new NotFoundException("project",projectName);
    }

    if (!projectService.hasReadPerm(operator.getId(), project)) {
      logger.error("User {} has no right permission for the project {} to post schedule",operator.getName(),project.getName());
      throw new PermissionException("project read or project owner",operator.getName());
    }

    //检查是否存在工作流
    ProjectFlow projectFlow = flowDao.projectFlowfindByName(project.getId(), workflowName);

    if (projectFlow == null) {
      logger.error("User {} has no exist workflow {} for the project {} to post schedule",operator.getName(),workflowName,project.getName());
      throw new NotModifiedException("User {0} has no exist workflow {1} for the project {2} to post schedule",operator.getName(),workflowName,project.getName());
    }

    Schedule scheduleObj = new Schedule();
    Date now = new Date();

    scheduleObj.setProjectName(projectName);
    scheduleObj.setFlowId(projectFlow.getId());
    scheduleObj.setFlowName(projectFlow.getName());

    try {
      ScheduleParam scheduleParam = JsonUtil.parseObject(schedule,ScheduleParam.class);
      scheduleObj.setStartDate(scheduleParam.getStartDate());
      scheduleObj.setEndDate(scheduleParam.getEndDate());
      scheduleObj.setCrontab(scheduleParam.getCrontab());
      scheduleObj.setNotifyType(notifyType);
      scheduleObj.setNotifyMailsStr(notifyMails);
      scheduleObj.setMaxTryTimes(maxTryTimes);
      scheduleObj.setFailurePolicy(failurePolicy);
      scheduleObj.setDepWorkflowsStr(depWorkflows);
      scheduleObj.setDepPolicy(depPolicyType);
      scheduleObj.setTimeout(timeout);
      scheduleObj.setCreateTime(now);
      scheduleObj.setModifyTime(now);
      scheduleObj.setOwnerId(operator.getId());
      scheduleObj.setOwner(operator.getName());
      scheduleObj.setScheduleStatus(OFFLINE);
    } catch (Exception e) {
      logger.error(e.toString());
      throw new BadRequestException("create schedule param object error");
    }

    try {
      scheduleMapper.insert(scheduleObj);
    } catch (DuplicateKeyException e) {
      logger.error("schedule has exist, can't create again.", e);
      throw new NotModifiedException("schedule has exist, can't create again.");
    }

    return scheduleObj;
  }

  /**
   * 修改一个调度
   *
   * @param operator
   * @param projectName
   * @param workflowName
   * @param schedule
   * @param notifyType
   * @param notifyMails
   * @param maxTryTimes
   * @param failurePolicy
   * @param depWorkflows
   * @param depPolicyType
   * @param timeout
   * @return
   */
  @Transactional(value = "TransactionManager", rollbackFor = Exception.class)
  public Schedule patchSchedule(User operator, String projectName, String workflowName, String schedule, NotifyType notifyType, String notifyMails, Integer maxTryTimes, FailurePolicyType failurePolicy, String depWorkflows, DepPolicyType depPolicyType, Integer timeout, ScheduleStatus scheduleStatus) {
    Project project = projectMapper.queryByName(projectName);

    if (project == null) {
      throw new NotFoundException("project",projectName);
    }

    if (!projectService.hasReadPerm(operator.getId(), project)) {
      logger.error("User {} has no right permission for the project {} to patch schedule",operator.getName(),project.getName());
      throw new PermissionException("project read or project owner",operator.getName());
    }

    //检查是否存在工作流
    ProjectFlow projectFlow = flowDao.projectFlowfindByName(project.getId(), workflowName);

    if (projectFlow == null) {
      logger.error("User {} has no exist workflow {} for the project {} to patch schedule",operator.getName(),workflowName,project.getName());
      throw new NotFoundException("workflow",workflowName);
    }

    //检查调度是否存在

    Schedule scheduleObj = scheduleMapper.selectByFlowId(projectFlow.getId());
    Date now = new Date();

    if (scheduleObj == null) {
      throw new NotFoundException("Schedule","Schedule.flowId:"+projectFlow.getId());
    }

    //封装检查更新参数
    try {
      if (!StringUtils.isEmpty(schedule)) {
        ScheduleParam scheduleParam = JsonUtil.parseObject(schedule,ScheduleParam.class);
        scheduleObj.setStartDate(scheduleParam.getStartDate());
        scheduleObj.setEndDate(scheduleParam.getEndDate());
        scheduleObj.setCrontab(scheduleParam.getCrontab());
      }
      if (notifyType != null) {
        scheduleObj.setNotifyType(notifyType);
      }
      if (!StringUtils.isEmpty(notifyMails)) {
        scheduleObj.setNotifyMailsStr(notifyMails);
      }

      if (maxTryTimes != null) {
        scheduleObj.setMaxTryTimes(maxTryTimes);
      }

      if (failurePolicy != null) {
        scheduleObj.setFailurePolicy(failurePolicy);
      }

      if (!StringUtils.isEmpty(depWorkflows)) {
        scheduleObj.setDepWorkflowsStr(depWorkflows);
      }

      if (depPolicyType != null) {
        scheduleObj.setDepPolicy(depPolicyType);
      }

      if (timeout != null) {
        scheduleObj.setTimeout(timeout);
      }

      if (scheduleStatus != null) {
        scheduleObj.setScheduleStatus(scheduleStatus);
      }
      scheduleObj.setModifyTime(now);
    } catch (Exception e) {
      logger.error(e.toString());
      throw new ParameterException("create schedule param object error");
    }

    scheduleMapper.update(scheduleObj);

    return scheduleObj;
  }

  /**
   * 创建并修改一个工作流
   *
   * @return
   */
  public Schedule putSchedule(User operator, String projectName, String workflowName, String schedule, NotifyType notifyType, String notifyMails, Integer maxTryTimes, FailurePolicyType failurePolicy, String depWorkflows, DepPolicyType depPolicyType, Integer timeout){
    Schedule scheduleObj = scheduleMapper.selectByFlowName(projectName, workflowName);
    if (scheduleObj == null) {
      return createSchedule(operator, projectName, workflowName, schedule, notifyType, notifyMails, maxTryTimes, failurePolicy, depWorkflows, depPolicyType, timeout);
    } else {
      return patchSchedule(operator, projectName, workflowName, schedule, notifyType, notifyMails, maxTryTimes, failurePolicy, depWorkflows, depPolicyType, timeout, null);
    }
  }

  /**
   * 设置一个调度的上下线
   */
  public void postScheduleStatus(User operator,String projectName,String workflowName,String scheduleStatus) throws Exception{
    Project project = projectMapper.queryByName(projectName);

    if (project == null) {
      throw new NotFoundException("project",projectName);
    }

    if (!projectService.hasReadPerm(operator.getId(), project)) {
      logger.error("User {} has no right permission for the project {} to post schedule status",operator.getName(),project.getName());
      throw new PermissionException("project read or project owner",operator.getName());
    }

    //检查是否存在工作流
    ProjectFlow projectFlow = flowDao.projectFlowfindByName(project.getId(), workflowName);

    if (projectFlow == null) {
      logger.error("User {} has no exist workflow {} for the project {} to post schedule status",operator.getName(),workflowName,project.getName());
      throw new NotFoundException("workflow",workflowName);
    }

    // 查看 master 是否存在
    MasterServer masterServer = masterServerMapper.query();
    if (masterServer == null) {
      logger.error("Master server does not exist.");
      throw new ServerErrorException("Master server does not exist.");
    }

    //检查调度是否存在

    Schedule scheduleObj = scheduleMapper.selectByFlowId(projectFlow.getId());
    Date now = new Date();

    if (scheduleObj == null) {
      throw new NotFoundException("schedule","schedule.flowId:"+projectFlow.getId());
    }

    switch (scheduleStatus){
      case "online":scheduleObj.setScheduleStatus(ScheduleStatus.ONLINE);break;
      case "offline":scheduleObj.setScheduleStatus(ScheduleStatus.OFFLINE);break;
      default:{
        logger.error("no support scheduleStatus: {}",scheduleStatus);
        throw new ParameterException("schedule status");
      }
    }

    scheduleMapper.update(scheduleObj);

    //链接execServer
    MasterClient masterClient = new MasterClient(masterServer.getHost(), masterServer.getPort());

    try {

      switch (scheduleObj.getScheduleStatus()){
        case ONLINE:{
          logger.info("Call master client set schedule online , project id: {}, flow id: {},host: {}, port: {}", project.getId(), projectFlow.getId(), masterServer.getHost(), masterServer.getPort());
          if (!masterClient.setSchedule(project.getId(), projectFlow.getId())) {
            logger.error("Call master client set schedule online false , project id: {}, flow id: {},host: {}, port: {}", project.getId(), projectFlow.getId(), masterServer.getHost(), masterServer.getPort());
            throw new ServerErrorException("Call master client set schedule online false , project id: {}, flow id: {},host: {}, port: {}", project.getId(), projectFlow.getId(), masterServer.getHost(), masterServer.getPort());
          }
          break;
        }
        case OFFLINE:{
          logger.info("Call master client set schedule offline , project id: {}, flow id: {},host: {}, port: {}", project.getId(), projectFlow.getId(), masterServer.getHost(), masterServer.getPort());
          if (!masterClient.deleteSchedule(project.getId(), projectFlow.getId())) {
            logger.error("Call master client set schedule offline false , project id: {}, flow id: {},host: {}, port: {}", project.getId(), projectFlow.getId(), masterServer.getHost(), masterServer.getPort());
            throw new ServerErrorException("Call master client set schedule offline false , project id: {}, flow id: {},host: {}, port: {}", project.getId(), projectFlow.getId(), masterServer.getHost(), masterServer.getPort());
          }
          break;
        }
        default:{
          logger.error("unknown schedule status {}",scheduleStatus.toString());
          throw new ParameterException("schedule status");
        }
      }

    } catch (Exception e) {
      logger.error("Call master client set schedule error", e);
      throw e;
    }

    return;

  }

  /**
   * 查询一个工作流的调度
   *
   * @param operator
   * @param projectName
   * @param workflowName
   * @return
   */
  public Schedule querySchedule(User operator, String projectName, String workflowName) {
    Project project = projectMapper.queryByName(projectName);

    if (project == null) {
      throw new NotFoundException("project",projectName);
    }

    if (!projectService.hasReadPerm(operator.getId(), project)) {
      logger.error("User {} has no right permission for the project {} to get schedule",operator.getName(),project.getName());
      throw new PermissionException("project read or project owner",operator.getName());
    }

    //检查是否存在工作流
    ProjectFlow projectFlow = flowDao.projectFlowfindByName(project.getId(), workflowName);

    if (projectFlow == null) {
      throw new NotFoundException("workflow",workflowName);
    }

    return scheduleMapper.selectByFlowId(projectFlow.getId());
  }

  /**
   * 根据项目查询调度
   * @param operator
   * @param projectName
   * @return
   */
  public List<Schedule> queryAllSchedule(User operator, String projectName) {
    Project project = projectMapper.queryByName(projectName);

    if (project == null) {
      throw new NotFoundException("project",projectName);
    }

    if (!projectService.hasReadPerm(operator.getId(), project)) {
      logger.error("User {} has no right permission for the project {} to get all schedule",operator.getName(),project.getName());
      throw new PermissionException("project read or project owner",operator.getName());
    }

    return scheduleMapper.selectByProject(projectName);
  }
}