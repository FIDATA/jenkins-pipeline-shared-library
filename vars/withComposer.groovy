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

/**
 * Gets Composer version as String, e.g. {@code 1.2.3}
 * @return Composer version
 */
String getComposerVersion() {
  String composerVersionOutput = exec('node --version', true)
  (composerVersionOutput =~ /^Composer version (\S+)/).with { Matcher matcher ->
    matcher.find() ? matcher.group(1) : null
  }
}

void call(Closure body) {
  /*
   * This should be done in fidata_build_toolset.
   * <grv87 2018-09-20>
   */
  echo 'Determining installed GnuPG version...'
  lock('node --version') {
    Boolean isComposerInstalled
    try {
      isGpgInstalled = Version.valueOf(getComposerVersion())?.greaterThanOrEqualTo(Version.forIntegers(1, 0, 0))
    } catch (IllegalArgumentException | ParseException ignored) {
      isGpgInstalled = false
    }
    if (!isComposerInstalled) {
      echo 'Installing recent Composer version...'
      if (isUnix()) {
        ws {
          String installComposerFilename = 'install-composer.sh'
          writeFile file: installComposerFilename, text: libraryResource("org/fidata/gpg/$installComposerFilename"), encoding: 'UTF-8'
          sh """\
            chmod +x $installComposerFilename
            sudo --set-home bash ./$installComposerFilename
          """.stripIndent()
        }
      } else {
        throw new UnsupportedOperationException('Installation of Composer under Windows is not supported yet')
      }
    }
  }

  body.call()
}
