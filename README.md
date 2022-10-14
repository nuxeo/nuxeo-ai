# Nuxeo AI Core

Core functionality for using AI with the Nuxeo Platform.

This repository provides 3 packages:

* `nuxeo-ai-core`: Contains the core interfaces and AI component
* `nuxeo-ai-image-quality`: Enrichment services that uses [Sightengine](https://sightengine.com/).
* `nuxeo-ai-aws`: Enrichment services that use Amazon Web Services.

## Installation

#### Version Support

| Ai-core Version | Nuxeo Version
| --- | --- |
| 2.0.1| 10.3  |
| 2.1.0| 10.10 |
| 2.1.1| 10.10-HF02 |
| 2.1.2| 10.10-HF05 |
| 2.1.3| 10.10-HF22 |
| 2.2.x| 10.10-HF23+ |
| 3.0.x| 11.1-SNAPSHOT |

1. Install the nuxeo-ai-core package. `./bin/nuxeoctl mp-install nuxeo-ai-core`

#### Export Configuration

You can set these in your `nuxeo.conf`.
<div class="table-scroll">
<table class="hover">
<tbody>
<tr>
<th width="250" colspan="1">Parameter</th>
<th colspan="1">Description</th>
<th width="250" colspan="1">Default value</th>
<th width="150" colspan="1">Since</th>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.export.batch.size</code></td>
<td colspan="1">Sets batch size for the TF Record export</td>
<td colspan="1"><code>20</code></td>
<td colspan="1">Since 2.1</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.export.bucket.size</code></td>
<td colspan="1">Sets bucket size for the TF Record export</td>
<td colspan="1"><code>100</code></td>
<td colspan="1">Since 2.1</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.export.training.batch.size</code></td>
<td colspan="1">Sets how many records are getting into the training writer</td>
<td colspan="1"><code>200</code></td>
<td colspan="1">Since 2.1</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.export.validation.batch.size</code></td>
<td colspan="1">Sets how many records are getting into the validation writer</td>
<td colspan="1"><code>100</code></td>
<td colspan="1">Since 2.1</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.conversion.rendition</code></td>
<td colspan="1">Rendition title for multi-picture views to return for predictions/exports</td>
<td colspan="1"><code>Small</code></td>
<td colspan="1">Since 2.3</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.conversion.strict</code></td>
<td colspan="1">Use Nuxeo renditions only during export</td>
<td colspan="1"><code>true</code></td>
<td colspan="1">Since 2.5</td>
</tr>
</tbody>
</table>
</div>

#### Indexing and Search

It is recommended that the Elasticsearch mappings are updated to allow a full text search on enrichment labels. The
following code will add this mapping to a server running locally.

```
curl -X PUT \
  http://localhost:9200/nuxeo/_mapping/doc/ \
  -H 'Cache-Control: no-cache' \
  -H 'Content-Type: application/json' \
  -d '{
  "dynamic_templates": [
      {
          "no_enriched_raw_template": {
              "path_match": "enrichment:items.raw.*",
              "mapping": {
                  "index": false
              }
          }
      },
      {
          "no_enriched_norms_template": {
              "path_match": "enrichment:items.normalized.*",
              "mapping": {
                  "index": false
              }
          }
      },
      {
          "no_enriched_history_template": {
              "path_match": "enrichment:history.*",
              "mapping": {
                  "index": false
              }
          }
      }
  ],
  "properties": {
      "enrichment:items": {
          "properties": {
              "model": {
                  "type": "keyword",
                  "ignore_above": 256
              },
              "inputProperties": {
                  "type": "keyword",
                  "ignore_above": 256
              },
              "suggestions": {
                  "properties": {
                      "labels": {
                          "properties": {
                              "confidence": {
                                  "type": "float"
                              },
                              "label": {
                                  "type": "keyword",
                                  "copy_to": [
                                      "all_field"
                                  ],
                                  "ignore_above": 256,
                                  "fields": {
                                      "fulltext": {
                                          "analyzer": "fulltext",
                                          "type": "text"
                                      }
                                  }
                              }
                          }
                      },
                      "property": {
                          "type": "keyword",
                          "ignore_above": 256
                      }
                  }
              }
          }
      },
      "enrichment:filled": {
          "type": "keyword",
          "ignore_above": 256
      },
      "enrichment:corrected": {
          "type": "keyword",
          "ignore_above": 256
      }
  }
}'
```

#### Configuration Parameters

You can set these in your `nuxeo.conf`.
<div class="table-scroll">
<table class="hover">
<tbody>
<tr>
<th width="250" colspan="1">Parameter</th>
<th colspan="1">Description</th>
<th width="250" colspan="1">Default value</th>
<th width="150" colspan="1">Since</th>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.export.min.docs</code></td>
<td colspan="1">Set minimum amount of documents required for continues expoort</td>
<td colspan="1"><code>10</code></td>
<td colspan="1">Since 2.1</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.images.enabled</code></td>
<td colspan="1">Create a stream for creation/modification of images.</td>
<td colspan="1"><code>false</code></td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.video.enabled</code></td>
<td colspan="1">Create a stream for creation/modification of video files.</td>
<td colspan="1"><code>false</code></td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.audio.enabled</code></td>
<td colspan="1">Create a stream for creation/modification of audio files.</td>
<td colspan="1"><code>false</code></td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.text.enabled</code></td>
<td colspan="1">Create a stream for text extracted from blobs.</td>
<td colspan="1"><code>false</code></td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.stream.config.name</code></td>
<td colspan="1">The name of the stream log config</td>
<td colspan="1"><code>pipes</code></td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.enrichment.source.stream</code></td>
<td colspan="1">The name of the stream that receives Enrichment data</td>
<td colspan="1"><code>ai/enrichment-in</code></td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.enrichment.save.tags</code></td>
<td colspan="1">Should enrichment labels be saved as a standard Nuxeo tags?</td>
<td colspan="1"><code>false</code></td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.enrichment.save.facets</code></td>
<td colspan="1">Should enrichment data be saved as a document facet?</td>
<td colspan="1"><code>true</code></td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.enrichment.raiseEvent</code></td>
<td colspan="1">Should an `enrichmentMetadataCreated` event be raised when new enrichment data is added to the stream?</td>
<td colspan="1"><code>true</code></td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.default.threshold</code></td>
<td colspan="1">Default Threshold value. Should be a float type between 0.0 and 1.0</td>
<td colspan="1"><code>0.75</code></td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.autofill.default.threshold</code></td>
<td colspan="1">Default Threshold value for autofill. Should be a float type between 0.0 and 1.0</td>
<td colspan="1"><code>0.75</code></td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1"><code>nuxeo.ai.autocorrect.default.threshold</code></td>
<td colspan="1">Default Threshold value for autocorrect. Should be a float type between 0.0 and 1.0</td>
<td colspan="1"><code>0.75</code></td>
<td colspan="1">Since 1.0</td>
</tr>
</tbody>
</table>
</div>

## Nuxeo AI Core

Nuxeo AI Core provides 3 Java modules:

* nuxeo-ai-core - Contains the core interfaces and AI component
* nuxeo-ai-pipes - Nuxeo Pipes, short for "Pipelines" provides the ability to operate
  with [Nuxeo Stream](https://github.com/nuxeo/nuxeo/tree/master/nuxeo-runtime/nuxeo-stream). Nuxeo Stream provides a
  Log storage abstraction and a Stream processing pattern. Nuxeo Stream has implementations
  with [Chronicle Queues](https://github.com/OpenHFT/Chronicle-Queue) or [Apache Kafka](http://kafka.apache.org/).
* nuxeo-ai-model - Adds support for custom machine learning models

### Features

* Provides an `AIComponent` to register services. eg. An enrichment service.
* Interfaces and helper classes for building services.
* Provides a `EnrichingStreamProcessor` to act on a stream using an Java `EnrichmentProvider`.
* An Operation called `EnrichmentOp` to call an `EnrichmentProvider` and return the result.
* Provides a `RestClient` and `RestEnrichmentProvider` for easily calling a custom json rest api.
* Provides a `ThresholdComponents` to register type/facet based thresholds.

 ```xml

<extension target="org.nuxeo.ai.configuration.ThresholdComponent"
           point="thresholdConfiguration">
  <thresholdConfiguration type="Document"
                          global="0.8">
    <thresholds>
      <threshold xpath="dc:title"
                 value="0.6"
                 autofill="0.65"
                 autocorrect="0.70"/>
    </thresholds>
  </thresholdConfiguration>
</extension>
```

* Continues Export - to enable such you must contribute a cron contribution to your instance. It has to
  fire `startContinuousExport` at a give time. For instance to export weekly you might use the following example:

  ```xml
    <extension target="org.nuxeo.ecm.core.scheduler.SchedulerService" point="schedule">
        <schedule id="continuous_export_default">
          <event>startContinuousExport</event>
          <!-- At 03:00 AM, every 7 days -->
          <cronExpression>0 0 3 */7 * ?</cronExpression>
        </schedule>
      </extension>
  ```

On such event the system will retrieve all AI Model defined under configured project and perform evaluation of the
current data to check if it needs to be uploaded.

## Nuxeo Pipes

### Features

* Enables sending custom events to a nuxeo stream.
* Provides a `FunctionStreamProcessorTopology` to act on a stream using a Java `Function<T, R>`.
* Provides 4 customizable document streams:
  * `images` - When a image is added to a document.
  * `videos` - When a video is added to a document.
  * `audio` - When an audio file is added to a document.
  * `text` - When binary text is extracted from a document.

These streams are *disabled by default* but can be enabled by
the [corresponding configuration parameters](#configuration-parameters).

### Customization

##### Sample configuration for DAM Installations.

The [configuration parameters](#configuration-parameters) are used to configure Nuxeo xml contributions, instead you can
provide your own configuration that meets your requirements.

A [Sample DAM configuration is available to download](https://github.com/nuxeo/nuxeo-ai/blob/master/nuxeo-ai-pipes/src/test/resources/ai-dam-config.xml)
, and defines 2 pipelines:

###### Images Pipeline

* Listens for `pictureViewsGenerationDone` and sends `picture:views/3/content` to the `images` stream.
* Configures an `EnrichingStreamProcessor` to read from the  `images` stream, calls the `aws.celebrityDetection`
  enrichment service and puts the response in the `ai/images-enrichment-in` stream.
* The next stream processor reads from the `ai/images-enrichment-in` stream, and raises an `imageMetadataCreated` event
  for each new enrichment entry.
* An example listener for the  `imageMetadataCreated` event writes a log message.

###### Video Pipeline

* Listens for new `vid:storyboard` modifications for a document in a path containing `movies` and sends 4 of the video
  storyboard images to the `video` stream.
* Configures an `EnrichingStreamProcessor` to read from the `video` stream, calls the `aws.imageLabels` enrichment
  service and puts the response in the `ai/video-enrichment-in` stream.
* The next stream processor reads from the `ai/video-enrichment-in` stream and creates document tags for the enrichment
  labels.

Please note that the `EnrichingStreamProcessors` are using a stream processing policy of `continueOnFailure=true`, this
means that *stream processing will continue even if the enrichment failed*.

##### Events

Using an Nuxeo extension you can dynamically register a pipeline for any custom event.  
For example to send `MY_EVENT` to a stream called `mystream` you would use the following configuration.

```xml

<extension point="pipes" target="org.nuxeo.ai.Pipeline">
  <pipe id="pipe.mypipe" enabled="true" function="org.nuxeo.my.DocumentPipeFunction">
    <supplier>
      <event name="MY_EVENT">
        <filter class="org.nuxeo.ai.pipes.filters.NotSystemOrProxyFilter"/>
      </event>
    </supplier>
    <consumer>
      <stream name="mystream"/>
    </consumer>
  </pipe>
</extension>
```

Transforming an input Event into an output stream is done using a function specified by the `function` parameter.
Functions are explained below.

##### Nuxeo Insight Cloud enrichments

To enable/disable Nuxeo Insight Cloud enrichers based on your custom model set `nuxeo.ai.insight.enrichment.enabled` to
the desired value. Default value is `true`

##### Custom enrichment services

New enrichment services can be added by implementing `EnrichmentProvider`.  `AbstractEnrichmentProvider` is a good
starting point. If you wish to call a custom rest api then extending `RestEnrichmentProvider` would allow access to the
various `RestClient`
helper methods. To register your extension you would use configuration similar to this.

 ```xml

<extension point="enrichment" target="org.nuxeo.ai.services.AIComponent">
  <enrichment name="custom1" kind="/classification/custom"
              class="org.nuxeo.ai.custom.CustomModelEnrichmentProvider" maxSize="10000000">
    <option name="minConfidence">0.75</option>
  </enrichment>
</extension>
```

##### Functions

Actions on streams or events are based on the standard Java `Function<T, R>` interface. To send an event to a stream you
would need to implement the `Function<Event, Record>` interface. `Record` is the type used for items in a nuxeo-stream.
For examples, look at `PropertiesToStream` and its helper class `DocEventToStream`.

These is also a `FilterFunction` that first tests a `Predicate` before applying the function. Predicates can be built
with the help of the `Predicates` class. To create a predicate for _only document events with documents which are not
system documents or proxies and aren't "Folderish"_ you would use this
predicate: `docEvent(notSystem().and(d -> !d.hasFacet("Folderish"))`.

##### Stream processing

Stream processing is achieved using a computation stream pattern that enables you to compose producers/consumers into a
complex topology.

To use a custom processor, create a class that implements `FunctionStreamProcessorTopology` and specify it in
the `class` parameter as shown below.

```xml

<extension target="org.nuxeo.runtime.stream.service" point="streamProcessor">
  <streamProcessor name="basicProcessor" defaultConcurrency="1" defaultPartitions="4"
                   class="org.nuxeo.my.custom.StreamProcessor">
    <option name="source">ai/mystream</option>
    <option name="sink">ai/mystream-out</option>
  </streamProcessor>
</extension>
```

##### Enrichment stream processing

You can register your custom enrichment services to act as a stream processor using the `EnrichingStreamProcessor`. For
example, the following configuration would register a stream processor that acts on a source stream called `images`, it
runs the `custom1` enrichment service on each record and sends the result to the `ai/enrichment-in` stream.

```xml

<extension target="org.nuxeo.runtime.stream.service" point="streamProcessor">
  <streamProcessor name="myCustomProcessor1" defaultConcurrency="2" defaultPartitions="4"
                   class="org.nuxeo.ai.enrichment.EnrichingStreamProcessor">
    <option name="source">ai/images</option>
    <option name="sink">ai/enrichment-in</option>
    <option name="enrichmentProviderName">custom1</option>
  </streamProcessor>
</extension>
```

### Monitoring

Nuxeo AI adds additional metrics to the
standard [Nuxeo Metrics reporting](https://doc.nuxeo.com/nxdoc/metrics-and-monitoring/#metrics).

| Metric name | Metric
| --- | --- |
|`nuxeo.ai.streams.[eventListener].events`| Count of events received.|
|`nuxeo.ai.streams.[eventListener].consumed`| Count of events that matched the filter condition and were processed.|
| `nuxeo.ai.enrichment.[enrichmentProvider].called`| Count of stream records received. |
| `nuxeo.ai.enrichment.[enrichmentProvider].errors`| Count of errors.|
| `nuxeo.ai.enrichment.[enrichmentProvider].produced`| How many records were produced after calling the service.|
| `nuxeo.ai.enrichment.[enrichmentProvider].retries`| Count of retries.|
| `nuxeo.ai.enrichment.[enrichmentProvider].cacheHit`| Count of times the result was returned from the cache rather than calling the enrichment service.|
| `nuxeo.ai.enrichment.[enrichmentProvider].unsupported`| Count of unprocessable records, perhaps due to mime-type or size.|
| `nuxeo.ai.enrichment.[enrichmentProvider].success`| Count of successful calls.|
| `nuxeo.ai.enrichment.[enrichmentProvider].circuitbreaker`| Incremented when the circuilt breaker is open, stopping the stream from any more processing.|
| `nuxeo.ai.enrichment.[enrichmentProvider].fatal`| Incremented when a fatal error occurs stopping the stream from any more processing.|
| `nuxeo.ai.streams.func.[functionName].called`| Count of stream records received.|
| `nuxeo.ai.streams.func.[functionName].errors`| Count of errors.|
| `nuxeo.ai.streams.func.[functionName].produced`| How many records were produced by the function.|

### Notes

* When using the Chronicle implementation of nuxeo-stream you should make sure your `defaultPartitons` setting for
  stream processors matches the number of partitions you have, eg. 4.

#### Useful log config:

Edit `$NUXEO_HOME/lib/log4j2.xml`, in the `<Appenders>` section, add a `AI-FILE` appender:

```xml

<RollingFile name="AI-FILE" fileName="${sys:nuxeo.log.dir}/nuxeo-ai.log"
             filePattern="${sys:nuxeo.log.dir}/nuxeo-ai-%d{yyyy-MM-dd}.log.gz" append="true">
  <PatternLayout pattern="%d{ISO8601} %-5p [%t] [%c] %m%n"/>
  <CronTriggeringPolicy schedule="0 0 0 * * ?" evaluateOnStartup="true"/> <!-- Rollover at midnight every day -->
  <DefaultRolloverStrategy/>
</RollingFile>
```

Then in the `<Loggers>` section, add a logger pointing to the `AI-FILE` appender:

```xml

<Logger name="org.nuxeo.ai" level="debug">
  <AppenderRef ref="AI-FILE"/>
</Logger>
```

#### Useful stream commands:

[Nuxeo Stream](https://github.com/nuxeo/nuxeo/tree/master/nuxeo-runtime/nuxeo-stream) is either implemented
with [Chronicle Queues](https://github.com/OpenHFT/Chronicle-Queue) or [Apache Kafka](http://kafka.apache.org/). To
watch the progress of messages in the stream you can use:

```
$NUXEO_HOME/bin/stream.sh --help
```

For example, to see the last 8 messages in the "images" stream, for chronicle you would use the first command below (
passing in `--chronicle nxserver/data/stream/pipes`) and for Kafka you would use the second command below (passing in
just `-k`).

```
./bin/stream.sh tail -n 8 --chronicle nxserver/data/stream/pipes -l images --codec avro
./bin/stream.sh tail -n 8 -k -l images --data-size 2000 --codec avro
```

Similarly, to view the consumer lag on the "images" stream, for chronicle use the first command, and the second for
kafka. The response format is Markdown.

```
./bin/stream.sh lag --chronicle nxserver/data/stream/pipes -l images --verbose
./bin/stream.sh lag -k -l images --verbose
```

### Automatic Property Filling

When a document is enriched with suggestions, the suggestions are stored in the `Enrichment` facet. It is possible to
automatically set a property with a suggested value in two ways. If the property currently has no value then you can
use `Autofill`, if the property already has a value then use `Autocorrect`.

#### Autofill Logic

* If the target property is `null` or its previously been autofilled then attempt to set the property.
* Calculate the highest ranking suggestion from the suggestions stored in the "Enrichment" facet.
  * If its confidence is greater than the threshold returned from the `ThresholdService` then set the property value.
* If we have already autofilled this property but it doesn't meet the threshold then reset it to `null`.
* Raise an `AUTO_FILLED` document event.

#### Autocorrect Logic

* If its previously autofilled then don't attempt an autocorrect because autofill and autocorrect are mutually
  exclusive.
* Calculate the highest ranking suggestion from the suggestions stored in the "Enrichment" facet.
  * If its confidence is greater than the threshold returned from the `ThresholdService` then set the property value.
  * Save the previous value in the history blob (unless it was previously auto-corrected).
* If we have already autocorrected this property but it doesn't meet the threshold then reset it to the previous value
  from the history.
* Raise an `AUTO_CORRECTED` document event.

#### Stream Bulk Actions

Dataset exports use the [Bulk Action Framework](https://doc.nuxeo.com/nxdoc/bulk-action-framework/). To track progress
of your bulk action you can use a command like this:

```
export COMMAND_ID=your-bulk-action-id
curl -s -X GET "localhost:8080/nuxeo/api/v1/bulk/$COMMAND_ID" -u Administrator:Administrator -H 'content-type: application/json'

```

The documentation on [Debugging The Bulk Action Framework](https://doc.nuxeo.com/nxdoc/bulk-action-framework/#debugging)
has more useful stream commands. Some further examples are:

```
./bin/stream.sh lag --chronicle /var/lib/nuxeo/data/stream/bulk -l ai/bulkDatasetExport
./bin/stream.sh lag --chronicle /var/lib/nuxeo/data/stream/bulk -l ai/training
./bin/stream.sh lag --chronicle /var/lib/nuxeo/data/stream/bulk -l ai/validation
./bin/stream.sh tail -n 8 --chronicle /var/lib/nuxeo/data/stream/bulk -l done --codec avro --schema-store /var/lib/nuxeo/data/avro/ --data-size 3000
./bin/stream.sh tail -n 8 --chronicle /var/lib/nuxeo/data/stream/bulk -l command --codec avro --schema-store /var/lib/nuxeo/data/avro/ --data-size 3000
```

# License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

# About Nuxeo

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile,
innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and
cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based
tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most
recognizable brands including Verizon, Electronic Arts, Netflix, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is
headquartered in New York and Paris. More information is available at www.nuxeo.com.
