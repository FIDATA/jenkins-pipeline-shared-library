#!/usr/bin/env groovy
/*
 * getGpgHome script
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
package org.fidata.gpg

@Grab('org.apache.commons:commons-lang3:[3, 4[')
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS
@Grab('net.java.dev.jna:jna-platform:[4, 5[')
import com.sun.jna.platform.win32.Advapi32Util
@Grab('net.java.dev.jna:jna-platform:[4, 5[')
import com.sun.jna.platform.win32.WinReg
@Grab('net.java.dev.jna:jna-platform:[4, 5[')
import com.sun.jna.platform.win32.Win32Exception
// TODO: unable to resolve class groovy.transform.CompileStatic
// import groovy.transform.CompileStatic
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Utils to work with GPG
 */
// @CompileStatic
final class GpgUtils {
  /**
   * Determines GPG home directory.
   * Doesn't check whether GPG is actually installed and doesn't try to run it.
   * if no explicit configuration is found it just assumes defaults
   *
   * @return GPG home directory
   */
  static final Path getGpgHome() {
    if (System.getenv().containsKey('GNUPGHOME')) {
      return Paths.get(System.getenv()['GNUPGHOME'])
    }

    if (IS_OS_WINDOWS) {
      try {
        String path = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, 'Software\\GNU\\GnuPG', 'HomeDir')
        if (path) {
          return Paths.get(path)
        }
      } catch (Win32Exception ignored) { } // TODO: check error codes and ignore non-existence only

      Path path = Paths.get(System.getenv()['APPDATA'], 'GnuPg')
      if (!path.toFile().exists()) {
        Path path2 = Paths.get(System.getenv()['USERPROFILE'], '.gnupg')
        if (path2.toFile().exists()) {
          // Old version of GnuPG under Windows (MinGW?) ? // TODO: log warning
          return path2
        }
      }
      return path
    }

    Paths.get(System.getenv()['HOME'], '.gnupg')
  }

  private GpgUtils() {
    throw new UnsupportedOperationException()
  }
}

print GpgUtils.getGpgHome()
