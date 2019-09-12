/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.sns;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A POJO representing AWS SNS Notification received upon completion of a job
 */
public class Notification {

    public static final String STATUS_SUCCEEDED = "SUCCEEDED";

    protected String type;

    protected String topicArn;

    protected String messageId;

    protected String message;

    protected String timestamp;

    protected String signatureVersion;

    protected String signature;

    protected String signCertURL;

    protected String unsubscribeURL;

    public Notification(@JsonProperty("Type") String type, @JsonProperty("TopicArn") String topicArn,
                        @JsonProperty("MessageId") String messageId, @JsonProperty("Message") String message,
                        @JsonProperty("Timestamp") String timestamp, @JsonProperty("SignatureVersion") String version,
                        @JsonProperty("Signature") String sign, @JsonProperty("SigningCertURL") String certURL,
                        @JsonProperty("UnsubscribeURL") String unsubscribeURL) {
        this.type = type;
        this.topicArn = topicArn;
        this.messageId = messageId;
        this.message = message;
        this.timestamp = timestamp;
        this.signatureVersion = version;
        this.signature = sign;
        this.signCertURL = certURL;
        this.unsubscribeURL = unsubscribeURL;
    }


    public String type() {
        return type;
    }

    public String topicArn() {
        return topicArn;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Notification that = (Notification) o;
        return type.equals(that.type) &&
                topicArn.equals(that.topicArn) &&
                messageId.equals(that.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, topicArn, messageId);
    }

    /**
     * POJO representing Job Details
     */
    public static class Message {

        protected String jobId;

        protected String status;

        protected String api;

        protected String jobTag;

        protected long timestamp;

        protected Video video;

        public Message(@JsonProperty("JobId") String jobId, @JsonProperty("Status") String status,
                       @JsonProperty("API") String api, @JsonProperty("JobTag") String jobTag,
                       @JsonProperty("Timestamp") long timestamp, @JsonProperty("Video") Video video) {
            this.jobId = jobId;
            this.status = status;
            this.api = api;
            this.jobTag = jobTag;
            this.timestamp = timestamp;
            this.video = video;
        }

        public String getJobId() {
            return jobId;
        }

        public String getStatus() {
            return status;
        }

        public String getApi() {
            return api;
        }

        public String getJobTag() {
            return jobTag;
        }

        public long getTimestamp() {
            return timestamp;
        }

        /**
         * @return true on successful end of job
         */
        public boolean isSucceeded() {
            return STATUS_SUCCEEDED.equals(status);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Message message = (Message) o;
            return jobId.equals(message.jobId) &&
                    status.equals(message.status) &&
                    api.equals(message.api) &&
                    jobTag.equals(message.jobTag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jobId, status, api, jobTag);
        }
    }

    /**
     * POJO representing s3 reference of the processed file
     */
    public static class Video {

        protected String objectName;

        protected String bucket;

        public Video(@JsonProperty("S3ObjectName") String objectName, @JsonProperty("S3Bucket") String bucket) {
            this.objectName = objectName;
            this.bucket = bucket;
        }

        public String getObjectName() {
            return objectName;
        }

        public String getBucket() {
            return bucket;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Video video = (Video) o;
            return Objects.equals(objectName, video.objectName) &&
                    Objects.equals(bucket, video.bucket);
        }

        @Override
        public int hashCode() {
            return Objects.hash(objectName, bucket);
        }
    }
}
