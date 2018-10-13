#!/usr/bin/env groovy
/*
 * configureGrape Jenkins Pipeline step
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
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import groovy.text.XmlTemplateEngine
import hudson.util.Secret

boolean call(String artifactoryCredentialId) {
  String GRAPE_CONFIG_FILE_NAME = 'grapeConfig.xml'
  String grapeConfigPath = "${ getHome() }/.groovy/$GRAPE_CONFIG_FILE_NAME"
  lock(grapeConfigPath) {
    boolean didWork = !fileExists(grapeConfigPath)
    if (didWork) {
      StandardUsernamePasswordCredentials credentials = CredentialsProvider.findCredentialById(
        artifactoryCredentialId,
        StandardUsernamePasswordCredentials,
        currentBuild.rawBuild
      )

      echo "Writing $GRAPE_CONFIG_FILE_NAME..."
      /*
       * CAVEAT:
       * First we create empty file and set permissions on it.
       * Only after that we write senstive content
       */
      writeFile file: grapeConfigPath, text: ''
      withEnv([
        "FILE=$grapeConfigPath"
      ]) {
        if (isUnix()) {
          sh '''\
            chmod 0600 "$FILE"
          '''.stripIndent()
        } else {
          batch '''\
            icacls "%FILE%" /inheritance:r /grant:r "%USERNAME%":(OI)(CI)F
          '''.stripIndent()
        }
      }
      /*
       * WORKAROUND:
       * XmlTemplateEngine removes XML declaration from template
       * <grv87 2018-10-13>
       */
      String encoding = 'UTF-8'
      writeFile file: grapeConfigPath,
        text: "<?xml version='1.0' encoding='$encoding'?>\n${ new XmlTemplateEngine().createTemplate(libraryResource("org/fidata/${ GRAPE_CONFIG_FILE_NAME }.gsp")).make([username: credentials.username, password: Secret.toString(credentials.password)]) }",
        encoding: encoding
    }
    didWork
  }
}
