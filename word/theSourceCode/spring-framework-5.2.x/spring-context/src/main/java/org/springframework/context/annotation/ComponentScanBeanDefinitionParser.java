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

package org.springframework.context.annotation;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.xml.XmlReaderContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.filter.*;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <context:component-scan>标签的解析器
 *
 *  参考：
 * 	<context:component-scan base-package="com.springstudy.msb.s_27" name-generator="" annotation-config="true"
 * 			resource-pattern="" scope-resolver="" scoped-proxy="" use-default-filters="true">
 * 		<context:exclude-filter type="annotation" expression="msb"/>
 * 		<context:include-filter type="regex" expression="msb"/>
 * 	</context:component-scan>
 *
 * Parser for the {@code <context:component-scan/>} element. —— {@code <context:component-scan>} 元素的解析器。
 *
 * @author Mark Fisher
 * @author Ramnivas Laddad
 * @author Juergen Hoeller
 * @since 2.5
 */
public class ComponentScanBeanDefinitionParser implements BeanDefinitionParser {

	/* 以下常量全部是<context:component-scan>标签的属性！ */

	private static final String BASE_PACKAGE_ATTRIBUTE = "base-package";

	private static final String RESOURCE_PATTERN_ATTRIBUTE = "resource-pattern";

	private static final String USE_DEFAULT_FILTERS_ATTRIBUTE = "use-default-filters";

	// 是否注册"注解配置处理器"的标识
	private static final String ANNOTATION_CONFIG_ATTRIBUTE = "annotation-config";

	private static final String NAME_GENERATOR_ATTRIBUTE = "name-generator";

	private static final String SCOPE_RESOLVER_ATTRIBUTE = "scope-resolver";

	private static final String SCOPED_PROXY_ATTRIBUTE = "scoped-proxy";

	private static final String EXCLUDE_FILTER_ELEMENT = "exclude-filter";

	private static final String INCLUDE_FILTER_ELEMENT = "include-filter";

	private static final String FILTER_TYPE_ATTRIBUTE = "type";

	private static final String FILTER_EXPRESSION_ATTRIBUTE = "expression";

	/**
	 * 解析<context:component-scan>标签
	 *
	 * 参考：
	 * <context:component-scan base-package="com.springstudy.msb.s_27" name-generator="" annotation-config="true"
	 * 			resource-pattern="" scope-resolver="" scoped-proxy="" use-default-filters="true">
	 * 		<context:exclude-filter type="annotation" expression="msb"/>
	 * 		<context:include-filter type="regex" expression="msb"/>
	 * 	</context:component-scan>
	 */
	@Override
	@Nullable
	public BeanDefinition parse(Element element, ParserContext parserContext) {
		/* 1、获取要扫描的包 */
		// 获取<context:component-san>标签的base-package属性值，例如：<context:component-scan base-package="com.springstudy.msb.s_27"/>
		String basePackage = element.getAttribute(BASE_PACKAGE_ATTRIBUTE/* base-package */);
		// 解析${}占位符
		basePackage = parserContext.getReaderContext().getEnvironment().resolvePlaceholders(basePackage);
		// 解析basePackage中的分割符，变成一个数组
		String[] basePackages = StringUtils.tokenizeToStringArray(basePackage,
				// CONFIG_LOCATION_DELIMITERS =【,;\t\n】
				ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS/* ,;\t\n *//* 分割符 */);

		/* 2、创建ClassPathBeanDefinitionScanner("类路径bd扫描器")，以及获取<context:component-san>标签里面的信息配置ClassPathBeanDefinitionScanner */
		// Actually scan for bean definitions and register them. —— 实际扫描bd并注册它们。
		// 创建一个ClassPathBeanDefinitionScanner("类路径bd扫描器")，以及获取<context:component-san>标签里面的信息配置ClassPathBeanDefinitionScanner
		ClassPathBeanDefinitionScanner/* 类路径bd扫描器 */ scanner = configureScanner(parserContext, element);

		/* 3、使用ClassPathBeanDefinitionScanner扫描指定包，返回符合条件的bd，并注册bd到BeanFactory中 */
		// ⚠️使用scanner对basePackages包进行扫描，返回符合条件的bd
		Set<BeanDefinitionHolder> beanDefinitions = scanner.doScan(basePackages);

		/* 4、注册组件 */
		// 注册组件（包括注册一些内部的注解后置处理器，触发注册事件）
		registerComponents(parserContext.getReaderContext(), beanDefinitions, element);

		return null;
	}

