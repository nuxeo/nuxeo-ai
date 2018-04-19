# Nuxeo Pipes
Nuxeo Pipes, short for "Pipelines" provides the ability to operate on Nuxeo Streams.

##Features
* Provides 4 customizable document streams:
    * `images` - When a image is added to a document.
    * `videos` - When a video is added to a document.
    * `audios` - When an audio file is added to a document.
    * `text` - When binary text is extracted from a document.

These streams are *disabled by default* but can be enabled by id. For example
 to enable "pipe.images"
:
 ```
  <extension point="pipes" target="org.nuxeo.runtime.stream.pipes.Pipeline">
    <pipe id="pipe.images" enabled="true" />
  </extension>
  ```
    
##Customization
Using an Nuxeo extension you can dynamically register a pipeline for any custom event.  
For example to send "MY_EVENT" to a stream called "mystream" you would use this configuration.
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
###Functions
Actions on streams or events are based on the standard Java `Function<T, R>` interface.  To send an event to a stream you would need to implement the `Function<Event, Record>` interface. `Record` is the type used for items in a nuxeo-stream. 
For examples, look at `DocumentPipeFunction` and its helper class `DocEventToStream`.

These is also a `FilterFunction` that first tests a `Predicate` before applying the function.  Predicates can be built with the help of the `Predicates` class.
 To create a predicate for _only document events with documents which are not system documents or proxies and aren't "Folderish"_ you would use this predicate: `docEvent(notSystem().and(d -> !d.hasFacet("Folderish"))`.
# License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

# About Nuxeo

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Netflix, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris. More information is available at www.nuxeo.com.
