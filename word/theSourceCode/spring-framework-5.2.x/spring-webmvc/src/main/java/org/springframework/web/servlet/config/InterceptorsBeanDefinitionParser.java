/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.servlet.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.lang.Nullable;
import org.springframework.util.xml.DomUtils;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.w3c.dom.Element;

import java.util.List;

/**
 * 处理<mvc:interceptors>标签的解析器
 *
 * {@link org.springframework.beans.factory.xml.BeanDefinitionParser} that parses a
 * {@code interceptors} element to register a set of {@link MappedInterceptor} definitions.
 *
 * @author Keith Donald
 * @since 3.0
 */
class InterceptorsBeanDefinitionParser implements BeanDefinitionParser {

	@Override
	@Nullable
	public BeanDefinition parse(Element element, ParserContext context) {
		context.pushContainingComponent(
				new CompositeComponentDefinition(element.getTagName(), context.extractSource(element)));

		RuntimeBeanReference pathMatcherRef = null;
		if (element.hasAttribute("path-matcher")) {
			pathMatcherRef = new RuntimeBeanReference(element.getAttribute("path-matcher"));
		}

		// 获取<mvc:interceptors>中bean、ref、interceptor开头的子标签
		List<Element> interceptors = DomUtils.getChildElementsByTagName/* 按标签名称获取子元素 */(element, "bean", "ref", "interceptor");
		for (Element interceptor : interceptors) {
			RootBeanDefinition mappedInterceptorDef = new RootBeanDefinition(MappedInterceptor.class);
			mappedInterceptorDef.setSource(context.extractSource(interceptor)/* null */);
			mappedInterceptorDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

			// 包含的路径
			ManagedList<String> includePatterns = null;
			// 排除的路径
			ManagedList<String> excludePatterns = null;
			Object interceptorBean;
			if ("interceptor".equals(interceptor.getLocalName())) {
				includePatterns = getIncludePatterns(interceptor, "mapping");
				excludePatterns = getIncludePatterns(interceptor, "exclude-mapping");
				Element beanElem = DomUtils.getChildElementsByTagName(interceptor, "bean", "ref").get(0);
				interceptorBean = context.getDelegate().parsePropertySubElement(beanElem, null);
			}
			else {
				interceptorBean = context.getDelegate().parsePropertySubElement/* 解析属性子元素 */(interceptor, null);
			}

			mappedInterceptorDef.getConstructorArgumentValues/* 获取构造函数参数值 */().addIndexedArgumentValue/* 添加索引参数值 */(0, includePatterns);
			mappedInterceptorDef.getConstructorArgumentValues().addIndexedArgumentValue(1, excludePatterns);
			mappedInterceptorDef.getConstructorArgumentValues().addIndexedArgumentValue(2, interceptorBean);

			if (pathMatcherRef != null) {
				mappedInterceptorDef.getPropertyValues().add("pathMatcher", pathMatcherRef);
			}

			String beanName = context.getReaderContext().registerWithGeneratedName(mappedInterceptorDef);
			context.registerComponent(new BeanComponentDefinition(mappedInterceptorDef, beanName));
		}

		context.popAndRegisterContainingComponent();
		return null;
	}

	private ManagedList<String> getIncludePatterns(Element interceptor, String elementName) {
		List<Element> paths = DomUtils.getChildElementsByTagName(interceptor, elementName);
		ManagedList<String> patterns = new ManagedList<>(paths.size());
		for (Element path : paths) {
			patterns.add(path.getAttribute("path"));
		}
		return patterns;
	}

}
