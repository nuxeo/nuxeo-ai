
# Nuxeo AI Addons

Currently there are the following AI Addons:
  * [nuxeo-ai-image-quality](https://github.com/nuxeo/nuxeo-ai/tree/master/addons/nuxeo-ai-image-quality-core#nuxeo-ai-image-quality) - Enrichment services that uses [Sightengine](https://sightengine.com/).
  * [nuxeo-ai-aws](https://github.com/nuxeo/nuxeo-ai/blob/master/addons/nuxeo-ai-aws-core/README.md#nuxeo-ai-aws-integration) - Enrichment services that use [Amazon Web Services](https://aws.amazon.com).
  * [nuxeo-ai-image-quality](https://github.com/nuxeo/nuxeo-ai/tree/master/addons/nuxeo-ai-image-quality-core#nuxeo-ai-image-quality) - Enrichment services that uses [Sightengine](https://sightengine.com/).
  * [nuxeo-ai-aws](https://github.com/nuxeo/nuxeo-ai/blob/master/addons/nuxeo-ai-aws-core/README.md#nuxeo-ai-aws-integration) - Enrichment services that use [Amazon Web Services](https://aws.amazon.com).
  * [nuxeo-ai-gcp](https://github.com/nuxeo/nuxeo-ai/blob/master/addons/nuxeo-ai-gcp-core/README.md) - Enrichment services that use [Google Vision](https://cloud.google.com/vision/).

## Implementing a custom addon

Each addon has a `-core` project for the Java code and a `-package` project to create a marketplace package.  

An addon must define one or more implementations of an `EnrichmentProvider` (normally by extending [AbstractEnrichmentProvider](
https://github.com/nuxeo/nuxeo-ai/blob/master/nuxeo-ai-core/src/main/java/org/nuxeo/ai/enrichment/AbstractEnrichmentProvider.java)).  The `EnrichmentProvider` must be registered as an extension with the `AIComponent`.  See [Custom enrichment services](https://github.com/nuxeo/nuxeo-ai#custom-enrichment-services).

To enrich documents in a nuxeo-stream the enrichment service needs to be registered as a Stream processor.  See [Enrichment stream processor](https://github.com/nuxeo/nuxeo-ai#enrichment-stream-processing).

# License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

# About Nuxeo

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Netflix, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris. More information is available at www.nuxeo.com.
