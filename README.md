<h1> :exclamation: This is not ready for productive usage</h1>

Configuration of the plugin may change without further notice in an incompatible way!

### Description

A Jenkins plugin to send Pipeline build logs to an [Elasticsearch](https://www.elastic.co/products/elasticsearch) instance.

This is an [implementation of JEP-210](https://github.com/jenkinsci/jep/blob/master/jep/210/README.adoc).


### Requirements

 - [Jenkins](https://jenkins.io/)
 - [Elasticsearch](https://www.elastic.co/products/elasticsearch)

### Download and Installation

In your Jenkins go to **Manage Jenkins > Manage Plugins > Available** check **Pipeline Logging via Elastic Search** and press **Install without restart**

### Configuration

Go to **Manage Jenkins > System Configuration > Logging to Elasticsearch for Pipelines** and select **Enable**.

### Limitations

Currently the plugin is only able to push the logs to Elasticsearch but the way back to read the logs from ElasticSearch and display in Jenkins is not yet implemented.
The initial scope of this plugin was to use it it in a [JenkinsFileRunner](https://github.com/jenkinsci/jenkinsfile-runner) scenario.


### Known Issues

None

### How to obtain support

Open an issue in [Github](https://github.com/sap/pipeline-elasticsearch-logs-plugin/issues)

### Contributing

Please open a Pull Request in our [Github](https://github.com/sap/elasticsearch-logs-plugin) repository.

#### Code Style

The [Jenkins Beginners Guide to Contribute](https://wiki.jenkins.io/display/JENKINS/Beginners+Guide+to+Contributing#BeginnersGuidetoContributing-CodeStyle) recommends the [Oracle Code Conventions for Java](http://www.oracle.com/technetwork/java/codeconvtoc-136057.html) from 1999.
[Those Guidelines](https://wiki.jenkins.io/display/JENKINS/Code+Style+Guidelines) however might better describe the Code Style rules we apply.

Most relevant rules:
- line width: 140
- indentation: 4 spaces
    - no tabs

### To-Do (upcoming changes)

- Implement a reader from Elastic Search
- Improve http connection handling

### License

Copyright (c) 2019 SAP SE or an SAP affiliate company. All rights reserved.
This project is licensed under "Apache Software License, v. 2" except as noted otherwise in the LICENSE file.
