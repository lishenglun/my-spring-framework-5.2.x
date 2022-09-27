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

import org.w3c.dom.Element;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * Abstract {@link BeanDefinitionParser} implementation providing
 * a number of convenience methods and a
 * {@link AbstractBeanDefinitionParser#parseInternal template method}
 * that subclasses must override to provide the actual parsing logic.
 *
 * <p>Use this {@link BeanDefinitionParser} implementation when you want
 * to parse some arbitrarily complex XML into one or more
 * {@link BeanDefinition BeanDefinitions}. If you just want to parse some
 * XML into a single {@code BeanDefinition}, you may wish to consider
 * the simpler convenience extensions of this class, namely
 * {@link AbstractSingleBeanDefinitionParser} and
 * {@link AbstractSimpleBeanDefinitionParser}.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Rick Evans
 * @author Dave Syer
 * @since 2.0
 */
public abstract class AbstractBeanDefinitionParser implements BeanDefinitionParser {

	/**
	 * Constant for the "id" attribute.
	 */
	public static final String ID_ATTRIBUTE = "id";

	/**
	 * Constant for the "name" attribute.
	 */
	public static final String NAME_ATTRIBUTE = "name";

	@Override
	@Nullable
	public final BeanDefinition parse(Element element, ParserContext parserContext) {

		/* 1、解析标签，得到对应的一个bd */
		/**
		 * 1、里面调用了一个doParse()，该方法由"自定义的标签解析器"重写该方法，进行具体标签的属性值解析工作
		 *
		 */
		/**
		 * 1、如果是<tx:advice>标签，那么得到的bd = TransactionInterceptor bd，
		 * 里面包含了transactionAttributeSource属性，属性值是NameMatchTransactionAttributeSource
		 */
		// ⚠️解析标签，得到一个bd
		// AbstractSingleBeanDefinitionParser#parseInternal()
		AbstractBeanDefinition definition = parseInternal/* 解析内部 */(element, parserContext);
		// 如果解析得到了一个bd，并且这个bd不存在内部bd
		if (definition != null && !parserContext.isNested()) {
			try {
				/* 2、获取标签的id属性 */
				// 获取标签的id属性，例如：<tx:advice id="txAdvice">，那么id=txAdvice
				String id = resolveId(element, definition, parserContext);
				if (!StringUtils.hasText(id)) {
					parserContext.getReaderContext().error(
							"Id is required for element '" + parserContext.getDelegate().getLocalName(element)
									+ "' when used as a top-level tag", element);
				}

				/* 3、获取标签中配置的别名（有些标签不存在这个属性） */
				// 解析别名
				String[] aliases = null;
				// 判断是否将名称解析为别名，默认返回true
				if (shouldParseNameAsAliases()/* 应该将名称解析为别名 */) {
					// 获取标签的name属性
					String name = element.getAttribute(NAME_ATTRIBUTE/* name */);
					// 如果存在name属性，则尝试用逗号分割为数组，因为别名可以配置多个
					if (StringUtils.hasLength(name)) {
						aliases = StringUtils.trimArrayElements(StringUtils.commaDelimitedListToStringArray(name)/* 逗号分隔列表到字符串数组 */);
					}
				}

				/* 4、注册刚刚解析标签得到的bd */
				/**
				 * 1、BeanDefinitionHolder
				 * BeanDefinitionHolder是对bd的包装，用于方便的将bd注册到beanFactory.beanDefinitionMap、beanFactory.aliasMap当中
				 * >>> 因为放入到beanFactory.beanDefinitionMap当中，key需要一个name值；以及beanFactory.aliasMap需要对应的别名称
				 * >>> 这些都要事先准备好，一起放入在一个地方，方便到时候直接使用。而这个地方就是BeanDefinitionHolder
				 */
				// 创建BeanDefinitionHolder，封装bd，beanName、aliases
				BeanDefinitionHolder holder = new BeanDefinitionHolder(definition, id/* 作为beanName */, aliases);
				// 🚩将对应的bd注册到beanFactory中
				registerBeanDefinition(holder, parserContext.getRegistry()/* DefaultListableBeanFactory */);

				// 判断是不是应该调用监听器进行处理，恒定返回true
				if (shouldFireEvents()/* 应该触发事件 *//* 默认为true */) {

					// 为给定的bean创建一个新的BeanComponentDefinition
					BeanComponentDefinition componentDefinition = new BeanComponentDefinition/* Bean组件定义 */(holder);

					// 空实现，什么事情都没干
					postProcessComponentDefinition(componentDefinition);

					// 调用监听器，注册组件
					parserContext.registerComponent(componentDefinition);
				}

			} catch (BeanDefinitionStoreException ex) {
				String msg = ex.getMessage();
				parserContext.getReaderContext().error((msg != null ? msg : ex.toString()), element);
				return null;
			}
		}
		return definition;
	}

	/**
	 * 获取标签的id属性，例如：<tx:advice id="txAdvice">，那么id=txAdvice
	 *
	 * Resolve the ID for the supplied {@link BeanDefinition}.
	 * <p>When using {@link #shouldGenerateId generation}, a name is generated automatically.
	 * Otherwise, the ID is extracted from the "id" attribute, potentially with a
	 * {@link #shouldGenerateIdAsFallback() fallback} to a generated id.
	 *
	 * @param element       the element that the bean definition has been built from
	 * @param definition    the bean definition to be registered
	 * @param parserContext the object encapsulating the current state of the parsing process;
	 *                      provides access to a {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
	 * @return the resolved id
	 * @throws BeanDefinitionStoreException if no unique name could be generated
	 *                                      for the given bean definition
	 */
	protected String resolveId(Element element, AbstractBeanDefinition definition, ParserContext parserContext)
			throws BeanDefinitionStoreException {

		if (shouldGenerateId()) {
			return parserContext.getReaderContext().generateBeanName(definition);
		} else {
			// ⚠️获取标签的id属性，例如：<tx:advice id="txAdvice">，那么id=txAdvice
			String id = element.getAttribute(ID_ATTRIBUTE/* id */);
			if (!StringUtils.hasText(id) && shouldGenerateIdAsFallback()) {
				id = parserContext.getReaderContext().generateBeanName(definition);
			}
			return id;
		}
	}

	/**
	 * Register the supplied {@link BeanDefinitionHolder bean} with the supplied
	 * {@link BeanDefinitionRegistry registry}.
	 * <p>Subclasses can override this method to control whether or not the supplied
	 * {@link BeanDefinitionHolder bean} is actually even registered, or to
	 * register even more beans.
	 * <p>The default implementation registers the supplied {@link BeanDefinitionHolder bean}
	 * with the supplied {@link BeanDefinitionRegistry registry} only if the {@code isNested}
	 * parameter is {@code false}, because one typically does not want inner beans
	 * to be registered as top level beans.
	 *
	 * @param definition the bean definition to be registered
	 * @param registry   the registry that the bean is to be registered with
	 * @see BeanDefinitionReaderUtils#registerBeanDefinition(BeanDefinitionHolder, BeanDefinitionRegistry)
	 */
	protected void registerBeanDefinition(BeanDefinitionHolder definition, BeanDefinitionRegistry registry) {
		BeanDefinitionReaderUtils.registerBeanDefinition(definition, registry);
	}


	/**
	 * Central template method to actually parse the supplied {@link Element}
	 * into one or more {@link BeanDefinition BeanDefinitions}.
	 *
	 * @param element       the element that is to be parsed into one or more {@link BeanDefinition BeanDefinitions}
	 * @param parserContext the object encapsulating the current state of the parsing process;
	 *                      provides access to a {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
	 * @return the primary {@link BeanDefinition} resulting from the parsing of the supplied {@link Element}
	 * @see #parse(org.w3c.dom.Element, ParserContext)
	 * @see #postProcessComponentDefinition(org.springframework.beans.factory.parsing.BeanComponentDefinition)
	 */
	@Nullable
	protected abstract AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext);

	/**
	 * Should an ID be generated instead of read from the passed in {@link Element}?
	 * <p>Disabled by default; subclasses can override this to enable ID generation.
	 * Note that this flag is about <i>always</i> generating an ID; the parser
	 * won't even check for an "id" attribute in this case.
	 *
	 * @return whether the parser should always generate an id
	 */
	protected boolean shouldGenerateId() {
		return false;
	}

	/**
	 * Should an ID be generated instead if the passed in {@link Element} does not
	 * specify an "id" attribute explicitly?
	 * <p>Disabled by default; subclasses can override this to enable ID generation
	 * as fallback: The parser will first check for an "id" attribute in this case,
	 * only falling back to a generated ID if no value was specified.
	 *
	 * @return whether the parser should generate an id if no id was specified
	 */
	protected boolean shouldGenerateIdAsFallback() {
		return false;
	}

	/**
	 * Determine whether the element's "name" attribute should get parsed as
	 * bean definition aliases, i.e. alternative bean definition names.
	 * <p>The default implementation returns {@code true}.
	 *
	 * @return whether the parser should evaluate the "name" attribute as aliases
	 * @since 4.1.5
	 */
	protected boolean shouldParseNameAsAliases() {
		return true;
	}

	/**
	 * Determine whether this parser is supposed to fire a
	 * {@link org.springframework.beans.factory.parsing.BeanComponentDefinition}
	 * event after parsing the bean definition.
	 * <p>This implementation returns {@code true} by default; that is,
	 * an event will be fired when a bean definition has been completely parsed.
	 * Override this to return {@code false} in order to suppress the event.
	 *
	 * @return {@code true} in order to fire a component registration event
	 * after parsing the bean definition; {@code false} to suppress the event
	 * @see #postProcessComponentDefinition
	 * @see org.springframework.beans.factory.parsing.ReaderContext#fireComponentRegistered
	 */
	protected boolean shouldFireEvents() {
		return true;
	}

	/**
	 * Hook method called after the primary parsing of a
	 * {@link BeanComponentDefinition} but before the
	 * {@link BeanComponentDefinition} has been registered with a
	 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}.
	 * <p>Derived classes can override this method to supply any custom logic that
	 * is to be executed after all the parsing is finished.
	 * <p>The default implementation is a no-op.
	 *
	 * @param componentDefinition the {@link BeanComponentDefinition} that is to be processed
	 */
	protected void postProcessComponentDefinition(BeanComponentDefinition componentDefinition) {
	}

}
