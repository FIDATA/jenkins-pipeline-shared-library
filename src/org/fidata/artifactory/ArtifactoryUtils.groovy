package org.fidata.artifactory

import static org.jfrog.hudson.util.RepositoriesUtils.getArtifactoryServers
import hudson.model.Item
import groovy.transform.CompileStatic
import org.jfrog.hudson.ArtifactoryServer
import org.jfrog.hudson.CredentialsConfig

/**
 * The whole purpose of this class is to get deployer credentials.
 *
 * Since {@link org.jfrog.hudson.pipeline.common.executors.GetArtifactoryServerExecutor} gives us resolving credentials only
 */
@CompileStatic
/*final*/ class ArtifactoryUtils {
  static org.jfrog.hudson.pipeline.common.types.ArtifactoryServer replaceCredentialsWithDeployment(org.jfrog.hudson.pipeline.common.types.ArtifactoryServer artifactoryServer, Item item) {
    /*final String serverId = artifactoryServer.serverName
    final ArtifactoryServer server = getArtifactoryServers().find { ArtifactoryServer server ->
      server.name == serverId
    }
    if (server == null) {
      throw new IllegalArgumentException(String.format('Server named %s not found', serverId))
    }
    final CredentialsConfig credentialsConfig = server.deployerCredentialsConfig
    artifactoryServer.username = credentialsConfig.provideUsername(item)
    artifactoryServer.password = credentialsConfig.providePassword(item)*/
    artifactoryServer
  }

  /*private ArtifactoryUtils() {
    throw new UnsupportedOperationException()
  }*/
}