	protected ClassPathBeanDefinitionScanner/* 类路径bd扫描器 */ configureScanner(ParserContext parserContext, Element element) {
		/* 1、确定"是否使用默认过滤器"。默认为true，代表使用默认过滤器。如果设置了use-default-filters属性，则以use-default-filters属性值为准。*/
		// 是否使用默认过滤器。默认为true，代表使用默认过滤器
		boolean useDefaultFilters = true;
		/**
		 * 参考：<context:component-scan use-default-filters="true">
		 */
		// 判断是否设置了<context:component-scan use-default-filters="true">标签中的use-default-filters属性，设置了，就获取use-default-filters属性值，作为"是否使用默认过滤器"的标识
		if (element.hasAttribute(USE_DEFAULT_FILTERS_ATTRIBUTE/* use-default-filters *//* 使用默认过滤器 */)) {
			useDefaultFilters = Boolean.parseBoolean(element.getAttribute(USE_DEFAULT_FILTERS_ATTRIBUTE/* use-default-filters */));
		}

		/*

		2、创建一个ClassPathBeanDefinitionScanner("类路径bd扫描器")

		注意：⚠️在里面往includeFilters中添加了@Component，代表默认过滤标注了@Component的类

		*/
		// Delegate bean definition registration to scanner class. —— 将bd注册，委托给扫描程序类。
		ClassPathBeanDefinitionScanner/* 类路径bd扫描器 */ scanner = createScanner(parserContext.getReaderContext(), useDefaultFilters);
		scanner.setBeanDefinitionDefaults(parserContext.getDelegate().getBeanDefinitionDefaults()/* BeanDefinitionDefaults */);
		scanner.setAutowireCandidatePatterns(parserContext.getDelegate().getAutowireCandidatePatterns()/* 自动注入模式 */);

		if (element.hasAttribute(RESOURCE_PATTERN_ATTRIBUTE/* resource-pattern *//* 资源模式 */)) {
			scanner.setResourcePattern(element.getAttribute(RESOURCE_PATTERN_ATTRIBUTE));
		}

		/*

		3、解析Bean名称生成器，设置到ClassPathBeanDefinitionScanner中

		判断是否设置了<context:component-scan>标签中的name-generator属性，也就是判断是否有配置"bean名称生成器"的类名，如果配置了，就实例化并且设置到"类路径bd扫描器"中

		*/
		try {
			// 解析Bean名称生成器
			// 判断是否有配置bean名称生成器的类名，如果配置了，就实例化并且设置到"类路径bd扫描器"中
			// 题外：bean名称生成器需要实现的接口：BeanNameGenerator
			parseBeanNameGenerator/* 解析Bean名称生成器 */(element, scanner);
		} catch (Exception ex) {
			parserContext.getReaderContext().error(ex.getMessage(), parserContext.extractSource(element), ex.getCause());
		}

		/*

		4、解析范围
		（1）判断是否设置了<context:component-scan>标签中的scope-resolver属性，也就是判断是否配置了范围解析器的类名，如果配置了，就实例化并且设置到"类路径bd扫描器"中
		（2）判断是否设置了<context:component-scan>标签中的scoped-proxy属性，也就是判断是否配置了范围代理的模式，配置了，就设置对应的范围代理模式

		*/
		try {
			// 解析范围
			// 判断是否配置了范围解析器的类名，如果配置了，就实例化并且设置到"类路径bd扫描器"中
			// 题外：范围解析球需要实现的接口：ScopeMetadataResolver
			// 判断是否配置了范围代理的模式，配置了，就设置对应的范围代理模式
			parseScope/* 解析范围 */(element, scanner);
		} catch (Exception ex) {
			parserContext.getReaderContext().error(ex.getMessage(), parserContext.extractSource(element), ex.getCause());
		}
		/*

		5、解析排除和包含过滤器元素 —— 处理<context:component-scan>标签中的include-filter、exclude-filter属性。

		题外：牵扯到的接口：TypeFilter
		题外：从里面可以看出：include-filter的处理优先级高于exclude-filter，且同时配置了的话，只处理其中的一个

		*/
		parseTypeFilters/* 解析类型过滤器 */(element, scanner, parserContext);

		return scanner;
	}

