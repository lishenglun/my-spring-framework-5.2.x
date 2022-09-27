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

package org.springframework.beans.factory.parsing;

import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * ComponentDefinition based on a standard BeanDefinition, exposing the given bean
 * definition as well as inner bean definitions and bean references for the given bean.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 2.0
 */
public class BeanComponentDefinition extends BeanDefinitionHolder implements ComponentDefinition {

	// 内部bean
	private BeanDefinition[] innerBeanDefinitions;

	// 引用的bean
	private BeanReference[] beanReferences;


	/**
	 * Create a new BeanComponentDefinition for the given bean.
	 * @param beanDefinition the BeanDefinition
	 * @param beanName the name of the bean
	 */
	public BeanComponentDefinition(BeanDefinition beanDefinition, String beanName) {
		this(new BeanDefinitionHolder(beanDefinition, beanName));
	}

	/**
	 * Create a new BeanComponentDefinition for the given bean.
	 * @param beanDefinition the BeanDefinition
	 * @param beanName the name of the bean
	 * @param aliases alias names for the bean, or {@code null} if none
	 */
	public BeanComponentDefinition(BeanDefinition beanDefinition, String beanName, @Nullable String[] aliases) {
		this(new BeanDefinitionHolder(beanDefinition, beanName, aliases));
	}

	/**
	 * Create a new BeanComponentDefinition for the given bean. —— 为给定的bean创建一个新的BeanComponentDefinition
	 *
	 * @param beanDefinitionHolder the BeanDefinitionHolder encapsulating
	 * the bean definition as well as the name of the bean
	 */
	public BeanComponentDefinition(BeanDefinitionHolder beanDefinitionHolder) {
		// BeanComponentDefinition extends BeanDefinitionHolder，所以将beanDefinitionHolder中的bd、beanName、aliases信息，再保存到BeanComponentDefinition中
		super(beanDefinitionHolder);

		// 内部bean
		List<BeanDefinition> innerBeans = new ArrayList<>();
		// 引用的bean
		List<BeanReference> references = new ArrayList<>();

		// 获取属性值集合
		PropertyValues propertyValues = beanDefinitionHolder.getBeanDefinition().getPropertyValues();

		// 遍历属性值集合
		for (PropertyValue propertyValue : propertyValues.getPropertyValues()) {
			// 获取属性值
			Object value = propertyValue.getValue();

			// 如果属性值是BeanDefinitionHolder类型，则获取BeanDefinitionHolder中的bd，添加到innerBeans中，作为内部bean
			if (value instanceof BeanDefinitionHolder) {
				innerBeans.add(((BeanDefinitionHolder) value).getBeanDefinition());
			}
			// 如果属性值是BeanDefinition类型，则直接添加属性值到innerBeans中，作为内部bean
			else if (value instanceof BeanDefinition) {
				innerBeans.add((BeanDefinition) value);
			}
			// 如果属性值是BeanReference类型，则直接添加属性值到references中，作为引用的bean
			else if (value instanceof BeanReference) {
				references.add((BeanReference) value);
			}
		}

		// 内部bean
		this.innerBeanDefinitions = innerBeans.toArray(new BeanDefinition[0]);
		// 引用的bean
		this.beanReferences = references.toArray(new BeanReference[0]);
	}


	@Override
	public String getName() {
		return getBeanName();
	}

	@Override
	public String getDescription() {
		return getShortDescription();
	}

	@Override
	public BeanDefinition[] getBeanDefinitions() {
		return new BeanDefinition[] {getBeanDefinition()};
	}

	@Override
	public BeanDefinition[] getInnerBeanDefinitions() {
		return this.innerBeanDefinitions;
	}

	@Override
	public BeanReference[] getBeanReferences() {
		return this.beanReferences;
	}


	/**
	 * This implementation returns this ComponentDefinition's description.
	 * @see #getDescription()
	 */
	@Override
	public String toString() {
		return getDescription();
	}

	/**
	 * This implementations expects the other object to be of type BeanComponentDefinition
	 * as well, in addition to the superclass's equality requirements.
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof BeanComponentDefinition && super.equals(other)));
	}

}
