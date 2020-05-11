#!/usr/bin/env groovy
/*
 * withNodeJs Jenkins Pipeline step
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
import groovy.text.StreamingTemplateEngine
import hudson.AbortException
import java.nio.file.Paths
import java.util.regex.Matcher

/**
 * Gets NodeJS version as String, e.g. {@code 1.2.3}
 * @return NodeJS version
 */
String getNodeJsVersion() {
  final String nodeJsVersionOutput = exec('node --version', true)
  echo nodeJsVersionOutput
  (nodeJsVersionOutput =~ /^v(\S+)/).with { Matcher matcher ->
    matcher.find() ? matcher.group(1) : null
  }
}

void call(String artifactoryServerId, boolean deployment = false, Closure body) {
  /*
   * This should be done in fidata_build_toolset.
   * <grv87 2018-09-20>
   */
  echo 'Determining installed Node.js version...'
  lock('node --version') { ->
    Boolean isNodeJsInstalled
    try {
      isNodeJsInstalled = Version.valueOf(getNodeJsVersion())?.greaterThanOrEqualTo(Version.forIntegers(10, 0, 0))
    } catch (AbortException | IllegalArgumentException | ParseException ignored) {
      isNodeJsInstalled = false
    }
    if (!isNodeJsInstalled) {
      echo 'Installing recent Node.js version...'
      if (isUnix()) {
        sh '''\
          curl -sL https://deb.nodesource.com/setup_10.x | sudo -E bash -
          sudo apt-get --assume-yes install nodejs
        '''.stripIndent()
      } else {
        throw new UnsupportedOperationException('Installation of Node.js under Windows is not supported yet')
      }
    }
  }

  withScope('NPM', 'file', Paths.get('.npmrc'), 'NPM_CONFIG_USERCONFIG', body) { ->
    echo "Writing $env.NPM_CONFIG_USERCONFIG..."
    withArtifactory(artifactoryServerId, 'ARTIFACTORY_URL', 'ARTIFACTORY_USER', 'ARTIFACTORY_PASSWORD', deployment) { ->
      writeFile file: env.NPM_CONFIG_USERCONFIG,
        text: new StreamingTemplateEngine().createTemplate(libraryResource('org/fidata/scope/.npmrc.template')).make(
          url: new URL(env.ARTIFACTORY_URL),
          artifactoryUsername: env.ARTIFACTORY_USER, // TODO: Deployer credentials may be needed
          artifactoryPasswordBase64: env.ARTIFACTORY_PASSWORD.bytes.encodeBase64().toString(),
          email: 'jenkins@fidata.org', // TODO
        ),
        encoding: 'UTF-8'
    }
  }
}
