/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.xml;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.xml.XmlValidationModeDetector;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Spring's default {@link DocumentLoader} implementation.
 *
 * <p>Simply loads {@link Document documents} using the standard JAXP-configured
 * XML parser. If you want to change the {@link DocumentBuilder} that is used to
 * load documents, then one strategy is to define a corresponding Java system property
 * when starting your JVM. For example, to use the Oracle {@link DocumentBuilder},
 * you might start your application like as follows:
 *
 * <pre code="class">java -Djavax.xml.parsers.DocumentBuilderFactory=oracle.xml.jaxp.JXDocumentBuilderFactory MyMainClass</pre>
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 2.0
 */
public class DefaultDocumentLoader implements DocumentLoader {

	/**
	 * JAXP attribute used to configure the schema language for validation.
	 */
	private static final String SCHEMA_LANGUAGE_ATTRIBUTE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";

	/**
	 * JAXP attribute value indicating the XSD schema language.
	 */
	private static final String XSD_SCHEMA_LANGUAGE = "http://www.w3.org/2001/XMLSchema";


	private static final Log logger = LogFactory.getLog(DefaultDocumentLoader.class);


	/**
	 * Load the {@link Document} at the supplied {@link InputSource} using the standard JAXP-configured
	 * XML parser.
	 * 使用标准 JAXP 配置的 XML 解析器在提供的 {@link InputSource} 加载 {@link Document}。
	 */
	@Override
	public Document loadDocument(InputSource inputSource, EntityResolver entityResolver,
								 ErrorHandler errorHandler, int validationMode, boolean namespaceAware) throws Exception {

		DocumentBuilderFactory factory = createDocumentBuilderFactory(validationMode, namespaceAware);
		if (logger.isTraceEnabled()) {
			logger.trace("Using JAXP provider [" + factory.getClass().getName() + "]");
		}
		// com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderImpl
		DocumentBuilder builder = createDocumentBuilder(factory, entityResolver, errorHandler);
		/**
		 * 1、里面做了一件非常重要的事情：
		 * 调用了ResourceEntityResolver#resolveEntity() ——> DelegatingEntityResolver#resolveEntity() ——> ️PluggableSchemaResolver#resolveEntity()
		 *
		 * （1）通过"xsd文件的网络位置"获取"本地xsd文件位置"，然后再通过位置获取"本地xsd文件"，的源码在PluggableSchemaResolver#resolveEntity()中
		 *
		 * 里面先是调用了PluggableSchemaResolver#getSchemaMappings()，获取了所有项目下的【spring.schemas】文件里面的内容；
		 * 然后再根据所有"spring.schemas"文件所配置的内容，通过"xsd文件的网络位置"获取"本地xsd文件位置"；
		 * 通过"本地xsd文件位置"获取对应文件的输入流，然后通过"输入流"读取xsd文件
		 *
		 * （2）通过"xsd文件的网络位置"，发送连网请求去获取"对应的xsd文件"，的源码在ResourceEntityResolver#resolveEntity()中
		 *
		 * 先是调用了DelegatingEntityResolver#resolveEntity() ——> ️PluggableSchemaResolver#resolveEntity()，去获取对应的本地的xsd文件，
		 * 如果没有发送连网请求去获取"对应的xsd文件"的输入流，然后通过"输入流"读取xsd文件；
		 * 如果连网请求，还没有获取到对应的"xsd文件"，就会报FileNotFoundException异常！
		 *
		 * （3）最终通过获取到的xsd文件所定义的规范来校验自己的格式！最终形成一个document。（这些是jdk内部自己完成的！）
		 *
		 * 2、题外：spring.schemas里面的配置的内容是：xsd文件所在的网络位置=本地xsd文件位置
		 */
		return builder.parse(inputSource);
	}

	/**
	 * Create the {@link DocumentBuilderFactory} instance.
	 *
	 * @param validationMode the type of validation: {@link XmlValidationModeDetector#VALIDATION_DTD DTD}
	 *                       or {@link XmlValidationModeDetector#VALIDATION_XSD XSD})
	 * @param namespaceAware whether the returned factory is to provide support for XML namespaces
	 * @return the JAXP DocumentBuilderFactory
	 * @throws ParserConfigurationException if we failed to build a proper DocumentBuilderFactory
	 */
	protected DocumentBuilderFactory createDocumentBuilderFactory(int validationMode, boolean namespaceAware)
			throws ParserConfigurationException {

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(namespaceAware);

		if (validationMode != XmlValidationModeDetector.VALIDATION_NONE/* 0：表示应禁用验证 */) {
			// 设置验证为true
			factory.setValidating(true);
			if (validationMode == XmlValidationModeDetector.VALIDATION_XSD/* 3：使用XSD验证 */) {
				// Enforce namespace aware for XSD... 对 XSD 强制命名空间感知...
				factory.setNamespaceAware(true);
				try {
					/**
					 * 参数1：属性名称
					 * 参数2：属性的值
					 */
					factory.setAttribute(SCHEMA_LANGUAGE_ATTRIBUTE/* http://java.sun.com/xml/jaxp/properties/schemaLanguage */
							, XSD_SCHEMA_LANGUAGE/* http://www.w3.org/2001/XMLSchema */);
				} catch (IllegalArgumentException ex) {
					ParserConfigurationException pcex = new ParserConfigurationException(
							"Unable to validate using XSD: Your JAXP provider [" + factory +
									"] does not support XML Schema. Are you running on Java 1.4 with Apache Crimson? " +
									"Upgrade to Apache Xerces (or Java 1.5) for full XSD support.");
					pcex.initCause(ex);
					throw pcex;
				}
			}
		}

		return factory;
	}

	/**
	 * Create a JAXP DocumentBuilder that this bean definition reader
	 * will use for parsing XML documents. Can be overridden in subclasses,
	 * adding further initialization of the builder.
	 *
	 * @param factory        the JAXP DocumentBuilderFactory that the DocumentBuilder
	 *                       should be created with
	 * @param entityResolver the SAX EntityResolver to use
	 * @param errorHandler   the SAX ErrorHandler to use
	 * @return the JAXP DocumentBuilder
	 * @throws ParserConfigurationException if thrown by JAXP methods
	 */
	protected DocumentBuilder createDocumentBuilder(DocumentBuilderFactory factory,
													@Nullable EntityResolver entityResolver, @Nullable ErrorHandler errorHandler)
			throws ParserConfigurationException {
		// DocumentBuilderImpl
		DocumentBuilder docBuilder = factory.newDocumentBuilder();
		if (entityResolver != null) {
			docBuilder.setEntityResolver(entityResolver);
		}
		if (errorHandler != null) {
			docBuilder.setErrorHandler(errorHandler);
		}
		return docBuilder;
	}

}
