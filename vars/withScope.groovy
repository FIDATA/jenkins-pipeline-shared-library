#!/usr/bin/env groovy
/*
 * withScope Jenkins Pipeline step
 * Copyright ©  Basil Peace
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

import groovy.text.GStringTemplateEngine
import java.nio.file.Paths

void callWithZeroOrOneArg(Serializable arg, Closure closure) {
  switch (closure.maximumNumberOfParameters) {
    case 0:
      closure.call()
      break
    case 1:
      closure.call(arg)
      break
    default:
      throw new IllegalArgumentException(String.format('Invalid number of closure parameters: %d', closure.maximumNumberOfParameters))
  }
}

void call(String name, String type, String path, String envVarName, String envVarTemplate = '$value', Closure body, Closure configClosure, Closure finalizeClosure = null) {
  final String title = "$name scope $type"
  final String envVarValue = new GStringTemplateEngine().createTemplate(envVarTemplate).make(value: path)
  path = Paths.get(pwd(), '.scope').resolve(path).toString()

  final String currEnvVarValue = env[envVarName]
  if (currEnvVarValue != null) {
    if (currEnvVarValue != envVarValue) {
      throw new IllegalStateException(String.format('Environment variable %s is already set to value %s. New value: %s', envVarName, currEnvVarValue, envVarValue))
    }
    echo "$title already configured"
    body.call()
    return
  }


  /*
   * CAVEAT:
   * First we create empty file and set permissions on it.
   * Only after that we write sensitive content
   */
  echo "Creating $title and set permissions..."
  switch (type) {
    case 'dir':
      dir path
      break
    case 'file':
      // dir path.parent.toString()
      touch path
      break
    default:
      throw new IllegalArgumentException(String.format('Unknown scope type: %s', $type))
  }
  if (isUnix()) {
    sh """\
      chmod 0700 "$path"
    """.stripIndent()
  } else {
    batch """\
      icacls "$path" /inheritance:r /grant:r "%USERNAME%:(OI)(CI)F"
    """.stripIndent()
  }

  try {
    withEnv(["$envVarName=$envVarValue"]) { ->
      echo "Configuring $title..."
      callWithZeroOrOneArg path, configClosure

      body.call()
    }
  } finally {
    if (finalizeClosure != null) {
      withEnv(["$envVarName=$envVarValue"]) { ->
        finalizeClosure.call()
      }
    }
    echo "Cleaning up $title..."
    try {
      switch (type) {
        case 'dir':
          deleteDir path
          break
        case 'file':
          fileOperations([fileDeleteOperation(includes: path)])
          break
      }
    } catch (ignored) { }
  }
}
