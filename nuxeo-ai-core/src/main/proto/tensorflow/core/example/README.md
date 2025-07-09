# **TensorFlow Example Protos**

`Example` and `Feature` are Protobuf message types defined in TensorFlow that serve as a flexible, efficient, and language-neutral format for serializing data—especially useful when handling large datasets for ML training or inference.
This directory contains .proto files used to generate Java classes for TensorFlow's Example and Feature message types.

### 📌 Why regenerate Java files?

* Previously, org.tensorflow.proto dependency was used to access the `Example` and `Feature` message types. The code generated there was old and it did not support newer protobuf versions.
* Regenerating ensures compatibility when upgrading to Protobuf newer versions.
* It avoids stale or incompatible code and ensures the Java sources reflect any proto changes.
* To mitigate the DOS vulnerability due to old java classes generated using older version of protoc. Ref - https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-h4h5-3hr4-j3g2

#### 🗂️ Java Output Location

Generated Java files will be placed here after build:

$HOME/nuxeo-ai/nuxeo-ai-core/target/generated-sources/protobuf

This mirrors the package org.tensorflow.example; defined inside the .proto files.

### 🔄 Generation Process

Java classes are **automatically generated during the Maven build process** using the `protobuf-maven-plugin`. This eliminates the need for manual `protoc` commands and ensures consistency with the Protobuf version defined in the project.

The generated files include:

* Example.java

* Feature.java

* additional nested types defined in these .proto files.

### 🔄 How it works

- The Maven plugin automatically detects `.proto` files in `src/main/proto`.
- It uses the `protoc` compiler (version defined in the parent `pom.xml`) to generate Java classes.
- The generated sources are added to the build path via the `build-helper-maven-plugin`.
- No manual cleanup or regeneration is required.

### 🛠️ When to Regenerate

If you update the `.proto` files or upgrade the Protobuf version in the parent `pom.xml`, simply run:
`mvn clean install`

>[!WARNING]
>
>**Do not change the location of the `.proto` files. This might fail the build. If you need to change it at all, make the required changes in `$HOME/nuxeo-ai/nuxeo-ai-core/pom.xml`**
