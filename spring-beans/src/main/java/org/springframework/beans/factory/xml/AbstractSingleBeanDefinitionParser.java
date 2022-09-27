/*
 * Copyright 2002-2017 the original author or authors.
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

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.lang.Nullable;

/**
 * Base class for those {@link BeanDefinitionParser} implementations that
 * need to parse and define just a <i>single</i> {@code BeanDefinition}.
 *
 * <p>Extend this parser class when you want to create a single bean definition
 * from an arbitrarily complex XML element. You may wish to consider extending
 * the {@link AbstractSimpleBeanDefinitionParser} when you want to create a
 * single bean definition from a relatively simple custom XML element.
 *
 * <p>The resulting {@code BeanDefinition} will be automatically registered
 * with the {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}.
 * Your job simply is to {@link #doParse parse} the custom XML {@link Element}
 * into a single {@code BeanDefinition}.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Rick Evans
 * @see #getBeanClass
 * @see #getBeanClassName
 * @see #doParse
 * @since 2.0
 */
public abstract class AbstractSingleBeanDefinitionParser extends AbstractBeanDefinitionParser {

	/**
	 * Creates a {@link BeanDefinitionBuilder} instance for the
	 * {@link #getBeanClass bean Class} and passes it to the
	 * {@link #doParse} strategy method.
	 *
	 * @param element       the element that is to be parsed into a single BeanDefinition
	 * @param parserContext the object encapsulating the current state of the parsing process
	 * @return the BeanDefinition resulting from the parsing of the supplied {@link Element}
	 * @throws IllegalStateException if the bean {@link Class} returned from
	 *                               {@link #getBeanClass(org.w3c.dom.Element)} is {@code null}
	 * @see #doParse
	 */
	@Override
	protected final AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
		/* 创建一个BeanDefinitionBuilder，用于构建bd */
		// 题外：GenericBeanDefinition：直接从配置文件读取过来的BeanDefinition类型，所以创建的是它
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition();

		/* 获取父类的名称 */
		String parentName = getParentName(element);
		if (parentName != null) {
			builder.getRawBeanDefinition().setParentName(parentName);
		}

		/* 获取存储当前标签信息的bean对象类型，这是作为当前标签对应的bd！ */
		/**
		 * 如果是<tx:advice>标签，那么beanClass = TransactionInterceptor.class，进入TxAdviceBeanDefinitionParser#getBeanClass()查看
		 * 如果是<context:property-placeholder>标签，那么beanClass = PropertySourcesPlaceholderConfigurer.class，进入PropertyPlaceholderBeanDefinitionParser#getBeanClass()查看
		 */
		// 获取自定义标签中的class，此时会调用自定义解析器
		// ⚠️由自定义解析器重写该方法，返回的Class是存储解析好的属性的对象类型，也就是说，将解析好的属性值放入什么类型的对象里面
		Class<?> beanClass = getBeanClass(element);
		if (beanClass != null) {
			builder.getRawBeanDefinition().setBeanClass(beanClass);
		} else {
			// 若子类没有重写getBeanClass()，则尝试检查子类是否重写getBeanClassName()，
			// 获取beanClassName
			String beanClassName = getBeanClassName(element);
			if (beanClassName != null) {
				builder.getRawBeanDefinition().setBeanClassName(beanClassName);
			}
		}

		builder.getRawBeanDefinition().setSource(parserContext.extractSource(element));

		// 获取内部bd
		BeanDefinition containingBd = parserContext.getContainingBeanDefinition/* 获取包含的bd */();
		if (containingBd != null) {
			// Inner bean definition must receive same scope as containing bean. —— 内部bd必须接收与包含bean相同的范围。
			// 若存在父类，则设置当前标签信息对应的bd的作用域为父类的作用域
			builder.setScope/* 设置作用域 */(containingBd.getScope());
		}

		// 判断是不是是默认的懒加载
		if (parserContext.isDefaultLazyInit()) {
			// Default-lazy-init applies to custom bean definitions as well. —— Default-lazy-init也适用于自定义bd。
			// 配置懒加载为true，代表使用懒加载方式
			builder.setLazyInit/* 设置懒加载 */(true);
		}

		// ⚠️由具体的标签解析器重写该方法，解析标签的属性，将解析好的属性值放入到BeanDefinitionBuilder中
		// 题外：我们可以自定义一个标签解析器，然后重写该方法。在这个方法里面，书写解析我们自定义标签的逻辑。
		doParse(element, parserContext, builder/* BeanDefinitionBuilder */);

		// 返回由标签信息构建而成的bd
		return builder.getBeanDefinition();
	}

	/**
	 * Determine the name for the parent of the currently parsed bean,
	 * in case of the current bean being defined as a child bean.
	 * <p>The default implementation returns {@code null},
	 * indicating a root bean definition.
	 *
	 * @param element the {@code Element} that is being parsed
	 * @return the name of the parent bean for the currently parsed bean,
	 * or {@code null} if none
	 */
	@Nullable
	protected String getParentName(Element element) {
		return null;
	}

	/**
	 * 获取存储当前标签属性值的对象类型
	 *
	 * 题外：要将解析好的标签属性值放入哪一个对象里面，这个对象是什么类型
	 *
	 * Determine the bean class corresponding to the supplied {@link Element}.
	 * <p>Note that, for application classes, it is generally preferable to
	 * override {@link #getBeanClassName} instead, in order to avoid a direct
	 * dependence on the bean implementation class. The BeanDefinitionParser
	 * and its NamespaceHandler can be used within an IDE plugin then, even
	 * if the application classes are not available on the plugin's classpath.
	 *
	 * @param element the {@code Element} that is being parsed
	 * @return the {@link Class} of the bean that is being defined via parsing
	 * the supplied {@code Element}, or {@code null} if none
	 * @see #getBeanClassName
	 */
	@Nullable
	protected Class<?> getBeanClass(Element element) {
		return null;
	}

	/**
	 * 获取存储当前标签属性值的对象类型的全限定类名
	 *
	 * Determine the bean class name corresponding to the supplied {@link Element}.
	 *
	 * @param element the {@code Element} that is being parsed
	 * @return the class name of the bean that is being defined via parsing
	 * the supplied {@code Element}, or {@code null} if none
	 * @see #getBeanClass
	 */
	@Nullable
	protected String getBeanClassName(Element element) {
		return null;
	}

	/**
	 * Parse the supplied {@link Element} and populate the supplied
	 * {@link BeanDefinitionBuilder} as required.
	 * <p>The default implementation delegates to the {@code doParse}
	 * version without ParserContext argument.
	 *
	 * @param element       the XML element being parsed
	 * @param parserContext the object encapsulating the current state of the parsing process
	 * @param builder       used to define the {@code BeanDefinition}
	 * @see #doParse(Element, BeanDefinitionBuilder)
	 */
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		doParse(element, builder);
	}

	/**
	 * Parse the supplied {@link Element} and populate the supplied
	 * {@link BeanDefinitionBuilder} as required.
	 * <p>The default implementation does nothing.
	 *
	 * @param element the XML element being parsed
	 * @param builder used to define the {@code BeanDefinition}
	 */
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
	}

}
