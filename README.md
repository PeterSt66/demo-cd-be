# Demo application for CD - Frontend
This example can be used as a demonstration backend application for Continuous Delivery in a container environment.
See demo-cd-fe

## Installation
Installation is quite easy, first you will have to install some front-end dependencies using Bower:
```
bower install
```

Then you can run Gradle to package the application:
```
mvn clean bootRepackage
```

Now you can run the Java application quite easily:
```
java -jar build/libs/be-1.0.0.jar
```

Alternatively, you could use Spring-boot to start the application in development mode:
```
gradle bootRun
```
## Docker
This project contains a Docker buildfile but it's not build-enabled.

## Jenkins and Openshift
The Jenkinsfile (should be used from a multi-branch job) will build the code, package it and construct a Docker container.
Next it will try to deploy the app to Openshift, in one of two ways:

- Clean deploy - delete any existing deployment and rebuild and redeploy. If the branch != master the build will wait for confirmation (look in Jenkins) and proceeds to delete all. This mimics a setup for a popup instance, usefull for api- and integration testing through curl / soapui / other tooling

- Upgrade existing deployment - only done if it's the master branch and a deployment exists. It will do a rolling update. To force a rebuild delete the deployment config from openshift (not the running deployment, the config)

- Jenkins build depend on the OC binary to be available as /usr/bin/oc
