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

package org.springframework.aop.framework;

import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

import java.io.Closeable;

/**
 * Base class with common functionality for proxy processors, in particular
 * ClassLoader management and the {@link #evaluateProxyInterfaces} algorithm.
 *
 * @author Juergen Hoeller
 * @since 4.1
 * @see AbstractAdvisingBeanPostProcessor
 * @see org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator
 */
@SuppressWarnings("serial")
public class ProxyProcessorSupport extends ProxyConfig implements Ordered, BeanClassLoaderAware, AopInfrastructureBean {

	/**
	 * This should run after all other processors, so that it can just add
	 * an advisor to existing proxies rather than double-proxy.
	 */
	private int order = Ordered.LOWEST_PRECEDENCE;

	@Nullable
	private ClassLoader proxyClassLoader = ClassUtils.getDefaultClassLoader();

	private boolean classLoaderConfigured = false;


	/**
	 * Set the ordering which will apply to this processor's implementation
	 * of {@link Ordered}, used when applying multiple processors.
	 * <p>The default value is {@code Ordered.LOWEST_PRECEDENCE}, meaning non-ordered.
	 * @param order the ordering value
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Set the ClassLoader to generate the proxy class in.
	 * <p>Default is the bean ClassLoader, i.e. the ClassLoader used by the containing
	 * {@link org.springframework.beans.factory.BeanFactory} for loading all bean classes.
	 * This can be overridden here for specific proxies.
	 */
	public void setProxyClassLoader(@Nullable ClassLoader classLoader) {
		this.proxyClassLoader = classLoader;
		this.classLoaderConfigured = (classLoader != null);
	}

	/**
	 * Return the configured proxy ClassLoader for this processor.
	 */
	@Nullable
	protected ClassLoader getProxyClassLoader() {
		return this.proxyClassLoader;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		if (!this.classLoaderConfigured) {
			this.proxyClassLoader = classLoader;
		}
	}


	/**
	 * 评估代理接口：
	 * (1)获取bean的所有接口，然后判断是否存在可用于代理的接口，
	 * (2)如果有可用于代理的接口，就添加bean的所有接口作为代理接口；
	 * (3)没有的话就设置proxyTargetClass属性(是否代理目标类的标识)为true
	 *
	 * 题外：只有当【不是容器回调接口 && 不是内部语言接口 && 接口中存在方法】才是可以用的接口
	 * 题外：容器回调接口：InitializingBean、DisposableBean、Closeable、AutoCloseable、Aware
	 * 题外：内部语言接口：groovy.lang.GroovyObject接口、以.cglib.proxy.Factory、.bytebuddy.MockAccess名称结尾的接口
	 *
	 * Check the interfaces on the given bean class and apply them to the {@link ProxyFactory},
	 * if appropriate.
	 * <p>Calls {@link #isConfigurationCallbackInterface} and {@link #isInternalLanguageInterface}
	 * to filter for reasonable proxy interfaces, falling back to a target-class proxy otherwise.
	 * @param beanClass the class of the bean
	 * @param proxyFactory the ProxyFactory for the bean
	 */
	protected void evaluateProxyInterfaces(Class<?> beanClass, ProxyFactory proxyFactory) {
		/*

		1、获取bean的所有接口，然后判断是否存在可用于代理的接口

		题外：只有当【不是容器回调接口 && 不是内部语言接口 && 接口中存在方法】才是可以用的接口

		*/
		// 获取当前类的所有接口
		Class<?>[] targetInterfaces = ClassUtils.getAllInterfacesForClass(beanClass, getProxyClassLoader());
		// 是否有可用于代理的接口的标识，true代表有，false代表没有
		boolean hasReasonableProxyInterface/* 有合理的代理接口 */ = false;
		// 遍历当前类的所有接口，判断是否有可用于代理的接口，因为并不是所有的接口都可以进行代理，所以要进行判断
		for (Class<?> ifc : targetInterfaces) {
			/**
			 * 1、isConfigurationCallbackInterface()：判断接口是不是容器回调接口，如果是容器回调接口，则当前接口不能被代理
			 *
			 * 容器回调接口：InitializingBean、DisposableBean、Closeable、AutoCloseable、Aware
			 *
			 * 2、isInternalLanguageInterface()：判断接口是不是内部语言接口，是的话，则当前接口不能被代理
			 *
			 * 内部语言接口：groovy.lang.GroovyObject接口、以.cglib.proxy.Factory、.bytebuddy.MockAccess名称结尾的接口
			 *
			 */
			// 判断当前接口是否可用，只有当【不是容器回调接口 && 不是内部语言接口 && 接口中存在方法】才是可以用的接口
			// 题外：并不是所有的接口都可以进行代理，所以有一系列的条件判断
			if (!isConfigurationCallbackInterface/* 是配置回调接口 */(ifc) && !isInternalLanguageInterface/* 是内部语言接口 */(ifc) &&
					ifc.getMethods().length > 0) {
				// 用接口代理，也就是jdk
				hasReasonableProxyInterface = true;
				break;
			}
		}

		/* 2、如果有可用于代理的接口，就添加bean的所有接口作为代理接口 */
		// 有可用于代理的接口
		if (hasReasonableProxyInterface) {
			// Must allow for introductions; can't just set interfaces to the target's interfaces only. —— 我们允许介绍；不能只将接口设置为目标的接口。
			for (Class<?> ifc : targetInterfaces) {
				// ⚠️添加代理接口（把当前类的所有接口都加入进去）
				proxyFactory.addInterface(ifc);
			}
		}
		/* 3、没有的话，就设置proxyTargetClass属性(是否代理目标类的标识)为true */
		// 没有可用于代理的接口，则设置proxyTargetClass属性(是否使用目标类代理的标识)为true
		else {
			proxyFactory.setProxyTargetClass(true);
		}
	}

