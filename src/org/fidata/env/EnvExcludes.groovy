package org.fidata.env

// @Grab('com.google.guava:guava:29.0-jre')
import com.google.common.collect.ImmutableList
import groovy.transform.CompileStatic

/*
 * TODO:
 * Move these filters into separate library
 * <grv87 2018-09-22>
 */
@CompileStatic
final class EnvExcludes {
  public static final List<String> EXCLUDES = new ImmutableList.Builder<String>()
    .add('*Password')
    .add('*Passphrase')
    .add('*SecretKey')
    .add('*SECRET_KEY')
    .add('*APIKey')
    .add('*_API_KEY')
    .add('*gradlePluginsKey')
    .add('*gradlePluginsSecret')
    .add('*OAuthClientSecret')
    .add('*Token')
    .add('*_AUTH') // COMPOSER_AUTH
    .build()

  private EnvExcludes() {
    throw new UnsupportedOperationException()
  }
}
