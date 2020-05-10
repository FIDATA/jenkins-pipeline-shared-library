#!/usr/bin/env groovy
/*
 * withPhp Jenkins Pipeline step
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

/**
 * Gets PHP version as String, e.g. {@code 1.2.3}
 * @return PHP version
 */
String getPhpVersion() {
  final String phpVersionOutput = exec('php --version', true)
  echo phpVersionOutput
  (phpVersionOutput =~ /^PHP (\S+)/).with { Matcher matcher ->
    matcher.find() ? matcher.group(1) : null
  }
}

void call(Closure body) {
  /*
   * This should be done in fidata_build_toolset.
   * <grv87 2018-09-20>
   */
  echo 'Determining installed PHP version...'
  lock('php --version') { ->
    Boolean isPhpInstalled
    try {
      isPhpInstalled = Version.valueOf(getPhpVersion())?.greaterThanOrEqualTo(Version.forIntegers(7, 0, 0))
    } catch (AbortException | IllegalArgumentException | ParseException ignored) {
      isPhpInstalled = false
    }
    if (!isPhpInstalled) {
      echo 'Installing recent PHP version...'
      if (isUnix()) {
        sh 'sudo apt-get --assume-yes install php7.0-cli php7.0-mbstring php7.0-zip php-ctype' // TOTEST // TODO: Move list of extensions to parameter ?
      } else {
        throw new UnsupportedOperationException('Installation of PHP under Windows is not supported yet')
      }
    }
  }

  body()
}
