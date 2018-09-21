#!/usr/bin/env groovy
/*
 * GpgUtils class
 * Copyright © 2018  Basil Peace
 *
 * This file is part of jenkins-pipeline-shared-library.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.fidata.gpg

import groovy.transform.CompileStatic

/**
 * Utils to work with GPG
 */
@CompileStatic
final class GpgUtils {
  /**
   * GPG configuration file name
   */
  static final String GPG_CONF_FILE_NAME = 'gpg.conf'
  /**
   * GPG agent configuration file name
   */
  static final String GPG_AGENT_CONF_FILE_NAME = 'gpg-agent.conf'

  private GpgUtils() {
    throw new UnsupportedOperationException()
  }
}
