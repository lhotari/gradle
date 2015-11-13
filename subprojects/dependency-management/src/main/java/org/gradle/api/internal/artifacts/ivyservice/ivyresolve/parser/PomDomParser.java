/*
 * Copyright 2014 the original author or authors.
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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.EntityReference;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.EventReaderDelegate;
import javax.xml.stream.util.StreamReaderDelegate;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stax.StAXSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class PomDomParser {
    private PomDomParser() {}

    public static String getTextContent(Element element) {
        StringBuilder result = new StringBuilder();

        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);

            switch (child.getNodeType()) {
                case Node.CDATA_SECTION_NODE:
                case Node.TEXT_NODE:
                    result.append(child.getNodeValue());
                    break;
                default:
                    break;
            }
        }

        return result.toString();
    }

    public static String getFirstChildText(Element parentElem, String name) {
        Element node = getFirstChildElement(parentElem, name);
        if (node != null) {
            return getTextContent(node);
        } else {
            return null;
        }
    }

    public static Element getFirstChildElement(Element parentElem, String name) {
        if (parentElem == null) {
            return null;
        }
        NodeList childs = parentElem.getChildNodes();
        for (int i = 0; i < childs.getLength(); i++) {
            Node node = childs.item(i);
            if (node instanceof Element && name.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    public static List<Element> getAllChilds(Element parent) {
        List<Element> r = new LinkedList<Element>();
        if (parent != null) {
            NodeList childs = parent.getChildNodes();
            for (int i = 0; i < childs.getLength(); i++) {
                Node node = childs.item(i);
                if (node instanceof Element) {
                    r.add((Element) node);
                }
            }
        }
        return r;
    }

    private static final XMLInputFactory XML_INPUT_FACTORY = createStaxXmlInputFactory();
    private static final Map<String, String> M2_ENTITIES_MAP = new M2EntitiesMap();
    private static final String BUNDLED_XALAN_TRANSFORMER_FACTORY_CLASS_NAME = "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";
    private static final TransformerFactory TRAX_FACTORY = createTransformerFactory();
    public static final boolean SUPPORTS_REQUIRED_STAX_FEATURES = doesSupportRequiredStaxFeatures(TRAX_FACTORY);

    public static Document parseToDom(InputStream stream, String systemId) throws XMLStreamException, TransformerException, IOException, SAXException {
        if (SUPPORTS_REQUIRED_STAX_FEATURES) {
            final XMLStreamReader xmlStreamReader = XML_INPUT_FACTORY.createXMLStreamReader(systemId, stream);
            return stax2dom(decorateWithM2EntityReplacement(xmlStreamReader));
        } else {
            // IBM Java 6 doesn't contain sufficient STAX support
            // copy https://repo1.maven.org/maven2/com/sun/xml/parsers/jaxp-ri/1.4.5/jaxp-ri-1.4.5.jar to JAVA_HOME/jre/lib/endorsed
            // when the support is needed on IBM Java 6

            // fallback to DOM parser without any m2-entities.ent support
            return createDocumentBuilder().parse(stream, systemId);
        }
    }

    // Ivy supports handcrafted pom xml files that use HTML4 entities like &copy;
    // This is an efficient way to resolve the entities without using the m2entities.ent DTD injection hack that Ivy uses
    private static XMLEventReader decorateWithM2EntityReplacement(final XMLStreamReader xmlStreamReader) throws XMLStreamException {
        return decorateWithEntitiesReplacement(xmlStreamReader, M2_ENTITIES_MAP);
    }

    private static XMLEventReader decorateWithEntitiesReplacement(final XMLStreamReader xmlStreamReader, final Map<String, String> entitiesMap) throws XMLStreamException {
        return new EventReaderDelegate(XML_INPUT_FACTORY.createXMLEventReader(decorateWithXmlVersionNullCheck(xmlStreamReader))) {
            @Override
            public XMLEvent nextEvent() throws XMLStreamException {
                return interceptEvent(super.nextEvent());
            }

            @Override
            public XMLEvent peek() throws XMLStreamException {
                return interceptEvent(super.peek());
            }

            private XMLEvent interceptEvent(XMLEvent xmlEvent) {
                if (xmlEvent.isEntityReference()) {
                    String entityName = ((EntityReference) xmlEvent).getName();
                    String replacement = entityName != null ? entitiesMap.get(entityName) : "";
                    return new CharactersXMLEvent(xmlEvent.getLocation(), replacement != null ? replacement : "");
                } else {
                    return xmlEvent;
                }
            }
        };
    }

    private static XMLStreamReader decorateWithXmlVersionNullCheck(final XMLStreamReader xmlStreamReader) {
        return new StreamReaderDelegate(xmlStreamReader) {
            @Override
            public String getVersion() {
                // make sure version is never null. Stax to DOM conversion fails with NPE without this.
                String version = super.getVersion();
                return version != null ? version : "1.0";
            }
        };
    }

    private static Document stax2dom(XMLEventReader eventReader) throws XMLStreamException, TransformerException {
        StAXSource source = new StAXSource(eventReader);
        return source2dom(TRAX_FACTORY, source);
    }

    private static Document source2dom(TransformerFactory traxFactory, Source xmlSource) throws TransformerException {
        DOMResult result = new DOMResult();
        final Transformer transformer = traxFactory.newTransformer();
        final AtomicReference<TransformerException> firstErrorHolder = new AtomicReference<TransformerException>();
        transformer.setErrorListener(new ErrorListener() {
            @Override
            public void warning(TransformerException exception) throws TransformerException {

            }

            @Override
            public void error(TransformerException exception) throws TransformerException {
                recordFirstError(exception);
            }

            @Override
            public void fatalError(TransformerException exception) throws TransformerException {
                recordFirstError(exception);
            }

            private void recordFirstError(TransformerException exception) {
                if (firstErrorHolder.get() == null) {
                    firstErrorHolder.set(exception);
                }
            }
        });
        transformer.transform(xmlSource, result);
        if (firstErrorHolder.get() != null) {
            throw firstErrorHolder.get();
        }
        return (Document) result.getNode();
    }

    private static XMLInputFactory createStaxXmlInputFactory() {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        inputFactory.setProperty(XMLInputFactory.IS_VALIDATING, false);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return inputFactory;
    }

    // Attempt to locate the Xalan Transformer bundled in the JVM, because
    // there might be an older Xalan version on the user's classpath which doesn't support StAXSource
    private static TransformerFactory createTransformerFactory() {
        ClassLoader classLoader = TransformerFactory.class.getClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        Class<?> bundledXalanTransformerFactoryClazz = null;
        try {
            bundledXalanTransformerFactoryClazz = Class.forName(BUNDLED_XALAN_TRANSFORMER_FACTORY_CLASS_NAME, false, classLoader);
        } catch (ClassNotFoundException e) {
            // ignore
        }
        if (bundledXalanTransformerFactoryClazz != null) {
            try {
                return TransformerFactory.newInstance(bundledXalanTransformerFactoryClazz.getName(), classLoader);
            } catch (TransformerFactoryConfigurationError e) {
                return TransformerFactory.newInstance();
            } catch (Exception e) {
                return TransformerFactory.newInstance();
            }
        } else {
            return TransformerFactory.newInstance();
        }
    }

    // check that the given TransformerFactory support StAXSource and that Stax driver supports disabling IS_REPLACING_ENTITY_REFERENCES
    private static boolean doesSupportRequiredStaxFeatures(TransformerFactory traxFactory) {
        try {
            Document doc = source2dom(traxFactory, new StAXSource(decorateWithM2EntityReplacement(XML_INPUT_FACTORY.createXMLStreamReader(new StringReader("<supports_stax_and_entity_replacement>&copy;</supports_stax_and_entity_replacement>")))));
            return doc != null;
        } catch (Throwable t) {
            // ignore
        }
        return false;
    }

    private static DocumentBuilder createDocumentBuilder() {
        try {
            DocumentBuilder docBuilder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
            docBuilder.setEntityResolver(NEVER_RESOLVE_ENTITY_RESOLVER);
            return docBuilder;
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = createDocumentBuilderFactory();

    private static DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        return factory;
    }

    private static final EntityResolver NEVER_RESOLVE_ENTITY_RESOLVER = new EntityResolver() {
        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            return new InputSource(new InputStream() {
                @Override
                public int read() throws IOException {
                    return -1;
                }
            });
        }
    };
}
