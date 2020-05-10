void call(final List<Map<String, String>> varAndPasswordList, final Closure closure) {
  wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: varAndPasswordList]) { ->
    withEnv(varAndPasswordList.collect { "${it.var}=${it.password}" }) { ->
      closure()
    }
  }
}
