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
package com.baifendian.swordfish.execserver.job.shell;

import com.baifendian.swordfish.dao.utils.json.JsonUtil;

import org.junit.Test;

public class ShellParamTest {

  @Test
  public void testParse(){
    String paramStr ="{\"script\":\"ls -l; echo $[yyyy-MM-dd/HH:mm:ss-1/24]; echo $[month_begin(yyyyMMdd,1)]\", \"resources\":[{ \"res\": \"ABC.conf\", \"alias\": \"aa\" }]}";
    ShellParam param = JsonUtil.parseObject(paramStr, ShellParam.class);
    System.out.println(param);
  }
}
