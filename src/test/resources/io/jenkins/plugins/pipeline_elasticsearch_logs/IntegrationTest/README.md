# Readme

The files in this folder are used by the tests with the same method names as the files.
There are currently three kinds of files supported:

- `<testName>.Jenkinsfile`
  contains the Pipeline to be executed by the test

- `<testName>.log`
  contains the expected plaintext log

- `<testName>.log.json`
  contains a JSON array with all expected entries sent to Elasticsearch.
  In values regular expressions can be used as strings starting and ending with '/', e.g. "/Hello .*/"
  "/.*/" matches anything, including complex objects, not only a string value.

## Hint

[JSONUtils.java](/src/test/java/io/jenkins/plugins/pipeline_elasticsearch_logs/testutils/JSONUtils.java) contains a method `writeTestResourceContent(...)` which allows to write a `<testName>.log.json` file based on actual Elasticsearch entries.
Simply debug-break at the end of your test and execute the following command:

```
io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.JSONUtils.writeTestResourceContent(
  new java.io.File("testResource.json"), mockWriter.getEntries())
```