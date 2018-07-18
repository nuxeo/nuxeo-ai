# Nuxeo AI Core
Core functionality for using AI with the Nuxeo Platform.

This modules provides 2 packages:
  * nuxeo-ai-core - Contains the core interfaces and AI component
  * nuxeo-pipes-core - Nuxeo Pipes, short for "Pipelines" provides the ability to operate with [Nuxeo Stream](https://github.com/nuxeo/nuxeo/tree/master/nuxeo-runtime/nuxeo-stream).  Nuxeo Stream provides a Log storage abstraction and a Stream processing pattern. Nuxeo Stream has implementations with [Chronicle Queues](https://github.com/OpenHFT/Chronicle-Queue) or [Apache Kafka](http://kafka.apache.org/).

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

These streams are *disabled by default* but can be enabled by id. For example
 to enable "pipe.images"
:
```xml
  <extension point="pipes" target="org.nuxeo.runtime.stream.pipes.Pipeline">
    <pipe id="pipe.images" enabled="true" />
  </extension>
```

### Customization
##### Events
Using an Nuxeo extension you can dynamically register a pipeline for any custom event.  
For example to send `MY_EVENT` to a stream called `mystream` you would use the following configuration.
```xml
  <extension point="pipes" target="org.nuxeo.runtime.stream.pipes.Pipeline">
    <pipe id="pipe.mypipe" enabled="true" function="org.nuxeo.my.DocumentPipeFunction">
      <supplier>
        <event name="MY_EVENT">
          <filter class="org.nuxeo.runtime.stream.pipes.filters.NotSystemOrProxyFilter"/>
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
 helper methods. See `CustomModelEnrichmentService` for an example.  To register your extension you would use configuration similar to this.
 ```xml
  <extension point="enrichment" target="org.nuxeo.ai.services.AIComponent">
    <enrichment name="custom1" kind="/classification/custom" class="org.nuxeo.ai.custom.CustomModelEnrichmentService">
      <option name="uri">http://localhost:7000/invocations</option>
      <option name="modelName">dnn</option>
      <option name="imageFeatureName">image</option>
      <option name="textFeatureName">text</option>
      <option name="minConfidence">0.55</option>
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
<streamProcessor name="basicProcessor" logConfig="${nuxeo.pipes.config.name}" defaultConcurrency="1" defaultPartitions="4"
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
                 logConfig="${nuxeo.pipes.config.name}"
                 class="org.nuxeo.ai.enrichment.EnrichingStreamProcessor">
  <option name="source">images</option>
  <option name="sink">enrichment.in</option>
  <option name="enrichmentServiceName">custom1</option>
</streamProcessor>
</extension>
```
### Notes
When using the Chronicle implementation of nuxeo-stream you should make sure your `defaultPartitons` setting for
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
