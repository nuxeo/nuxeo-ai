# Nuxeo AI Core
Core functionality for using AI with the Nuxeo Platform.

This modules provides 2 packages:
  * nuxeo-ai-core - Contains the core interfaces and AI component
  * nuxeo-pipes-core - Nuxeo Pipes, short for "Pipelines" provides the ability to operate with [Nuxeo Stream](https://github.com/nuxeo/nuxeo/tree/master/nuxeo-runtime/nuxeo-stream).  Nuxeo Stream provides a Log storage abstraction and a Stream processing pattern. Nuxeo Stream has implementations with [Chronicle Queues](https://github.com/OpenHFT/Chronicle-Queue) or [Apache Kafka](http://kafka.apache.org/).

## Nuxeo AI Core
### Features
 * Provides an `AIComponent` to register services.  eg. An enrichment service.
 * Interfaces and helper classes for building services.

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
 ```
  <extension point="pipes" target="org.nuxeo.runtime.stream.pipes.Pipeline">
    <pipe id="pipe.images" enabled="true" />
  </extension>
  ```

### Customization
##### Events
Using an Nuxeo extension you can dynamically register a pipeline for any custom event.  
For example to send `MY_EVENT` to a stream called `mystream` you would use the following configuration.
```
  <extension point="pipes" target="org.nuxeo.runtime.stream.pipes.Pipeline">
    <pipe id="pipe.mypipe" enabled="true" function="org.nuxeo.my.DocumentPipeFunction">
      <supplier>
        <event>MY_EVENT</event>
      </supplier>
      <consumer>
        <stream name="mystream" />
      </consumer>
    </pipe>
  </extension>
```

 Transforming an input Event into an output stream is done using a function specified by the `function` parameter. Functions are explained below.


##### Functions
Actions on streams or events are based on the standard Java `Function<T, R>` interface.  To send an event to a stream you would need to implement the `Function<Event, Record>` interface. `Record` is the type used for items in a nuxeo-stream.
For examples, look at `DocumentPipeFunction` and its helper class `DocEventToStream`.

These is also a `FilterFunction` that first tests a `Predicate` before applying the function.  Predicates can be built with the help of the `Predicates` class.
 To create a predicate for _only document events with documents which are not system documents or proxies and aren't "Folderish"_ you would use this predicate: `docEvent(notSystem().and(d -> !d.hasFacet("Folderish"))`.

##### Stream processing
Stream processing is achieved using a computation stream pattern that enables you to compose producers/consumers into a complex topology.

To use a custom processor, create a class that implements `FunctionStreamProcessorTopology` and specify it in the `class` parameter as shown below.

```
<extension target="org.nuxeo.runtime.stream.service" point="streamProcessor">
<streamProcessor name="basicProcessor" logConfig="${nuxeo.pipes.config.name}" defaultConcurrency="1" defaultPartitions="4"
               class="org.nuxeo.my.custom.StreamProcessor">
 <option name="source">mystream</option>
 <option name="sink">mystream.out</option>
</streamProcessor>
</extension>
```

# License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

# About Nuxeo

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Netflix, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris. More information is available at www.nuxeo.com.
