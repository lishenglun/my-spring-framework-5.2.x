/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.aop.framework.autoproxy;

import org.aopalliance.aop.Advice;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyProcessorSupport;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} implementation
 * that wraps each eligible bean with an AOP proxy, delegating to specified interceptors
 * before invoking the bean itself.
 *
 * <p>This class distinguishes between "common" interceptors: shared for all proxies it
 * creates, and "specific" interceptors: unique per bean instance. There need not be any
 * common interceptors. If there are, they are set using the interceptorNames property.
 * As with {@link org.springframework.aop.framework.ProxyFactoryBean}, interceptors names
 * in the current factory are used rather than bean references to allow correct handling
 * of prototype advisors and interceptors: for example, to support stateful mixins.
 * Any advice type is supported for {@link #setInterceptorNames "interceptorNames"} entries.
 *
 * <p>Such auto-proxying is particularly useful if there's a large number of beans that
 * need to be wrapped with similar proxies, i.e. delegating to the same interceptors.
 * Instead of x repetitive proxy definitions for x target beans, you can register
 * one single such post processor with the bean factory to achieve the same effect.
 *
 * <p>Subclasses can apply any strategy to decide if a bean is to be proxied, e.g. by type,
 * by name, by definition details, etc. They can also return additional interceptors that
 * should just be applied to the specific bean instance. A simple concrete implementation is
 * {@link BeanNameAutoProxyCreator}, identifying the beans to be proxied via given names.
 *
 * <p>Any number of {@link TargetSourceCreator} implementations can be used to create
 * a custom target source: for example, to pool prototype objects. Auto-proxying will
 * occur even if there is no advice, as long as a TargetSourceCreator specifies a custom
 * {@link org.springframework.aop.TargetSource}. If there are no TargetSourceCreators set,
 * or if none matches, a {@link org.springframework.aop.target.SingletonTargetSource}
 * will be used by default to wrap the target bean instance.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Rob Harrop
 * @since 13.10.2003
 * @see #setInterceptorNames
 * @see #getAdvicesAndAdvisorsForBean
 * @see BeanNameAutoProxyCreator
 * @see DefaultAdvisorAutoProxyCreator
 */
@SuppressWarnings("serial")
public abstract class AbstractAutoProxyCreator extends ProxyProcessorSupport
		implements SmartInstantiationAwareBeanPostProcessor, BeanFactoryAware {

	/**
	 * Convenience constant for subclasses: Return value for "do not proxy".
	 * @see #getAdvicesAndAdvisorsForBean
	 */
	@Nullable
	protected static final Object[] DO_NOT_PROXY = null;

	/**
	 * Convenience constant for subclasses: Return value for
	 * "proxy without additional interceptors, just the common ones".
	 * @see #getAdvicesAndAdvisorsForBean
	 */
	protected static final Object[] PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS = new Object[0];


	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	/** Default is global AdvisorAdapterRegistry. */
	private AdvisorAdapterRegistry advisorAdapterRegistry = GlobalAdvisorAdapterRegistry.getInstance();

	/**
	 * Indicates whether or not the proxy should be frozen. Overridden from super
	 * to prevent the configuration from becoming frozen too early.
	 */
	private boolean freezeProxy = false;

	/** Default is no common interceptors. —— 默认是没有通用拦截器。 */
	// 注册的通用的Advisor（不是Spring默认提供的，而是我们自定义注册的）
	private String[] interceptorNames = new String[0];

	// 是否首先应用通用Advisor的标识，默认为true
	private boolean applyCommonInterceptorsFirst = true;

	// 一般不会配置这个
	@Nullable
	private TargetSourceCreator[] customTargetSourceCreators/* 自定义目标源创建者 */;

	@Nullable
	private BeanFactory beanFactory;

	// 在resolveBeforeInstantiation()中已经为某个beanName创建了动态代理，就存储这个beanName
	private final Set<String> targetSourcedBeans = Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	// 保存，已经处理过的对象
	private final Map<Object, Object> earlyProxyReferences/* 早起代理参考 */ = new ConcurrentHashMap<>(16);

	private final Map<Object, Class<?>> proxyTypes = new ConcurrentHashMap<>(16);

	// 存储Pointcut、Advisor、Advice、AopInfrastructureBean类型的bean
	// key：beanName / &beanName / 类全限定名
	// value：false
	private final Map<Object, Boolean> advisedBeans = new ConcurrentHashMap<>(256);


	/**
	 * Set whether or not the proxy should be frozen, preventing advice
	 * from being added to it once it is created.
	 * <p>Overridden from the super class to prevent the proxy configuration
	 * from being frozen before the proxy is created.
	 */
	@Override
	public void setFrozen(boolean frozen) {
		this.freezeProxy = frozen;
	}

	@Override
	public boolean isFrozen() {
		return this.freezeProxy;
	}

	/**
	 * Specify the {@link AdvisorAdapterRegistry} to use.
	 * <p>Default is the global {@link AdvisorAdapterRegistry}.
	 * @see org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry
	 */
	public void setAdvisorAdapterRegistry(AdvisorAdapterRegistry advisorAdapterRegistry) {
		this.advisorAdapterRegistry = advisorAdapterRegistry;
	}

	/**
	 * Set custom {@code TargetSourceCreators} to be applied in this order.
	 * If the list is empty, or they all return null, a {@link SingletonTargetSource}
	 * will be created for each bean.
	 * <p>Note that TargetSourceCreators will kick in even for target beans
	 * where no advices or advisors have been found. If a {@code TargetSourceCreator}
	 * returns a {@link TargetSource} for a specific bean, that bean will be proxied
	 * in any case.
	 * <p>{@code TargetSourceCreators} can only be invoked if this post processor is used
	 * in a {@link BeanFactory} and its {@link BeanFactoryAware} callback is triggered.
	 * @param targetSourceCreators the list of {@code TargetSourceCreators}.
	 * Ordering is significant: The {@code TargetSource} returned from the first matching
	 * {@code TargetSourceCreator} (that is, the first that returns non-null) will be used.
	 */
	public void setCustomTargetSourceCreators(TargetSourceCreator... targetSourceCreators) {
		this.customTargetSourceCreators = targetSourceCreators;
	}

	/**
	 * Set the common interceptors. These must be bean names in the current factory.
	 * They can be of any advice or advisor type Spring supports.
	 * <p>If this property isn't set, there will be zero common interceptors.
	 * This is perfectly valid, if "specific" interceptors such as matching
	 * Advisors are all we want.
	 */
	public void setInterceptorNames(String... interceptorNames) {
		this.interceptorNames = interceptorNames;
	}

	/**
	 * Set whether the common interceptors should be applied before bean-specific ones.
	 * Default is "true"; else, bean-specific interceptors will get applied first.
	 */
	public void setApplyCommonInterceptorsFirst(boolean applyCommonInterceptorsFirst) {
		this.applyCommonInterceptorsFirst = applyCommonInterceptorsFirst;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	/**
	 * Return the owning {@link BeanFactory}.
	 * May be {@code null}, as this post-processor doesn't need to belong to a bean factory.
	 */
	@Nullable
	protected BeanFactory getBeanFactory() {
		return this.beanFactory;
	}


	@Override
	@Nullable
	public Class<?> predictBeanType(Class<?> beanClass, String beanName) {
		if (this.proxyTypes.isEmpty()) {
			return null;
		}
		Object cacheKey = getCacheKey(beanClass, beanName);
		return this.proxyTypes.get(cacheKey);
	}

	@Override
	@Nullable
	public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName) {
		return null;
	}

	/**
	 * 注意：该方法只会由三级缓存的{@link AbstractAutowireCapableBeanFactory#getEarlyBeanReference(String, RootBeanDefinition, Object)}方法进行调用
	 */
	@Override
	public Object getEarlyBeanReference(Object bean, String beanName) {
		Object cacheKey = getCacheKey(bean.getClass(), beanName);
		// ⚠️
		this.earlyProxyReferences.put(cacheKey, bean);
		return wrapIfNecessary(bean, beanName, cacheKey);
	}

	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
		/*

		获取当前bean的key：
		1、如果beanName不为空，且不是FactoryBean类型，则以beanName为key；
		2、如果beanName不为空，但是FactoryBean类型，则以&+beanName为key；
		3、如果beanName为空，则以当前bean对应的class为key

		 */
		Object cacheKey = getCacheKey(beanClass, beanName);

		/*

		1、判断，当前bd，是否要跳过，不进行动态代理。【是基础类 || 当前bd是切面】，则都不需要进行动态代理，直接跳过。

		题外：这里只是做了一个预先判断，后面，还需要判断，当targetSource不为null时，才会进行真正的动态代理，而targetSource一般为null

		 */
		if (!StringUtils.hasLength(beanName)/* 判断当前beanName是否有值，有返回true */  || !this.targetSourcedBeans.contains(beanName)) {
			// 先从advisedBeans缓存中查询，当前bean是不是-不需要进行动态代理的bean
			// advisedBeans中会记录不需要创建动态代理的bean，如果当前bean存在于advisedBeans中，则说明当前bean不需要进行动态代理，直接跳过
			if (this.advisedBeans.containsKey(cacheKey)) {
				return null;
			}
			/**
			 * 1、isInfrastructureClass()：判断当前要创建的bean是不是一个AOP基础设施类，是的话就跳过
			 *
			 * AOP基础设施类：Pointcut、Advisor、Advice、AopInfrastructureBean接口的实例都是Aop基础设施类
			 *
			 * 2、shouldSkip()：⚠️获取所有容器中的Advisor bean，然后判断当前bd是不是切面，是的话，就跳过，不需要进行动态代理(因为切面类自身不需要被代理，所以直接跳过去)
			 *
			 * 疑问：如果是在创建一个Advisor bean的时候，它也进入到shouldSkip()，它也去获取所有容器的Advisor bean，又能获取到的自己，又去实例化，自己，岂不是无限循环了？
			 * >>> 解决：当是一个Advisor bd进到当前方法的时候，isInfrastructureClass(beanClass)就会识别到它是一个AOP基础设施类，为true，就直接跳过了，所以不会无限循环
			 */
			//【是AOP基础设施类 || 当前bd是切面】，则都不需要进行动态代理，直接跳过
			if (isInfrastructureClass(beanClass) || shouldSkip(beanClass, beanName)/* ⚠️ */) {
				// 记录跳过的，也就是不需要创建动态代理的bean到advisedBeans中，方便下次直接从advisedBeans中判断当前bean是否要创建代理对象
				this.advisedBeans.put(cacheKey, Boolean.FALSE);
				return null;
			}
		}

		/*

		2、获取自定义的TargetSource，如果targetSource不为null，就创建代理对象！

		题外：为什么这里不主动创建AOP代理对象？因为如果当前bd符合一个标准的AOP代理对象，后续流程会创建，在这就不需要对它进行提前创建了，
		resolveBeforeInstantiation()更加强调的是用户自定义代理的方式，针对于当前的"被代理类"需要经过标准的"AOP代理流程"来创建对象不在这里处理。
		(这里强调的是用户自定义的代理方式；不是spring对于一个bean处理的标准流程。用户想自定义代理对象，不想要走spring bean的标准流程，例如：populateBean()，就可以在这里进行创建代理对象返回了)

		 */
		// Create proxy here if we have a custom TargetSource.
		// Suppresses unnecessary default instantiation of the target bean:
		// The TargetSource will handle target instances in a custom fashion.
		// 上面的翻译：如果我们有自定义TargetSource，在这就可以创建代理了。抑制目标bean的不必要的默认实例化：TargetSource将以自定义方式处理目标实例。

		// TargetSource：自定义目标源。我现在想要为某一个对象创建动态代理对象，那此时是否需要知道，我现在为哪一个类创建
		TargetSource targetSource/* 目标源 */ = getCustomTargetSource(beanClass, beanName);
		// ⚠️目标源不为null，才会创建动态代理，但是这个一般为null
		if (targetSource != null) {
			if (StringUtils.hasLength(beanName)) {
				this.targetSourcedBeans.add(beanName);
			}

			/*

			获取能够增强当前bean的所有Advisor bean(通知)

			题外：只要当前bean对象中的一个方法被某个Advisor中的切入点所匹配，这个bean对象就需要被代理，这个Advisor也就作为代理中的拦截器。
			那肯定有疑惑，为什么一个方法被匹配了，就要对整个bean对象进行代理，其余的方法可能是不需要增强的？因为代理粒度是对象级别的，所以一个方法匹配了就对整个对象进行代理。
			后续在代理对象内部再判断，当前方法是不是要被拦截的！

			题外：扩展advisor的逻辑在里面：会添加一个DefaultPointcutAdvisor Advisor，里面包含了ExposeInvocationInterceptor advice；

			题外：sortAdvisor()的逻辑在里面

			题外：⚠️@Transaction的解析逻辑在里面

			 */

			Object[] specificInterceptors = getAdvicesAndAdvisorsForBean/* 获得Bean的建议和顾问 */(beanClass, beanName, targetSource);
			// ⚠️创建我们的代理
			Object proxy = createProxy(beanClass, beanName, specificInterceptors, targetSource);
			this.proxyTypes.put(cacheKey, proxy.getClass());
			return proxy;
		}

		return null;
	}

	@Override
	public boolean postProcessAfterInstantiation(Object bean, String beanName) {
		return true;
	}

	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
		return pvs;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	/**
	 * 此处是真正创建aop代理的地方，在实例化之后，初始化之后就行处理
	 * 首先查看是否在earlyProxyReferences里存在，如果有就说明处理过了，不存在就考虑是否要包装，也就是代理
	 *
	 * Create a proxy with the configured interceptors if the bean is
	 * identified as one to proxy by the subclass. - 如果Bean被子类标识为要代理的bean，则使用配置的拦截器创建代理。
	 * @see #getAdvicesAndAdvisorsForBean
	 */
	@Override
	public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
		/*

		1、判断当前对象是不是被处理过了，没被处理过，就调用wrapIfNecessary()进行下一步的"是否需要动态代理判断"

		 */
		if (bean != null) {
			Object cacheKey = getCacheKey(bean.getClass(), beanName);
			/**
			 * 只有调用三级缓存的{@link AbstractAutowireCapableBeanFactory#getEarlyBeanReference(String, RootBeanDefinition, Object)}，
			 * 然后再调用{@link AbstractAutoProxyCreator#getEarlyBeanReference(Object, String)}时，内部会执行this.earlyProxyReferences.put(cacheKey, bean)操作
			 */
			// 判断当前bean是否正在被代理的处理过程中，如果正在处理过程中则不进行再次代理处理
			// 注意：⚠️如果this.earlyProxyReferences.remove(cacheKey)返回的是null。null!=bean是可以进行比较的，不会出错！
			// 是否是由于避免循环依赖而创建的bean代理
			if (this.earlyProxyReferences.remove(cacheKey) != bean) {
				// 如果它需要被代理，则需要封装指定bean
				return wrapIfNecessary(bean, beanName, cacheKey);
			}
		}
		return bean;
	}


	/**
	 * Build a cache key for the given bean class and bean name.
	 * <p>Note: As of 4.2.3, this implementation does not return a concatenated
	 * class/name String anymore but rather the most efficient cache key possible:
	 * a plain bean name, prepended with {@link BeanFactory#FACTORY_BEAN_PREFIX}
	 * in case of a {@code FactoryBean}; or if no bean name specified, then the
	 * given bean {@code Class} as-is.
	 * @param beanClass the bean class
	 * @param beanName the bean name
	 * @return the cache key for the given class and name
	 */
	protected Object getCacheKey(Class<?> beanClass, @Nullable String beanName) {
		/*

		获取当前bean的key：
		1、如果beanName不为空，且不是FactoryBean类型，则以beanName为key；
		2、如果beanName不为空，但是FactoryBean类型，则以&+beanName为key；
		3、如果beanName为空，则以当前bean对应的class为key

		 */
		if (StringUtils.hasLength(beanName)) {
			return (FactoryBean.class.isAssignableFrom(beanClass) ?
					BeanFactory.FACTORY_BEAN_PREFIX/* & */ + beanName : beanName);
		}
		else {
			return beanClass;
		}
	}

	/**
	 * 创建代理，主要包含2个步骤：
	 * （1）获取增强方法或者增强器
	 * （2）根据获取的增强进行代理
	 *
	 * Wrap the given bean if necessary, i.e. if it is eligible for being proxied. —— 如有必要，包装给定的bean，即如果它有资格被代理。
	 *
	 * @param bean the raw bean instance
	 * @param beanName the name of the bean
	 * @param cacheKey the cache key for metadata access
	 * @return a proxy wrapping the bean, or the raw bean instance as-is
	 */
	protected Object wrapIfNecessary/* 必要时包装 */(Object bean, String beanName, Object cacheKey) {
		/* 1、判断一下是否已经自定义创建过当前beanName对应的代理对象，创建过，就返回当前bean，不再重复创建 */
		/**
		 * 只有在{@link AbstractAutoProxyCreator#postProcessBeforeInstantiation}时用户自定义创建动态代理，
		 * 才会执行this.targetSourcedBeans.add(beanName)操作
		 */
		// 如果已经处理过
		if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
			return bean;
		}

		/*

		2、判断当前bean是不是不需要被代理的对象，如果是，则直接跳过，返回原生bean

		题外：当前bean是【AOP基础设施类 || 切面类】，则都不需要进行动态代理

		*/
		/**
		 * 在createBean() ——> resolveBeforeInstantiation() —— AbstractAutoProxyCreator#postProcessBeforeInstantiation()中，
		 * 如果当前对象是要跳过，不需要创建代理对象的，那么会放入advisedBeans中，key为cacheKey，value为Boolean.FALSE
		 */
		// 这里advisedBeans缓存了不需要进行动态代理的beanName，如果缓存中存在，则代表当前bean不需要进行动态代理，所以直接返回当前bean
		if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
			return bean;
		}
		/**
		 * 1、isInfrastructureClass()：判断当前bean是不是一个AOP基础设施类，是的话就跳过
		 *
		 * 2、shouldSkip()：获取所有容器中的Advisor bean，然后判断当前bd是不是切面，是的话，就跳过，不需要进行动态代理(因为切面类自身不需要被代理，所以直接跳过去)
		 */
		// ⚠️如果当前bean【是AOP基础设施类 || 是切面类】，则都不需要进行动态代理，所以直接返回当前bean
		// 题外：AOP基础设施类不应代理
		if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
			this.advisedBeans.put(cacheKey, Boolean.FALSE);
			return bean;
		}

		/*

		3、创建代理主要包含2个步骤
		（1）获取增强方法或者增强器
		（2）根据获取的增强进行代理

		*/

		// Create proxy if we have advice. —— 如果我们有建议，请创建代理。

		/*

		（1）获取能够增强当前bean的所有Advisor bean(通知)

		题外：只要当前bean对象中的一个方法被某个Advisor中的切入点所匹配，这个bean对象就需要被代理，这个Advisor也就作为代理中的拦截器。
		那肯定有疑惑，为什么一个方法被匹配了，就要对整个bean对象进行代理，其余的方法可能是不需要增强的？因为代理粒度是对象级别的，所以一个方法匹配了就对整个对象进行代理。
		后续在代理对象内部再判断，当前方法是不是要被拦截的！

		题外：扩展advisor的逻辑在里面：会添加一个DefaultPointcutAdvisor Advisor，里面包含了ExposeInvocationInterceptor advice；

		题外：sortAdvisor()的逻辑在里面

		题外：⚠️@Transaction的解析逻辑在里面

		 */
		// 获取能够增强当前对象的所有Advisor bean(通知类型),相当于获取bean的拦截器
		//
		// AbstractAdvisorAutoProxyCreator
		Object[] specificInterceptors/* 特定的拦截器 */ = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);

		/*

		（2）如果存在增强当前bean的Advisor bean，就创建代理

		 */
		// 对当前bean的代理状态进行缓存
		if (specificInterceptors != DO_NOT_PROXY /* null */) {
			// 对当前bean的代理状态进行缓存，表示我已经经过消息通知的处理了
			this.advisedBeans.put(cacheKey, Boolean.TRUE);

			// ⚠️创建代理
			// ️根据获取到的Advices和Advisors为当前bean生成代理对象
			Object proxy = createProxy(bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean)/* ⚠️ */);

			// 缓存生成的代理bean的类型，并且返回生成的代理bean
			this.proxyTypes.put(cacheKey, proxy.getClass());
			return proxy;
		}

		this.advisedBeans.put(cacheKey, Boolean.FALSE);
		return bean;
	}

	/**
	 * 判断beanClass是不是一个AOP基础设施类，是的话就跳过
	 *
	 * Return whether the given bean class represents an infrastructure class
	 * that should never be proxied.
	 * <p>The default implementation considers Advices, Advisors and
	 * AopInfrastructureBeans as infrastructure classes.
	 * @param beanClass the class of the bean
	 * @return whether the bean represents an infrastructure class
	 * @see org.aopalliance.aop.Advice
	 * @see org.springframework.aop.Advisor
	 * @see org.springframework.aop.framework.AopInfrastructureBean
	 * @see #shouldSkip
	 */
	protected boolean isInfrastructureClass(Class<?> beanClass) {
		boolean retVal = Advice.class.isAssignableFrom(beanClass) ||
				Pointcut.class.isAssignableFrom(beanClass) ||
				Advisor.class.isAssignableFrom(beanClass) ||
				AopInfrastructureBean.class.isAssignableFrom(beanClass);

		if (retVal && logger.isTraceEnabled()) {
			logger.trace("Did not attempt to auto-proxy infrastructure class [" + beanClass.getName() + "]");
		}

		return retVal;
	}

	/**
	 * Subclasses should override this method to return {@code true} if the
	 * given bean should not be considered for auto-proxying by this post-processor.
	 * <p>Sometimes we need to be able to avoid this happening, e.g. if it will lead to
	 * a circular reference or if the existing target instance needs to be preserved.
	 * This implementation returns {@code false} unless the bean name indicates an
	 * "original instance" according to {@code AutowireCapableBeanFactory} conventions.
	 * @param beanClass the class of the bean
	 * @param beanName the name of the bean
	 * @return whether to skip the given bean
	 * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory#ORIGINAL_INSTANCE_SUFFIX
	 */
	protected boolean shouldSkip(Class<?> beanClass, String beanName) {
		return AutoProxyUtils.isOriginalInstance/* 是原始实例 */(beanName, beanClass);
	}

	/**
	 * Create a target source for bean instances. Uses any TargetSourceCreators if set.
	 * Returns {@code null} if no custom TargetSource should be used.
	 * <p>This implementation uses the "customTargetSourceCreators" property.
	 * Subclasses can override this method to use a different mechanism.
	 * @param beanClass the class of the bean to create a TargetSource for
	 * @param beanName the name of the bean
	 * @return a TargetSource for this bean
	 * @see #setCustomTargetSourceCreators
	 */
	@Nullable
	protected TargetSource getCustomTargetSource(Class<?> beanClass, String beanName) {
		// We can't create fancy target sources for directly registered singletons. —— 我们不能为直接注册的单例创建花哨的目标源
		if (this.customTargetSourceCreators != null &&
				this.beanFactory != null && this.beanFactory.containsBean(beanName)) {
			for (TargetSourceCreator tsc : this.customTargetSourceCreators) {
				TargetSource ts = tsc.getTargetSource(beanClass, beanName);
				if (ts != null) {
					// Found a matching TargetSource. —— 找到匹配的TargetSource
					if (logger.isTraceEnabled()) {
						logger.trace("TargetSourceCreator [" + tsc +
								"] found custom TargetSource for bean with name '" + beanName + "'");
					}
					return ts;
				}
			}
		}

		// No custom TargetSource found. —— 未找到自定义TargetSource
		return null;
	}

	/**
	 * Create an AOP proxy for the given bean.
	 * @param beanClass the class of the bean
	 * @param beanName the name of the bean
	 * @param specificInterceptors the set of interceptors that is specific to this bean (may be empty, but not null)
	 * @param targetSource the TargetSource for the proxy,
	 * already pre-configured to access the bean
	 * @return the AOP proxy for the bean
	 * @see #buildAdvisors
	 */
	protected Object createProxy(Class<?> beanClass, @Nullable String beanName,
			@Nullable Object[] specificInterceptors, TargetSource targetSource/* SingletonTargetSource */) {

		if (this.beanFactory instanceof ConfigurableListableBeanFactory) {
			// 给bd设置暴露属性
			AutoProxyUtils.exposeTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName, beanClass);
		}

		/* 初始化ProxyFactory，进而对真正的创建代理做准备 */

		// 创建ProxyFactory(代理工厂)
		// 注意：⚠️在其父类ProxyCreatorSupport构造器中，创建了AopProxyFactory=DefaultAopProxyFactory
		ProxyFactory proxyFactory = new ProxyFactory();

		// 1、获取当前对象中相关属性：把当前对象里面的属性值放到我们的工厂中
		proxyFactory.copyFrom(this);

		// 2、决定当前bean是否应该使用"目标类"还是"它的接口"进行代理，
		// 如果使用目标类进行代理，则设置proxyTargetClass属性(是否代理目标类的标识)为true，也意味着，是采用cglib代理；
		// 如果是使用接口进行代理，则添加代理接口，也意味着，采用jdk代理
		// 所以在某种程度上来说，这是判断，是使用jdk还是cglib进行动态代理，当然后面也会进一步的进行判断！
		// 不过一般不会使用目标类进行代理，所以这里一般的职责是添加接口
		if (!proxyFactory.isProxyTargetClass()) {
			// 判断是不是使用目标类型进行代理
			// 题外：里面采用的是当前bean对应的bd里面的preserveTargetClass属性来做判断的
			if (shouldProxyTargetClass/* 应该代理目标类 */(beanClass, beanName) /* 判断是不是进行类代理 */ ) {
				// 如果是代理目标类，就设置proxyTargetClass属性(是否代理目标类的标识)为true —— cglib
				proxyFactory.setProxyTargetClass(true);
			} else {
				/**
				 * 评估代理接口：
				 * (1)获取bean的所有接口，然后判断是否存在可用于代理的接口，
				 * (2)如果有可用于代理的接口，就添加bean的所有接口作为代理接口；
				 * (3)没有的话就设置proxyTargetClass属性(是否代理目标类的标识)为true
				 * 题外：只有当【不是容器回调接口 && 不是内部语言接口 && 接口中存在方法】才是可以用的接口
				 * 题外：容器回调接口：InitializingBean、DisposableBean、Closeable、AutoCloseable、Aware
				 * 题外：内部语言接口：groovy.lang.GroovyObject接口、以.cglib.proxy.Factory、.bytebuddy.MockAccess名称结尾的接口
				 */
				// 如果不是代理目标类，就添加代理接口 —— jdk
				evaluateProxyInterfaces(beanClass, proxyFactory);
			}
		}

		// ⚠️构建完整的Advisor：会将"能够增强当前bean的Advisor"与"注册的通用的Advisor"进行整合
		Advisor[] advisors = buildAdvisors(beanName, specificInterceptors);
		// 3、添加Advisor（包含了"能够增强当前bean的Advisor"与"注册的通用的Advisor"）
		proxyFactory.addAdvisors(advisors);

		// 4、设置要代理的目标源，里面包含了目标对象
		// 一般为：SingletonTargetSource
		proxyFactory.setTargetSource(targetSource);

		// 5、为子类提供的"定制代理"函数，子类可以在此函数中对ProxyFactory进行进一步的封装
		customizeProxyFactory(proxyFactory);

		// 用来控制代理工厂被配置之后，是否还允许修改通知，默认值是false (即在代理被配置之后，不允许修改代理的配置)
		// 冰冻：当我这里添加完成之后，后续是否允许被修改：想不能被修改则设置为true，可以被修改就设置为false
		proxyFactory.setFrozen/* 冰冻 */(this.freezeProxy);

		// 判断，当前的Advisor是否允许前置过滤
		if (advisorsPreFiltered()) {
			proxyFactory.setPreFiltered(true);
		}

		// 6、真正获取(创建)代理对象
		return proxyFactory.getProxy(getProxyClassLoader());
	}

	/**
	 * 判断是否有PRESERVE_TARGET_CLASS_ATTRIBUTE属性，有的话就要设置ProxyTargetClass=true
	 *
	 * Determine whether the given bean should be proxied with its target class rather than its interfaces. - 确定是否应使用给定的bean替代其目标类而不是其接口。
	 * <p>Checks the {@link AutoProxyUtils#PRESERVE_TARGET_CLASS_ATTRIBUTE "preserveTargetClass" attribute}
	 * of the corresponding bean definition.
	 * @param beanClass the class of the bean
	 * @param beanName the name of the bean
	 * @return whether the given bean should be proxied with its target class
	 * @see AutoProxyUtils#shouldProxyTargetClass
	 */
	protected boolean shouldProxyTargetClass(Class<?> beanClass, @Nullable String beanName) {
		return (this.beanFactory instanceof ConfigurableListableBeanFactory &&
				AutoProxyUtils.shouldProxyTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName));
	}

	/**
	 * Return whether the Advisors returned by the subclass are pre-filtered
	 * to match the bean's target class already, allowing the ClassFilter check
	 * to be skipped when building advisors chains for AOP invocations.
	 * <p>Default is {@code false}. Subclasses may override this if they
	 * will always return pre-filtered Advisors.
	 * @return whether the Advisors are pre-filtered
	 * @see #getAdvicesAndAdvisorsForBean
	 * @see org.springframework.aop.framework.Advised#setPreFiltered
	 */
	protected boolean advisorsPreFiltered() {
		return false;
	}

	/**
	 * 构建完整的Advisor：会将"能够增强当前bean的Advisor"与"注册的通用的Advisor"进行整合
	 *
	 * Determine the advisors for the given bean, including the specific interceptors
	 * as well as the common interceptor, all adapted to the Advisor interface.
	 *
	 * 确定给定bean的Advisor，包括特定的拦截器以及公共拦截器，所有这些都适应Advisor接口。
	 *
	 * @param beanName the name of the bean
	 * @param specificInterceptors the set of interceptors that is
	 *                             能够增强当前bean的Advisor
	 * specific to this bean (may be empty, but not null)
	 * @return the list of Advisors for the given bean
	 */
	protected Advisor[] buildAdvisors(@Nullable String beanName, @Nullable Object[] specificInterceptors/* 特定的拦截器 */) {
		// Handle prototypes correctly... —— 正确处理原型...
		/* 1、获取注册的通用的Advisor(默认为null，需要配置) */
		Advisor[] commonInterceptors = resolveInterceptorNames();

		/* 2、将"能够增强当前bean的Advisor"与"通用的Advisor"进行整合 */
		// 所有的Advisor
		List<Object> allInterceptors = new ArrayList<>();
		if (specificInterceptors != null) {
			/* (1)添加能够增强当前bean的Advisor */
			/**
			 * 1、为什么添加能够增强当前bean的Advisor？
			 * 这个疑问没有意义，因为先添加和后添加是一样的，因为会判断是否首先应用通用Advisor，来决定是通用Advisor在前面，还是"能够增强当前bean的Advisor"在前，这个顺序是可以被改变的
			 */
			allInterceptors.addAll(Arrays.asList(specificInterceptors));
			/* (2)添加通用Advisor */
			if (commonInterceptors.length > 0) {
				/* (2.1)如果需要首先应用通用Advisor，则把通用Advisor放在最前面 */
				// 如果"是否首先应用通用Advisor的标识"为true(默认为true)，则将通用Advisor放在最前面
				if (this.applyCommonInterceptorsFirst/* 首先应用通用拦截器，默认为true */) {
					allInterceptors.addAll(0, Arrays.asList(commonInterceptors));
				}
				/* (2.2)否则，放到最后面 */
				else {
					allInterceptors.addAll(Arrays.asList(commonInterceptors));
				}
			}
		}

		if (logger.isTraceEnabled()) {
			int nrOfCommonInterceptors = commonInterceptors.length;
			int nrOfSpecificInterceptors = (specificInterceptors != null ? specificInterceptors.length : 0);
			logger.trace("Creating implicit proxy for bean '" + beanName + "' with " + nrOfCommonInterceptors +
					" common interceptors and " + nrOfSpecificInterceptors + " specific interceptors");
		}

		/* 3、确保所有的增强器都是Advisor类型，如果是Advice类型，则包装Advice为一个Advisor */
		Advisor[] advisors = new Advisor[allInterceptors.size()];
		for (int i = 0; i < allInterceptors.size(); i++) {
			/**
			 * allInterceptors里面的数据大多数情况都是Advisor，少部分情况是Advice，
			 * 可是我们要的统一是Advisor，为了防止部分Advice的情况，所以就要把Advice包装成一个Advisor进行返回；
			 * 如果是Advisor的话，就不需要包装，直接类型转换为Advisor进行返回
			 */
			// 包装Advice为一个Advisor（Advisor不需要包装）
			// 拦截然进行封装转化为Advisor
			// 题外：⚠️由于Spring中涉及过多的拦截器、增强器、增强方法，等方式来对逻辑进行增强，所以非常有必要统一封装成Advisor来进行代理的创建
			advisors[i] = this.advisorAdapterRegistry.wrap(allInterceptors.get(i));
		}

		return advisors;
	}

	/**
	 * 获取注册的通用的Advisor(默认为null，需要配置)
	 *
	 * Resolves the specified interceptor names to Advisor objects. —— 将指定的拦截器名称解析为Advisor对象
	 * @see #setInterceptorNames
	 */
	private Advisor[] resolveInterceptorNames() {
		BeanFactory bf = this.beanFactory;
		// bf是ConfigurableBeanFactory类型，则转换为ConfigurableBeanFactory；不是则是null
		ConfigurableBeanFactory cbf = (bf instanceof ConfigurableBeanFactory ? (ConfigurableBeanFactory) bf : null);

		List<Advisor> advisors = new ArrayList<>();

		for (String beanName : this.interceptorNames) {
			// "cbf == null"相当于beanFactory不是ConfigurableBeanFactory类型
			// beanFactory不是ConfigurableBeanFactory类型 || beanName对应的bean不是正在创建过程中
			if (cbf == null || !cbf.isCurrentlyInCreation/* 正在创建中 */(beanName)) {
				Assert.state(bf != null, "BeanFactory required for resolving interceptor names"/* 解析拦截器名称所需的BeanFactory */);
				// 获取拦截器
				Object next = bf.getBean(beanName);
				// ⚠️拦截器转换为Advisor，然后添加这个Advisor
				advisors.add(this.advisorAdapterRegistry.wrap(next));
			}
		}

		return advisors.toArray(new Advisor[0]);
	}

	/**
	 * 为子类提供的"定制代理"函数，子类可以在此函数中对ProxyFactory进行进一步的封装
	 *
	 * Subclasses may choose to implement this: for example,
	 * to change the interfaces exposed.
	 * <p>The default implementation is empty.
	 * @param proxyFactory a ProxyFactory that is already configured with
	 * TargetSource and interfaces and will be used to create the proxy
	 * immediately after this method returns
	 */
	protected void customizeProxyFactory(ProxyFactory proxyFactory) {
	}


	/**
	 * 获取能够增强当前bean的所有Advisor bean(通知)
	 *
	 * 题外：只要当前bean对象中的一个方法被某个Advisor中的切入点所匹配，这个bean对象就需要被代理，这个Advisor也就作为代理中的拦截器。
	 * 那肯定有疑惑，为什么一个方法被匹配了，就要对整个bean对象进行代理，其余的方法可能是不需要增强的？因为代理粒度是对象级别的，所以一个方法匹配了就对整个对象进行代理。
	 * 后续在代理对象内部再判断，当前方法是不是要被拦截的！
	 *
	 * 题外：扩展advisor的逻辑在里面：会添加一个DefaultPointcutAdvisor Advisor，里面包含了ExposeInvocationInterceptor advice；
	 *
	 * 题外：sortAdvisor()的逻辑在里面
	 *
	 * 题外：⚠️@Transaction的解析逻辑在里面
	 *
	 * Return whether the given bean is to be proxied, what additional
	 * advices (e.g. AOP Alliance interceptors) and advisors to apply.
	 * @param beanClass the class of the bean to advise
	 * @param beanName the name of the bean
	 * @param customTargetSource the TargetSource returned by the
	 * {@link #getCustomTargetSource} method: may be ignored.
	 * Will be {@code null} if no custom target source is in use.
	 * @return an array of additional interceptors for the particular bean;
	 * or an empty array if no additional interceptors but just the common ones;
	 * or {@code null} if no proxy at all, not even with the common interceptors.
	 * See constants DO_NOT_PROXY and PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS.
	 * @throws BeansException in case of errors
	 * @see #DO_NOT_PROXY
	 * @see #PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS
	 */
	@Nullable
	protected abstract Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName,
			@Nullable TargetSource customTargetSource) throws BeansException;

}
