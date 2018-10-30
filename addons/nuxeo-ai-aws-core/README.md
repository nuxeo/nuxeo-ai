
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