	protected ClassPathBeanDefinitionScanner createScanner(XmlReaderContext readerContext, boolean useDefaultFilters) {
		// 创建一个"类路径bd扫描器"
		return new ClassPathBeanDefinitionScanner(readerContext.getRegistry(), useDefaultFilters,
				readerContext.getEnvironment(), readerContext.getResourceLoader());
	}

	/**
	 * 题外：只是第一步的加载，这个加载并没有完成
	 * @param readerContext
	 * @param beanDefinitions
	 * @param element
	 */
	protected void registerComponents(
			XmlReaderContext readerContext, Set<BeanDefinitionHolder> beanDefinitions, Element element) {
		// 使用注解的tagName和source构建CompositeComponentDefinition
		Object source = readerContext.extractSource/* 提取来源 */(element);

		// 创建一个"️复合的组件定义类"
		// 题外：什么叫复合的？就是一层层的嵌套
		CompositeComponentDefinition/* 复合组件定义 */ compositeDef = new CompositeComponentDefinition(element.getTagName(), source);

		// 将扫描到的所有beanDefinition添加到compositeDef的nestedComponents属性中
		for (BeanDefinitionHolder beanDefHolder : beanDefinitions) {
			compositeDef.addNestedComponent/* 添加嵌套组件 */(new BeanComponentDefinition/* bean组件定义 */(beanDefHolder));
		}

		// Register annotation config processors, if necessary. —— 如有必要，注册注解配置处理器。

		// 是否注册"注解配置处理器"的标识，默认为true
		boolean annotationConfig = true;
		// 获取<context:component-scan>标签的annotation-config属性值
		if (element.hasAttribute(ANNOTATION_CONFIG_ATTRIBUTE/* annotation-config */)) {
			annotationConfig = Boolean.parseBoolean(element.getAttribute(ANNOTATION_CONFIG_ATTRIBUTE));
		}
		if (annotationConfig) {
			/**
			 * ⚠️⚠️⚠️ConfigurationClassPostProcessor就是在这里进行注册的
			 */
			// 如果annotation-config属性值为true，在给定的注册表中注册所有用于注解的bean后置处理器
			Set<BeanDefinitionHolder> processorDefinitions =
					AnnotationConfigUtils.registerAnnotationConfigProcessors(readerContext.getRegistry(), source);

			for (BeanDefinitionHolder processorDefinition : processorDefinitions) {
				// 将注册的注解后置处理器的BeanDefinition添加到compositeDef的nestedComponents属性中
				compositeDef.addNestedComponent(new BeanComponentDefinition/* bean组件定义 */(processorDefinition));
			}
		}

		// 触发组件注册时间，默认实现为EmptyReaderEventListener
		readerContext.fireComponentRegistered(compositeDef);
	}

