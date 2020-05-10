#!/usr/bin/env groovy
/*
 * withArtifactory Jenkins Pipeline step
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

import static org.jfrog.hudson.util.RepositoriesUtils.getArtifactoryServers
@Grab('com.github.zafarkhaja:java-semver:[0, 1[')
import com.github.zafarkhaja.semver.ParseException
@Grab('com.github.zafarkhaja:java-semver:[0, 1[')
import com.github.zafarkhaja.semver.Version
import hudson.AbortException
import java.util.regex.Matcher
import org.jenkinsci.plugins.credentialsbinding.MultiBinding
import org.jfrog.hudson.ArtifactoryServer
import org.jfrog.hudson.CredentialsConfig
import org.jfrog.hudson.util.Credentials
import org.jfrog.hudson.util.plugins.PluginsUtils

/**
 * Gets Artifactory version as String, e.g. {@code 1.2.3}
 * @return Artifactory version
 */
String getJFrogCliVersion() {
  final String jfrogCliVersionOutput = exec('jfrog --version', true)
  echo jfrogCliVersionOutput
  (jfrogCliVersionOutput =~ /^jfrog version (\S+)/).with { Matcher matcher ->
    matcher.find() ? matcher.group(1) : null
  }
}

void call(String scopeDir, String serverId, Closure body) {
  /*
   * This should be done in fidata_build_toolset.
   * <grv87 2018-09-20>
   */
  try {
    echo 'Determining installed JFrog CLI version...'
    lock('jfrog --version') { ->
      Boolean isJFrogCliInstalled
      try {
        isJFrogCliInstalled = Version.valueOf(getJFrogCliVersion())?.greaterThanOrEqualTo(Version.forIntegers(1, 0, 0))
      } catch (AbortException | IllegalArgumentException | ParseException ignored) {
        isJFrogCliInstalled = false
      }
      if (!isJFrogCliInstalled) {
        echo 'Installing recent JFrog CLI version...'
        if (isUnix()) {
          ws { ->
            sh 'curl -fL https://getcli.jfrog.io | sudo sh'
          }
        } else {
          throw new UnsupportedOperationException('Installation of JFrog CLI under Windows is not supported yet')
        }
      }
    }

    final ArtifactoryServer server = getArtifactoryServers().find { ArtifactoryServer server ->
      server.name == serverId
    }
    if (server == null) {
      throw new IllegalArgumentException(String.format('Server named %s not found', serverId))
    }

    final CredentialsConfig deployerCredentialsConfig = server.deployerCredentialsConfig
    final List<Map<String, String>> secretEnv = []
    final List<MultiBinding<?>> credentialBindings = []
    if (PluginsUtils.credentialsPluginEnabled) {
      credentialBindings.add usernamePassword(credentialsId: deployerCredentialsConfig.credentialsId, usernameVariable: 'ARTIFACTORY_USER', passwordVariable: 'ARTIFACTORY_PASSWORD')
    } else {
      Credentials credentials = deployerCredentialsConfig.provideCredentials(currentBuild.rawBuild)
      secretEnv.add[var: 'ARTIFACTORY_USER', password: credentials.username] // TOTHINK
      secretEnv.add[var: 'ARTIFACTORY_PASSWORD', password: credentials.password]
    }

    withEnv([
      "JFROG_CLI_HOME_DIR=$scopeDir",
      "JFROG_CLI_JCENTER_REMOTE_SERVER=$serverId",
      'JFROG_CLI_JCENTER_REMOTE_REPO=com.bintray.jcenter',
      "ARTIFACTORY_THREADS=$server.deploymentThreads",
      'CI=true', // Disables interactive prompts and progress bar in JFrog CLI (see `jfrog --help`)
      "ARTIFACTORY_SERVER_ID=${ server.name }",
      "ARTIFACTORY_URL=${ server.url }",
    ]) { ->
      echo 'Creating JFrog scope directory and set permissions...'
      if (isUnix()) {
        sh '''\
        mkdir --parents "JFROG_CLI_HOME_DIR"
        chmod 0700 "JFROG_CLI_HOME_DIR"
      '''.stripIndent()
      } else {
        batch '''\
        md "%JFROG_CLI_HOME_DIR%"
        icacls "%JFROG_CLI_HOME_DIR%" /inheritance:r /grant:r "%USERNAME%":(OI)(CI)F
      '''.stripIndent()
      }

      withCredentials(credentialBindings) { ->
        withSecretEnv(secretEnv) { ->
          exec "jfrog rt config --url=${ e('ARTIFACTORY_URL') } --user=${ e('ARTIFACTORY_USER') } --password=${ e('ARTIFACTORY_PASSWORD') } --interactive=false ${ e('ARTIFACTORY_SERVER_ID') }"
          exec "jfrog rt use ${ e('ARTIFACTORY_SERVER_ID') }"
        }
      }

      body.call()
    }
  } finally {
    withEnv([
      "JFROG_CLI_HOME_DIR=$scopeDir"
    ]) { ->
      echo 'Cleaning up JFrog scope directory...'
      if (isUnix()) {
        try {
          sh 'rm --recursive --force "JFROG_CLI_HOME_DIR"'
        } catch (ignored) { }
      } else {
        try {
          batch 'rmdir /s /q "%JFROG_CLI_HOME_DIR%"'
        } catch (ignored) { }
      }
    }
  }
}
