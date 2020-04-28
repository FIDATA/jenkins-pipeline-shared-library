#!/usr/bin/env groovy
/*
 * Default Jenkins pipeline for semantic-release
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
void call(final Map<String, Object> config = [:]) {
  Map<String, Integer> timeouts = (Map<String, Integer>)config.getOrDefault('timeouts', [:])

  String branchName = env.BRANCH_NAME
  boolean isNotPR = !env.CHANGE_ID

  properties [
    disableConcurrentBuilds(),
  ]

  node {
    ansiColor {
      stage('Checkout') {
        List<Map<String, ? extends Serializable>> extensions = [
          [$class: 'WipeWorkspace'],
          [$class: 'CloneOption', noTags: false, shallow: true /* TOTEST */],
        ]
        if (isNotPR) {
          extensions.add([$class: 'LocalBranch', localBranch: branchName])
        }
        checkout([
          $class: 'GitSCM',
          branches: scm.branches,
          doGenerateSubmoduleConfigurations: scm.doGenerateSubmoduleConfigurations,
          extensions: extensions,
          userRemoteConfigs: scm.userRemoteConfigs,
        ])
        gitAuthor()
      }

      withEnv([
        "ARTIFACTORY_URL=${ Artifactory.server('FIDATA').url }",
      ]) {
        timeout(time: timeouts.getOrDefault('Release', 1), unit: 'MINUTES') {
          withCredentials([
            usernameColonPassword(credentialsId: 'Artifactory', variable: 'ARTIFACTORY_USERNAME_PASSWORD')
          ]) {
            withNodeJs {
              exec 'sudo npm install -g @semantic-release/exec'
              withComposer {
                stage('Release') {
                  exec 'npx semantic-release'
                }
              }
            }
          }
        }
      }
    }
  }
}
