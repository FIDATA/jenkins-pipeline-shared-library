import static org.fidata.artifactory.ArtifactoryUtils.provideCredentials
import org.jfrog.hudson.pipeline.common.types.ArtifactoryServer

void call(String serverId, boolean deployment, Closure body) {
  ArtifactoryServer server = Artifactory.server(serverId)
  provideCredentials server, deployment, currentBuild.rawBuild

  body.call(server)
}
