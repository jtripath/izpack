/*
 * IzPack - Copyright 2001-2010 Julien Ponge, All Rights Reserved.
 *
 * http://izpack.org/
 * http://izpack.codehaus.org/
 *
 * Copyright 2009 Laurent Bovet, Alex Mathey
 * Copyright 2010 Rene Krell
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
 */

package com.izforge.izpack.util.xmlmerge.factory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jaxen.JaxenException;
import org.jaxen.jdom.JDOMXPath;
import org.jdom.Element;

import com.izforge.izpack.util.xmlmerge.*;

/**
 * An operation factory that resolves operations given a map { xpath (as String), Operation }. The
 * order in the map is relevant if several XPath matches.
 *
 * @author Laurent Bovet (LBO)
 * @author Alex Mathey (AMA)
 */
public class XPathOperationFactory implements OperationFactory
{

    /**
     * A map containing configuration properties.
     */
    Map<String, Operation> m_map = new HashMap<String, Operation>();

    /**
     * The default operation returned by this factory.
     */
    Operation m_defaultOperation;

    /**
     * Sets the factory's map containing configuration properties.
     *
     * @param map A map containing configuration properties.
     */
    public void setOperationMap(Map<String, Operation> map)
    {
        this.m_map = map;
    }

    /**
     * Sets the default operation returned by this factory.
     *
     * @param operation The default operation returned by this factory.
     */
    public void setDefaultOperation(Operation operation)
    {
        this.m_defaultOperation = operation;
    }

    /**
     * {@inheritDoc}
     */
    public Operation getOperation(Element originalElement, Element patchElement)
            throws AbstractXmlMergeException
    {
        for (String xPath : m_map.keySet())
        {
            if (matches(originalElement, xPath) || matches(patchElement, xPath))
            {
                return m_map.get(xPath);
            }
        }
        return m_defaultOperation;
    }

    /**
     * Detects whether the given element matches the given XPath string.
     *
     * @param element The element which will be checked
     * @param xPathString The XPath expression the element will be checked against
     * @return True if the given element matches the given XPath string
     * @throws AbstractXmlMergeException If an error occurred during the matching process
     */
    private boolean matches(Element element, String xPathString) throws AbstractXmlMergeException
    {

        if (element == null) { return false; }

        try
        {
            JDOMXPath xPath = new JDOMXPath(xPathString);

            boolean result = xPath.selectNodes(element.getParent()).contains(element);

            return result;

        }
        catch (JaxenException e)
        {
            throw new MatchException(element, e);
        }
    }

}
