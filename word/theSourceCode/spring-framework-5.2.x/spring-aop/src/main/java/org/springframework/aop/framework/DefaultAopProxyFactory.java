/*
 * Copyright 2002-2015 the original author or authors.
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

import org.springframework.aop.SpringProxy;

import java.io.Serializable;
import java.lang.reflect.Proxy;

/**
 * Default {@link AopProxyFactory} implementation, creating either a CGLIB proxy
 * or a JDK dynamic proxy.
 *
 * <p>Creates a CGLIB proxy if one the following is true for a given
 * {@link AdvisedSupport} instance:
 * <ul>
 * <li>the {@code optimize} flag is set
 * <li>the {@code proxyTargetClass} flag is set
 * <li>no proxy interfaces have been specified
 * </ul>
 *
 * <p>In general, specify {@code proxyTargetClass} to enforce a CGLIB proxy,
 * or specify one or more interfaces to use a JDK dynamic proxy.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 12.03.2004
 * @see AdvisedSupport#setOptimize
 * @see AdvisedSupport#setProxyTargetClass
 * @see AdvisedSupport#setInterfaces
 */
@SuppressWarnings("serial")
public class DefaultAopProxyFactory implements AopProxyFactory, Serializable {

	/**
	 * 确定使用哪种动态代理方式，jdk还是cglib
	 * ⚠️spring是如何选择代理方式：配置项中指定使用cglib，或者目标类没有接口，那么就使用cglib；否则默认使用jdk
	 *
	 * jdk：JdkDynamicAopProxy
	 * cglib：ObjenesisCglibAopProxy（ObjenesisCglibAopProxy extends CglibAopProxy）
	 */
	@Override
	public AopProxy createAopProxy(AdvisedSupport config/* ProxyFactory */) throws AopConfigException {
		/*

		1、确定使用哪种动态代理方式，jdk还是cglib
		⚠️spring是如何选择代理方式：配置项中指定使用cglib，或者目标类没有接口，那么就使用cglib；否则默认使用jdk

		*/
		/**
		 *
		 * 1、config.isOptimize()：是否对代理类的生成使用策略优化，其作用是和isProxyTargetClass是一样的，默认为false
		 *
		 * 题外：optimize：xml可配置，「AdvisedSupport extends ProxyConfig」，在「ProxyConfig」中有「optimize」成员变量，默认为false
		 *
		 * 2、config.isProxyTargetClass()：是否强制使用Cglib的方式创建代理对象，默认为false
		 *
		 * proxyTargetClass：在「@EnableAsync、@EnableAspectJAutoProxy」，<aop:aspectj-autoproxy proxy-target-class="true">当中配置，默认为false
		 *
		 * 3、hasNoUserSuppliedProxyInterfaces()：目标类是不是没有用户提供的代理接口（目标类是否有实现除SpringProxy之外的接口）；true：没有接口；false：有接口
		 */
		/* 判断选择哪种方式创建代理对象，是cglib还是jdk */
		// 【optimize=true || proxyTargetClass=true || 目标类没有实现接口】条件成立
		if (config.isOptimize()/* 是否进行优化 */  || config.isProxyTargetClass() || hasNoUserSuppliedProxyInterfaces(config)) {

			// 获取目标类Class对象
			// 题外：Class对象：类对象
			Class<?> targetClass = config.getTargetClass();
			if (targetClass == null) {
				// TargetSource无法确定目标类：创建代理需要接口或目标。
				throw new AopConfigException("TargetSource cannot determine target class: " +
						"Either an interface or a target is required for proxy creation.");
			}

			/* jdk */
			// 决定使用cglib之前，接着判断一下，目标类是不是接口，目标类是不是jdk的Proxy类型
			// 如果【目标类是接口 || 目标类是jdk的Proxy类型】则还是使用JDK的方式生成代理对象
			if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
				return new JdkDynamicAopProxy(config);
			}

			/* cglib */
			// 配置项中指定使用Cglib进行动态代理，或者目标类没有接口，那么就使用Cglib的方式创建代理对象
			// 题外：ObjenesisCglibAopProxy extends CglibAopProxy
			return new ObjenesisCglibAopProxy(config);
		}
		/* jdk */
		else {
			return new JdkDynamicAopProxy(config);
		}
	}

	/**
	 * 目标类是否有实现除SpringProxy之外的接口，false：有接口；true：没有接口
	 *
	 * Determine whether the supplied {@link AdvisedSupport} has only the
	 * {@link org.springframework.aop.SpringProxy} interface specified
	 * (or no proxy interfaces specified at all).
	 */
	private boolean hasNoUserSuppliedProxyInterfaces(AdvisedSupport config) {
		Class<?>[] ifcs = config.getProxiedInterfaces();
		return (ifcs.length == 0 || (ifcs.length == 1 && SpringProxy.class.isAssignableFrom(ifcs[0])));
	}

}
