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

The Elasticsearch [Java High Level REST Client](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high.html)
used by this plugin [should match](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high-compatibility.html) the Elasticsearch server version in order to prevent unexpected results.

In order to not only support the latest Elasticsearch version, and since we did not experience any issues (client: 6.x, server: 7.x), [we decided](https://github.com/SAP/elasticsearch-logs-plugin/issues/13) to stay on a smaller client major version. If you experience any problems please create a GitHub issue.

### Known Issues

None

### How to obtain support

Open an issue in [Github](https://github.com/sap/pipeline-elasticsearch-logs-plugin/issues)

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

