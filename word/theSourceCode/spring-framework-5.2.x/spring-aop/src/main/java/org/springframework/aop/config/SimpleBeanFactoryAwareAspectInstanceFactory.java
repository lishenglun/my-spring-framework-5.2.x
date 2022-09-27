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

package org.springframework.aop.config;

import org.springframework.aop.aspectj.AspectInstanceFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * AspectInstanceFactory的实现类，通过一个配置的beanName来
 *
 * Implementation of {@link AspectInstanceFactory} that locates the aspect from the
 * {@link org.springframework.beans.factory.BeanFactory} using a configured bean name.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 2.0
 */
public class SimpleBeanFactoryAwareAspectInstanceFactory implements AspectInstanceFactory, BeanFactoryAware {

	@Nullable
	private String aspectBeanName;

	// 因为advice可以作用在一个类上面，也可以作用在十个类上面，我是不知道这些类是哪些的，所以我需要知道当前容器的工厂，
	// 当我有了工厂之后，我就可以随意的从工厂里面获取具体的对象
	@Nullable
	private BeanFactory beanFactory;


	/**
	 * Set the name of the aspect bean. This is the bean that is returned when calling
	 * {@link #getAspectInstance()}.
	 */
	public void setAspectBeanName(String aspectBeanName) {
		this.aspectBeanName = aspectBeanName;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		Assert.notNull(this.aspectBeanName, "'aspectBeanName' is required");
	}


	/**
	 * Look up the aspect bean from the {@link BeanFactory} and returns it.
	 * @see #setAspectBeanName
	 */
	@Override
	public Object getAspectInstance() {
		Assert.state(this.beanFactory != null, "No BeanFactory set");
		Assert.state(this.aspectBeanName != null, "No 'aspectBeanName' set");
		return this.beanFactory.getBean(this.aspectBeanName);
	}

	@Override
	@Nullable
	public ClassLoader getAspectClassLoader() {
		if (this.beanFactory instanceof ConfigurableBeanFactory) {
			return ((ConfigurableBeanFactory) this.beanFactory).getBeanClassLoader();
		}
		else {
			return ClassUtils.getDefaultClassLoader();
		}
	}

	@Override
	public int getOrder() {
		if (this.beanFactory != null && this.aspectBeanName != null &&
				this.beanFactory.isSingleton(this.aspectBeanName) &&
				this.beanFactory.isTypeMatch(this.aspectBeanName, Ordered.class)) {
			return ((Ordered) this.beanFactory.getBean(this.aspectBeanName)).getOrder();
		}
		return Ordered.LOWEST_PRECEDENCE;
	}

}
