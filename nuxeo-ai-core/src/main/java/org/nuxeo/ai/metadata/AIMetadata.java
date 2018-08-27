/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Gethin James
 */
package org.nuxeo.ai.metadata;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A normalized view of metadata returned by an intelligent service
 */
public abstract class AIMetadata implements Serializable {

    private static final long serialVersionUID = 4590486107702875095L;

    public final Instant created;

    public final String creator;

    public final String serviceName;

    public final Context context;

    public final String kind;

    public final String rawKey;

    public AIMetadata(String serviceName, String kind, Context context,
                      String creator, Instant created, String rawKey) {
        this.kind = kind;
        this.context = context;
        this.created = created;
        this.creator = creator;
        this.rawKey = rawKey;
        this.serviceName = serviceName;
    }

    public String getKind() {
        return kind;
    }

    public Instant getCreated() {
        return created;
    }

    public String getCreator() {
        return creator;
    }

    public String getRawKey() {
        return rawKey;
    }

    public String getServiceName() {
        return serviceName;
    }

    @JsonIgnore
    public boolean isHuman() {
        return StringUtils.isNotEmpty(creator);
    }

    public Context getContext() {
        return context;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        AIMetadata that = (AIMetadata) o;
        return Objects.equals(created, that.created) &&
                Objects.equals(creator, that.creator) &&
                Objects.equals(serviceName, that.serviceName) &&
                Objects.equals(kind, that.kind) &&
                Objects.equals(context, that.context) &&
                Objects.equals(rawKey, that.rawKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(created, creator, serviceName, kind, context, rawKey);
    }

    /**
     * The context about which the metadata was created
     */
    public static class Context implements Serializable {

        private static final long serialVersionUID = 3212595777234047338L;

        public final String repositoryName;

        public final String documentRef; //Document reference

        public final String blobDigest;

        public final Set<String> inputProperties;

        @JsonCreator
        public Context(@JsonProperty("repositoryName") String repositoryName,
                       @JsonProperty("documentRef") String documentRef,
                       @JsonProperty("blobDigest") String blobDigest,
                       @JsonProperty("inputProperties") Set<String> inputProperties) {
            this.repositoryName = repositoryName;
            this.documentRef = documentRef;
            this.blobDigest = blobDigest;
            this.inputProperties = inputProperties != null ? unmodifiableSet(inputProperties) : emptySet();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) { return true; }
            if (o == null || getClass() != o.getClass()) { return false; }
            Context context = (Context) o;
            return Objects.equals(repositoryName, context.repositoryName) &&
                    Objects.equals(documentRef, context.documentRef) &&
                    Objects.equals(blobDigest, context.blobDigest) &&
                    Objects.equals(inputProperties, context.inputProperties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(repositoryName, documentRef, blobDigest, inputProperties);
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("repositoryName", repositoryName)
                    .append("documentRef", documentRef)
                    .append("blobDigest", blobDigest)
                    .append("inputProperties", inputProperties)
                    .toString();
        }
    }

    /**
     * A label
     */
    public static class Label implements Serializable {

        private static final long serialVersionUID = 8838956163616827139L;

        private final String name;

        private final float confidence;

        @JsonCreator
        public Label(@JsonProperty("name") String name, @JsonProperty("confidence") float confidence) {
            this.name = name;
            this.confidence = confidence;
        }

        public String getName() {
            return name;
        }

        public float getConfidence() {
            return confidence;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Label label = (Label) o;
            return Float.compare(label.confidence, confidence) == 0 &&
                    Objects.equals(name, label.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, confidence);
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("name", name)
                    .append("confidence", confidence)
                    .toString();
        }
    }

    /**
     * A tag with location
     */
    public static class Tag implements Serializable {
        private static final long serialVersionUID = -5340157925873173455L;

        public final String name;

        public final String kind;

        public final String reference;

        public final Box box;

        public final float confidence;

        public final List<Label> features;

        @JsonCreator
        public Tag(@JsonProperty("name") String name,
                   @JsonProperty("kind") String kind,
                   @JsonProperty("reference") String reference,
                   @JsonProperty("box") Box box,
                   @JsonProperty("features") List<Label> features,
                   @JsonProperty("confidence") float confidence) {
            this.name = name;
            this.kind = kind;
            this.reference = reference;
            this.box = box;
            this.confidence = confidence;
            if (features == null) {
                this.features = emptyList();
            } else {
                this.features = unmodifiableList(features);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) { return true; }
            if (o == null || getClass() != o.getClass()) { return false; }
            Tag tag = (Tag) o;
            return Float.compare(tag.confidence, confidence) == 0 &&
                    Objects.equals(name, tag.name) &&
                    Objects.equals(kind, tag.kind) &&
                    Objects.equals(reference, tag.reference) &&
                    Objects.equals(box, tag.box) &&
                    Objects.equals(features, tag.features);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, kind, reference, box, confidence, features);
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("name", name)
                    .append("kind", kind)
                    .append("reference", reference)
                    .append("box", box)
                    .append("confidence", confidence)
                    .append("features", features)
                    .toString();
        }
    }

    /**
     * A Bounding box
     */
    public static class Box implements Serializable {
        private static final long serialVersionUID = -6230967082128905676L;

        public final float width;

        public final float height;

        public final float left;

        public final float top;

        @JsonCreator
        public Box(@JsonProperty("width") float width,
                   @JsonProperty("height") float height,
                   @JsonProperty("left") float left,
                   @JsonProperty("top") float top) {
            this.width = width;
            this.height = height;
            this.left = left;
            this.top = top;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) { return true; }
            if (o == null || getClass() != o.getClass()) { return false; }
            Box box = (Box) o;
            return Float.compare(box.width, width) == 0 &&
                    Float.compare(box.height, height) == 0 &&
                    Float.compare(box.left, left) == 0 &&
                    Float.compare(box.top, top) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(width, height, left, top);
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("width", width)
                    .append("height", height)
                    .append("left", left)
                    .append("top", top)
                    .toString();
        }
    }
}
