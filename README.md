# Tor repackager

A utility project that allows repackaging the Tor, obfs4proxy and snowflake
binaries from the Tor expert bundle and Tor browser as Java libraries to
Maven so that they can be consumed as Maven artifacts from other projects.

## Usage

Run this project on Linux. To prepare for execution, run this:

    ./gradlew clean createRuntime

Afterwards, you can run either of two scripts:

    ./scripts/create-or-update-projects-expert-bundle
    ./scripts/create-or-update-projects-tor-browser

The first one will download the expert bundle for each OS (macOS, Windows,
Linux) and repackage them into projects within the `projects` subdirectory
of this repository. It uses the `template` project as a blueprint to set up
each project, replacing some variables in the Gradle files.
For each project created that way, it will run the Gradle task to publish
its artifacts to Maven Central or to a local flat file repository located
in the `maven` subdirectory of this repository.

The second script does the same, except it only works on the macOS binaries
and it doesn't use the tor expert bundle.
Instead, it uses the binaries shipped with Tor browser for macOS.

By default, both tasks publish to the local flat file repository in `maven`.
To change the behavior to publish to Maven Central, edit the file
`template/build.gradle` and remove the stanza that defines the custom
repository location at the end of the file. Instead set things up to upload
to Maven Central (add username and password or other authentication method).
