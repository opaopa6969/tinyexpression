# prepare maven setteings.xml for sonatype uploading

https://support.sonatype.com/hc/en-us/articles/360049469534-401-Content-access-is-protected-by-token-when-accessing-repositories


get user token from https://oss.sonatype.org/#profile;User%20Token and paste to settings.xml

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0" 
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">   <servers> 
          :
          :
   <server>
    <id>ossrh</id>
      <username>+5sgetXb</username>
      <password>fadgf+YdsokwlasffarRPz1OlO8B5uk92adjsdf741sgf2sdgfY</password>
    </server>
  </servers> 
```

# deploy commands

```console
# set maven opts for nexus-staging-maven-plugin
source setMavenOpts.sh

# import pgp key for signing
gpg2 --import /cygdrive/c/Dropbox/key/pgp4sonatype-unlaxer/secring.gpg
sdk use java  17-open
mvn clean deploy
```
