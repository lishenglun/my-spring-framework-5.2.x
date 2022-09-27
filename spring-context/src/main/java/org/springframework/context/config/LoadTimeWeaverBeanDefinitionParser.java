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

package org.springframework.context.config;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.weaving.AspectJWeavingEnabler;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * 解析<context:load-time-weaver>标签，一共注册2个bd：
 * 解析标签的时候，会注册一个bean；同时对于标签本身，spring也会以bean的形式保存
 * （1）org.springframework.context.config.internalAspectJWeavingEnabler = AspectJWeavingEnabler
 * （2）loadTimeWeaver = DefaultContextLoadTimeWeaver
 *
 * Parser for the &lt;context:load-time-weaver/&gt; element.
 *
 * @author Juergen Hoeller
 * @since 2.5
 */
class LoadTimeWeaverBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	/**
	 * The bean name of the internally managed AspectJ weaving enabler.
	 *
	 * @since 4.3.1
	 */
	public static final String ASPECTJ_WEAVING_ENABLER_BEAN_NAME =
			"org.springframework.context.config.internalAspectJWeavingEnabler";

	private static final String ASPECTJ_WEAVING_ENABLER_CLASS_NAME =
			"org.springframework.context.weaving.AspectJWeavingEnabler";

	private static final String DEFAULT_LOAD_TIME_WEAVER_CLASS_NAME =
			"org.springframework.context.weaving.DefaultContextLoadTimeWeaver";

	private static final String WEAVER_CLASS_ATTRIBUTE = "weaver-class";

	private static final String ASPECTJ_WEAVING_ATTRIBUTE = "aspectj-weaving";

	/**
	 * 1、解析标签的时候，会注册一个bean；同时对于标签本身，spring也会以bean的形式保存
	 *
	 * （1）解析标签的时候，会注册一个bean
	 *
	 * 注册一个对于AspectJ处理的对象：AspectJWeavingEnabler bd，beanName = org.springframework.context.config.internalAspectJWeavingEnabler
	 * 参考：{@link LoadTimeWeaverBeanDefinitionParser#doParse(Element, ParserContext, BeanDefinitionBuilder)}
	 *
	 * （2）同时对于标签本身，spring也会以bean的形式保存
	 *
	 * 也就是：当Spring在读取到自定义标签<context:load-time-weaver/>后会产生一个bean，
	 * 而这个bean的id为loadTimeWeaver；class为org.springframework.context.weaving.DefaultContextLoadTimeWeaver，
	 * 也就是完成了 DefaultContextLoadTimeWeaver类的注册
	 * 参考：{@link LoadTimeWeaverBeanDefinitionParser#getBeanClassName(Element)}
	 * 参考：{@link LoadTimeWeaverBeanDefinitionParser#resolveId(Element, AbstractBeanDefinition, ParserContext)}
	 *
	 * 题外：DefaultContextLoadTimeWeaver implements LoadTimeWeaver
	 * 题外：AspectJWeavingEnabler implements BeanFactoryPostProcessor, BeanClassLoaderAware, LoadTimeWeaverAware
	 *
	 * 3、完成了以上的注册功能后，并不意味在Spring中就可以使用AspectJ了，还需要注册LoadTimeWeaverAwareProcessor，只有注册了LoadTimeWeaverAwareProcessor才会激活整个AspectJ的功能
	 *
	 * 在{@link org.springframework.context.support.AbstractApplicationContext#prepareBeanFactory(ConfigurableListableBeanFactory)}中注册
	 */

	/**
	 * 当Spring在读取到自定义标签<context:load-time-weaver/>后会产生一个bean，而这个bean的class为org.springframework.context.weaving.DefaultContextLoadTimeWeaver
	 */
	@Override
	protected String getBeanClassName(Element element) {
		if (element.hasAttribute(WEAVER_CLASS_ATTRIBUTE/* weaver-class */)) {
			return element.getAttribute(WEAVER_CLASS_ATTRIBUTE/* weaver-class */);
		}
		return DEFAULT_LOAD_TIME_WEAVER_CLASS_NAME/* org.springframework.context.weaving.DefaultContextLoadTimeWeaver */;
	}

	/**
	 * 当Spring在读取到自定义标签<context:load-time-weaver/>后会产生一个bean，而这个bean的id为loadTimeWeaver
	 */
	@Override
	protected String resolveId(Element element, AbstractBeanDefinition definition, ParserContext parserContext) {
		return ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME/* loadTimeWeaver */;
	}

	/**
	 * 注册一个对于AspectJ处理的对象：AspectJWeavingEnabler bd
	 *
	 * @param element       the XML element being parsed
	 * @param parserContext the object encapsulating the current state of the parsing process
	 * @param builder       used to define the {@code BeanDefinition}
	 */
	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

		/**
		 * 之前虽然反复提到了在配置文件中加入了<context:load-time-weaver/>便相当于加入了AspectJ开关。
		 * 但是，并不是配置了这个标签就意味着开启了AspectJ功能，这个标签中还有一个属性aspectj-weaving，这个属性有3个备选值，on、off、autodetect，
		 * 默认为autodetect，也就是说，如果我们只是使用了<context:load-time-weaver/>，那么Spring会帮助我们检测是否可以使用AspectJ功能，而检测的依据便是文件META-INF/aop.xml是否存在
		 */
		// ⚠️是否开启AspectJ
		if (isAspectJWeavingEnabled(element.getAttribute(ASPECTJ_WEAVING_ATTRIBUTE/* aspectj-weaving */), parserContext)) {
			if (!parserContext.getRegistry().containsBeanDefinition(ASPECTJ_WEAVING_ENABLER_BEAN_NAME/* org.springframework.context.config.internalAspectJWeavingEnabler */)) {

				// AspectJWeavingEnabler bd
				// 注册一个对于AspectJ处理的类：org.springframework.context.weaving.AspectJWeavingEnabler
				RootBeanDefinition def = new RootBeanDefinition(ASPECTJ_WEAVING_ENABLER_CLASS_NAME/* org.springframework.context.weaving.AspectJWeavingEnabler */);
				parserContext.registerBeanComponent(
						new BeanComponentDefinition(def, ASPECTJ_WEAVING_ENABLER_BEAN_NAME/* org.springframework.context.config.internalAspectJWeavingEnabler */));
			}

			if (isBeanConfigurerAspectEnabled(parserContext.getReaderContext().getBeanClassLoader())) {
				new SpringConfiguredBeanDefinitionParser().parse(element, parserContext);
			}
		}
	}

	/**
	 * 是否开启AspectJ
	 *
	 * 之前虽然反复提到了在配置文件中加入了<context:load-time-weaver/>便相当于加入了AspectJ开关。
	 * 但是，并不是配置了这个标签就意味着开启了AspectJ功能，这个标签中还有一个属性aspectj-weaving，这个属性有3个备选值，on、off、autodetect，
	 * 默认为autodetect，也就是说，如果我们只是使用了<context:load-time-weaver/>，那么Spring会帮助我们检测是否可以使用AspectJ功能，而检测的依据便是文件META-INF/aop.xml是否存在
	 *
	 * @param value
	 * @param parserContext
	 * @return
	 */
	protected boolean isAspectJWeavingEnabled(String value, ParserContext parserContext) {
		if ("on".equals(value)) {
			return true;
		} else if ("off".equals(value)) {
			return false;
		}
		// spring帮我们自动检测是否可以使用AspectJ功能，检测的依据便是文件META-INF/aop.xml是否存在
		// 如果存在，则代表开启了AspectJ功能
		else {
			// Determine default... —— 确定默认...
			ClassLoader cl = parserContext.getReaderContext().getBeanClassLoader();

			// 注意：⚠️这里读取META-INF/aop.xml，只是为了判断一下，是否开启AspectJ，并不会去读取META-INF/aop.xml中的内容进行操作
			// >>> 读取META-INF/aop.xml中的内容进行操作，由AspectJ做。
			// >>> AspectJ会读取META-INF/aop.xml中配置的增强器，然后通过AspectJ内部自定义的ClassFileTransformer，织入到对于的类中！
			return (cl != null && cl.getResource(AspectJWeavingEnabler.ASPECTJ_AOP_XML_RESOURCE/* META-INF/aop.xml */) != null);
		}
	}

	protected boolean isBeanConfigurerAspectEnabled(@Nullable ClassLoader beanClassLoader) {
		return ClassUtils.isPresent(SpringConfiguredBeanDefinitionParser.BEAN_CONFIGURER_ASPECT_CLASS_NAME,
				beanClassLoader);
	}

}