	/**
	 * 判断接口是不是容器回调接口，是的话，则当前接口不能被代理
	 *
	 * 容器回调接口：InitializingBean、DisposableBean、Closeable、AutoCloseable、Aware
	 *
	 * Determine whether the given interface is just a container callback and
	 * therefore not to be considered as a reasonable proxy interface.
	 *
	 * 确定给定的接口是否只是一个容器回调，因此不被认为是一个合理的代理接口。
	 *
	 * <p>If no reasonable proxy interface is found for a given bean, it will get
	 * proxied with its full target class, assuming that as the user's intention.
	 * @param ifc the interface to check
	 * @return whether the given interface is just a container callback
	 */
	protected boolean isConfigurationCallbackInterface/* 是配置回调接口 */(Class<?> ifc/* 接口 */) {
		return (InitializingBean.class == ifc || DisposableBean.class == ifc || Closeable.class == ifc ||
				AutoCloseable.class == ifc ||
				// 接口实现的接口中包含Aware
				ObjectUtils.containsElement(ifc.getInterfaces(), Aware.class));
	}

	/**
	 * 判断接口是不是内部语言接口，是的话，则当前接口不能被代理
	 *
	 * 内部语言接口：groovy.lang.GroovyObject接口、以.cglib.proxy.Factory、.bytebuddy.MockAccess名称结尾的接口
	 *
	 * Determine whether the given interface is a well-known internal language interface
	 *
	 * 确定给定接口是否是众所周知的内部语言接口，因此不被视为合理的代理接口。
	 *
	 * and therefore not to be considered as a reasonable proxy interface.
	 * <p>If no reasonable proxy interface is found for a given bean, it will get
	 * proxied with its full target class, assuming that as the user's intention.
	 * @param ifc the interface to check
	 * @return whether the given interface is an internal language interface
	 */
	protected boolean isInternalLanguageInterface(Class<?> ifc) {
		return (ifc.getName().equals("groovy.lang.GroovyObject") ||
				ifc.getName().endsWith(".cglib.proxy.Factory") ||
				ifc.getName().endsWith(".bytebuddy.MockAccess"));
	}

}
