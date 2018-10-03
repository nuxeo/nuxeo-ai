# Nuxeo AI Image Quality
An implementation of an enrichment service that uses [Sightengine](https://sightengine.com/). Based on the work done by [Joaquin Garzon](https://github.com/joaquinNX).

## Installation
#### Quick start
1. Either build locally (`mvn install`) or download the package from [https://maven.nuxeo.org](https://maven.nuxeo.org/nexus/#nexus-search;gav~~org.nuxeo.ai)
and install using the command line, e.g.
```
./bin/nuxeoctl mp-install PATH_TO_DOWNLOAD/nuxeo-ai-core-1.0.zip
./bin/nuxeoctl mp-install YOUR_PATH/nuxeo-ai-image-quality-package/target/nuxeo-ai-image-quality-package-1.0-SNAPSHOT.zip
```

2. Add the following parameters to `nuxeo.conf`.
```
nuxeo.ai.images.enabled=true
nuxeo.enrichment.save.tags=true
nuxeo.enrichment.save.facets=true
nuxeo.enrichment.raiseEvent=true
nuxeo.ai.sightengine.apiKey=YOUR_API_KEY
nuxeo.ai.sightengine.apiSecret=YOUR_API_SECRET
```

3. Start Nuxeo and upload an image.  
4. Wait 10 seconds then look at the document tags and document json `enrichment:items` facet.
### Configuration Parameters
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
<td colspan="1">`nuxeo.ai.sightengine.apiKey`</td>
<td colspan="1">The api key for sightengine</td>
<td colspan="1"></td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.ai.sightengine.apiSecret`</td>
<td colspan="1">The api secret for sightengine</td>
<td colspan="1"></td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.ai.sightengine.all`</td>
<td colspan="1">Configure an enrichment service to process the `images` stream and call all sightengine models</td>
<td colspan="1">`true`</td>
<td colspan="1">Since 1.0</td>
</tr>
</tbody>
</table>
</div>


# License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

# About Nuxeo

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Netflix, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris. More information is available at www.nuxeo.com.
