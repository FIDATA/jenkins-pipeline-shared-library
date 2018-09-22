#!/usr/bin/env groovy
/*
 * GpgUtils class
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
package org.fidata.gpg

import groovy.transform.CompileStatic
@Grab('org.bouncycastle:bcpg-jdk15on:[1, 2[')
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPKeyFlags

/**
 * Utils to work with GPG
 */
@CompileStatic
final class GpgUtils {
  /**
   * GPG configuration file name
   */
  static final String GPG_CONF_FILE_NAME = 'gpg.conf'
  /**
   * GPG agent configuration file name
   */
  static final String GPG_AGENT_CONF_FILE_NAME = 'gpg-agent.conf'

  /**
   * Gets GPG key usages as set of constants.
   * See {@code org.bouncycastle.openpgp.PGPKeyFlags} or {@code org.bouncycastle.openpgp.KeyFlags}
   * for constant values
   *
   * @param key Secret key
   * @return Set of key usages
   */
  // TODO: BouncyCastle could have provided enum of key usages. Then we could use EnumSet
  @NonCPS
  static Set<Integer> getKeyUsages(PGPSecretKey key) {
    int keyFlags = 0

    if (key.publicKey.version >= 4) {
      ((Iterator<PGPSignature>)key.publicKey.signatures).each { PGPSignature signature ->
        keyFlags |= signature.hashedSubPackets.keyFlags
      }
    }
    if (keyFlags == 0) {
      // TODO: log warning about this situation
      keyFlags = Integer.MAX_VALUE
    }
    Set<Integer> keyUsages = []
    [
      PGPKeyFlags.CAN_CERTIFY,
      PGPKeyFlags.CAN_SIGN,
      PGPKeyFlags.CAN_ENCRYPT_COMMS,
      PGPKeyFlags.CAN_ENCRYPT_STORAGE,
      PGPKeyFlags.CAN_AUTHENTICATE,
    ].each { Integer keyFlag ->
      if (keyFlags & keyFlag) {
        keyUsages.add keyFlag
      }
    }
    if (!key.signingKey) {
      keyUsages.remove PGPKeyFlags.CAN_CERTIFY
      keyUsages.remove PGPKeyFlags.CAN_SIGN
    }
    if (!key.publicKey.encryptionKey) {
      keyUsages.remove PGPKeyFlags.CAN_ENCRYPT_STORAGE
      keyUsages.remove PGPKeyFlags.CAN_ENCRYPT_COMMS
    }
    keyUsages
  }

  private GpgUtils() {
    throw new UnsupportedOperationException()
  }
}
