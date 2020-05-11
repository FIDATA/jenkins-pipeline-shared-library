#!/usr/bin/env groovy
/*
 * withArtifactoryCli Jenkins Pipeline step
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

@Grab('com.github.zafarkhaja:java-semver:[0, 1[')
import com.github.zafarkhaja.semver.ParseException
@Grab('com.github.zafarkhaja:java-semver:[0, 1[')
import com.github.zafarkhaja.semver.Version
import hudson.AbortException
import java.util.regex.Matcher
import org.fidata.env.EnvExcludes
import org.jfrog.hudson.pipeline.common.types.ArtifactoryServer

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

void call(String serverId, boolean deployment = true, Closure body) {
  /*
   * This should be done in fidata_build_toolset.
   * <grv87 2018-09-20>
   */
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

  withArtifactory(serverId, deployment) { ArtifactoryServer server ->
    withEnv([
      "JFROG_CLI_JCENTER_REMOTE_SERVER=$server.serverName",
      'JFROG_CLI_JCENTER_REMOTE_REPO=com.bintray.jcenter',
      "ARTIFACTORY_THREADS=$server.deploymentThreads",
      'CI=true', // Disables interactive prompts and progress bar in JFrog CLI (see `jfrog --help`)
      "JFROG_CLI_ENV_EXCLUDE=${ EnvExcludes.EXCLUDES.join(';') }"
    ]) { ->
      withScope('JFrog', 'dir', scopeDir, 'JFROG_CLI_HOME_DIR', body) { ->
        echo "Configuring JFrog Artifactory CLI..."
          withEnv([
            "$urlEnvVar=$server.url",
          ]) { ->
          withSecretEnv([
            [var: usernameEnvVar, password: server.username], // TOTHINK
            [var: passwordEnvVar, password: server.password],
          ]) { ->
            exec "jfrog rt config --url=${ e('ARTIFACTORY_URL') } --user=${ e('ARTIFACTORY_USER') } --password=${ e('ARTIFACTORY_PASSWORD') } --interactive=false \"$serverId\""
            exec "jfrog rt use \"$serverId\""
          }
        }
      }
    }
  }
}
