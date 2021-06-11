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
import org.nuxeo.ai.pipes.types.ExportRecord;

/**
 * Simple writer that doesn't work
 */
public class BadRecordWriter extends AbstractRecordWriter {

    public BadRecordWriter(String name, String bad) {
        super(name);
    }

    @Override
    public long write(List<ExportRecord> list) {
        // Will never get here
        return 99;
    }

    @Override
    public boolean write(ExportRecord record) throws IOException {
        return false;
    }
}
