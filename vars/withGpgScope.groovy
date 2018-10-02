#!/usr/bin/env groovy
/*
 * withGpgScope Jenkins Pipeline step
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
import static org.fidata.gpg.GpgUtils.GPG_CONF_FILE_NAME
import static org.fidata.gpg.GpgUtils.GPG_AGENT_CONF_FILE_NAME
import static org.fidata.gpg.GpgUtils.getKeyUsages
import com.cloudbees.plugins.credentials.CredentialsProvider
import org.jenkinsci.plugins.plaincredentials.FileCredentials
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
@Grab('org.bouncycastle:bcpg-jdk15on:[1, 2[')
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.PGPKeyFlags
import java.util.regex.Matcher
@Grab('com.github.zafarkhaja:java-semver:[0, 1[')
import com.github.zafarkhaja.semver.Version
import com.github.zafarkhaja.semver.ParseException
// com.google.common.hash.HashCode is @Beta (unstable)
@Grab('commons-codec:commons-codec:[1, 2[')
import org.apache.commons.codec.binary.Hex

/**
 * Gets GPG version as String, e.g. {@code 1.2.3}
 * @return GPG version
 */
String getGpgVersion() {
  String gpgVersionOutput = exec('gpg --version', true)
  (gpgVersionOutput =~ /^gpg \(GnuPG\) (\S+)/).with { Matcher matcher ->
    matcher.find() ? matcher.group(1) : null
  }
}

/**
 * Determines GPG home directory.
 * Doesn't check whether GPG is actually installed and doesn't try to run it.
 * If no explicit configuration is found it just assumes defaults
 *
 * @return GPG home directory
 */
String getGpgHome() {
  /*
   * WORKAROUND:
   * This should be done in fidata_build_toolset.
   * See https://github.com/FIDATA/infrastructure/issues/84
   * <grv87 2018-09-20>
   */
  if (isUnix()) {
    lock('apt-get install groovy') {
      sh 'sudo apt-get --assume-yes install groovy'
    }
  }

  String getGpgHomeFilename = 'getGpgHome.groovy'
  ws {
    writeFile file: getGpgHomeFilename, text: libraryResource("org/fidata/gpg/$getGpgHomeFilename"), encoding: 'UTF-8'
    exec "groovy $getGpgHomeFilename", true
  }
}

