
# Nuxeo AI AWS integration

Currently provides the following enrichment services:
  * aws.imageLabels - Calls AWS Rekognition for object detection in images
  * aws.textSentiment - Call AWS Comprehend for sentiment analysis
  * aws.unsafeImages - Calls AWS Rekognition to detect unsafe images
  * aws.textDetection - Calls AWS Rekognition to detect text in images
  * aws.faceDetection - Calls AWS Rekognition to detect faces in images
  * aws.celebrityDetection - Calls AWS Rekognition to detect celebrity faces in images
  * aws.translate.* - Calls AWS Translate to translate text
  * aws.documentText - Calls AWS Textract to detect text in a document image
  * aws.documentAnalyze - Calls AWS Textract to analyze text that's detected in a document image
  
#### Credentials
Credentials are discovered using `nuxeo-runtime-aws`.
The chain searches for credentials in order: Nuxeo's AWSConfigurationService, environment variables, system properties, profile credentials, EC2Container credentials.

If you choose to use nuxeo.conf then the properties are:
```
nuxeo.aws.accessKeyId=your_AWS_ACCESS_KEY_ID
nuxeo.aws.secretKey=your_AWS_SECRET_ACCESS_KEY
nuxeo.aws.sessionToken=your_AWS_SESSION_TOKEN
nuxeo.aws.region=your_AWS_REGION
```

##### Overriding the default region
To specify that the AI AWS enrichment services use a different region from the `nuxeo-runtime-aws` config, you should add a contribution like this:
```
<extension target="org.nuxeo.runtime.aws.AWSConfigurationService" point="configuration">
  <configuration id="nuxeo-ai-aws">
    <region>MY_REGION</region>
  </configuration>
</extension>
  
<extension target="org.nuxeo.runtime.ConfigurationService" point="configuration">
  <property name="nuxeo.enrichment.aws.s3">false</property>
</extension>
```

If you are only using images and an S3 BinaryManager is already being used then it re-uses the image data to pass a reference instead of uploading the binary again.
The configuration above turns off S3 BinaryManager re-use because a different region is being used.

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

    For AWS Textract only, the correct parameters are:
    ```
    nuxeo.ai.images.enabled=true
    nuxeo.enrichment.aws.document.text=true
    nuxeo.enrichment.aws.document.analyze=false
    nuxeo.enrichment.save.facets=true
    ```

3. Set your AWS credentials [AWS credentials](#credentials).
4. Start Nuxeo and upload an image.
5. Wait 10 seconds then look at the document tags and document json `enrichment:items` facet
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
<td colspan="1">`nuxeo.enrichment.aws.images`</td>
<td colspan="1">Run AWS enrichiment services on images.</td>
<td colspan="1">`false`</td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.enrichment.aws.text`</td>
<td colspan="1">Run AWS enrichiment services on text.</td>
<td colspan="1">`false`</td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.enrichment.aws.document.text`</td>
<td colspan="1">Run AWS Textract Detect Document Text API on document images.</td>
<td colspan="1">`false`</td>
<td colspan="1">Since 2.1.2</td>
</tr>
<tr>
<td colspan="1">`nuxeo.enrichment.aws.document.analyze`</td>
<td colspan="1">Run AWS Textract Analyze Document API on document images.</td>
<td colspan="1">`false`</td>
<td colspan="1">Since 2.1.2</td>
</tr>
</tbody>
</table>
</div>

# License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

# About Nuxeo

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Netflix, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris. More information is available at www.nuxeo.com.
