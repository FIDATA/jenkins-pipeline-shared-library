jenkins-pipeline-shared-library
===============================

Shared utils for Jenkins Pipelines for FIDATA projects.

Pipelines:
*   `defaultJvmPipeline` — default pipeline to build JVM projects
    with Gradle

Steps:

*   `exec` — Executes command using method appropriate for this node

*   `tryExec` — Executes command using method appropriate for this node
    ignoring its exit code

*   `e` — Adds prefix and/or suffix appropriate for this node
    to environment variable name so that it can be used in command line

*   `withGpgScope` — Creates temporal (scoped) GPG home
    and imports GPG key from specified Jenkins credential there.

    Original idea goes to https://github.com/indutny/scoped-gpg


------------------------------------------------------------------------
Copyright © 2018  Basil Peace

This file is part of jenkins-pipeline-shared-library.

Copying and distribution of this file, with or without modification,
are permitted in any medium without royalty provided the copyright
notice and this notice are preserved.  This file is offered as-is,
without any warranty.
