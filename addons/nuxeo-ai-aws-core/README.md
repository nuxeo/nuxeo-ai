# Nuxeo AI AWS integration

Currently, provides the following enrichment services:

* aws.imageLabels - Calls AWS Rekognition for object detection in images
* aws.textSentiment - Call AWS Comprehend for sentiment analysis
* aws.textKeyphrase - Call AWS Comprehend for keyphrase extraction
* aws.textEntities - Call AWS Comprehend for entities analysis
* aws.unsafeImages - Calls AWS Rekognition to detect unsafe images
* aws.textDetection - Calls AWS Rekognition to detect text in images
* aws.faceDetection - Calls AWS Rekognition to detect faces in images
* aws.celebrityDetection - Calls AWS Rekognition to detect celebrity faces in images
* aws.translate.* - Calls AWS Translate to translate text
* aws.documentText - Calls AWS Textract to detect text in a document image
* aws.documentAnalyze - Calls AWS Textract to analyze text that's detected in a document image
* aws.videoLabels - Calls AWS Rekognition for object detection in videos
* aws.unsafeVideo - Calls AWS Rekognition to detect unsafe videos
* aws.videoFaceDetection - Calls AWS Rekognition to detect faces in videos
* aws.videoCelebrityDetection - Calls AWS Rekognition to detect celebrity faces in videos

#### Credentials

Credentials are discovered using `nuxeo-runtime-aws`. The chain searches for credentials in order: Nuxeo's
AWSConfigurationService, environment variables, system properties, profile credentials, EC2Container credentials.

If you choose to use nuxeo.conf then the properties are:

```
nuxeo.aws.accessKeyId=your_AWS_ACCESS_KEY_ID
nuxeo.aws.secretKey=your_AWS_SECRET_ACCESS_KEY
nuxeo.aws.sessionToken=your_AWS_SESSION_TOKEN
nuxeo.aws.region=your_AWS_REGION
```

##### Overriding the default region

To specify that the AI AWS enrichment services use a different region from the `nuxeo-runtime-aws` config, you should
add a contribution like this:

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

If you are only using images and an S3 BinaryManager is already being used then it re-uses the image data to pass a
reference instead of uploading the binary again. The configuration above turns off S3 BinaryManager re-use because a
different region is being used.

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

To enable AWS Transcribe on video, enable it via:

```
nuxeo.enrichment.aws.transcribe.enabled=true
```

along with enabled video pipeline

```
nuxeo.ai.video.enabled=true 
```

Transcribe works via the same enrichment pipeline and detects language automatically. Despite that, you might want to
override the default Enrichment provider and supply a list of languages that might appear on the video

```xml

<extension target="org.nuxeo.runtime.stream.service" point="streamProcessor">
  <streamProcessor name="videoCelebrityProcessor" defaultConcurrency="1" defaultPartitions="4"
                   logConfig="${nuxeo.ai.stream.config.name}"
                   class="org.nuxeo.ai.enrichment.EnrichingStreamProcessor">
    <option name="source">videos</option>
    <option name="sink">enrichment.in</option>
    <option name="enrichmentProviderName">aws.transcribe</option>
    <option name="languages">af-ZA,ar-AE,ar-SA</option>
    <option name="cache">${nuxeo.enrichment.aws.cache}</option>
  </streamProcessor>
</extension>
```
For Language Options refer to [AWS Documentation](https://docs.aws.amazon.com/transcribe/latest/dg/API_StartTranscriptionJob.html#API_StartTranscriptionJob_RequestSyntax)

For AWS Rekognition video analysis:
Video analysis relies on AWS SNS. Make sure the used role has permissions to create SNS topics. SNS will send a
notification upon completion to the defined endpoint at
`https://your_host/nuxeo/site/aiaddons/rekognition/callback/labels`

To be able resolving HOST `nuxeo.url` must be provided in `nuxeo.conf` with pattern matches `http[s]://host:[port]`

Create a service role that will be able to push to an SNS topic. Example:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "RandomSidValue",
      "Effect": "Allow",
      "Action": "SNS:Publish",
      "Resource": "arn:aws:sns:*:*:*"
    }
  ]
}
```

Given role allows to publish to any topic.
`ai-dev-rekognition-sns` - was created for Nuxeo developers under `test-role`

ARN of the role must be a part of `nuxeo.conf`. It will be used by Rekognition to push messages to SNS

```yaml
nuxeo.ai.aws.rekognition.role.arn=arn:aws:iam::your_profile:role/role_name
```

To enable video you need to create a topic in the same region as your base services and add following properties

```yaml
 nuxeo.ai.video.enabled=true
 nuxeo.enrichment.aws.sns.topic.arn=arn:aws:sns:[REGION]:[ID]:[TOPIC_NAME]
```

Where `nuxeo.enrichment.aws.sns.topic.arn` is the topic ARN that can be obtained through AWS CLI with
`aws sns list-topics` or through AWS admin center upon topic creation

##### Video analysis limitation

AWS Rekognition supports only MOV and MP4 files Size limitation is 8GB

SNS will try to reach the endpoint 3 more times on unsuccessful delivery with timeout of 20 seconds

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
<td colspan="1">Run AWS enrichment services on images.</td>
<td colspan="1">`false`</td>
<td colspan="1">Since 1.0</td>
</tr>
<tr>
<td colspan="1">`nuxeo.enrichment.aws.text`</td>
<td colspan="1">Run AWS enrichment services on text.</td>
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
<tr>
<td colspan="1">`nuxeo.enrichment.aws.video`</td>
<td colspan="1">Run AWS enrichment service on video.</td>
<td colspan="1">`false`</td>
<td colspan="1">Since 2.2.0</td>
</tr>
</tbody>
</table>
</div>

# License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

# About Nuxeo

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile,
innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and
cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based
tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most
recognizable brands including Verizon, Electronic Arts, Netflix, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is
headquartered in New York and Paris. More information is available at www.nuxeo.com.
