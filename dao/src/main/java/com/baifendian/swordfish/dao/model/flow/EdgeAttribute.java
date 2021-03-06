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
package com.baifendian.swordfish.dao.model.flow;

public class EdgeAttribute {
  /**
   * 边的方向
   */
  private String direction;

  /**
   * 对 start 来说, 可能还要指定里面的分组 id
   */
  private String fromGroupId;

  /**
   * 对 end 来说, 可能还要指定 port 的分组 id
   */
  private String toPortGroupId;

  /**
   * getter method
   *
   * @return the direction
   * @see EdgeAttribute#direction
   */
  public String getDirection() {
    return direction;
  }

  /**
   * setter method
   *
   * @param direction the direction to set
   * @see EdgeAttribute#direction
   */
  public void setDirection(String direction) {
    this.direction = direction;
  }

  public String getFromGroupId() {
    return fromGroupId;
  }

  public void setFromGroupId(String fromGroupId) {
    this.fromGroupId = fromGroupId;
  }

  public String getToPortGroupId() {
    return toPortGroupId;
  }

  public void setToPortGroupId(String toPortGroupId) {
    this.toPortGroupId = toPortGroupId;
  }
}
