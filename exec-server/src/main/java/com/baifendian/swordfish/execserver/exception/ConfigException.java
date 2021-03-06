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
package com.baifendian.swordfish.execserver.exception;

/**
 * 配置信息错误异常 <p>
 *
 */
public class ConfigException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /**
   * @param msg
   */
  public ConfigException(String msg) {
    super(msg);
  }

  /**
   * @param msg
   * @param cause
   */
  public ConfigException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
