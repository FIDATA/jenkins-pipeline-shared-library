#!/usr/bin/env groovy
/*
 * Jenkins pipeline running semantic-release with Composer
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

void call(final Map<String, ? extends Serializable> config = [:]) {
  try {
    final Map<String, Integer> timeouts = (Map<String, Integer>) config.getOrDefault('timeouts', [:])

    final String branchName = env.BRANCH_NAME
    final boolean isNotPR = !env.CHANGE_ID

    properties[
      disableConcurrentBuilds(),
    ]

    node { ->
      ansiColor { ->
        stage('Checkout') { ->
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

        final String artifactoryServerId = 'FIDATA'
        final String githubCredentialId = 'GitHub'
        final String gpgKeyCredentialId = 'GPG'
        final String gpgPassphraseCredentialId = 'GPG_KEY_PASSWORD'

        withNodeJs(artifactoryServerId) { ->
          exec 'sudo --preserve-env npm install --global semantic-release @fidata/semantic-release-composer-artifactory-plugin'
          withComposer(artifactoryServerId, githubCredentialId) { ->
            withGpg(artifactoryServerId, gpgKeyCredentialId, gpgPassphraseCredentialId) { ->
              withArtifactoryCli(artifactoryServerId) { ->
                stage('Release') { ->
                  timeout(time: timeouts.getOrDefault('Release', 1), unit: 'MINUTES') { ->
                    withCredentials([
                      string(credentialsId: githubCredentialId, variable: 'GITHUB_TOKEN'), // For semantic-release
                    ]) { ->
                      exec 'semantic-release'
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  } finally {
    chuckNorris()
  }
}
