#!/usr/bin/env groovy
/*
 * withComposer Jenkins Pipeline step
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

import com.cloudbees.plugins.credentials.CredentialsProvider
@Grab('com.github.zafarkhaja:java-semver:[0, 1[')
import com.github.zafarkhaja.semver.ParseException
@Grab('com.github.zafarkhaja:java-semver:[0, 1[')
import com.github.zafarkhaja.semver.Version
import hudson.AbortException
import hudson.util.Secret
import java.nio.file.Paths
import java.util.regex.Matcher
import org.jenkinsci.plugins.plaincredentials.StringCredentials

/**
 * Gets Composer version as String, e.g. {@code 1.2.3}
 * @return Composer version
 */
String getComposerVersion() {
  final String composerVersionOutput = exec('composer --version', true)
  echo composerVersionOutput
  (composerVersionOutput =~ /^Composer version (\S+)/).with { Matcher matcher ->
    matcher.find() ? matcher.group(1) : null
  }
}

void call(String artifactoryServerId, String githubCredentialId, Closure body) {
  /*
   * This should be done in fidata_build_toolset.
   * <grv87 2018-09-20>
   */
  withPhp { ->
    echo 'Determining installed Composer version...'
    lock('composer --version') { ->
      Boolean isComposerInstalled
      try {
        isComposerInstalled = Version.valueOf(getComposerVersion())?.greaterThanOrEqualTo(Version.forIntegers(1, 0, 0))
      } catch (AbortException | IllegalArgumentException | ParseException ignored) {
        isComposerInstalled = false
      }
      if (!isComposerInstalled) {
        echo 'Installing recent Composer version...'
        if (isUnix()) {
          ws { ->
            String installComposerFilename = 'install-composer.sh'
            writeFile file: installComposerFilename, text: libraryResource("org/fidata/composer/$installComposerFilename"), encoding: 'UTF-8'
            sh """\
              chmod +x $installComposerFilename
              sudo --set-home bash ./$installComposerFilename
              sudo mv composer.phar /usr/local/bin/composer
            """.stripIndent()
          }
        } else {
          throw new UnsupportedOperationException('Installation of Composer under Windows is not supported yet')
        }
      }
    }
    withScope('Composer', 'dir', Paths.get('.composer') /* TOTHINK */, 'COMPOSER_HOME', body) { ->
      withArtifactory(artifactoryServerId, 'ARTIFACTORY_URL', 'ARTIFACTORY_USERNAME', 'ARTIFACTORY_PASSWORD', false) { ->
        final URL url = new URL(env.ARTIFACTORY_URL)

        echo "Writing $env.COMPOSER_HOME/config.json..."
        final Map<String, ?> config = [
          repositories: [
            [
              type: 'composer',
              url: "$url/api/composer/composer-local",
            ],
            [
              type: 'composer',
              url: "$url/api/composer/org.packagist",
            ],
            [
              packagist: false,
            ],
          ],
        ]
        writeJSON file: "$env.COMPOSER_HOME/config.json", json: config

        echo "Writing $env.COMPOSER_HOME/auth.json..."
        StringCredentials credentials = CredentialsProvider.findCredentialById(
          githubCredentialId,
          StringCredentials,
          currentBuild.rawBuild
        )
        final Map<String, ?> auth = [
          'http-basic': [
            (url.host): [
              'username': env.ARTIFACTORY_USERNAME,
              'password': env.ARTIFACTORY_PASSWORD,
            ],
          ],
          'github-oauth': [
            'github.com': Secret.toString(credentials.secret),
          ],
        ]
        writeJSON file: "$env.COMPOSER_HOME/auth.json", json: auth
    }
  }
}
