# Releasing

1. Update the `VERSION_NAME` in `gradle.properties` to the release version.

2. Update the `CHANGELOG.md`:
   1. Change the `Unreleased` header to the release version.
   2. Add a link URL to ensure the header link works.
   3. Add a new `Unreleased` section to the top.

3. Update the `README.md`:
   1. Change the "Download" section to reflect the new release version.
   2. Change the snapshot section to reflect the next "SNAPSHOT" version, if it is changing.

4. Commit

   ```
   $ git commit -am "Prepare version X.Y.X"
   ```

5. Tag

   ```
   $ git tag -am "Version X.Y.Z" X.Y.Z
   ```

6. Update the `VERSION_NAME` in `gradle.properties` to the next "SNAPSHOT" version.

7. Commit

   ```
   $ git commit -am "Prepare next development version"
   ```

8. Push!

   ```
   $ git push && git push --tags
   ```

   This will trigger a GitHub Action workflow which will create a GitHub release and upload the
   artifacts to Maven Central. The workflow fails if the tag does not match `VERSION_NAME`.

9. Publish the deployment by hand at [central.sonatype.com](https://central.sonatype.com/publishing/deployments).

   When the `MAVEN_CENTRAL_RELEASE` repository variable is `true`, the workflow publishes it
   automatically instead.

## Snapshots

Every push to `master` publishes a snapshot of `VERSION_NAME` to the Central Portal Snapshots
repository when the `MAVEN_CENTRAL_SNAPSHOTS` repository variable is `true`. Snapshots must first be
enabled for the `org.unitedsoftware` namespace in the Central Portal.
