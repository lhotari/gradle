/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import java.io.IOException;
import java.io.Writer;

class CharactersXMLEvent implements Characters {
    private final Location location;
    private final String data;

    CharactersXMLEvent(Location location, String data) {
        this.location = location;
        this.data = data;
    }

    @Override
    public Location getLocation() {
        return location;
    }

    @Override
    public String getData() {
        return data;
    }

    @Override
    public int getEventType() {
        return XMLStreamConstants.CHARACTERS;
    }

    @Override
    public boolean isStartElement() {
        return false;
    }

    @Override
    public boolean isAttribute() {
        return false;
    }

    @Override
    public boolean isNamespace() {
        return false;
    }

    @Override
    public boolean isEndElement() {
        return false;
    }

    @Override
    public boolean isEntityReference() {
        return false;
    }

    @Override
    public boolean isProcessingInstruction() {
        return false;
    }

    @Override
    public boolean isCharacters() {
        return true;
    }

    @Override
    public boolean isStartDocument() {
        return false;
    }

    @Override
    public boolean isEndDocument() {
        return false;
    }

    @Override
    public StartElement asStartElement() {
        return null;
    }

    @Override
    public EndElement asEndElement() {
        return null;
    }

    @Override
    public Characters asCharacters() {
        return this;
    }

    @Override
    public QName getSchemaType() {
        return null;
    }

    @Override
    public void writeAsEncodedUnicode(Writer writer) throws XMLStreamException {
        try {
            writer.write(getData());
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    @Override
    public boolean isWhiteSpace() {
        return false;
    }

    @Override
    public boolean isCData() {
        return false;
    }

    @Override
    public boolean isIgnorableWhiteSpace() {
        return false;
    }

    @Override
    public String toString() {
        return getData();
    }
}
