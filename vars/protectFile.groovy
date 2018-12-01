#!/usr/bin/env groovy
/*
 * protectFile Jenkins Pipeline step
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
String call(String file) {
  withEnv([
    "FILE=$file"
  ]) {
    // TODO: this code for dir, for file it would be different
    if (isUnix()) {
      sh '''\
        chmod 0700 "$FILE"
      '''.stripIndent()
    } else {
      batch '''\
        icacls "%FILE%" /inheritance:r /grant:r "%USERNAME%":(OI)(CI)F
      '''.stripIndent()
    }
  }
}
