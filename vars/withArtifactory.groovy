import static org.jfrog.hudson.util.RepositoriesUtils.getArtifactoryServers
import org.jenkinsci.plugins.credentialsbinding.MultiBinding
import org.jfrog.hudson.ArtifactoryServer
import org.jfrog.hudson.CredentialsConfig
import org.jfrog.hudson.util.Credentials
import org.jfrog.hudson.util.plugins.PluginsUtils

/**
 * The whole purpose of this step is to get deployer credentials.
 * {@link org.jfrog.hudson.pipeline.common.executors.GetArtifactoryServerExecutor} gives us resolving credentials only
 */
void call(String serverId, String urlEnvVar, String usernameEnvVar, String passwordEnvVar, boolean deployment, Closure body) {
  /*final ArtifactoryServer server = getArtifactoryServers().find { ArtifactoryServer server ->
    server.name == serverId
  }
  if (server == null) {
    throw new IllegalArgumentException(String.format('Server named %s not found', serverId))
  }*/

  // final CredentialsConfig credentialsConfig = deployment ? server.deployerCredentialsConfig : server.resolvingCredentialsConfig
  final List<Map<String, String>> secretEnv = []
  final List<MultiBinding<?>> credentialBindings = []
  /*if (PluginsUtils.credentialsPluginEnabled) {
    credentialBindings.add usernamePassword(credentialsId: credentialsConfig.credentialsId, usernameVariable: usernameEnvVar, passwordVariable: passwordEnvVar)
  } else {
    Credentials credentials = credentialsConfig.provideCredentials(currentBuild.rawBuild)
    secretEnv.add [var: usernameEnvVar, password: credentials.username] // TOTHINK
    secretEnv.add [var: passwordEnvVar, password: credentials.password]
  }*/

  withEnv([
    // "$urlEnvVar=$server.url",
  ]) { ->
    withCredentials(credentialBindings) { ->
      withSecretEnv(secretEnv) { ->
        body.call()
      }
    }
  }
}
