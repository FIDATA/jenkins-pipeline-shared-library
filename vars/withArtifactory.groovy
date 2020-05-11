import static org.fidata.artifactory.ArtifactoryUtils.replaceCredentialsWithDeployment
import org.jfrog.hudson.pipeline.common.types.ArtifactoryServer

void call(String serverId, boolean deployment, Closure body) {
  ArtifactoryServer server = Artifactory.server(serverId)
  if (deployment) {
    // TODO: doesn't work yet replaceCredentialsWithDeployment server, currentBuild.rawBuild
  }

  body.call(server)
}
