# Nuxeo AI AWS integration

Currently, provides the following enrichment services:
  * gcp.imageLabels - Calls GCP for object detection in images to produce labels
  * gcp.textDetection - Calls GCP to detect text, and its bounding boxes in images
  * gcp.faceDetection - Calls GCP to detect faces, and their bounding boxes in images
  * gcp.logoDetection - Calls GCP to detect logos, and their bounding boxes in images
  * gcp.landmarkDetection - Calls GCP to detect landmarks in images
  * gcp.objectLocalizer - Calls GCP to detect objects, and their bounding boxes in images
  
#### Credentials
Credentials discovery using `nuxeo-runtime-aws`.
The chain searches for credentials in order: Nuxeo's AWSConfigurationService, environment variables, system properties, profile credentials, EC2Container credentials.

If you choose to use nuxeo.conf then the properties are:
```
nuxeo.gcp.credentials=your_PATH_TO_GCP_KEY
```

or have Environment variable GOOGLE_CREDENTIALS_PATH in your system 

## Installation
#### Quick start
1. Install the nuxeo-ai-gcp package. `./bin/nuxeoctl mp-install nuxeo-ai-gcp`

2. Add the following parameters to `nuxeo.conf`.
```
nuxeo.ai.images.enabled=true
nuxeo.enrichment.gcp.images=true
nuxeo.enrichment.save.tags=true
nuxeo.enrichment.save.facets=true
nuxeo.enrichment.raiseEvent=true
```

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
<tr>
<td colspan="1">`nuxeo.enrichment.gcp.images`</td>
<td colspan="1">Run GCP enrichment services on images.</td>
<td colspan="1">`false`</td>
<td colspan="1">Since 1.0</td>
</tr>
</tbody>
</table>
</div>

# License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

# About Nuxeo

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Netflix, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris. More information is available at www.nuxeo.com.