void call(String gpgScope, String keyCredentialId, String passphraseCredentialId, Closure body) {
  try {
    /*
     * WORKAROUND:
     * We need at least GnuPG 2.1.13 to have --create-socketdir argument,
     * and Ubuntu backports don't have gnupg2 package
     *
     * This should be done in fidata_build_toolset.
     * See https://github.com/FIDATA/infrastructure/issues/83
     * <grv87 2018-09-20>
     */
    echo 'Determining installed GnuPG version...'
    lock('gpg --version') {
      Boolean isGpgInstalled
      try {
        isGpgInstalled = Version.valueOf(getGpgVersion())?.greaterThanOrEqualTo(Version.forIntegers(2, 1, 13))
      } catch (ParseException ignored) {
        isGpgInstalled = false
      }
      if (!isGpgInstalled) {
        echo 'Installing recent GnuPG version...'
        if (isUnix()) {
          ws {
            String installGnuPGFilename = 'install-gnupg-2.2.10.sh'
            writeFile file: installGnuPGFilename, text: libraryResource("org/fidata/gpg/$installGnuPGFilename"), encoding: 'UTF-8'
            sh """\
              chmod +x $installGnuPGFilename
              sudo --set-home bash ./$installGnuPGFilename
            """.stripIndent()
          }
        } else {
          throw new UnsupportedOperationException('Installation of GnuPG under Windows is not supported yet')
        }
      }
    }

    echo 'Looking for signing GPG key in credentials...'
    FileCredentials credentials = CredentialsProvider.findCredentialById(
      keyCredentialId,
      FileCredentials,
      currentBuild.rawBuild
    )
    String fingerprint = /*HashCode.fromBytes(*/Hex.encodeHexString(
      new PGPSecretKeyRing(
        PGPUtil.getDecoderStream(credentials.content),
        new JcaKeyFingerprintCalculator()
      ).find { PGPSecretKey key ->
        !key.privateKeyEmpty &&
        getKeyUsages(key).contains(PGPKeyFlags.CAN_SIGN)
      }.publicKey.fingerprint
    )/*.toString()*/.toUpperCase()
    echo "Found GPG key $fingerprint"

    echo 'Creating GPG scope directory and set permissions...'
    withEnv([
      "GNUPGHOME=$gpgScope"
    ]) {
      if (isUnix()) {
        sh '''\
          mkdir --parents "$GNUPGHOME"
          chmod 0700 "$GNUPGHOME"
        '''.stripIndent()
      } else {
        batch '''
          md "%GNUPGHOME%"
          icacls "%GNUPGHOME%" /inheritance:r /grant:r "%USERNAME%":(OI)(CI)F
        '''.stripIndent()
      }
    }

    echo 'Getting GPG home...'
    String originalGpgHome = getGpgHome()

    echo 'Copying existing GPG configuration files to GPG scope...'
    withEnv([
      "GNUPGHOME=$gpgScope",
      "ORIGINAL_GNUPGHOME=$originalGpgHome"
    ]) {
      if (isUnix()) {
        sh '''\
          if [ -e "$ORIGINAL_GNUPGHOME/*.conf" ]
            then cp "$ORIGINAL_GNUPGHOME/*.conf" "$GNUPGHOME"
          fi
        '''.stripIndent()
      } else {
        batch '''\
          IF EXIST "%ORIGINAL_GNUPGHOME%\\*.conf" { 
            COPY "%ORIGINAL_GNUPGHOME%\\*.conf" "%GNUPGHOME%"
          }
        '''.stripIndent()
      }
    }

    /*
     * WORKAROUND:
     * This should be done in fidata_build_toolset.
     * See https://github.com/FIDATA/infrastructure/issues/83
     * <grv87 2018-09-20>
     */
    echo 'Adding GPG configuration options for unattended GPG usage...'
    dir(gpgScope) {
      exec """\
        (
          echo use-agent
          echo pinentry-mode loopback
          echo batch
          echo no-tty
          echo yes
        ) >> $GPG_CONF_FILE_NAME
        (
          echo allow-loopback-pinentry
          echo allow-preset-passphrase
        ) >> $GPG_AGENT_CONF_FILE_NAME
      """.stripIndent()
    }

    withEnv([
      "GNUPGHOME=$gpgScope"
    ]) {
      /*
       * WORKAROUND:
       * error `gpg-agent[...]: socket name '.../.scoped-gpg/S.gpg-agent' is too long
       * CAVEAT:
       * --create-socketdir requires gpg >= 2.1.13
       * <grv87 2018-09-09>
       */
      if (isUnix()) {
        echo 'Configuring GPG agent to use /run-based socket...'
        sh '''\
          mkdir --parents /var/run/user/$(id -u)
          gpgconf --create-socketdir
        '''.stripIndent()
      }

      withCredentials([string(credentialsId: passphraseCredentialId, variable: 'GPG_KEY_PASSPHRASE')]) {
        echo 'Importing GPG key...'
        ws {
          withCredentials([file(credentialsId: keyCredentialId, variable: 'GPG_KEY_FILE')]) {
            exec "gpg --passphrase \"${ e('GPG_KEY_PASSPHRASE') }\" --import \"${ e('GPG_KEY_FILE') }\""
          }
        }

        echo 'Exporting GPG secret keys into legacy secring...'
        dir(gpgScope) {
          exec "gpg --passphrase \"${ e('GPG_KEY_PASSPHRASE') }\" --output secring.gpg --export-secret-keys"
        }
      }

      body(fingerprint)
    }
  } finally {
    withEnv([
      "GNUPGHOME=$gpgScope"
    ]) {
      echo 'Turning off scoped GPG services...'
      tryExec 'gpgconf --kill all'

      echo 'Cleaning up GPG scope directory...'
      if (isUnix()) {
        try {
          sh 'rm --recursive --force "$GNUPGHOME"'
        } catch (ignored) { }
      } else {
        try {
          batch 'rmdir /s /q "%GNUPGHOME%"'
        } catch (ignored) { }
      }
    }
  }
}