	protected void parseBeanNameGenerator(Element element, ClassPathBeanDefinitionScanner scanner) {
		// 1、判断是否有配置bean名称生成器
		if (element.hasAttribute(NAME_GENERATOR_ATTRIBUTE/* name-generator */)) {
			// 2、如果有配置bean名称生成器的话，就实例化bean名称生成器
			BeanNameGenerator beanNameGenerator = (BeanNameGenerator) instantiateUserDefinedStrategy/* 实例化用户定义的策略 */(
					element.getAttribute(NAME_GENERATOR_ATTRIBUTE), BeanNameGenerator.class,
					scanner.getResourceLoader().getClassLoader());
			// 3、设置刚刚实例化好的bean名称生成器
			scanner.setBeanNameGenerator(beanNameGenerator);
		}
	}

	protected void parseScope(Element element, ClassPathBeanDefinitionScanner scanner) {
		/* 1、如果配置了范围解析器的类名，就实例化范围解析器，并设置到"类路径的bd扫描器" */

		// Register ScopeMetadataResolver if class name provided. —— 如果提供了类名，则注册 ScopeMetadataResolver。
		if (element.hasAttribute(SCOPE_RESOLVER_ATTRIBUTE/* scope-resolver *//* 范围解析器 */)) {
			if (element.hasAttribute(SCOPED_PROXY_ATTRIBUTE)) {
				throw new IllegalArgumentException(
						"Cannot define both 'scope-resolver' and 'scoped-proxy' on <component-scan> tag");
			}
			ScopeMetadataResolver scopeMetadataResolver = (ScopeMetadataResolver) instantiateUserDefinedStrategy(
					element.getAttribute(SCOPE_RESOLVER_ATTRIBUTE), ScopeMetadataResolver.class/* 范围元数据解析器 */,
					scanner.getResourceLoader().getClassLoader());
			scanner.setScopeMetadataResolver(scopeMetadataResolver);
		}

		/* 2、如果配置了范围代理的模式，就设置对应的范围代理模式 */

		if (element.hasAttribute(SCOPED_PROXY_ATTRIBUTE/* scoped-proxy *//* 范围代理 */)) {
			String mode = element.getAttribute(SCOPED_PROXY_ATTRIBUTE);
			// ScopedProxyMode：范围代理模式
			if ("targetClass".equals(mode)) {
				// 创建基于类的代理（使用CGLIB）
				scanner.setScopedProxyMode(ScopedProxyMode.TARGET_CLASS);
			} else if ("interfaces".equals(mode)) {
				// 创建一个JDK动态代理，实现由目标对象的类公开的所有接口。
				scanner.setScopedProxyMode(ScopedProxyMode.INTERFACES);
			} else if ("no".equals(mode)) {
				// 不要创建范围代理
				scanner.setScopedProxyMode(ScopedProxyMode.NO);
			} else {
				// 错误信息：scoped-proxy 仅支持 'no'、'interfaces' 和 'targetClass'
				throw new IllegalArgumentException("scoped-proxy only supports 'no', 'interfaces' and 'targetClass'");
			}
		}
	}

