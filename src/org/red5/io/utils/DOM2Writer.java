package org.red5.io.utils;

/*
 * Copyright 2001-2004 The Apache Software Foundation.
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

import java.io.PrintWriter;
import java.io.Writer;

import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class is a utility to serialize a DOM node as XML. This class uses the
 * <code>DOM Level 2</code> APIs. The main difference between this class and
 * DOMWriter is that this class generates and prints out namespace declarations.
 * 
 * @author Matthew J. Duftler (duftler@us.ibm.com)
 * @author Joseph Kesselman
 */
public class DOM2Writer {

	/**
	 * Serialize this node into the writer as XML.
	 */
	public static void serializeAsXML(Node node, Writer writer) {
		PrintWriter out = new PrintWriter(writer);
		print(node, node, out);
		out.flush();
	}

	private static void print(Node node, Node startnode, PrintWriter out) {
		if (node == null) {
			return;
		}

		boolean hasChildren = false;
		int type = node.getNodeType();

		switch (type) {
			case Node.DOCUMENT_NODE: {
				NodeList children = node.getChildNodes();

				if (children != null) {
					int numChildren = children.getLength();

					for (int i = 0; i < numChildren; i++) {
						print(children.item(i), startnode, out);
					}
				}
				break;
			}

			case Node.ELEMENT_NODE: {

				out.print('<' + node.getNodeName());

				NamedNodeMap attrs = node.getAttributes();
				int len = (attrs != null) ? attrs.getLength() : 0;

				for (int i = 0; i < len; i++) {
					Attr attr = (Attr) attrs.item(i);

					out.print(' ' + attr.getNodeName() + "=\""
							+ attr.getValue() + '\"');

				}

				NodeList children = node.getChildNodes();

				if (children != null) {
					int numChildren = children.getLength();

					hasChildren = (numChildren > 0);

					if (hasChildren) {
						out.print('>');
					}

					for (int i = 0; i < numChildren; i++) {
						print(children.item(i), startnode, out);
					}
				} else {
					hasChildren = false;
				}

				if (!hasChildren) {
					out.print("/>");
				}
				break;
			}

			case Node.ENTITY_REFERENCE_NODE: {
				out.print('&');
				out.print(node.getNodeName());
				out.print(';');
				break;
			}

			case Node.CDATA_SECTION_NODE: {
				out.print("<![CDATA[");
				out.print(node.getNodeValue());
				out.print("]]>");
				break;
			}

			case Node.TEXT_NODE: {
				out.print(node.getNodeValue());
				break;
			}
		}
		if (type == Node.ELEMENT_NODE && hasChildren == true) {
			out.print("</");
			out.print(node.getNodeName());
			out.print('>');
			hasChildren = false;
		}
	}
}