void call() {
  lock('git config --global') {
    exec 'git config --global user.name "FIDATA Jenkins"'
    exec 'git config --global user.email jenkins@fidata.org'
  }
}
