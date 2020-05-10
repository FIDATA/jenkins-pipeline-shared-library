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
import java.util.regex.Matcher
@Grab('com.github.zafarkhaja:java-semver:[0, 1[')
import com.github.zafarkhaja.semver.Version
import com.github.zafarkhaja.semver.ParseException
import hudson.AbortException
import groovy.json.JsonBuilder
import groovy.json.JsonOutput

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
    final ArtifactoryServer server = Artifactory.server(artifactoryServerId)
    final URL url = new URL(server.url)

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
            Map<String, ?> config = [
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
            writeFile file: "${ getHome() }/.composer/config.json", text: JsonOutput.toJson(config), encoding: 'UTF-8'
          }
        } else {
          throw new UnsupportedOperationException('Installation of Composer under Windows is not supported yet')
        }
      }
    }
    // Resolver credentials enough here
    withSecretEnv([
      [var: 'ARTIFACTORY_USERNAME', password: server.username],
      [var: 'ARTIFACTORY_PASSWORD', password: server.password],
    ]) { ->
      withCredentials([
        string(variable: 'GITHUB_TOKEN', credentialsId: githubCredentialId),
      ]) { ->
        final Map<String, ?> composerAuth = [
          'http-basic': [
            (url.host): [
              'username': env.ARTIFACTORY_USERNAME,
              'password': env.ARTIFACTORY_PASSWORD,
            ],
          ],
          'github-oauth': [
            'github.com': env.GITHUB_TOKEN,
          ],
        ]
        withEnv([
          "COMPOSER_AUTH=${ new JsonBuilder(composerAuth).toString() }",
        ]) { ->
          body.call()
        }
      }
    }
  }
}
