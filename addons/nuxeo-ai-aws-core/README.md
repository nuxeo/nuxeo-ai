
# Nuxeo AI AWS integration

Currently provides the following enrichment services:
  * aws.imageLabels - Calls AWS Rekognition for object detection in images
  * aws.textSentiment - Call AWS Comprehend for sentiment analysis
  * aws.unsafeImages - Calls AWS Rekognition to detect unsafe images
  * aws.textDetection - Calls AWS Rekognition to detect text in images
  * aws.faceDetection - Calls AWS Rekognition to detect faces in images
  * aws.celebrityDetection - Calls AWS Rekognition to detect celebrity faces in images
  * aws.translate.* - Calls AWS Translate to translate text

#### Credentials
Credentials are discovered using `nuxeo-runtime-aws`.
The chain searches for credentials in order: Nuxeo's AWSConfigurationService, environment variables, system properties, profile credentials, EC2Container credentials.

If you choose to use nuxeo.conf then the properties are:
```
nuxeo.aws.accessKeyId=your_AWS_ACCESS_KEY_ID
nuxeo.aws.secretKey=your_AWS_SECRET_ACCESS_KEY
nuxeo.aws.region=your_AWS_REGION
```

If you are only using images and an S3 BinaryManager is already being used then it re-uses the image data to pass a reference instead of uploading the binary again.  
## Installation
#### Quick start
1. Install the nuxeo-ai-aws package. `./bin/nuxeoctl mp-install nuxeo-ai-aws`

2. Add the following parameters to `nuxeo.conf`.
```
nuxeo.ai.images.enabled=true
nuxeo.ai.text.enabled=true
nuxeo.enrichment.aws.images=true
nuxeo.enrichment.aws.text=true
nuxeo.enrichment.save.tags=true
nuxeo.enrichment.save.facets=true
nuxeo.enrichment.raiseEvent=true
```
3. Set your AWS credentials [AWS credentials](#credentials).
3. Start Nuxeo and upload an image.
4. Wait 10 seconds then look at the document tags and document json `enrichment:items` facet
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
<td colspan="1">`nuxeo.ai.images.enabled`</td>
<td colspan="1">Run AWS enrichiment services on images.</td>
<td colspan="1">`false`</td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.ai.text.enabled`</td>
<td colspan="1">Run AWS enrichiment services on text.</td>
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
