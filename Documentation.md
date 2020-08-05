# Events
Besides the log lines the plugin will sent additional events with flow node information.
The following table describes the different event types.

| event type | description |
| ----- | ----- |
| flowGraph::buildStart | Sent once at the start of the build |
| flowGraph::buildEnd | Sent once at the end of the build |
| flowGraph::nodeStart | Sent once at the start of  a flow node |
| flowGraph::nodeEnd | Sent once at the end of the a flow node |
| buildMessage | A log line sent from the pipeline execution engine (In Jenkins these are the lines displayed in light grey) |
| nodeMessage | A log output line sent from the execution of a flow node |

### Event types
The following table lists the fields that are sent for each event and for which type of event

| Field | description | buildStart | buildEnd | nodeStart | nodeEnd | buildMessage | nodeMessage | 
|-------|-------------|:----------:|:--------:|:----------:|:-------:| :----------: | :---------: |
| eventType | The type of event  | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| timestamp | UTC timestamp string | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| timestampMillis | tmestamp in ms since 1970-01-01 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| project | The project that is built | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| build | The build number  | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| instance | The Jenkins instance  | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| message | The log line without annotations| | | | | ✓ | ✓ |
| messageId | An unique ID for this message. Split messages have the same ID. | | | | | ✓ | ✓ |
| messageCount | The counter for split messages | | | | | ✓ | ✓ |
| flowNodeId | The id of the flow node | |  |  ✓ | ✓ |  | ✓ |
| step | The name of the step | |  |  ✓ | ✓ |  | ✓ |
| stageName | The name of the enclosing stage | |  |  ✓ | ✓ |  | ✓ |
| stageId | The id of the enclosing stage flow node | |  |  ✓ | ✓ | | ✓ |
| parallelBranchName | The name of the enclosing parallel branch | |  |  ✓ | ✓ |  | ✓ |
| parallelBranchId | The id of the enclosing parallel branch flow node | |  |  ✓ | ✓ | | ✓ |
| agent | The name of the enclosing agent (node step) | |  |  ✓ | ✓ |  | ✓ |
| nodes | The list of flow nodes and their status |  | ✓ |  ✓ | ✓ |  | |
| annotations | A List of annotations that were extracted from the message | | | | | ✓ | ✓ |

To reduce that amount of data sent with flowGraph node events the nodes field contains the list of flow nodes that have a changed status.
The buildEnd event will contain the full list of flow nodes with their status.

#### Flow node list
The list of flow nodes contains the following fields:

| Field | description |
| ----- | -----|
| displayName | The desription of the step as shown in the pipeline steps UI | 
| errorMessage | The message of the Exception if the step failed | 
| step | The name of the step | 
| id | The id of this flow node |
| enclosingId | The id of the enclosing flow node | 
| status | The status of the execution of the node |
| duration | The duration of the step | 
| parallelBranchName | The duration of the step | 
| stageName | The duration of the step | 
