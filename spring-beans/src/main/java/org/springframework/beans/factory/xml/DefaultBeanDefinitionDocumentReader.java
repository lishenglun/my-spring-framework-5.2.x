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
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Default implementation of the {@link BeanDefinitionDocumentReader} interface that
 * reads bean definitions according to the "spring-beans" DTD and XSD format
 * (Spring's default XML bean definition format).
 *
 * <p>The structure, elements, and attribute names of the required XML document
 * are hard-coded in this class. (Of course a transform could be run if necessary
 * to produce this format). {@code <beans>} does not need to be the root
 * element of the XML document: this class will parse all bean definition elements
 * in the XML file, regardless of the actual root element.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Erik Wiersma
 * @since 18.12.2003
 */
public class DefaultBeanDefinitionDocumentReader implements BeanDefinitionDocumentReader {

	public static final String BEAN_ELEMENT = BeanDefinitionParserDelegate.BEAN_ELEMENT;

	public static final String NESTED_BEANS_ELEMENT = "beans";

	public static final String ALIAS_ELEMENT = "alias";

	public static final String NAME_ATTRIBUTE = "name";

	public static final String ALIAS_ATTRIBUTE = "alias";

	public static final String IMPORT_ELEMENT = "import";

	public static final String RESOURCE_ATTRIBUTE = "resource";

	public static final String PROFILE_ATTRIBUTE = "profile";


	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private XmlReaderContext readerContext;

	@Nullable
	private BeanDefinitionParserDelegate delegate;


	/**
	 * This implementation parses bean definitions according to the "spring-beans" XSD
	 * (or DTD, historically).
	 * <p>Opens a DOM Document; then initializes the default settings
	 * specified at the {@code <beans/>} level; then parses the contained bean definitions.
	 */
	@Override
	public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
		this.readerContext = readerContext;
		doRegisterBeanDefinitions(doc.getDocumentElement());
	}

	/**
	 * Return the descriptor for the XML resource that this parser works on.
	 */
	protected final XmlReaderContext getReaderContext() {
		Assert.state(this.readerContext != null, "No XmlReaderContext available");
		return this.readerContext;
	}

	/**
	 * Invoke the {@link org.springframework.beans.factory.parsing.SourceExtractor}
	 * to pull the source metadata from the supplied {@link Element}.
	 */
	@Nullable
	protected Object extractSource(Element ele) {
		return getReaderContext().extractSource(ele);
	}


	/**
	 * Register each bean definition within the given root {@code <beans/>} element.
	 */
	@SuppressWarnings("deprecation")  // for Environment.acceptsProfiles(String...)
	protected void doRegisterBeanDefinitions(Element root) {

		/* 创建BeanDefinitionParserDelegate */

		// Any nested <beans> elements will cause recursion in this method. In
		// order to propagate and preserve <beans> default-* attributes correctly,
		// keep track of the current (parent) delegate, which may be null. Create
		// the new (child) delegate with a reference to the parent for fallback purposes,
		// then ultimately reset this.delegate back to its original (parent) reference.
		// this behavior emulates a stack of delegates without actually necessitating one.
		// 任何嵌套的 <beans> 元素都将导致此方法中的递归。为了正确传播和保留 <beans> 默认属性，请跟踪当前（父）委托，它可能为空。
		// 创建新的（子）委托，并引用父级以用于回退目的，然后最终将 this.delegate 重置回其原始（父级）引用。这种行为模拟了一堆委托，而实际上并不需要一个。
		BeanDefinitionParserDelegate/* bd解析器委托 */ parent = this.delegate;
		this.delegate = createDelegate(getReaderContext()/* XmlReaderContext */, root, parent);

		/**
		 * 如果root的namespaceUri是http://www.springframework.org/schema/beans的话，就代表是默认的命名空间，也代表是根元素
		 * <beans xmlns="http://www.springframework.org/schema/beans"
		 * 	   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		 * 	   xmlns:context="http://www.springframework.org/schema/context"
		 * 	   xmlns:msb="http://www.mashibing.com/schema/user"
		 * 	   xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
		 * 	    http://www.springframework.org/schema/context https://www.springframework.org/schema/context/spring-context.xsd
		 * 		http://www.mashibing.com/schema/user http://www.mashibing.com/schema/user.xsd"
		 * 	   default-autowire="constructor"  profile="" >
		 */
		if (this.delegate.isDefaultNamespace(root)) {
			// <beans>标签里面可以配置profile属性！
			String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE/* profile */);
			if (StringUtils.hasText(profileSpec)) {
				String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
						profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
				// We cannot use Profiles.of(...) since profile expressions are not supported
				// in XML config. See SPR-12458 for details.
				if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Skipped XML bean definition file due to specified profiles [" + profileSpec +
								"] not matching: " + getReaderContext().getResource());
					}
					return;
				}
			}
		}

		// 钩子方法
		preProcessXml(root);

		/* 解析标签，注册bd */
		// ⚠️解析bd（用解析器从根节点开始解析document中的标签元素，得到bd）
		parseBeanDefinitions(root/* 根节点 */, this.delegate/* 解析器 */);

		// 钩子方法
		postProcessXml(root);

		// 最终将 this.delegate 重置回其原始（父级）引用
		this.delegate = parent;
	}

	protected BeanDefinitionParserDelegate createDelegate(
			XmlReaderContext readerContext, Element root, @Nullable BeanDefinitionParserDelegate parentDelegate) {
		// Bean 定义解析器委托，里面包装了XmlReaderContext
		BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
		// 填充了一些默认的属性
		delegate.initDefaults(root, parentDelegate);
		return delegate;
	}

	/**
	 * 解析标签，注册bd
	 *
	 * Parse the elements at the root level in the document:
	 * "import", "alias", "bean".
	 *
	 * @param root the DOM root element of the document
	 */
	protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate/* bd解析委托器：不是实际处理解析的，但是要通过它，也就是委托它获取到命名空间处理器进行解析！ */) {
		/**
		 * 判断【默认的命名空间uri(namespaceUri)】是不是等于【http://www.springframework.org/schema/beans】
		 * 如果是的话就代表是根元素，例如：【<beans xmlns="http://www.springframework.org/schema/beans" 】
		 * <p>
		 * 如果【默认的命名空间uri】是【http://www.springframework.org/schema/beans】，那么在写<bean>、<import>、<alias>等标签时就不用加前缀
		 */
		if (delegate.isDefaultNamespace(root)) {
			NodeList nl = root.getChildNodes();
			// 遍历标签
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				// 如果是一个标签
				if (node instanceof Element) {

					Element ele = (Element) node;

					/* 1、默认命名空间的标签的解析方式 */
					/**
					 * 每一个外层标签，就是一个元素，每一个元素都会带上它自身所属的命名空间url，
					 * 比如：<bean>标签的默认命名空间url是http://www.springframework.org/schema/beans，
					 * 只要是属于http://www.springframework.org/schema/beans这个命名空间url的，那么就是默认命名空间
					 * 那么就按照默认元素进行处理
					 * 默认元素的处理方式，是直接找对应的方法进行处理，不会去spring.handlers里面找对应的NamespaceHandler
					 */
					if (delegate.isDefaultNamespace(ele)) {
						// ⚠️默认命名空间的标签的解析方式
						parseDefaultElement(ele, delegate);
					}
					/* ️2、其它命名空间的标签的解析方式（也可以说是自定义标签的解析方式） */
					else {
						/**
						 * delegate = BeanDefinitionParserDelegate
						 *
						 * 走这里的标签：
						 * （1）<context:component-scan>
						 * （2）<context:property-placeholder>
						 * <context:property-placeholder location=""/>标签的话，会往容器里面添加一个BeanFactoryPostProcessor：PlaceholderConfigurerSupport
						 * PlaceholderConfigurerSupport：会把${}里面的值替换成实际配置文件中的值
						 *
						 */
						// ⚠️其它命名空间的标签的解析方式（题外：自定义标签的解析处理在这里）
						// 题外：此处会根据我们配置的标签来决定是否完成某些内部bean bd的加载，比如<context:component-scan>、<aop:config>相关的标签时，会完成一些内部bean bd的加载！
						// >>> 对应的，就是一堆internal的内部对象（大部分跟注解相关），例如：ConfigurationClassPostProcess、AutowiredAnnotationBeanPostProcess、CommonAnnotationBeanPostProcessor
						// >>> 具体参考：AnnotationConfigUtils配置类、AopConfigUtils配置类
						delegate/* 代表 */.parseCustomElement(ele);
					}
				}
			}
		} else {
			delegate.parseCustomElement(root);
		}
	}

	private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT/* import */)) {
			importBeanDefinitionResource/* 导入bd资源 */(ele);
		} else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT/* alias */)) {
			processAliasRegistration/* 处理别名注册 */(ele);
		} else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT/* bean */)) {
			// ⚠️解析并注册
			processBeanDefinition/* 处理bean定义 */(ele, delegate);
		} else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT/* beans */)) {
			doRegisterBeanDefinitions/* 处理多个bean定义 */(ele);
		}
	}

	/**
	 * Parse an "import" element and load the bean definitions
	 * from the given resource into the bean factory.
	 */
	protected void importBeanDefinitionResource(Element ele) {
		String location = ele.getAttribute(RESOURCE_ATTRIBUTE);
		if (!StringUtils.hasText(location)) {
			getReaderContext().error("Resource location must not be empty", ele);
			return;
		}

		// Resolve system properties: e.g. "${user.dir}"
		location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);

		Set<Resource> actualResources = new LinkedHashSet<>(4);

		// Discover whether the location is an absolute or relative URI
		boolean absoluteLocation = false;
		try {
			absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
		} catch (URISyntaxException ex) {
			// cannot convert to an URI, considering the location relative
			// unless it is the well-known Spring prefix "classpath*:"
		}

		// Absolute or relative?
		if (absoluteLocation) {
			try {
				int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from URL location [" + location + "]");
				}
			} catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from URL location [" + location + "]", ele, ex);
			}
		} else {
			// No URL -> considering resource location as relative to the current file.
			try {
				int importCount;
				Resource relativeResource = getReaderContext().getResource().createRelative(location);
				if (relativeResource.exists()) {
					importCount = getReaderContext().getReader().loadBeanDefinitions(relativeResource);
					actualResources.add(relativeResource);
				} else {
					String baseLocation = getReaderContext().getResource().getURL().toString();
					importCount = getReaderContext().getReader().loadBeanDefinitions(
							StringUtils.applyRelativePath(baseLocation, location), actualResources);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from relative location [" + location + "]");
				}
			} catch (IOException ex) {
				getReaderContext().error("Failed to resolve current resource location", ele, ex);
			} catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from relative location [" + location + "]", ele, ex);
			}
		}
		Resource[] actResArray = actualResources.toArray(new Resource[0]);
		getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
	}

	/**
	 * Process the given alias element, registering the alias with the registry.
	 */
	protected void processAliasRegistration(Element ele) {
		String name = ele.getAttribute(NAME_ATTRIBUTE);
		String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
		boolean valid = true;
		if (!StringUtils.hasText(name)) {
			getReaderContext().error("Name must not be empty", ele);
			valid = false;
		}
		if (!StringUtils.hasText(alias)) {
			getReaderContext().error("Alias must not be empty", ele);
			valid = false;
		}
		if (valid) {
			try {
				getReaderContext().getRegistry().registerAlias(name, alias);
			} catch (Exception ex) {
				getReaderContext().error("Failed to register alias '" + alias +
						"' for bean with name '" + name + "'", ele, ex);
			}
			getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
		}
	}

	/**
	 * Process the given bean element, parsing the bean definition
	 * and registering it with the registry.
	 * 处理给定的 bean 元素，解析 bean 定义并将其注册到注册表。
	 */
	protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {

		/**
		 * 到目前位置已经做的事情：
		 * 1、从xml里面读取到了对应的标签和元素
		 * 2、解析从xml中获取到的标签元素，给转换为一个BeanDefinition
		 * 3、把BeanDefinition注册到BeanFactory中的beanDefinitionMap、beanDefinitionNames中
		 */


		// ⚠️重要的代码：完成解析工作
		// BeanDefinitionHolder是"BeanDefinition对象的封装类"，封装了BeanDefinition，beanName和别名数组。用它来完成IOC容器的注册
		// 得到这个BeanDefinitionHolder就意味着BeanDefinition是通过BeanDefinitionParserDelegate对xml元素的信息按照spring的bean规则进行解析得到的
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
				// 向ioc容器注册解析得到BeanDefinition的地方
				// Register the final decorated instance.
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			} catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			// 在BeanDefinition向ioc容器注册完成之后，发送消息！
			// Send registration event.
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}


	/**
	 * Allow the XML to be extensible by processing any custom element types first,
	 * before we start to process the bean definitions. This method is a natural
	 * extension point for any other custom pre-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 *
	 * @see #getReaderContext()
	 */
	protected void preProcessXml(Element root) {
	}

	/**
	 * Allow the XML to be extensible by processing any custom element types last,
	 * after we finished processing the bean definitions. This method is a natural
	 * extension point for any other custom post-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 *
	 * @see #getReaderContext()
	 */
	protected void postProcessXml(Element root) {
	}

}