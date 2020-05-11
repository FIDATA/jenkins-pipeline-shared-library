#!/usr/bin/env groovy
/*
 * withGroovy Jenkins Pipeline step
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
import groovy.text.XmlTemplateEngine
import java.nio.file.Paths

boolean call(String artifactoryServerId, Closure body) {
  /*
   * WORKAROUND:
   * This should be done in fidata_build_toolset.
   * See https://github.com/FIDATA/infrastructure/issues/84
   * <grv87 2018-09-20>
   */
  if (isUnix()) {
    lock('apt-get install groovy2') {
      sh 'sudo apt-get --assume-yes install groovy2'
    }
  } else {
    throw new UnsupportedOperationException('Installing groovy under Windows is not supported yet')
  }

  withScope('Grape', 'file', Paths.get('.groovy/grapeConfig.xml'), 'JAVA_OPTS', new GStringTemplateEngine().createTemplate('-Dgrape.config=$value'), body) { String grapeConfigPath ->
    echo "Writing $grapeConfigPath..."
    withArtifactory(artifactoryServerId, 'ARTIFACTORY_URL', 'ARTIFACTORY_USER', 'ARTIFACTORY_PASSWORD', false) {
      /*
       * WORKAROUND:
       * XmlTemplateEngine removes XML declaration from template
       * <grv87 2018-10-13>
       */
      String encoding = 'UTF-8'
      writeFile file: grapeConfigPath,
        text: """\
          <?xml version='1.0' encoding='$encoding'?>
          ${ new XmlTemplateEngine().createTemplate(libraryResource('org/fidata/scope/grapeConfig.xml.gsp')).make([
            url: new URL(env.ARTIFACTORY_URL),
            username: env.ARTIFACTORY_USER,
            password: env.ARTIFACTORY_PASSWORD,
          ]) }
        """.stripIndent(),
        encoding: encoding
    }
  }
}
