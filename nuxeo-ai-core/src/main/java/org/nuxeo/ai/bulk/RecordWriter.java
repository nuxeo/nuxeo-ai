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
package org.nuxeo.ai.bulk;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.lib.stream.computation.Record;

/**
 * Writes stream records
 */
public interface RecordWriter {

    /**
     * Finish writing to the file, save it and return an optional reference.
     */
    Optional<Blob> complete(String id) throws IOException;

    /**
     * Write the records using this writer.  It is assumed that the implementation writes to a file.
     */
    void write(List<Record> list) throws IOException;

    /**
     * Indicates if a file exists for this id
     */
    boolean exists(String id);

}
