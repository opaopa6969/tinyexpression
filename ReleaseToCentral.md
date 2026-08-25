# Maven Central Publisher Portal release preparation

Never commit Sonatype credentials, user tokens, signing keys, or generated
`settings.xml` files. A credential that has ever been committed must be revoked
and replaced; deleting it from the current file does not remove it from Git
history.

Configure a Central Portal user token outside the repository, for example in
the user's `~/.m2/settings.xml`:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <servers>
    <server>
      <id>central</id>
      <username>${env.CENTRAL_USERNAME}</username>
      <password>${env.CENTRAL_PASSWORD}</password>
    </server>
  </servers>
</settings>
```

Keep the signing key in the local GPG keyring or CI secret store. Run a local
release verification without publishing:

```console
mvn -B clean verify -Dgpg.skip=true
```

The project uses `central-publishing-maven-plugin` with automatic publishing and
waits for the deployment to reach `PUBLISHED`. Run `mvn -B clean deploy` only
after checking the release version, coordinates, signing identity, credentials,
and target account.
