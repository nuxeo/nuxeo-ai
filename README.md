# Nuxeo AI Core
Core functionality for using AI with the Nuxeo Platform.

This modules provides 3 packages:
  * nuxeo-ai-core - Contains the core interfaces and AI component
  * nuxeo-ai-pipes - Nuxeo Pipes, short for "Pipelines" provides the ability to operate with [Nuxeo Stream](https://github.com/nuxeo/nuxeo/tree/master/nuxeo-runtime/nuxeo-stream).  Nuxeo Stream provides a Log storage abstraction and a Stream processing pattern. Nuxeo Stream has implementations with [Chronicle Queues](https://github.com/OpenHFT/Chronicle-Queue) or [Apache Kafka](http://kafka.apache.org/).
  * nuxeo-ai-model - Adds support for custom machine learning models
## Installation
#### Version Support

| Ai-core Version | Nuxeo Version
| --- | --- |
| 1.0.X| 9.10 |
| 2.0.X| 10.2 |

Download the package from [https://maven.nuxeo.org](https://maven.nuxeo.org/nexus/#nexus-search;gav~org.nuxeo.ai).
Install using the command line, e.g.
```
./bin/nuxeoctl mp-install PATH_TO_DOWNLOAD/nuxeo-ai-core-1.0.zip
```
#### Indexing and Search
It is recommended that the Elasticsearch mappings are updated to allow a full text search on enrichment labels.
 The following code will add this mapping to a server running locally.
```json
curl -X PUT \
  http://localhost:9200/nuxeo/_mapping/doc/ \
  -H 'Cache-Control: no-cache' \
  -H 'Content-Type: application/json' \
  -d '{
  "properties": {
    "enrichment:items": {
      "properties": {
        "labels": {
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
<td colspan="1">`nuxeo.ai.images.enabled`</td>
<td colspan="1">Create a stream for creation/modification of images.</td>
<td colspan="1">`false`</td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.ai.video.enabled`</td>
<td colspan="1">Create a stream for creation/modification of video files.</td>
<td colspan="1">`false`</td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.ai.audio.enabled`</td>
<td colspan="1">Create a stream for creation/modification of audio files.</td>
<td colspan="1">`false`</td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.ai.text.enabled`</td>
<td colspan="1">Create a stream for text extracted from blobs.</td>
<td colspan="1">`false`</td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.ai.stream.config.name`</td>
<td colspan="1">The name of the stream log config</td>
<td colspan="1">`pipes`</td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.enrichment.source.stream`</td>
<td colspan="1">The name of the stream that receives Enrichment data</td>
<td colspan="1">`enrichment.in`</td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.enrichment.save.tags`</td>
<td colspan="1">Should enrichment labels be saved as a standard Nuxeo tags?</td>
<td colspan="1">`false`</td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.enrichment.save.facets`</td>
<td colspan="1">Should enrichment data be saved as a document facet?</td>
<td colspan="1">`true`</td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.enrichment.raiseEvent`</td>
<td colspan="1">Should an `enrichmentMetadataCreated` event be raised when new enrichment data is added to the stream?</td>
<td colspan="1">`true`</td>
<td colspan="1">Since 1.0</td>
</tr>
</tbody>
</table>
</div>


## Nuxeo AI Core
### Features
 * Provides an `AIComponent` to register services.  eg. An enrichment service.
 * Interfaces and helper classes for building services.
 * Provides a `EnrichingStreamProcessor` to act on a stream using an Java `EnrichmentService`.
 * An Operation called `EnrichmentOp` to call an `EnrichmentService` and return the result.
 * Provides a `RestClient` and `RestEnrichmentService` for easily calling a custom json rest api.


## Nuxeo Pipes
### Features
 * Enables sending custom events to a nuxeo stream.
 * Provides a `FunctionStreamProcessorTopology` to act on a stream using a Java `Function<T, R>`.
 * Provides 4 customizable document streams:
    * `images` - When a image is added to a document.
    * `videos` - When a video is added to a document.
    * `audio` - When an audio file is added to a document.
    * `text` - When binary text is extracted from a document.

These streams are *disabled by default* but can be enabled by the [corresponding configuration parameters](#configuration-parameters).

### Customization
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
        <stream name="mystream" />
      </consumer>
    </pipe>
  </extension>
```
Transforming an input Event into an output stream is done using a function specified by the `function` parameter. Functions are explained below.

##### Custom enrichment services
 New enrichment services can be added by implementing `EnrichmentService`.  `AbstractEnrichmentService` is a good starting point.
 If you wish to call a custom rest api then extending `RestEnrichmentService` would allow access to the various `RestClient`
 helper methods. To register your extension you would use configuration similar to this.
 ```xml
  <extension point="enrichment" target="org.nuxeo.ai.services.AIComponent">
    <enrichment name="custom1" kind="/classification/custom"
                class="org.nuxeo.ai.custom.CustomModelEnrichmentService" maxSize="10000000">
      <option name="minConfidence">0.75</option>
    </enrichment>
  </extension>
```

##### Functions
Actions on streams or events are based on the standard Java `Function<T, R>` interface.  To send an event to a stream you would need to implement the `Function<Event, Record>` interface. `Record` is the type used for items in a nuxeo-stream.
For examples, look at `DocumentPipeFunction` and its helper class `DocEventToStream`.

These is also a `FilterFunction` that first tests a `Predicate` before applying the function.  Predicates can be built with the help of the `Predicates` class.
 To create a predicate for _only document events with documents which are not system documents or proxies and aren't "Folderish"_ you would use this predicate: `docEvent(notSystem().and(d -> !d.hasFacet("Folderish"))`.

##### Stream processing
Stream processing is achieved using a computation stream pattern that enables you to compose producers/consumers into a complex topology.

To use a custom processor, create a class that implements `FunctionStreamProcessorTopology` and specify it in the `class` parameter as shown below.

```xml
<extension target="org.nuxeo.runtime.stream.service" point="streamProcessor">
<streamProcessor name="basicProcessor" logConfig="${nuxeo.ai.stream.config.name}" defaultConcurrency="1" defaultPartitions="4"
               class="org.nuxeo.my.custom.StreamProcessor">
 <option name="source">mystream</option>
 <option name="sink">mystream.out</option>
</streamProcessor>
</extension>
```

##### Enrichment stream processing
You can register your custom enrichment services to act as a stream processor using the `EnrichingStreamProcessor`.
For example, the following configuration would register a stream processor that acts on a source stream called `images`,
it runs the `custom1` enrichment service on each record and sends the result to the `enrichment.in` stream.
```xml
<extension target="org.nuxeo.runtime.stream.service" point="streamProcessor">
<streamProcessor name="myCustomProcessor1" defaultConcurrency="2" defaultPartitions="4"
                 logConfig="${nuxeo.ai.stream.config.name}"
                 class="org.nuxeo.ai.enrichment.EnrichingStreamProcessor">
  <option name="source">images</option>
  <option name="sink">enrichment.in</option>
  <option name="enrichmentServiceName">custom1</option>
</streamProcessor>
</extension>
```
### Notes
* When using the Chronicle implementation of nuxeo-stream you should make sure your `defaultPartitons` setting for
stream processors matches the number of partitions you have, eg. 4.

#### Useful log config:
```xml
  <appender name="STREAMS" class="org.apache.log4j.FileAppender">
    <errorHandler class="org.apache.log4j.helpers.OnlyOnceErrorHandler" />
    <param name="File" value="${nuxeo.log.dir}/streams.log" />
    <param name="Append" value="false" />
    <layout class="org.apache.log4j.PatternLayout">
      <param name="ConversionPattern" value="%d{ISO8601} %-5p [%t][%c] %m%n" />
    </layout>
  </appender>
  <category name="org.nuxeo.ai">
    <priority value="DEBUG" />
    <appender-ref ref="STREAMS" />
  </category>
  <category name="org.nuxeo.lib.stream">
    <priority value="DEBUG" />
    <appender-ref ref="STREAMS" />
  </category>
  <category name="org.nuxeo.runtime.stream">
    <priority value="DEBUG" />
    <appender-ref ref="STREAMS" />
  </category>
```

# License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

# About Nuxeo

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Netflix, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris. More information is available at www.nuxeo.com.
