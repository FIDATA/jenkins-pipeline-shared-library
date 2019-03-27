#!/usr/bin/env groovy
/*
 * Default Jenkins pipeline for JVM projects
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
import org.jfrog.hudson.pipeline.common.types.ArtifactoryServer
import org.jfrog.hudson.pipeline.common.types.packageManagerBuilds.GradleBuild
import org.jfrog.hudson.pipeline.common.types.buildInfo.BuildInfo

void call(
  final Map<String, Object> config
) {
  boolean publicReleases = config['publicReleases']
  Map<String, Integer> timeouts = (Map<String, Integer>)config.getOrDefault('timeouts', [:])
  Set<String> tests = (Set<String>)config.getOrDefault('tests', [].toSet())
  boolean compatTest = config.getOrDefault('compatTest', Boolean.TRUE)
  Set<String> customCodenarcReports = (Set<String>)config.getOrDefault('customCodenarcReports', [].toSet())
  boolean gradlePlugin = config.getOrDefault('gradlePlugin', Boolean.FALSE)

  String projectName = JOB_NAME.split('/')[0]

  properties([
    parameters([
      booleanParam(defaultValue: false, description: 'Whether to release a new version', name: 'shouldRelease'),
    ])
  ])

  node {
    ansiColor {
      GradleBuild rtGradle

      stage('Checkout') {
        List<Map<String, ? extends Serializable>> extensions = [
          [$class: 'WipeWorkspace'],
          [$class: 'CloneOption', noTags: false, shallow: false],
        ]
        if (!env.CHANGE_ID) {
          extensions.add([$class: 'LocalBranch', localBranch: env.BRANCH_NAME])
        }
        checkout([
          $class: 'GitSCM',
          branches: scm.branches,
          doGenerateSubmoduleConfigurations: scm.doGenerateSubmoduleConfigurations,
          extensions: extensions,
          userRemoteConfigs: scm.userRemoteConfigs,
        ])
        gitAuthor()
        configureGrape('Artifactory')
      }

      ArtifactoryServer server = Artifactory.server 'FIDATA'
      rtGradle = Artifactory.newGradleBuild()
      rtGradle.useWrapper = true
      rtGradle.usesPlugin = true

      boolean alwaysLinkReportsToLastBuild = env.BRANCH_NAME == 'master' && !env.CHANGE_ID

      /*
       * WORKAROUND:
       * Disabling Gradle Welcome message
       * should be done in fidata_build_toolset.
       * See https://github.com/FIDATA/infrastructure/issues/85
       * <grv87 2018-09-21>
       */
      /*
       * WORKAROUND:
       * Gradle can't provide console with colors but no other rich features.
       * So, we use plain console for now
       * https://github.com/gradle/gradle/issues/6843
       * <grv87 2018-09-21>
       */
      /*
       * WORKAROUND:
       * Build cache should be turned on in gradle.properties
       * as soon as we move sensitive properties to separate place
       * and put gradle.properties under version control
       * <grv87 2018-09-22>
       */
      String gradleSwitches = '-Dorg.gradle.internal.launcher.welcomeMessageEnabled=false --no-daemon --parallel --configure-on-demand --console=plain --info --warning-mode all --full-stacktrace --build-cache'

      withGpgScope("${ pwd() }/.scoped-gpg", 'GPG', 'GPG_KEY_PASSWORD') { String fingerprint ->
        withEnv([
          "ORG_GRADLE_PROJECT_shouldRelease=$params.shouldRelease",
          "ORG_GRADLE_PROJECT_gpgKeyId=$fingerprint",
        ]) {
          List credentials = [
            usernamePassword(credentialsId: 'Github 2', usernameVariable: 'ORG_GRADLE_PROJECT_gitUsername', passwordVariable: 'ORG_GRADLE_PROJECT_gitPassword'),
            string(credentialsId: 'Github', variable: 'ORG_GRADLE_PROJECT_ghToken'),
            usernamePassword(credentialsId: 'Artifactory', usernameVariable: 'ORG_GRADLE_PROJECT_artifactoryUser', passwordVariable: 'ORG_GRADLE_PROJECT_artifactoryPassword'),
            string(credentialsId: 'GPG_KEY_PASSWORD', variable: 'ORG_GRADLE_PROJECT_gpgKeyPassphrase'),
          ]
          if (publicReleases) {
            credentials.add usernamePassword(credentialsId: 'Bintray', usernameVariable: 'ORG_GRADLE_PROJECT_bintrayUser', passwordVariable: 'ORG_GRADLE_PROJECT_bintrayAPIKey')
            if (gradlePlugin) {
              credentials.add usernamePassword(credentialsId: 'Gradle Plugins', usernameVariable: 'ORG_GRADLE_PROJECT_gradlePluginsKey', passwordVariable: 'ORG_GRADLE_PROJECT_gradlePluginsSecret')
            }
          }
          withCredentials(credentials) {
            BuildInfo buildInfo = null
            try {
              stage('Generate Changelog') {
                timeout(time: timeouts.getOrDefault('Generate Changelog', 5), unit: 'MINUTES') {
                  buildInfo = rtGradle.run tasks: 'generateChangelog', switches: gradleSwitches, buildInfo: buildInfo
                  /*
                   * TODO:
                   * Move these filters into separate library
                   * <grv87 2018-09-22>
                   */
                  buildInfo.env.filter.clear()
                  buildInfo.env.filter.addExclude('*Password')
                  buildInfo.env.filter.addExclude('*Passphrase')
                  buildInfo.env.filter.addExclude('*SecretKey')
                  buildInfo.env.filter.addExclude('*SECRET_KEY')
                  buildInfo.env.filter.addExclude('*APIKey')
                  buildInfo.env.filter.addExclude('*_API_KEY')
                  buildInfo.env.filter.addExclude('*gradlePluginsKey')
                  buildInfo.env.filter.addExclude('*gradlePluginsSecret')
                  buildInfo.env.filter.addExclude('*OAuthClientSecret')
                  buildInfo.env.filter.addExclude('*Token')
                  buildInfo.env.collect()
                }
                dir('build/changelog') {
                  exec 'pandoc --from=markdown_github --to=html --output=CHANGELOG.html CHANGELOG.md'
                }
                publishHTML(target: [
                  reportName: 'CHANGELOG',
                  reportDir: 'build/changelog',
                  reportFiles: 'CHANGELOG.html',
                  allowMissing: false,
                  keepAll: true,
                  alwaysLinkToLastBuild: alwaysLinkReportsToLastBuild
                ])
              }
              stage('Assemble') {
                try {
                  timeout(time: timeouts.getOrDefault('Assemble', 5), unit: 'MINUTES') {
                    buildInfo = rtGradle.run tasks: 'assemble', switches: gradleSwitches, buildInfo: buildInfo
                  }
                } finally {
                  warnings(
                    consoleParsers: [
                      [parserName: 'Java Compiler (javac)'],
                      [parserName: 'JavaDoc Tool'],
                    ]
                  )
                }
              }
              try {
                stage('Lint') {
                  try {
                    timeout(time: timeouts.getOrDefault('Lint', 5), unit: 'MINUTES') {
                      buildInfo = rtGradle.run tasks: 'lint', switches: "$gradleSwitches --continue".toString(), buildInfo: buildInfo
                    }
                  } finally {
                    Set<String> codenarcReports = [
                      'main',
                      'buildSrc'
                    ]
                    codenarcReports.addAll tests
                    if (compatTest) {
                      codenarcReports.add 'compatTest'
                    }
                    codenarcReports.addAll customCodenarcReports

                    publishHTML(target: [
                      reportName: 'CodeNarc',
                      reportDir: 'build/reports/html/codenarc',
                      reportFiles: codenarcReports.collect { "${ it }.html" }.join(', '), // TODO: read from directory ?
                      allowMissing: true,
                      keepAll: true,
                      alwaysLinkToLastBuild: alwaysLinkReportsToLastBuild
                    ])
                  }
                }
              } finally {
                stage('Test') {
                  try {
                    timeout(time: timeouts.getOrDefault('Test', 5), unit: 'MINUTES') {
                      buildInfo = rtGradle.run tasks: 'check', switches: gradleSwitches, buildInfo: buildInfo
                    }
                  } finally {
                    warnings(
                      consoleParsers: [
                        [parserName: 'Java Compiler (javac)'],
                        [parserName: 'JavaDoc Tool'],
                      ]
                    )
                    junit(
                      testResults: 'build/reports/xml/**/*.xml',
                      allowEmptyResults: true,
                      keepLongStdio: true,
                    )
                    tests.each { String test ->
                      publishHTML(target: [
                        reportName: test.capitalize(),
                        reportDir: "build/reports/html/$test".toString(),
                        reportFiles: 'index.html',
                        allowMissing: true,
                        keepAll: true,
                        alwaysLinkToLastBuild: alwaysLinkReportsToLastBuild
                      ])
                    }
                    if (compatTest) {
                      publishHTML(target: [
                        reportName: 'CompatTest',
                        reportDir: 'build/reports/html/compatTest',
                        reportFiles:
                          readFile(file: '.stutter/java8.lock', encoding: 'UTF-8') // TODO: respect other Java versions
                            .split('[\r\n]+')
                          // Copy of algorithm from StutterExtension.getLockedVersions
                            .findAll { !it.startsWith('#') }
                            .collect { "${ it.trim() }/index.html" }
                            .join(', '),
                        allowMissing: true,
                        keepAll: true,
                        alwaysLinkToLastBuild: alwaysLinkReportsToLastBuild
                      ])
                    }
                  }
                }
              }
              stage('Release') {
                try {
                  timeout(time: timeouts.getOrDefault('Release', 5), unit: 'MINUTES') {
                    milestone()
                    lock("$projectName@gh-pages".toString()) {
                      buildInfo = rtGradle.run tasks: 'release', switches: gradleSwitches, buildInfo: buildInfo
                    }
                  }
                } finally {
                  warnings(
                    consoleParsers: [
                      [parserName: 'Java Compiler (javac)'],
                      [parserName: 'JavaDoc Tool'],
                    ]
                  )
                }
              }
            } finally {
              server.publishBuildInfo buildInfo
            }
          }
        }
      }
    }
  }
}
