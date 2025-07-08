# **TensorFlow Example Protos**

`Example` and `Feature` are Protobuf message types defined in TensorFlow that serve as a flexible, efficient, and language-neutral format for serializing data—especially useful when handling large datasets for ML training or inference.
This directory contains .proto files used to generate Java classes for TensorFlow's Example and Feature message types.

### 📌 Why regenerate Java files?

* Previously, org.tensorflow.proto dependency was used to access the `Example` and `Feature` message types. The code generated there was old and it did not support newer protobuf versions.
* Regenerating ensures compatibility when upgrading to Protobuf newer versions.
* It avoids stale or incompatible code and ensures the Java sources reflect any proto changes.
* To mitigate the DOS vulnerability due to old java classes generated using older version of protoc. Ref - https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-h4h5-3hr4-j3g2

#### 🗂️ Java Output Location

Generated Java files are placed here:

$HOME/nuxeo-ai/nuxeo-ai-core/src/main/java/org/tensorflow/example

This mirrors the package org.tensorflow.example; defined inside the .proto files.

### 🔄 Regeneration Process

When updating Protobuf versions (e.g., upgrading beyond 4.29.3), follow these steps:

1. Prerequisites:
    
   Ensure protoc is installed (v3.x+ or 4.x+). If not, run `brew install protobuf`. Check `protoc --version`.

    Ensure protobuf-java in project parent pom.xml. It should match the version of protoc.


2. Clean up old generated files:
   
   `rm -f $HOME/nuxeo-ai/nuxeo-ai-core/src/main/java/org/tensorflow/example/*.java`


3. Generate new Java files:
   
    Navigate to directory $HOME/nuxeo-ai/nuxeo-ai-core

    `protoc 
  -I=src/main/proto 
  --java_out=src/main/java \
  src/main/proto/tensorflow/core/example/example.proto \
  src/main/proto/tensorflow/core/example/feature.proto
    `

The generated files include:

* Example.java

* Feature.java

* additional nested types defined in these .proto files.

4. Verify:

   Ensure the new .java files exist in the org/tensorflow/example directory and compile without errors.

### ✅ Summary of Commands

#### Navigate to project root directory
`cd $HOME/nuxeo-ai/nuxeo-ai-core/`

#### Remove old generated Java sources
`rm -f src/main/java/org/tensorflow/example/*.java`

#### Regenerate Java from proto files
`protoc 
  -I=src/main/proto 
  --java_out=src/main/java \
  src/main/proto/tensorflow/core/example/example.proto \
  src/main/proto/tensorflow/core/example/feature.proto`

#### Build project from $HOME/nuxeo-ai
`mvn clean install`