	/**
	 * 参考：
	 * 	<context:component-scan base-package="com.springstudy.msb.s_27">
	 * 		<context:include-filter type="annotation" expression="com.springstudy.msb.s_27.tx_xml.MyComponent"/>
	 * 	</context:component-scan>
	 *
	 * @param element
	 * @param scanner
	 * @param parserContext
	 */
	protected void parseTypeFilters(Element element, ClassPathBeanDefinitionScanner scanner, ParserContext parserContext) {
		/*

		1、解析排除过滤器和包含过滤器

		牵扯到的接口：TypeFilter

		*/
		// Parse exclude and include filter elements. —— 解析排除和包含过滤器元素。
		ClassLoader classLoader = scanner.getResourceLoader().getClassLoader();
		NodeList nodeList = element.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE/* 1 *//* 元素节点 */) {
				String localName = parserContext.getDelegate().getLocalName(node);
				try {

					// ⚠️从下面可以看出，include-filter的处理优先级高于exclude-filter，且同时配置了的话，只处理其中的一个

					/* 包含的过滤器 */
					if (INCLUDE_FILTER_ELEMENT/* include-filter */.equals(localName)) {
						TypeFilter typeFilter = createTypeFilter((Element) node, classLoader, parserContext);
						scanner.addIncludeFilter(typeFilter);
					}
					/* 排除的过滤器 */
					else if (EXCLUDE_FILTER_ELEMENT/* exclude-filter */.equals(localName)) {
						TypeFilter typeFilter = createTypeFilter((Element) node, classLoader, parserContext);
						scanner.addExcludeFilter(typeFilter);
					}
				} catch (ClassNotFoundException ex) {
					parserContext.getReaderContext().warning(
							"Ignoring non-present type filter class: "/* 忽略不存在的类型过滤器类： */ + ex, parserContext.extractSource(element));
				} catch (Exception ex) {
					parserContext.getReaderContext().error(
							ex.getMessage(), parserContext.extractSource(element), ex.getCause());
				}
			}
		}
	}

	/**
	 * 参考：
	 * 	<context:component-scan base-package="com.springstudy.msb.s_27">
	 * 		<context:include-filter type="annotation" expression="com.springstudy.msb.s_27.tx_xml.MyComponent"/>
	 * 	</context:component-scan>
	 *
	 * @param element
	 * @param classLoader
	 * @param parserContext
	 * @return
	 * @throws ClassNotFoundException
	 */
	@SuppressWarnings("unchecked")
	protected TypeFilter createTypeFilter(Element element, @Nullable ClassLoader classLoader,
										  ParserContext parserContext) throws ClassNotFoundException {

		String filterType = element.getAttribute(FILTER_TYPE_ATTRIBUTE/* type */);
		String expression = element.getAttribute(FILTER_EXPRESSION_ATTRIBUTE/* expression */);
		// 解决${...}占位符
		expression = parserContext.getReaderContext().getEnvironment().resolvePlaceholders(expression);

		if ("annotation".equals(filterType)) {
			return new AnnotationTypeFilter((Class<Annotation>) ClassUtils.forName(expression, classLoader));
		} else if ("assignable".equals(filterType)) {
			return new AssignableTypeFilter(ClassUtils.forName(expression, classLoader));
		} else if ("aspectj".equals(filterType)) {
			return new AspectJTypeFilter(expression, classLoader);
		} else if ("regex".equals(filterType)) {
			return new RegexPatternTypeFilter(Pattern.compile(expression));
		} else if ("custom".equals(filterType)) {
			Class<?> filterClass = ClassUtils.forName(expression, classLoader);
			if (!TypeFilter.class.isAssignableFrom(filterClass)) {
				throw new IllegalArgumentException(
						"Class is not assignable to [" + TypeFilter.class.getName() + "]: " + expression);
			}
			return (TypeFilter) BeanUtils.instantiateClass(filterClass);
		} else {
			throw new IllegalArgumentException("Unsupported filter type: " + filterType);
		}
	}

	@SuppressWarnings("unchecked")
	private Object instantiateUserDefinedStrategy(
			String className, Class<?> strategyType, @Nullable ClassLoader classLoader) {

		Object result;
		try {
			result = ReflectionUtils.accessibleConstructor/* 可访问的构造函数 */(ClassUtils.forName(className, classLoader)).newInstance();
		} catch (ClassNotFoundException ex) {
			throw new IllegalArgumentException("Class [" + className + "] for strategy [" +
					strategyType.getName() + "] not found", ex);
		} catch (Throwable ex) {
			throw new IllegalArgumentException("Unable to instantiate class [" + className + "] for strategy [" +
					strategyType.getName() + "]: a zero-argument constructor is required", ex);
		}

		if (!strategyType.isAssignableFrom(result.getClass())) {
			throw new IllegalArgumentException("Provided class name must be an implementation of " + strategyType);
		}
		return result;
	}

}
