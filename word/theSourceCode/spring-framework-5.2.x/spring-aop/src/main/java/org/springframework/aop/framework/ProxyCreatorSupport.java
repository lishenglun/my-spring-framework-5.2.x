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

import org.springframework.util.Assert;

import java.util.LinkedList;
import java.util.List;

/**
 * 引用了AopProxyFactory用来创建代理对象
 *
 * Base class for proxy factories.
 * Provides convenient access to a configurable AopProxyFactory.
 *
 * @author Juergen Hoeller
 * @since 2.0.3
 * @see #createAopProxy()
 */
@SuppressWarnings("serial")
public class ProxyCreatorSupport extends AdvisedSupport {

	// 注意：这个AopProxyFactory是在创建ProxyFactory时，在ProxyFactory的父类ProxyCreatorSupport的构造器中创建的：AopProxyFactory = DefaultAopProxyFactory
	private AopProxyFactory aopProxyFactory;

	private final List<AdvisedSupportListener> listeners = new LinkedList<>();

	/** Set to true when the first AOP proxy has been created. */
	private boolean active = false;


	/**
	 * Create a new ProxyCreatorSupport instance.
	 */
	public ProxyCreatorSupport() {
		this.aopProxyFactory = new DefaultAopProxyFactory();
	}

	/**
	 * Create a new ProxyCreatorSupport instance.
	 * @param aopProxyFactory the AopProxyFactory to use
	 */
	public ProxyCreatorSupport(AopProxyFactory aopProxyFactory) {
		Assert.notNull(aopProxyFactory, "AopProxyFactory must not be null");
		this.aopProxyFactory = aopProxyFactory;
	}


	/**
	 * Customize the AopProxyFactory, allowing different strategies
	 * to be dropped in without changing the core framework.
	 * <p>Default is {@link DefaultAopProxyFactory}, using dynamic JDK
	 * proxies or CGLIB proxies based on the requirements.
	 */
	public void setAopProxyFactory(AopProxyFactory aopProxyFactory) {
		Assert.notNull(aopProxyFactory, "AopProxyFactory must not be null");
		this.aopProxyFactory = aopProxyFactory;
	}

	/**
	 * Return the AopProxyFactory that this ProxyConfig uses.
	 */
	public AopProxyFactory getAopProxyFactory() {
		return this.aopProxyFactory;
	}

	/**
	 * Add the given AdvisedSupportListener to this proxy configuration.
	 * @param listener the listener to register
	 */
	public void addListener(AdvisedSupportListener listener) {
		Assert.notNull(listener, "AdvisedSupportListener must not be null");
		this.listeners.add(listener);
	}

	/**
	 * Remove the given AdvisedSupportListener from this proxy configuration.
	 * @param listener the listener to deregister
	 */
	public void removeListener(AdvisedSupportListener listener) {
		Assert.notNull(listener, "AdvisedSupportListener must not be null");
		this.listeners.remove(listener);
	}


	/**
	 * 通过AopProxyFactory确定使用哪种代理方式，jdk还是cglib，最终得到一个AopProxy
	 * >>> spring是如何选择代理方式？：配置项中指定使用cglib，或者目标类没有接口，那么就使用cglib；否则默认使用jdk
	 * >>> jdk：JdkDynamicAopProxy
	 * >>> cglib：ObjenesisCglibAopProxy（ObjenesisCglibAopProxy extends CglibAopProxy）
	 *
	 * 题外：如果激活了，就需要有激活通知
	 *
	 * Subclasses should call this to get a new AOP proxy. They should <b>not</b>
	 * create an AOP proxy with {@code this} as an argument.
	 *
	 * 子类应该调用它来获得一个新的AOP代理。他们应该<b>不<b>创建一个以{@code this}作为参数的AOP代理
	 */
	protected final synchronized AopProxy createAopProxy() {
		// 看一下有没有对应的一些监听器，有的话就激活
		if (!this.active) {
			// 监听调用AdvisedSupportListener实现类的activated()
			activate();
		}

		/*

		通过AopProxyFactory确定使用哪种代理方式，jdk还是cglib，最终得到一个AopProxy
		>>> spring是如何选择代理方式？：配置项中指定使用cglib，或者目标类没有接口，那么就使用cglib；否则默认使用jdk
		>>> jdk：JdkDynamicAopProxy
		>>> cglib：ObjenesisCglibAopProxy（ObjenesisCglibAopProxy extends CglibAopProxy）

		 */
		/**
		 * 1、getAopProxyFactory()：获取AopProxyFactory = DefaultAopProxyFactory
		 *
		 * 注意：这个AopProxyFactory是在创建ProxyFactory时，在ProxyFactory的父类ProxyCreatorSupport的构造器中创建的：AopProxyFactory = DefaultAopProxyFactory
		 *
		 * 2、AopProxy：不同的创建AOP动态代理方式的顶层接口，是jdk还是cglib
		 */
		// 通过AopProxyFactory，确定使用哪种代理方式去创建aop动态代理对象，jdk还是cglib，最终得到一个AopProxy
		return getAopProxyFactory().createAopProxy(this/* ProxyFactory */);
	}

	/**
	 * 激活通知，跟前面的添加接口的通知一样，都是给AdvisedSupportListener通知
	 *
	 * Activate this proxy configuration.
	 * @see AdvisedSupportListener#activated
	 */
	private void activate() {
		this.active = true;
		for (AdvisedSupportListener listener : this.listeners) {
			listener.activated(this);
		}
	}

	/**
	 * Propagate advice change event to all AdvisedSupportListeners.
	 * @see AdvisedSupportListener#adviceChanged
	 */
	@Override
	protected void adviceChanged() {
		super.adviceChanged();
		synchronized (this) {
			if (this.active) {
				for (AdvisedSupportListener listener : this.listeners) {
					listener.adviceChanged(this);
				}
			}
		}
	}

	/**
	 * Subclasses can call this to check whether any AOP proxies have been created yet.
	 */
	protected final synchronized boolean isActive() {
		return this.active;
	}

}
