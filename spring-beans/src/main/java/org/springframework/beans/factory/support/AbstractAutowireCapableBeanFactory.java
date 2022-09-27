/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.beans.factory.support;

import org.apache.commons.logging.Log;
import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.*;
import org.springframework.core.*;
import org.springframework.lang.Nullable;
import org.springframework.util.*;
import org.springframework.util.ReflectionUtils.MethodCallback;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Abstract bean factory superclass that implements default bean creation,
 * with the full capabilities specified by the {@link RootBeanDefinition} class.
 * Implements the {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory}
 * interface in addition to AbstractBeanFactory's {@link #createBean} method.
 *
 * <p>Provides bean creation (with constructor resolution), property population,
 * wiring (including autowiring), and initialization. Handles runtime bean
 * references, resolves managed collections, calls initialization methods, etc.
 * Supports autowiring constructors, properties by name, and properties by type.
 *
 * <p>The main template method to be implemented by subclasses is
 * {@link #resolveDependency(DependencyDescriptor, String, Set, TypeConverter)},
 * used for autowiring by type. In case of a factory which is capable of searching
 * its bean definitions, matching beans will typically be implemented through such
 * a search. For other factory styles, simplified matching algorithms can be implemented.
 *
 * <p>Note that this class does <i>not</i> assume or implement bean definition
 * registry capabilities. See {@link DefaultListableBeanFactory} for an implementation
 * of the {@link org.springframework.beans.factory.ListableBeanFactory} and
 * {@link BeanDefinitionRegistry} interfaces, which represent the API and SPI
 * view of such a factory, respectively.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @author Costin Leau
 * @author Chris Beams
 * @author Sam Brannen
 * @author Phillip Webb
 * @since 13.02.2004
 * @see RootBeanDefinition
 * @see DefaultListableBeanFactory
 * @see BeanDefinitionRegistry
 */
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
		implements AutowireCapableBeanFactory {

	/**
	 * bean的生成策略，默认是cglib
	 *
	 * Strategy for creating bean instances.
	 */
	private InstantiationStrategy instantiationStrategy = new CglibSubclassingInstantiationStrategy();

	/**
	 * 解析策略的方法参数
	 * Resolver strategy for method parameter names. */
	@Nullable
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();


	/**
	 * 尝试解析循环引用
	 *
	 * Whether to automatically try to resolve circular references between beans. */
	// 是否允许循环依赖
	private boolean allowCircularReferences = true;

	/**
	 * 在循环引用的情况下，是否需要注入一个原始的bean实例
	 *
	 * Whether to resort to injecting a raw bean instance in case of circular reference,
	 * even if the injected bean eventually got wrapped.
	 *
	 * 在循环引用的情况下，是否求助于注入原始bean实例，即使注入的bean最终被包裹也是如此。
	 */
	private boolean allowRawInjectionDespiteWrapping = false;

	/**
	 * Dependency types to ignore on dependency check and autowire, as Set of
	 * Class objects: for example, String. Default is none.
	 */
	private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();

	/**
	 * Dependency interfaces to ignore on dependency check and autowire, as Set of
	 * Class objects. By default, only the BeanFactory interface is ignored.
	 */
	private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();

	/**
	 * The name of the currently created bean, for implicit dependency registration
	 * on getBean etc invocations triggered from a user-specified Supplier callback.
	 *
	 * 当前创建的bean的名称，用于在用户指定的Supplier回调中触发的getBean等调用上的隐式依赖注册。
	 */
	private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal<>("Currently created bean");

	/** Cache of unfinished FactoryBean instances: FactoryBean name to BeanWrapper. —— 未完成的 FactoryBean 实例的缓存：FactoryBean 名称到 BeanWrapper。 */
	private final ConcurrentMap<String, BeanWrapper> factoryBeanInstanceCache = new ConcurrentHashMap<>();

	/** Cache of candidate factory methods per factory class. */
	private final ConcurrentMap<Class<?>, Method[]> factoryMethodCandidateCache = new ConcurrentHashMap<>();

	/** Cache of filtered PropertyDescriptors: bean Class to PropertyDescriptor array. */
	private final ConcurrentMap<Class<?>, PropertyDescriptor[]> filteredPropertyDescriptorsCache =
			new ConcurrentHashMap<>();


	/**
	 * Create a new AbstractAutowireCapableBeanFactory.
	 */
	public AbstractAutowireCapableBeanFactory() {
		super();
		// 忽略要依赖的接口
		ignoreDependencyInterface(BeanNameAware.class);
		ignoreDependencyInterface(BeanFactoryAware.class);
		ignoreDependencyInterface(BeanClassLoaderAware.class);
	}

	/**
	 * Create a new AbstractAutowireCapableBeanFactory with the given parent.
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 */
	public AbstractAutowireCapableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this();
		setParentBeanFactory(parentBeanFactory);
	}


	/**
	 * Set the instantiation strategy to use for creating bean instances.
	 * Default is CglibSubclassingInstantiationStrategy.
	 * @see CglibSubclassingInstantiationStrategy
	 */
	public void setInstantiationStrategy(InstantiationStrategy instantiationStrategy) {
		this.instantiationStrategy = instantiationStrategy;
	}

	/**
	 * Return the instantiation strategy to use for creating bean instances.
	 */
	protected InstantiationStrategy getInstantiationStrategy() {
		return this.instantiationStrategy;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed (e.g. for constructor names).
	 * <p>Default is a {@link DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(@Nullable ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * Return the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed.
	 *
	 * 如果需要，返回 ParameterNameDiscoverer 以用于解析方法参数名称。
	 */
	@Nullable
	protected ParameterNameDiscoverer getParameterNameDiscoverer() {
		return this.parameterNameDiscoverer;
	}

	/**
	 * Set whether to allow circular references between beans - and automatically
	 * try to resolve them.
	 * <p>Note that circular reference resolution means that one of the involved beans
	 * will receive a reference to another bean that is not fully initialized yet.
	 * This can lead to subtle and not-so-subtle side effects on initialization;
	 * it does work fine for many scenarios, though.
	 * <p>Default is "true". Turn this off to throw an exception when encountering
	 * a circular reference, disallowing them completely.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans. Refactor your application logic to have the two beans
	 * involved delegate to a third bean that encapsulates their common logic.
	 */
	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.allowCircularReferences = allowCircularReferences;
	}

	/**
	 * Set whether to allow the raw injection of a bean instance into some other
	 * bean's property, despite the injected bean eventually getting wrapped
	 * (for example, through AOP auto-proxying).
	 * <p>This will only be used as a last resort in case of a circular reference
	 * that cannot be resolved otherwise: essentially, preferring a raw instance
	 * getting injected over a failure of the entire bean wiring process.
	 * <p>Default is "false", as of Spring 2.0. Turn this on to allow for non-wrapped
	 * raw beans injected into some of your references, which was Spring 1.2's
	 * (arguably unclean) default behavior.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans, in particular with auto-proxying involved.
	 * @see #setAllowCircularReferences
	 */
	public void setAllowRawInjectionDespiteWrapping(boolean allowRawInjectionDespiteWrapping) {
		this.allowRawInjectionDespiteWrapping = allowRawInjectionDespiteWrapping;
	}

	/**
	 * Ignore the given dependency type for autowiring:
	 * for example, String. Default is none.
	 */
	public void ignoreDependencyType(Class<?> type) {
		this.ignoredDependencyTypes.add(type);
	}

	/**
	 * Ignore the given dependency interface for autowiring.
	 * <p>This will typically be used by application contexts to register
	 * dependencies that are resolved in other ways, like BeanFactory through
	 * BeanFactoryAware or ApplicationContext through ApplicationContextAware.
	 * <p>By default, only the BeanFactoryAware interface is ignored.
	 * For further types to ignore, invoke this method for each type.
	 * @see org.springframework.beans.factory.BeanFactoryAware
	 * @see org.springframework.context.ApplicationContextAware
	 */
	public void ignoreDependencyInterface(Class<?> ifc) {
		this.ignoredDependencyInterfaces.add(ifc);
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		super.copyConfigurationFrom(otherFactory);
		if (otherFactory instanceof AbstractAutowireCapableBeanFactory) {
			AbstractAutowireCapableBeanFactory otherAutowireFactory =
					(AbstractAutowireCapableBeanFactory) otherFactory;
			this.instantiationStrategy = otherAutowireFactory.instantiationStrategy;
			this.allowCircularReferences = otherAutowireFactory.allowCircularReferences;
			this.ignoredDependencyTypes.addAll(otherAutowireFactory.ignoredDependencyTypes);
			this.ignoredDependencyInterfaces.addAll(otherAutowireFactory.ignoredDependencyInterfaces);
		}
	}


	//-------------------------------------------------------------------------
	// Typical methods for creating and populating external bean instances
	//-------------------------------------------------------------------------

	@Override
	@SuppressWarnings("unchecked")
	public <T> T createBean(Class<T> beanClass) throws BeansException {
		// Use prototype bean definition, to avoid registering bean as dependent bean.
		// 上面的翻译：使用原型 bean 定义，避免将 bean 注册为依赖 bean。

		// 封装RootBeanDefinition
		RootBeanDefinition bd = new RootBeanDefinition(beanClass);
		// 设置bean的作用域
		bd.setScope(SCOPE_PROTOTYPE/* prototype */);
		// 是否允许被缓存
		bd.allowCaching = ClassUtils.isCacheSafe(beanClass, getBeanClassLoader());
		return (T) createBean(beanClass.getName(), bd, null);
	}

	@Override
	public void autowireBean(Object existingBean) {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		// 上面的翻译：使用非单例 bean 定义，避免将 bean 注册为依赖 bean。

		// 使用非单例的beanDefinition，防止注册bean为bean的依赖
		RootBeanDefinition bd = new RootBeanDefinition(ClassUtils.getUserClass(existingBean));
		// 设置作用域
		bd.setScope(SCOPE_PROTOTYPE/* prototype */);
		// 是否允许被缓存
		bd.allowCaching = ClassUtils.isCacheSafe(bd.getBeanClass(), getBeanClassLoader());
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		// 初始化beanWrapper
		initBeanWrapper(bw);
		// 给bean的属性赋值
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public Object configureBean(Object existingBean, String beanName) throws BeansException {
		// 如果已经创建了bean,那么bean的定义要清除
		markBeanAsCreated(beanName);
		// 重新设置bean的定义
		BeanDefinition mbd = getMergedBeanDefinition(beanName);
		RootBeanDefinition bd = null;
		if (mbd instanceof RootBeanDefinition) {
			RootBeanDefinition rbd = (RootBeanDefinition) mbd;
			bd = (rbd.isPrototype() ? rbd : rbd.cloneBeanDefinition());
		}
		if (bd == null) {
			bd = new RootBeanDefinition(mbd);
		}
		if (!bd.isPrototype()) {
			bd.setScope(SCOPE_PROTOTYPE);
			bd.allowCaching = ClassUtils.isCacheSafe(ClassUtils.getUserClass(existingBean), getBeanClassLoader());
		}
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		// 初始化BeanWrapper
		initBeanWrapper(bw);
		// 给bean的属性赋值
		populateBean(beanName, bd, bw);
		// 调用init方法，完成初始化
		return initializeBean(beanName, existingBean, bd);
	}


	//-------------------------------------------------------------------------
	// Specialized methods for fine-grained control over the bean lifecycle
	//-------------------------------------------------------------------------

	@Override
	public Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		return createBean(beanClass.getName(), bd, null);
	}

	@Override
	public Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		// 如果是构造器注入
		if (bd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR) {
			return autowireConstructor(beanClass.getName(), bd, null, null).getWrappedInstance();
		}
		else {
			Object bean;
			if (System.getSecurityManager() != null) {
				bean = AccessController.doPrivileged(
						(PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(bd, null, this),
						getAccessControlContext());
			}
			else {
				// 生成bean
				bean = getInstantiationStrategy().instantiate(bd, null, this);
			}
			// 给bean的属性赋值
			populateBean(beanClass.getName(), bd, new BeanWrapperImpl(bean));
			return bean;
		}
	}

	@Override
	public void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)
			throws BeansException {
		// 如果注入类型是构造器注入，直接抛出异常
		if (autowireMode == AUTOWIRE_CONSTRUCTOR) {
			throw new IllegalArgumentException("AUTOWIRE_CONSTRUCTOR not supported for existing bean instance");
		}
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd =
				new RootBeanDefinition(ClassUtils.getUserClass(existingBean), autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition bd = getMergedBeanDefinition(beanName);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		applyPropertyValues(beanName, bd, bw, bd.getPropertyValues());
	}

	@Override
	public Object initializeBean(Object existingBean, String beanName) {
		return initializeBean(beanName, existingBean, null);
	}

	@Override
	public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
			throws BeansException {

		// 初始化返回结果为existingBean
		Object result = existingBean;
		// 调用所有的BeanPostProcessors，对目前的Bean进行处理！
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			/**
			 * 1、ApplicationContextAwareProcessor：对一堆Aware接口的处理！
			 *
			 * 2、CommonAnnotationBeanPostProcessor extends InitDestroyAnnotationBeanPostProcessor
			 *
			 * InitDestroyAnnotationBeanPostProcessor当中实现了postProcessBeforeInitialization()：对@PostConstruct标注的方法进行了调用
			 *
			 * 题外：CommonAnnotationBeanPostProcessor的构造方法中设置了优先级，为"最低优先级-3"
			 *
			 * 3、PostProcessorRegistrationDelegate$BeanPostProcessorChecker：仅完成日志的记录
			 *
			 */
			// ⚠️postProcessBeforeInitialization()：在调用bean初始化方法之前调用(如初始化Bean的afterPropertiesSet或自定义的init方法)
			// 将此BeanPostProcessor应用到给定的新Bean实例。Bean已经填充了属性，返回的Bean实例可能是原始Bean的包装器
			// 默认实现按原样返回给定的Bean

			//
			Object current = processor.postProcessBeforeInitialization(result, beanName);
			if (current == null) {
				// 如果current为null，直接返回result，中断后续的BeanPostProcessor处理
				return result;
			}
			// 让result引用BeanPostProcessor的返回结果，使其经过所有BeanPostProcessor对象的后置处理的层层包装
			result = current;
		}
		return result;
	}

	@Override
	public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			/**
			 * 1、AOP
			 * 注解方式：AnnotationAwareAspectJAutoProxyCreator
			 * xml方式：AspectJAwareAdvisorAutoProxyCreator
			 *
			 * 题外：AnnotationAwareAspectJAutoProxyCreator继承AspectJAwareAdvisorAutoProxyCreator；
			 * AspectJAwareAdvisorAutoProxyCreator间接实现AbstractAutoProxyCreator，是AbstractAutoProxyCreator实现了postProcessAfterInitialization()，
			 * 所以走的是AbstractAutoProxyCreator#postProcessAfterInitialization
			 */
			Object current = processor.postProcessAfterInitialization(result, beanName);
			if (current == null) {
				/**
				 * 题外：如果想中断后续的BeanPostProcessor处理，就可以返回null；如果想继续让后续的BeanPostProcessor处理，可以返回一个对象，例如可以直接返回result。
				 * >>> 一般processor对不感兴趣的bean会回调直接返回result，使其能继续回调后续的BeanPostProcessor处理;
				 * >>> 但是有些processor会返回null，来中断后续的BeanPostProcessor处理。
				 */
				// 如果current为null，直接返回result，中断其后续的BeanPostProcessor处理
				return result;
			}
			// 让result引用processor的返回结果，使其经过所有BeanPostProcess对象的后置处理的层层包装
			result = current;
		}
		// 返回经过所有BeanPostProcess对象的后置处理，层层包装后的result
		return result;
	}

	@Override
	public void destroyBean(Object existingBean) {
		new DisposableBeanAdapter(existingBean, getBeanPostProcessors(), getAccessControlContext()).destroy();
	}


	//-------------------------------------------------------------------------
	// Delegate methods for resolving injection points
	//-------------------------------------------------------------------------

	@Override
	public Object resolveBeanByName(String name, DependencyDescriptor descriptor) {
		InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
		try {
			return getBean(name, descriptor.getDependencyType());
		}
		finally {
			ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
		}
	}

	@Override
	@Nullable
	public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName) throws BeansException {
		return resolveDependency(descriptor, requestingBeanName, null, null);
	}


	//---------------------------------------------------------------------
	// Implementation of relevant AbstractBeanFactory template methods
	//---------------------------------------------------------------------

	/**
	 * Central method of this class: creates a bean instance,
	 * populates the bean instance, applies post-processors, etc.
	 * @see #doCreateBean
	 */
	@Override
	protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		if (logger.isTraceEnabled()) {
			logger.trace("Creating instance of bean '" + beanName + "'");
		}
		RootBeanDefinition mbdToUse = mbd;

		/* 1、获取bean的Class对象 */

		// Make sure bean class is actually resolved at this point, and
		// clone the bean definition in case of a dynamically resolved Class
		// which cannot be stored in the shared merged bean definition. - 确保此时确实解析了bean类，并在无法动态存储的Class不能存储在共享合并bean定义中的情况下克隆bean定义。

		/**
		 * 做各种各样的属性值赋值的：resolveBeanClass()：将bean类名解析为Class引用（如果需要）
		 * 锁定class，根据设置的class属性或者根据className来解析class
		 * 最终得到的是bean的Class对象，因为要进行反射创建对象，就需要Class对象！有了Class就可以进行反射创建对象！
		 */
		// 获取class对象
		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
		// 进行条件筛选，重新赋值RootBeanDefinition，并设置BeanClass属性
		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
			/**
			 * bean的Class对象不为空 && bd的beanClass属性不是Class对象 && bd的beanClass属性不为空，才能进来，
			 * 然后重新设置bd的beanClass属性为Class对象，因为最初bd的beanClass属性可能是一个类的全限定类名的字符串，
			 * 但是最终需要的是Class对象，才能进行反射创建对象，而不是一个字符串，如果是字符串，最终还是要进行转换，所以设置对应的Class对象
			 */
			// 重新创建一个RootBeanDefinition对象
			mbdToUse = new RootBeanDefinition(mbd);
			// 设置bd的beanClass属性值为解析得到的Class对象
			mbdToUse.setBeanClass(resolvedClass);
		}

		/* 2、有没有方法能够对它进行重新覆盖，method overrides：<lookup-method>和<replace-method>标签 */

		// Prepare method overrides. - 准备方法替代。
		try {
			/**
			 * ⚠️处理<lookup-method>和<replace-method>标签配置，Spring当中，将所有的lookup-method和replace-method统称为method overrides
			 *
			 * 相似处：在AbstractApplicationContext#refresh() ——> obtainFreshBeanFactory() ——> AbstractRefreshableApplicationContext#refreshBeanFactory()
			 * ——> customizeBeanFactory()中有allowBeanDefinitionOverriding属性，是否允许覆盖同名称的不同定义的对象
			 */
			// 验证及准备覆盖的方法(里面只是将overloaded标识位设置为false)，当需要创建的bean对象中包含了lookup-method和replace-method标签的时候，会产生覆盖操作
			mbdToUse.prepareMethodOverrides();
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}

		/*

		3、在bean实例化之前调用InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation()，给BeanPostProcessor一个机会返回当前bean的代理对象

		在创建bean之前，可以利用BeanPostProcessors，返回一个代理实例，去代替我们目标的bean实例对象，后续就不会走创建bean的流程了 —— 提前通过自定义的动态代理方式，创建出来具体的对象

		⚠️注意：这里是返回自己创建的bd，整个bd的实例化和初始化都是自己把控的，跟后面spring把控创建bean的整个过程都没有关系！所以这块跟后面spring处理循环依赖也没有关系！

		*/
		try {
			/**
			 * 在bean初始化之前应用InstantiationAwareBeanPostProcessor后置处理器，如果InstantiationAwareBeanPostProcessor后置处理器返回的bean不为空，
			 * 则会调用所有BeanPostProcessor#after()方法，并返回bean。
			 * (BeanPostProcessor#after()中有对aop的处理)
			 *
			 * 内部InstantiationAwareBeanPostProcessor extends BeanPostProcessor
			 * 		把一个对象的所有依赖关系去掉，可以实现InstantiationAwareBeanPostProcessor，重写postProcessBeforeInstantiation，直接返回一个bean出去，这个对象当中所有的依赖都不会去维护了
			 */
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance. —— 让BeanPostProcessors有机会，返回一个代理，而不是目标bean实例
			// 给BeanPostProcessors一个机会，返回一个代理实例，去代替我们目标的bean实例对象
			// 最根据的目的是为了检查说，在我们正而八经开始实例化之前，它有一个预处理的方式，能让我们返回出来一个具体的代理对象
			/**
			 * ⚠️resolveBeforeInstantiation()更加强调的是用户自定义代理的方式，针对于当前的被代理类需要经过标准的代理流程来创建对象不在这里处理，在后面处理（一个是标准化流程，一个是自定义的，看你怎么选择）；
			 */
			Object bean = resolveBeforeInstantiation/* 在实例化之前解决，应用，实例化前的前置处理器 */(beanName, mbdToUse);
			if (bean != null) {
				return bean;
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
					"BeanPostProcessor before instantiation of bean failed"/* bean实例化之前的BeanPostProcessor调用失败 */, ex);
		}

		/**
		 * doCreateBean()不一定会执行，要是否会执行，取决于，当前执行的逻辑里面，是否包含了"提前创建bean对象的BeanPostProcess"，
		 * 如果包含了就提前创建bean和采用这个bean，不会再调用下面的doCreateBean()继续创建bean；不包含再接着往下走doCreateBean()进行创建bean
		 */

		/* 4、创建bean */
		try {
			// ⚠️创建bean —— 实际创建bean的调用
			Object beanInstance = doCreateBean(beanName, mbdToUse, args);
			if (logger.isTraceEnabled()) {
				logger.trace("Finished creating instance of bean '" + beanName + "'");
			}
			return beanInstance;
		}
		catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
			// A previously detected exception with proper bean creation context already,
			// or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
		}
	}

	/**
	 * Actually create the specified bean. Pre-creation processing has already happened
	 * at this point, e.g. checking {@code postProcessBeforeInstantiation} callbacks.
	 * <p>Differentiates between default bean instantiation, use of a
	 * factory method, and autowiring a constructor.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 * @see #instantiateBean
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 */
	protected Object doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {
		/*

		1、创建对象（实例化）：只是在堆里面开辟了一块空间

		只是简简单的创建出来一个对象，对象里面的属性值还未赋值！

		*/
		// Instantiate the bean.
		// BeanWrapper包裹了真实的实例对象；通过「getWrappedInstance();」可以获取真实的对象
		// 这个BeanWrapper是用来持有创建出来的bean对象的
		BeanWrapper instanceWrapper = null;
		if (mbd.isSingleton()) {
			// 如果是单例对象，从factoryBean实例缓存中，移除当前bd
			// factoryBeanInstanceCache：通过FactoryBean方式创建的bean对象
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
		if (instanceWrapper == null) {
			/**
			 * 题外：如果一个对象需要被动态代理，那么它的普通对象也会被创建，只不过在后续的流程中，我会把这个普通对象来进行一个替换而已
			 */
			// ⚠️1、创建bean实例，是原生bean对象（原生bean的所有属性值都是默认属性值（int的为0，引用对象为null））
			// 根据执行bean使用对应的策略创建新的实例，如，工厂方法，构造函数主动注入、简单初始化
			instanceWrapper = createBeanInstance(beanName, mbd, args);
		}
		/**
		 * ⚠️1.1、获取原生bean对象，不是代理对象，原生bean的所有属性值都是默认属性值（int的为0，引用对象为null）
		 */
		// 从包装类中获取原始bean
		Object bean = instanceWrapper.getWrappedInstance();
		// 获取具体的bean对象的Class
		Class<?> beanType = instanceWrapper.getWrappedClass();
		// 如果不等于NullBean类型，那么修改目标类型
		if (beanType != NullBean.class) {
			// 如果不为空，就设置要处理的目标类型，说我要处理的目标类型是什么类型
			mbd.resolvedTargetType/* 已解决的目标类型 */ = beanType;
		}

		/*

		2、允许BeanPostProcessor去修改"合并的bd信息"，主要是对一些注解进行相关的解析工作，以及把解析到的信息放入bd中（也就是修改了bd），
		方便后面的属性填充和方法调用（例如：对@Autowired的属性填充和调用@PostConstruct标注的初始化方法）

		️例如：里面解析了@PostConstruct和@PreDestroy标注的方法、@Resource标注的字段和方法，@Autowired标注的字段和方法，
		然后将解析好的字段和方法放入到了bd中，也就是修改了bd！

		调用的是：MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition()

		*/

		/*

		问题：在实例化和初始化的中间加这么一步操作的意义是什么？当前的这个bean实例化完成了，为什么还要对bd进行修改？

		答：因为接下来要进行初始化了！初始化分两个步骤：1、属性赋值；2、调用初始化方法。
		在我们定义的类里面，可以用xml配置文件的方式，也可以用注解，甚至两个东西进行混用，来声明我们要注入的属性，以及初始化方法是哪个。
		xml的方式，之前一次性解析过了，如果不是xml的方式，而是用注解的方式来声明要注入的属性值和初始化方法，对应的注解目前是还没有被解析的，
		注解要被识别解析到，要有注解的解析工作，另外"属性的注入工作"和"初始化方法的调用工作"是在实例化完成之后，才能进行的，所以就在实例化完成之后，
		在这里对注解进行解析，得到对应的信息。
		得到的"属性注入"和"初始化方法"等信息，这些信息肯定是要存放在某一个地方，将来才能被使用的，而bd是贯穿整个bean创建始终的，如果不放在bd里面，那就要
		弄其它的工具类什么的进行存储，所以为了方便起见，存储在bd里面最合适，所以修改bd，把解析好的信息存入bd当中！—— 存入bd也就是修改了bd！

		原答：
		因为接下来要进行初始化了！初始化分两个步骤：1、属性赋值；2、调用初始化方法。
		在我们定义的类里面，可以用xml配置文件的方式，也可以用注解，甚至两个东西进行混用，来声明我们要注入的属性，以及初始化方法是哪个。
		我想往里面注入属性的时候，注解要被识别到，这块刚刚好完成了注解的解析工作，后面在完成属性值注入的时候，就能够取到对应的信息，然后就可以往里面完成赋值操作
		如果不是配置文件的话，是注解的，我也要让其识别到，所以这里要放入注解的解析工作
		不修改bd，后面在赋值的时候，我怎么知道你哪些被注解处理了！哪些不是被注解处理了，所以我这要把它给区分开！

		 */

		// Allow post-processors to modify the merged bean definition. —— 允许后处理器修改合并好的bean定义信息。
		/**
		 * ⚠️bd的配置信息在前面冰冻起来了，为什么后面还要修改？
		 *
		 * 答：前面的冰冻是为了在实例化之前不做任何的修改工作，这里的修改是在实例化之后，对象已经创建完成了，
		 * 所以可以修改，我可以往里面再加一些其它的描述信息，来方便后续的一些结果调用！
		 */
		// 允许BeanPostProcessor去修改合并的bd
		synchronized (mbd.postProcessingLock) {
			// 判断当前bd是否被处理过（修改过），没处理过就可以进去
			if (!mbd.postProcessed) {
				try {
					/**
					 * 1、CommonAnnotationBeanPostProcessor
					 * @PostConstruct、@PreDestroy标注的方法、@Resource标注的字段和方法、@WebServiceRef、@EJB
					 *
					 * 2、AutowiredAnnotationBeanPostProcessor
					 * @Autowired、@Value、@Inject标注的字段和方法，等其它的一些注解
					 *
					 * 上面这些解析工作都在这里面！然后将解析好的字段和方法信息放入到了bd中，也就是修改了bd！
					 */
					// 执行MergedBeanDefinitionPostProcessor接口类型的bean
					// 题外：MergedBeanDefinitionPostProcessor：修改"合并bd信息"的后置处理器
					applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Post-processing of merged bean definition failed", ex);
				}
				// 表示bd已经被处理过了（修改过）
				mbd.postProcessed = true;
			}
		}

		/*

		3、把现在处于非完整状态的对象，放入三级缓存。提前暴露对象，解决循环依赖问题。最主要的目的是为了解决，我们在AOP过程中产生的循环依赖问题。

		题外：是将"ObjectFactory的lambda表达式"存入三级缓存中，包含原生对象。

		*/

		/**
		 * 题外：this.allowCircularReferences被设置的地方在：
		 * obtainFreshBeanFactory() ——> refreshBeanFactory() ——> customizeBeanFactory(beanFactory)中
		 */

		// Eagerly cache singletons to be able to resolve circular references
		// even when triggered by lifecycle interfaces like BeanFactoryAware.
		// 上面的翻译：即使被BeanFactoryAware等生命周期接口触发，也急切地缓存单例以解析循环引用。

		// 判断当前bean是否需要放入三级缓存，进行提前暴露
		// 只有当【单例 & 允许循环依赖 & 当前bean正在创建中】这个三个条件全部满足，才需要放入三级缓存，进行提前暴露
		boolean earlySingletonExposure/* 早期单例暴露 */ = (mbd.isSingleton() /*  */
				&& this.allowCircularReferences/* 是否允许循环依赖，默认为true */
				&& isSingletonCurrentlyInCreation(beanName)/* 当前bean是否正在被创建过程中 */);
		if (earlySingletonExposure) {
			if (logger.isTraceEnabled()) {
				logger.trace("Eagerly caching bean '" + beanName +
						"' to allow for resolving potential circular references");
			}
			// 为避免后期循环依赖，可以在bean初始化完成前，将创建实例的ObjectFactory加入工厂
			addSingletonFactory(beanName,
					/**
					 * 不管当前对象是否需要被创建具体的代理对象，都会往三级缓存里面放入一个lambda表达式的匿名内部类，
					 * 方便后续在获取具体对象的时候进行调用，能够创建一个代理对象。需要的时候就进行调用，不需要就不进行调用。
					 */

					// 用于创建代理对象的lambda形式的匿名对象，会放入三级缓存中
					() -> getEarlyBeanReference/* 获取早期的bean引用 */(beanName, mbd, bean /* 原生bean对象 */ ));

			/**
			 * 自己写的代码：
			 * 测试：只添加到二级缓存，用二级缓存解决循环依赖！
			 */
			 //addSingletonFactoryTest(beanName,bean);
		}

		// 得到bean原生对象，不是代理对象
		// Initialize the bean instance. —— 初始化bean实例
		// 初始化bean实例
		Object exposedObject = bean;
		try {

			/* 4、填充属性（属性的基本填充工作） */

			/**
			 * ⚠️2、填充属性的方法，意即：完成自动装配的。🎈循坏引用的内部逻辑是在这里面解决的！
			 * 主要借助了两个后置处理器来填充属性的
			 * 		CommonAnnotationBeanPostProcessor处理@Resource、@PostConstruct的实现
			 * 		AutowiredAnnotationBeanPostProcessor处理@Autowired的
			 */
			// 对bean的属性进行填充，将各个属性值注入。其中，可能存在依赖于其他bean的属性，则会递归初始化依赖的bean
			populateBean(beanName, mbd, instanceWrapper);

			// ⚠️⚠️⚠️⚠️⚠️⚠️⚠️里面的某些属性可能没赋值完，所以下面进行初始化操作！

			/* 5、初始化bean */

			/**
			 * ⚠️3、初始化bean(如果没加Aop，则依旧是原生对象)：执行所有的后置处理器
			 *
			 * aop是在这里完成的处理(⚠️️被aop，那将bean原生对象变为代理对象。aop就是通过原生对象变成代理对象的这么一个规则实现的)
			 * 		例如：被aop,那么exposedObject开始是原生对象,执行完initializeBean()就变成了代理对象
			 *
			 */
			// 执行初始化逻辑
			// 题外：当执行完这个方法，得到的相当于是一个完整的对象
			exposedObject/* 暴露对象 */ = initializeBean(beanName, exposedObject, mbd);
		}
		catch (Throwable ex) {
			if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
				throw (BeanCreationException) ex;
			}
			else {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
			}
		}

		/* 6、验证我们的循环依赖的 */

		if (earlySingletonExposure/* 早期单身暴露 */) {
			// 从缓存中获取具体的对象
			Object earlySingletonReference/* 早期单例参考 */ = getSingleton(beanName, false/* false：代表不会去创建早期引用 */);
			// earlySingletonReference只有在检测到有循环依赖的情况下，才会不为空
			if (earlySingletonReference != null) {
				/**
				 * 当前对象还在创建的过程中，还未创建完成是不会放入一级缓存中的，
				 * 另外调用getSingleton(beanName, false)，传入的allowEarlyReference参数是false，代表不会去创建早期引用，也就是
				 * 不会去走三级缓存，调用ObjectFactory#getObject()获取一个对象
				 * 所以只有可能是从二级缓存中获取到对象！
				 *
				 * 而只有在别人调用了getSingleton(beanName, false)，来获取当前对象的引用作为依赖时，
				 * 会走三级缓存，触发了ObjectFactory#getObject()获取当前提前暴露的对象时，才会去把当前beanName相关的对象（可能是原生，可能是代理）
				 * 放入到二级缓存中！
				 *
				 * 所以当earlySingletonReference不为null，一定当前对象在创建的过程中，去创建了依赖对象，而依赖对象，又依赖了当前对象，
				 * 所以去触发了三级缓存，获取到了当前beanName的ObjectFactory，调用了ObjectFactory#getObject()，去获取当前对象的最终对象作为依赖！
				 * 也就是循环依赖！
				 *
				 * ⚠️简单来说，earlySingletonReference不为null，就是触发了当前beanName的三级缓存中的ObjectFactory#getObject()，确定了最终暴露的对象
				 * 也是当前beanName、bd的最终对象！—— 当前beanName对应的ObjectFactory#getObject()已经被执行了，earlySingletonReference是最终对象
				 *
				 * bean始终指向原生对象，
				 * exposedObject最初也是指向原生对象，但是经过initializeBean()初始化过后，有可能会被Aop增强，变为代理对象
				 *
				 * 所以这里判断，"initializeBean()初始化完成后的对象"是否和"原始实例化的对象"相同
				 *
				 * ⚠️如果不相同，证明exposedObject在initializeBean()中，已经被增强，变为代理对象了！
				 *
				 * 如果相同，那么证明exposedObject在initializeBean()中，未被增强！有两种情况是未被增强：
				 * （1）不需要被增强；
				 * 题外：循环依赖的情况下，不需要被增强，三级缓存中当前beanName对应的ObjectFactory#getObject()，也会被执行，只不过返回的是原生对象，
				 * 放入二级缓存中，所以earlySingletonReference不为null
				 * ⚠️（2）已经被增强过了！—— 也就是已经执行三级缓存中当前beanName对应的ObjectFactory#getObject()中进行增强了，变为代理对象了！所以在initializeBean()中，不会进行二次增强。
				 * 由于这种情况，earlySingletonReference有可能是增强的代理对象，所以当earlySingletonReference!=null：
				 * >>>（a）但是exposedObject=bean时，就把exposedObject=earlySingletonReference，毕竟earlySingletonReference是最终的对象！
				 * >>>（b）如果exposedObject!=bean，证明，exposedObject是在initializeBean()中进行增强的，exposedObject作为最终的对象！
				 *
				 * 题外：要么是在ObjectFactory#getObject()中被增强；要么是在initializeBean()中被增强。在ObjectFactory#getObject()中被增强，那么initializeBean()中就不会重复增强了！
				 */
				// 如果exposedObject没有在初始化方法中被改变，也就是没有被增强
				if (exposedObject == bean) {
					/**
					 * 当前beanName对应的ObjectFactory#getObject()已经被执行了，earlySingletonReference是最终对象
					 * 无论当前bean是否要被增强，都会在ObjectFactory#getObject()中被增强，
					 * (1)在ObjectFactory#getObject()中，不被增强（也就是不需要被增强），earlySingletonReference就是原生对象，
					 * >>> exposedObject也等于bean
					 * (2)在ObjectFactory#getObject()中，被增强，earlySingletonReference就是代理对象，
					 * >>> 而当earlySingletonReference已经在ObjectFactory#getObject()中被增强为代理对象时，在initializeBean()中，是不会再次增强的！所以exposedObject=bean
					 * >>> 为了当前earlySingletonReference有可能是代理对象，所以将 exposedObject = earlySingletonReference
					 * >>> >>>（即使earlySingletonReference不是代理对象，再次指向一次也无关紧要！）
					 * >>> >>>（⚠️无论如何只要earlySingletonReference！=null，且exposedObject == bean，那么earlySingletonReference就是作为最终的对象）
					 */
					// ⚠️⚠️⚠️️无论如何只要earlySingletonReference！=null，且exposedObject == bean，那么earlySingletonReference就是作为最终的对象
					exposedObject = earlySingletonReference;
				}
				else if (!this.allowRawInjectionDespiteWrapping/* 尽管包装,允许原始注入，默认false */ && hasDependentBean(beanName)/* 有依赖的bean */) {
					// 获取我们依赖的对象名称，添加我们对应的依赖关系，是依赖的处理
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans/* 实际的依赖Bean */ = new LinkedHashSet<>(dependentBeans.length);
					for (String dependentBean : dependentBeans) {
						/**
						 * 			this.singletonObjects.remove(beanName);
						 * 			this.singletonFactories.remove(beanName);
						 * 			this.earlySingletonObjects.remove(beanName);
						 * 			this.registeredSingletons.remove(beanName);
						 */
						// alreadyCreated当中已经包含了，会返回false，if条件成立
						// 返回false说明依赖还没实例化好
						if (!removeSingletonIfCreatedForTypeCheckOnly/* 如果仅为类型检查创建，则删除单例 */(dependentBean)) {
							// actualDependentBeans.add()：说明当前的依赖还没有实例化完成
							actualDependentBeans.add(dependentBean);
						}
					}
					// 因为bean创建后所依赖的bean一定是已经创建的
					// actualDependentBeans不为空，则表示当前bean创建其依赖的bean却没有全部创建完，也就是说存在循环依赖
					if (!actualDependentBeans/* 实际的依赖Bean */.isEmpty()) {
						/**
						 * 名称为"beanName"的Bean已作为循环引用的一部分被注入到其原始版本中的其他bean[StringUtils.collectionToCommaDelimitedString(actualDependentBeans)]中，
						 * 但最终已被包装。这意味着所说的其他bean不使用bean的最终版本。
						 * 这通常是过度渴望类型匹配的结果 - 例如，考虑使用'getBeanNamesForType'并关闭'allowEagerInit(允许急切初始化)'标志。
						 */
						throw new BeanCurrentlyInCreationException(beanName,
								"Bean with name '" + beanName + "' has been injected into other beans [" +
								StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
								"] in its raw version as part of a circular reference, but has eventually been " +
								"wrapped. This means that said other beans do not use the final version of the " +
								"bean. This is often the result of over-eager type matching - consider using " +
								"'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
					}
				}
			}
		}

		/*

		7、为当前bean销毁做准备：注册销毁时的DisposableBean（对象销毁时需要调用的接口）

		注册bean对象的【一次性Bean适配器】到工厂的一次性Bean列表中，方便后续在容器销毁的时候(ac.close())进行调用，去销毁当前bean，执行当前bean生命周期中的销毁流程

		题外：手动关系容器时：为了给我们的销毁来进行相关的使用的，如果需要销毁我就用，不需要销毁就不用

		*/

		// Register bean as disposable. —— 将bean注册为一次性的。
		try {
			// 注册bean对象，方便后续在容器销毁的时候销毁对象（用一次之后，就把它销毁，为了后续在进行对象销毁的时候进行调用的）
			registerDisposableBeanIfNecessary/* 如有必要，注册一次性(销毁的)Bean */(beanName, bean, mbd);
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
		}

		return exposedObject;
	}

	@Override
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		// 确定给bean定义的目标类型
		Class<?> targetType = determineTargetType(beanName, mbd, typesToMatch);
		// Apply SmartInstantiationAwareBeanPostProcessors to predict the
		// eventual type after a before-instantiation shortcut.
		if (targetType != null && !mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			boolean matchingOnlyFactoryBean = typesToMatch.length == 1 && typesToMatch[0] == FactoryBean.class;
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					Class<?> predicted = ibp.predictBeanType(targetType, beanName);
					if (predicted != null &&
							(!matchingOnlyFactoryBean || FactoryBean.class.isAssignableFrom(predicted))) {
						return predicted;
					}
				}
			}
		}
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 */
	@Nullable
	protected Class<?> determineTargetType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = mbd.getTargetType();
		if (targetType == null) {
			targetType = (mbd.getFactoryMethodName() != null ?
					getTypeForFactoryMethod(beanName, mbd, typesToMatch) :
					resolveBeanClass(mbd, beanName, typesToMatch));
			if (ObjectUtils.isEmpty(typesToMatch) || getTempClassLoader() == null) {
				mbd.resolvedTargetType = targetType;
			}
		}
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition which is based on
	 * a factory method. Only called if there is no singleton instance registered
	 * for the target bean already.
	 * <p>This implementation determines the type matching {@link #createBean}'s
	 * different creation strategies. As far as possible, we'll perform static
	 * type checking to avoid creation of the target bean.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 * @see #createBean
	 */
	@Nullable
	protected Class<?> getTypeForFactoryMethod(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		ResolvableType cachedReturnType = mbd.factoryMethodReturnType;
		if (cachedReturnType != null) {
			return cachedReturnType.resolve();
		}

		Class<?> commonType = null;
		Method uniqueCandidate = mbd.factoryMethodToIntrospect;

		if (uniqueCandidate == null) {
			Class<?> factoryClass;
			boolean isStatic = true;

			String factoryBeanName = mbd.getFactoryBeanName();
			if (factoryBeanName != null) {
				if (factoryBeanName.equals(beanName)) {
					throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
							"factory-bean reference points back to the same bean definition");
				}
				// Check declared factory method return type on factory class.
				factoryClass = getType(factoryBeanName);
				isStatic = false;
			}
			else {
				// Check declared factory method return type on bean class.
				factoryClass = resolveBeanClass(mbd, beanName, typesToMatch);
			}

			if (factoryClass == null) {
				return null;
			}
			factoryClass = ClassUtils.getUserClass(factoryClass);

			// If all factory methods have the same return type, return that type.
			// Can't clearly figure out exact method due to type converting / autowiring!
			int minNrOfArgs =
					(mbd.hasConstructorArgumentValues() ? mbd.getConstructorArgumentValues().getArgumentCount() : 0);
			Method[] candidates = this.factoryMethodCandidateCache.computeIfAbsent(factoryClass,
					clazz -> ReflectionUtils.getUniqueDeclaredMethods(clazz, ReflectionUtils.USER_DECLARED_METHODS));

			for (Method candidate : candidates) {
				if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate) &&
						candidate.getParameterCount() >= minNrOfArgs) {
					// Declared type variables to inspect?
					if (candidate.getTypeParameters().length > 0) {
						try {
							// Fully resolve parameter names and argument values.
							Class<?>[] paramTypes = candidate.getParameterTypes();
							String[] paramNames = null;
							ParameterNameDiscoverer pnd = getParameterNameDiscoverer();
							if (pnd != null) {
								paramNames = pnd.getParameterNames(candidate);
							}
							ConstructorArgumentValues cav = mbd.getConstructorArgumentValues();
							Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
							Object[] args = new Object[paramTypes.length];
							for (int i = 0; i < args.length; i++) {
								ConstructorArgumentValues.ValueHolder valueHolder = cav.getArgumentValue(
										i, paramTypes[i], (paramNames != null ? paramNames[i] : null), usedValueHolders);
								if (valueHolder == null) {
									valueHolder = cav.getGenericArgumentValue(null, null, usedValueHolders);
								}
								if (valueHolder != null) {
									args[i] = valueHolder.getValue();
									usedValueHolders.add(valueHolder);
								}
							}
							Class<?> returnType = AutowireUtils.resolveReturnTypeForFactoryMethod(
									candidate, args, getBeanClassLoader());
							uniqueCandidate = (commonType == null && returnType == candidate.getReturnType() ?
									candidate : null);
							commonType = ClassUtils.determineCommonAncestor(returnType, commonType);
							if (commonType == null) {
								// Ambiguous return types found: return null to indicate "not determinable".
								return null;
							}
						}
						catch (Throwable ex) {
							if (logger.isDebugEnabled()) {
								logger.debug("Failed to resolve generic return type for factory method: " + ex);
							}
						}
					}
					else {
						uniqueCandidate = (commonType == null ? candidate : null);
						commonType = ClassUtils.determineCommonAncestor(candidate.getReturnType(), commonType);
						if (commonType == null) {
							// Ambiguous return types found: return null to indicate "not determinable".
							return null;
						}
					}
				}
			}

			mbd.factoryMethodToIntrospect = uniqueCandidate;
			if (commonType == null) {
				return null;
			}
		}

		// Common return type found: all factory methods return same type. For a non-parameterized
		// unique candidate, cache the full type declaration context of the target factory method.
		cachedReturnType = (uniqueCandidate != null ?
				ResolvableType.forMethodReturnType(uniqueCandidate) : ResolvableType.forClass(commonType));
		mbd.factoryMethodReturnType = cachedReturnType;
		return cachedReturnType.resolve();
	}

	/**
	 * This implementation attempts to query the FactoryBean's generic parameter metadata
	 * if present to determine the object type. If not present, i.e. the FactoryBean is
	 * declared as a raw type, checks the FactoryBean's {@code getObjectType} method
	 * on a plain instance of the FactoryBean, without bean properties applied yet.
	 * If this doesn't return a type yet, and {@code allowInit} is {@code true} a
	 * full creation of the FactoryBean is used as fallback (through delegation to the
	 * superclass's implementation).
	 * <p>The shortcut check for a FactoryBean is only applied in case of a singleton
	 * FactoryBean. If the FactoryBean instance itself is not kept as singleton,
	 * it will be fully created to check the type of its exposed object.
	 */
	@Override
	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		// Check if the bean definition itself has defined the type with an attribute
		ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
		if (result != ResolvableType.NONE) {
			return result;
		}

		ResolvableType beanType =
				(mbd.hasBeanClass() ? ResolvableType.forClass(mbd.getBeanClass()) : ResolvableType.NONE);

		// For instance supplied beans try the target type and bean class
		if (mbd.getInstanceSupplier() != null) {
			result = getFactoryBeanGeneric(mbd.targetType);
			if (result.resolve() != null) {
				return result;
			}
			result = getFactoryBeanGeneric(beanType);
			if (result.resolve() != null) {
				return result;
			}
		}

		// Consider factory methods
		String factoryBeanName = mbd.getFactoryBeanName();
		String factoryMethodName = mbd.getFactoryMethodName();

		// Scan the factory bean methods
		if (factoryBeanName != null) {
			if (factoryMethodName != null) {
				// Try to obtain the FactoryBean's object type from its factory method
				// declaration without instantiating the containing bean at all.
				BeanDefinition factoryBeanDefinition = getBeanDefinition(factoryBeanName);
				Class<?> factoryBeanClass;
				if (factoryBeanDefinition instanceof AbstractBeanDefinition &&
						((AbstractBeanDefinition) factoryBeanDefinition).hasBeanClass()) {
					factoryBeanClass = ((AbstractBeanDefinition) factoryBeanDefinition).getBeanClass();
				}
				else {
					RootBeanDefinition fbmbd = getMergedBeanDefinition(factoryBeanName, factoryBeanDefinition);
					factoryBeanClass = determineTargetType(factoryBeanName, fbmbd);
				}
				if (factoryBeanClass != null) {
					result = getTypeForFactoryBeanFromMethod(factoryBeanClass, factoryMethodName);
					if (result.resolve() != null) {
						return result;
					}
				}
			}
			// If not resolvable above and the referenced factory bean doesn't exist yet,
			// exit here - we don't want to force the creation of another bean just to
			// obtain a FactoryBean's object type...
			if (!isBeanEligibleForMetadataCaching(factoryBeanName)) {
				return ResolvableType.NONE;
			}
		}

		// If we're allowed, we can create the factory bean and call getObjectType() early
		if (allowInit) {
			FactoryBean<?> factoryBean = (mbd.isSingleton() ?
					getSingletonFactoryBeanForTypeCheck(beanName, mbd) :
					getNonSingletonFactoryBeanForTypeCheck(beanName, mbd));
			if (factoryBean != null) {
				// Try to obtain the FactoryBean's object type from this early stage of the instance.
				Class<?> type = getTypeForFactoryBean(factoryBean);
				if (type != null) {
					return ResolvableType.forClass(type);
				}
				// No type found for shortcut FactoryBean instance:
				// fall back to full creation of the FactoryBean instance.
				return super.getTypeForFactoryBean(beanName, mbd, true);
			}
		}

		if (factoryBeanName == null && mbd.hasBeanClass() && factoryMethodName != null) {
			// No early bean instantiation possible: determine FactoryBean's type from
			// static factory method signature or from class inheritance hierarchy...
			return getTypeForFactoryBeanFromMethod(mbd.getBeanClass(), factoryMethodName);
		}
		result = getFactoryBeanGeneric(beanType);
		if (result.resolve() != null) {
			return result;
		}
		return ResolvableType.NONE;
	}

	private ResolvableType getFactoryBeanGeneric(@Nullable ResolvableType type) {
		if (type == null) {
			return ResolvableType.NONE;
		}
		return type.as(FactoryBean.class).getGeneric();
	}

	/**
	 * Introspect the factory method signatures on the given bean class,
	 * trying to find a common {@code FactoryBean} object type declared there.
	 * @param beanClass the bean class to find the factory method on
	 * @param factoryMethodName the name of the factory method
	 * @return the common {@code FactoryBean} object type, or {@code null} if none
	 */
	private ResolvableType getTypeForFactoryBeanFromMethod(Class<?> beanClass, String factoryMethodName) {
		// CGLIB subclass methods hide generic parameters; look at the original user class.
		Class<?> factoryBeanClass = ClassUtils.getUserClass(beanClass);
		FactoryBeanMethodTypeFinder finder = new FactoryBeanMethodTypeFinder(factoryMethodName);
		ReflectionUtils.doWithMethods(factoryBeanClass, finder, ReflectionUtils.USER_DECLARED_METHODS);
		return finder.getResult();
	}

	/**
	 * This implementation attempts to query the FactoryBean's generic parameter metadata
	 * if present to determine the object type. If not present, i.e. the FactoryBean is
	 * declared as a raw type, checks the FactoryBean's {@code getObjectType} method
	 * on a plain instance of the FactoryBean, without bean properties applied yet.
	 * If this doesn't return a type yet, a full creation of the FactoryBean is
	 * used as fallback (through delegation to the superclass's implementation).
	 * <p>The shortcut check for a FactoryBean is only applied in case of a singleton
	 * FactoryBean. If the FactoryBean instance itself is not kept as singleton,
	 * it will be fully created to check the type of its exposed object.
	 */
	@Override
	@Deprecated
	@Nullable
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		return getTypeForFactoryBean(beanName, mbd, true).resolve();
	}

	/**
	 * 获取早期bean的引用 —— 也就是获取提前暴露出去的bean对象引用，也称之为提前公开的对象！
	 *
	 * Obtain a reference for early access to the specified bean,
	 * typically for the purpose of resolving a circular reference.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param bean the raw bean instance
	 * @return the object to expose as bean reference
	 */
	protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {

		/**
		 * ⚠️exposedObject要不是原生bean镀锡，要不就是通过ibp.getEarlyBeanReference()生成的代理对象，
		 * 只可能是其中的一个！所以保证了我们的bean对象全局有且仅有一个！
		 * 引用者也只能引用得到一个对象，要不就是原生对象，要不就是代理对象
		 */

		// 默认最终公开(提前暴露的bean对象)的对象是原生bean对象 —— 默认，提前暴露的bean对象是原生bean对象
		Object exposedObject/* 暴露对象 */ = bean;
		// mbd的synthetic属性：设置此bd是否是"synthetic"。一般是指只有AOP相关的pointCut配置或者Advice配置才会将synthetic设置为true。
		// 如果mbd不是synthetic && 此工厂拥有InstantiationAwareBeanPostProcessor实例
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			// 如果这个if没有进来，那么返回的就是普通的对象；
			// 如果这个if进来了，那么返回的将是代理对象！
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor/* 智能实例化感知BeanPostProcessor */) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					// ⚠️解决循环依赖问题，通过此方法提前暴露一个合格的对象，有可能是原生对象，有可能是代理对象
					// 题外：会让exposedObject经过每个SmartInstantiationAwareBeanPostProcessor的包装
					// 题外：AbstractAutoProxyCreator#getEarlyBeanReference()内部会执行this.earlyProxyReferences.put(cacheKey, bean)操作
					exposedObject/* 原生对象/代理对象 */ = ibp.getEarlyBeanReference(exposedObject/* 原生对象 */, beanName);
				}
			}
		}
		// 返回最终经过层次包装后的对象
		return exposedObject;
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * Obtain a "shortcut" singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		synchronized (getSingletonMutex()) {
			BeanWrapper bw = this.factoryBeanInstanceCache.get(beanName);
			if (bw != null) {
				return (FactoryBean<?>) bw.getWrappedInstance();
			}
			Object beanInstance = getSingleton(beanName, false);
			if (beanInstance instanceof FactoryBean) {
				return (FactoryBean<?>) beanInstance;
			}
			if (isSingletonCurrentlyInCreation(beanName) ||
					(mbd.getFactoryBeanName() != null && isSingletonCurrentlyInCreation(mbd.getFactoryBeanName()))) {
				return null;
			}

			Object instance;
			try {
				// Mark this bean as currently in creation, even if just partially.
				beforeSingletonCreation(beanName);
				// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
				instance = resolveBeforeInstantiation(beanName, mbd);
				if (instance == null) {
					bw = createBeanInstance(beanName, mbd, null);
					instance = bw.getWrappedInstance();
				}
			}
			catch (UnsatisfiedDependencyException ex) {
				// Don't swallow, probably misconfiguration...
				throw ex;
			}
			catch (BeanCreationException ex) {
				// Instantiation failure, maybe too early...
				if (logger.isDebugEnabled()) {
					logger.debug("Bean creation exception on singleton FactoryBean type check: " + ex);
				}
				onSuppressedException(ex);
				return null;
			}
			finally {
				// Finished partial creation of this bean.
				afterSingletonCreation(beanName);
			}

			FactoryBean<?> fb = getFactoryBean(beanName, instance);
			if (bw != null) {
				this.factoryBeanInstanceCache.put(beanName, bw);
			}
			return fb;
		}
	}

	/**
	 * Obtain a "shortcut" non-singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getNonSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		if (isPrototypeCurrentlyInCreation(beanName)) {
			return null;
		}

		Object instance;
		try {
			// Mark this bean as currently in creation, even if just partially.
			beforePrototypeCreation(beanName);
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			instance = resolveBeforeInstantiation(beanName, mbd);
			if (instance == null) {
				BeanWrapper bw = createBeanInstance(beanName, mbd, null);
				instance = bw.getWrappedInstance();
			}
		}
		catch (UnsatisfiedDependencyException ex) {
			// Don't swallow, probably misconfiguration...
			throw ex;
		}
		catch (BeanCreationException ex) {
			// Instantiation failure, maybe too early...
			if (logger.isDebugEnabled()) {
				logger.debug("Bean creation exception on non-singleton FactoryBean type check: " + ex);
			}
			onSuppressedException(ex);
			return null;
		}
		finally {
			// Finished partial creation of this bean.
			afterPrototypeCreation(beanName);
		}

		return getFactoryBean(beanName, instance);
	}

	/**
	 * 执行MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition()方法
	 *
	 * Apply MergedBeanDefinitionPostProcessors to the specified bean definition,
	 * invoking their {@code postProcessMergedBeanDefinition} methods.
	 * @param mbd the merged bean definition for the bean
	 * @param beanType the actual type of the managed bean instance
	 * @param beanName the name of the bean
	 * @see MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition
	 */
	protected void applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd, Class<?> beanType, String beanName) {
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			// ⚠️MergedBeanDefinitionPostProcessor
			if (bp instanceof MergedBeanDefinitionPostProcessor) {
				/**
				 * 1、CommonAnnotationBeanPostProcessor
				 * 它对应的父类是InitDestroyAnnotationBeanPostProcessor，
				 * InitDestroyAnnotationBeanPostProcessor里面解析出来了当前bean对象中标注了@PostConstruct、@PreDestroy（也就是当前bean对象的初始化方法和销毁方法），然后放入bd中！
				 * 以及它自身解析出来了@Resource标注的字段和方法，放入bd中
				 * 以及它自身解析了@WebServiceRef、@EJB
				 *
				 * 2、AutowiredAnnotationBeanPostProcessor
				 * 里面解析出来了@Autowired、@Value、@Inject标注的字段和方法的一些信息，然后将这些解析到的信息放入bd中，方便后面的属性填充
				 */
				MergedBeanDefinitionPostProcessor bdp = (MergedBeanDefinitionPostProcessor) bp;
				bdp.postProcessMergedBeanDefinition(mbd, beanType, beanName);
			}
		}
	}

	/**
	 * 调用预实例化的postprocessor，处理是否有预实例化的快捷方式对于特殊的bean
	 *
	 * Apply before-instantiation post-processors, resolving whether there is a
	 * before-instantiation shortcut for the specified bean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the shortcut-determined bean instance, or {@code null} if none
	 */
	@Nullable
	protected Object resolveBeforeInstantiation/* 在实例化之前解决 */(String beanName, RootBeanDefinition mbd) {
		Object bean = null;
		// 如果beforeInstantiationResolved值为null或者true，那么表示尚未被处理，进行后续的处理
		if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved/* 在实例化解决之前 *//* 默认false */)) {
			// Make sure bean class is actually resolved at this point. —— 确保此时实际解析了bean类
			// 确认beanClass确实在此处进行处理
			// 判断当前mbd是否是合成的(只有在实现aop的时候，synthetic的值才为true)，并且是否实现了InstantiationAwareBeanPostProcessors
			// 题外：什么叫是否是合成的？也就是说，是我们具体的一个系统调用的，还是我们自己定义的，这样的一个区分
			if (!mbd.isSynthetic/* 是合成的 */() && hasInstantiationAwareBeanPostProcessors()/* 具有实例化感知Bean后处理器 */) {
				/**
				 * 1、实例化InstantiationAwareBeanPostProcessor bean的时机
				 *
				 * InstantiationAwareBeanPostProcessor是BeanPostProcessor的子接口，
				 * 所以如果存在实现了InstantiationAwareBeanPostProcessor bd，那么会在AbstractApplicationContext#refresh() ——> registerBeanPostProcessors()中注册该BeanPostProcessor，
				 *
				 * 题外：同时在registerBeanPostProcessors()里面，注册BeanPostProcessor的时候，会判断如果有InstantiationAwareBeanPostProcessor的实现类注册，
				 * >>> 还会把hasInstantiationAwareBeanPostProcessors标识设置为true，代表存在InstantiationAwareBeanPostProcessor bean
				 * >>> 后续在实例化bean的时候，就能进入这个if判断里面来，执行对应的InstantiationAwareBeanPostProcessor bean
				 *
				 *
				 *
				 * 2、InstantiationAwareBeanPostProcessor介绍：
				 * （1）内部可以根据情况判断，是否返回对应的bean。这个bean可以是一个普通对象，也可以是一个代理对象，但是无论是返回什么对象，
				 * 只要返回了bean对象，那么将会采用这个自己返回的bean，来作为当前bd要实例化的bean，不会再走后面的doCreateBean()逻辑实例化bean了，
				 * 也就是说：采用返回的bean对象，代替掉doCreateBean()时要实例化的bean。
				 *
				 * 题外：如果返回了一个bean，就不会走后面的doCreateBean()逻辑来实例化当前bd的bean了，那也意味着后面的populateBean()、initializeBean()，等一系列对当前bean操作的逻辑都不会走，什么填充之前配置的依赖、属性、对其进行AOP动态代理之类的，全部都失效
				 *
				 * （2）内部也可以不返回bean，而是在当前bean，或者所有bean实例化之前，提前执行一些想要执行的逻辑，例如：对aop相关的bd（切入点bd、切入点通知bd）进行实例化操作。
				 *
				 * 3、InstantiationAwareBeanPostProcessor实现子类
				 * （1）AOP
				 * 注解的方式，ibp = AnnotationAwareAspectJAutoProxyCreator
				 * xml的方式，ibp = AspectJAwareAdvisorAutoProxyCreator
				 *
				 * 题外：只要在AbstractApplicationContext#refresh() ——> registerBeanPostProcessors()中实例化注册好了AOP相关的InstantiationAwareBeanPostProcessor bean，
				 * 那么在registerBeanPostProcessors()中，如果后续还有BeanPostProcessor要实例化注册，那么就会触发AOP相关的InstantiationAwareBeanPostProcessor bean执行了！
				 */
				// 获取当前bd的目标类型
				Class<?> targetType = determineTargetType/* 确定目标类型 */(beanName, mbd);
				if (targetType != null) {
					// 实例化之前调用的方法 —— 执行InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation()
					bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
					if (bean != null) {
						// 实例化之后调用的方法 —— 执行InstantiationAwareBeanPostProcessor#postProcessAfterInitialization()
						// 题外：只有不为null，才代表实例化完成了，所以这里是实例化之后调用的方法
						bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
					}
				}
			}

			// 是否在doCreateBean()之前，通过InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation()返回了一个对象，替换掉了后面doCreateBean()时实例化的对象
			mbd.beforeInstantiationResolved/* 在实例化解决之前 */ = (bean != null);
		}
		return bean;
	}

	/**
	 * 遍历执行InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation()
	 *
	 * Apply InstantiationAwareBeanPostProcessors to the specified bean definition
	 * (by class and name), invoking their {@code postProcessBeforeInstantiation} methods.
	 * <p>Any returned object will be used as the bean instead of actually instantiating
	 * the target bean. A {@code null} return value from the post-processor will
	 * result in the target bean being instantiated.
	 * @param beanClass the class of the bean to be instantiated
	 * @param beanName the name of the bean
	 * @return the bean object to use instead of a default instance of the target bean, or {@code null}
	 * @see InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation
	 */
	@Nullable
	protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
		// 遍历执行InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation()
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			if (bp instanceof InstantiationAwareBeanPostProcessor) {
				/**
				 * 题外：把一个对象的所有依赖关系去掉，可以实现InstantiationAwareBeanPostProcessor，然后重写postProcessBeforeInstantiation()，
				 * 直接返回一个bean出去，这个对象当中之前所有配置的依赖就都不存在了
				 */
				InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
				Object result = ibp.postProcessBeforeInstantiation(beanClass, beanName);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	/**
	 * Create a new instance for the specified bean, using an appropriate instantiation strategy:
	 * factory method, constructor autowiring, or simple instantiation.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a BeanWrapper for the new instance
	 * @see #obtainFromSupplier
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 * @see #instantiateBean
	 *
	 * Bean的三种方式创建的代码在这里：
	 * 		静态方法构建bean
	 * 		普通方法构建bean
	 * 		构造函数
	 */
	protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
		// Make sure bean class is actually resolved at this point. —— 确保此时实际解析了 bean 类。
		// 确认需要创建的bean实例的类可以实例化
		// 获取bd的Class对象
		Class<?> beanClass = resolveBeanClass(mbd, beanName);

		// 验证访问修饰符
		// 检测一个类的访问权限，Spring默认情况下对非public的类是允许访问的
		// 确保class不为空，并且访问权限是public，这样我才能进行访问
		// 如果class不为空，但是访问修饰符不是public的，就抛出异常（访问修饰符必须是public的，我才能够进行访问）
		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers/* 获取修饰符 */()) && !mbd.isNonPublicAccessAllowed/* 是否允许非公共访问 */()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					// Bean 类不公开，不允许非公开访问
					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
		}

		/* 1、Supplier（Supplier："实例供应器"） */

		// 判断bd中是否包含Supplier，存在就通过Supplier创建对象返回
		// 题外：mbd.getInstanceSupplier()获取到的Supplier：已经被包装在我们当前的bd里面去了
		Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
		if (instanceSupplier != null) {
			// 调用supplier的方法，完成实例化工作
			// 题外：这样就没有走反射流程创建实例了
			// 题外：指定具体的静态方法来创建bean的实例
			return obtainFromSupplier(instanceSupplier, beanName);
		}

		/* 2、factory-method */

		/**
		 * ⚠️1、2：如果工厂方法不为空，则通过工厂方法构建bean对象，也就是：
		 * 		静态方法构建bean
		 * 		普通方法构建bean
		 */
		// 判断bd中是否包含工厂方法，如果包含，就通过工厂方法来创建具体的对象返回
		if (mbd.getFactoryMethodName() != null) {
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}

		/*

		3、反射

		获取当前bean的构造方法来生成具体的对象，有两种方式：1、使用自动注入的构造器来生成；2、通过默认构造器来生成

		*/

		/**
		 * 从spring的原始注释可以知道这是一个Shortcut(捷径),什么意思呢？
		 * 当多次构建同一个bean时，可以使用这个Shortcut，也就是说不再需要推断应该使用哪种方式构建bean
		 * 比如在多次构建同一个prototype(原型)类型的bean时，就可以走此处的Shortcut
		 *
		 * 题外：这里的resolved和mbd.constructorArgumentsResolved将会在bean第一次实例化的过程中被设置
		 */
		// Shortcut when re-creating the same bean... - 重新创建相同bean时的快捷方式...

		/**
		 * ⚠️一个类可能有多个构造器，所以Spring得根据参数个数、类型选择出一个合适的构造器，来创建对象
		 * 在使用构造器创建实例后，Spring会将解析过后选择出来的构造器或工厂方法保存在缓存中，避免下次创建相同bean时再次解析构造器
		 */

		// 标记下，防止重复创建同一个bean
		boolean resolved = false;
		// 是否需要自动装配
		boolean autowireNecessary = false;
		// 如果没有参数
		if (args == null) {
			synchronized (mbd.constructorArgumentLock) {
				// 因为一个类可能有多个构造函数，每个构造函数都有不同的参数，所以需要根据配置文件中配置的参数，或传入的参数来确定最终调用的构造函数或对应的工厂方法。
				// 因为解析确定构造器的过程会比较麻烦，所以spring会将解析、确定好的构造函数缓存到BeanDefinition中的resolvedConstructorOrFactoryMethod属性中
				// 在下次创建相同bean对象时，直接获取RootBeanDefinition中的resolvedConstructorOrFactoryMethod属性缓存的构造器，避免再次解析
				if (mbd.resolvedConstructorOrFactoryMethod != null/* 代表存在之前已经解析好的构造器 */) {
					resolved = true;
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}
		// 有解析好的构造器或者工厂方法，则直接使用解析好的
		if (resolved) {
			// 构造器有参数
			if (autowireNecessary) {
				// 通过构造器自动装配的方式创建bean对象
				return autowireConstructor/* 自动装配构造函数 */(beanName, mbd, null, null);
			}
			// 构造器无参数，通过默认的无参构造器执行构建bean对象
			else {
				return instantiateBean(beanName, mbd);
			}
		}

		/*

		通过BeanPostProcessor来获取构造器，或者通过选择最合适的构造器。

		调用的是SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors()

		*/
		/**
		 * 实例化对象的时候，Spring并不知道哪种方式来实例化这个对象，于是就先得到类当中的所有构造方法，得到了可以实例化的构造方法，就用这个构造方法进行实例化，如果没有得到构造方法就会往下走
		 * 获取类的构造方法，然后决定用哪个构造方法来对对象进行实例化
		 * 为什么要定义一个方法获取所有类的所有构造方法，为什么不通过类获取所有的构造方法，然后再决定用哪个构造方法进行实例化？
		 *
		 * ctors：除了默认构造函数之外的候选构造函数，没有则为null。如果类有默认的构造方法，则ctors为空。
		 * 		如果存在多个有参构造函数，则返回null，并且会在接下来的过程中报错。
		 * 		如果存在一个有参构造函数，则数组为1，但是构造参数不是要注入的对象，例如String，那么在接下来的过程中报错
		 * 		(⚠️在目前的Spring版本，ctors只会返回一个有参构造函数，即使类中存在多个构造函数)
		 */
		// Candidate constructors for autowiring? - 自动装配的候选构造函数？
		// 从bean后置处理器中为自动装配寻找构造方法，有且仅有一个有参构造或者有且仅有@Autowired注解构造，它才能够识别到，如果不是这样的情况，它是识别不到的
		// 由后置处理器决定返回哪些构造方法 —— 根据参数解析构造函数，获取到所有符合规则的构造器
		// 💡提示：SmartInstantiationAwareBeanPostProcessor在里面
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors/* 去BeanPostProcessor里面确定我们指定的构造器 */(beanClass, beanName);
		/**
		 * mbd.getResolvedAutowireMode()默认为no
		 * 		no和by-type的区别很大：Spring自动装配的模型是no，但是no是采用类型自动注入的技术
		 *
		 * 自动装配模型 != 自动注入技术，这是两个概念的东西。
		 * 自动装配模型有：by-name、by-type、constructor、no、default，真正可以自动装配的模型只有：by-name、by-type、constructor。而no、default都0，default就是no，
		 * 		在未设置自动装配模型的情况下，默认为no。在设置了为byName/byType时，只要存在属性(set方法)，那么可以不用写@Autowird注解
		 * 自动注入技术：例如@Autowrid(@Autowrid是通过类型自动注入,但是和by-type是两回事 - 采用by-type可以不写@Autowrid)
		 *
		 * 		拓展：@Autowird是通过File注入类型,然后装配;by-type是通过属性注入类型,然后装配
		 *
		 * 	题外：自动装配的时候，有几种方式可以选择？5种：byType、byName、constructor、default、no
		 *
		 */
		// 以下情况符合其一即可进入
		// 1、存在可选构造方法
		// 2、自动装配模型为constructor("构造器自动装配")
		// 3、给BeanDefinition中设置了构造参数值
		// 4、有参与构造器参数列表的参数
		if (ctors != null || mbd.getResolvedAutowireMode/* 自动装配的模型 */() == AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues/* 有没有构造器参数值 */() || !ObjectUtils.isEmpty(args)) {
			// 对符合规则的构造器作进一步的筛选，并且直接用最终筛选到的构造器创建出来一个bean实例
			return autowireConstructor(beanName, mbd, ctors, args);
		}

		// Preferred constructors for default construction? —— 默认构造的首选构造函数？
		// 找出最合适的默认构造方法
		// 获取首选构造函数
		ctors = mbd.getPreferredConstructors/* 获取首选构造函数 */();
		if (ctors != null) {
			/**
			 * ⚠️3、不存在无参构造方法进行初始化，走这里
			 */
			// 构造函数自动注入
			return autowireConstructor(beanName, mbd, ctors, null);
		}

		/**
		 * ⚠️3、️使用默认的无参构造方法进行初始化，走这里
		 */
		// No special handling: simply use no-arg constructor.
		// 实例化 Bean
		// 使用默认构造函数构造
		// 使用默认无参构造函数创建对象，如果没有无参构造且存在多个有参构造且没有@AutoWired注解构造，会报错
		return instantiateBean(beanName, mbd);
	}

	/**
	 * 从supplier获取bean
	 *
	 * Obtain a bean instance from the given supplier.
	 * @param instanceSupplier the configured supplier
	 * @param beanName the corresponding bean name
	 * @return a BeanWrapper for the new instance
	 * @since 5.0
	 * @see #getObjectForBeanInstance
	 */
	protected BeanWrapper obtainFromSupplier(Supplier<?> instanceSupplier, String beanName) {
		Object instance;

		// 获取原先创建的beanName
		String outerBean = this.currentlyCreatedBean/* 当前创建的Bean */.get();
		// 用当前作对做替换
		this.currentlyCreatedBean.set(beanName);
		try {
			// 调用supplier的方法，完成实例化工作
			// 题外：这样就没有走反射流程创建实例了
			// 题外：指定具体的静态方法来创建bean的实例
			instance = instanceSupplier.get();
		}
		finally {
			if (outerBean != null) {
				this.currentlyCreatedBean.set(outerBean);
			}
			else {
				this.currentlyCreatedBean.remove();
			}
		}

		// 如果没有创建对象，默认为NullBean
		if (instance == null) {
			instance = new NullBean();
		}
		// 包装一下实例
		BeanWrapper bw = new BeanWrapperImpl(instance);
		// 初始化BeanWrapper
		initBeanWrapper(bw);
		// 返回包装类
		return bw;
	}

	/**
	 * Overridden in order to implicitly register the currently created bean as
	 * dependent on further beans getting programmatically retrieved during a
	 * {@link Supplier} callback.
	 * @since 5.0
	 * @see #obtainFromSupplier
	 */
	@Override
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		String currentlyCreatedBean = this.currentlyCreatedBean.get();
		if (currentlyCreatedBean != null) {
			registerDependentBean(beanName, currentlyCreatedBean);
		}

		return super.getObjectForBeanInstance(beanInstance, name, beanName, mbd);
	}

	/**
	 * Determine candidate constructors to use for the given bean, checking all registered - 确定要用于给定bean的候选构造函数，检查所有已注册
	 * {@link SmartInstantiationAwareBeanPostProcessor SmartInstantiationAwareBeanPostProcessors}.
	 * @param beanClass the raw class of the bean
	 * @param beanName the name of the bean
	 * @return the candidate constructors, or {@code null} if none specified
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors
	 */
	@Nullable
	protected Constructor<?>[] determineConstructorsFromBeanPostProcessors(@Nullable Class<?> beanClass, String beanName)
			throws BeansException {

		if (beanClass != null && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				// ⚠️SmartInstantiationAwareBeanPostProcessor
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					// AutowiredAnnotationBeanPostProcessor
					Constructor<?>[] ctors = ibp.determineCandidateConstructors(beanClass, beanName);
					if (ctors != null) {
						return ctors;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Instantiate the given bean using its default constructor. - 使用其默认构造函数实例化给定的bean。
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return a BeanWrapper for the new instance
	 */
	protected BeanWrapper instantiateBean(String beanName, RootBeanDefinition mbd) {
		try {
			Object beanInstance;
			if (System.getSecurityManager() != null) {
				beanInstance = AccessController.doPrivileged(
						(PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(mbd, beanName, this),
						getAccessControlContext());
			}
			else {
				/**
				 * beanInstance：里面创建了一个原生的bean
				 *
				 * getInstantiationStrategy()：获取bean的生成策略。
				 * 固定返回的是CglibSubclassingInstantiationStrategy对象，但是并不是真正的cglib，只是名称是cglib，底层还是调用的反射；
				 * 		那为什么要叫做cglib呢？因为如果类当中配置了lookup-method或replace-method，就需使用CGLIB构建对象
				 *
				 * 	CglibSubclassingInstantiationStrategy extends SimpleInstantiationStrategy
				 * 		也只有SimpleInstantiationStrategy实现了instantiate()，所以走的是也只有SimpleInstantiationStrategy+instantiate()
				 *
				 * 	⚠️进入instantiate()
				 */
				// 获取实例化策略，并且进行实例化操作
				beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, this);
			}

			/**
			 * ⚠️原生对象不行吗？为什么要包装？
			 *
			 * 有一堆属性值，这些属性值填充到当前对象里面的属性需要进行一些修改编辑操作
			 * 默认属性编辑器，前面设置的一些自定义属性编辑器，类型转换器，都包含在包装类里面，
			 * 有了这些功能，在进行属性赋值的时候，可以对属性值进行类型转换和属性编辑，之后的操作都在当前的包装类里面完成即可
			 * 如果只有一个实体类的话，后面还需要一些工具类来对里面的属性值进行相关的编辑修改工作
			 * 现在全部放入到一个包装类里面了，包装类里面本身就提供了很多这样的功能，所以我直接使用当前包装类，就能完成当前需要的所有功能，直接干就完事了
			 * 这是它设计方便的一个地方
			 */
			// 包装成BeanWrapperImpl
			BeanWrapper bw = new BeanWrapperImpl(beanInstance);
			/**
			 * ⚠️里面填充了BeanWrapperImpl的overriddenDefaultEditors、customEditors属性，也就是：
			 * （1）将DefaultListableBeanFactory的propertyEditorRegistrars set集合中的属性编辑器放入BeanWrapperImpl的overriddenDefaultEditors set集合当中
			 * （2）将DefaultListableBeanFactory的customEditors map集合中的属性编辑器放入BeanWrapperImpl的customEditors map集合当中
			 */
			// 初始化BeanWrapper
			initBeanWrapper(bw);
			return bw;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Instantiation of bean failed", ex);
		}
	}

	/**
	 * 通过工厂方法实例化，先获取构造器解析器，然后用工厂方法进行实例化
	 *
	 * Instantiate the bean using a named factory method. The method may be static, if the
	 * mbd parameter specifies a class, rather than a factoryBean, or an instance variable
	 * on a factory object itself configured using Dependency Injection.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 * @see #getBean(String, Object[])
	 */
	protected BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {
		// 创建构造器的一个处理器并使用factory-method进行实例化操作
		// explicitArgs：显示的参数，就是说我需要往里面注入什么样一些参数值，这里会帮我们显示出来
		return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs/* 显式参数 */);
	}

	/**
	 * 自动装配的构造方法
	 *
	 * "autowire constructor" (with constructor arguments by type) behavior.
	 * Also applied if explicit constructor argument values are specified,
	 * matching all remaining arguments with beans from the bean factory.
	 * <p>This corresponds to constructor injection: In this mode, a Spring
	 * bean factory is able to host components that expect constructor-based
	 * dependency resolution.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param ctors the chosen candidate constructors
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 */
	protected BeanWrapper autowireConstructor(
			String beanName, RootBeanDefinition mbd, @Nullable Constructor<?>[] ctors, @Nullable Object[] explicitArgs) {
		// ConstructorResolver：构造器的处理器，
		// autowireConstructor()：通过构造器的处理器选择具体的构造器
		return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
	}

	/**
	 * Populate the bean instance in the given BeanWrapper with the property values
	 * from the bean definition.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param bw the BeanWrapper with bean instance
	 */
	@SuppressWarnings("deprecation")  // for postProcessPropertyValues
	protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
		/*

		1、包装类为空的话，无法进行属性的填充。
		所以如果包装类为空，且有需要进行设置的属性，那么就报错；如果没有要设置的属性，就直接退出属性填充步骤！

		*/

		// 包装类是否为空
		if (bw == null) {
			// 判断bd中是否有需要设置的属性值（bd中是否包含MutablePropertyValues对象）
			if (mbd.hasPropertyValues()) {
				// 如果包装类为空，并且bd里面有需要处理的属性值的话，直接报错
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance"/* 不能将属性应用给一个空的实例 */);
			}
			else {
				// Skip property population phase for null instance. —— 跳过空实例的属性填充阶段。
				// 没有可填充的属性值，直接跳过当前bean，不填充属性
				return;
			}
		}

		/*

		2、调用InstantiationAwareBeanPostProcessor#postProcessAfterInstantiation() —— 实例化后调用的方法
		（1）️该方法可以给bean的属性填充值(完成属性的赋值工作)，
		（2）以及决定要不要进行属性填充。返回值如果为true，代表允许属性填充；如果为false，代表不允许属性填充！

		题外：InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation()实例化前调用的方法，
		在createBean() ——> resolveBeforeInstantiation()当中被调用的，也就是在doCreateBean()之前被调用的！

		*/
		// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
		// state of the bean before properties are set. This can be used, for example,
		// to support styles of field injection.
		// 上面的翻译：让任何InstantiationAwareBeanPostProcessors有机会在设置属性之前修改bean的状态。例如，这可以用于支持字段注入的样式。
		// 给任何实现了InstantiationAwareBeanPostProcessors的子类机会去修改bean的状态，在设置属性之前，可以被用来支持类型的字段注入

		// 是否是synthetic。一般是指只有AOP相关的prointCut配置，或者Advice配置，才会将synthetic设置为true
		// 如果mbd不是"synthetic"，且工厂拥有InstantiationAwareBeanPostProcessor
		if (!mbd.isSynthetic/* 合成 */() && hasInstantiationAwareBeanPostProcessors()/* 实例化感知 */) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					/**
					 * Spring默认的InstantiationAwareBeanPostProcessor：全部返回true
					 * 		ConfigurationClassPostProcessor$ImportAwareBeanPostProcessor 间接实现 InstantiationAwareBeanPostProcessor，返回的为true
					 * 		CommonAnnotationBeanPostProcessor，返回的为true
					 * 		AutowiredAnnotationBeanPostProcessor，返回的为true
					 */
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					/**
					 * ⚠️⚠️⚠️ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)：
					 * 如果返回为true，代表允许后续填充属性值；
					 * 如果返回为false，代表不允许后续填充属性值，也就是说该bean不需要再填充属性值了！
					 */
					// 进行实例化之后的属性值的设置工作
					if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
						return;
					}
				}
			}
		}

		/*

		3、获取<property>标签中定义的属性值：PropertyValues对象

		*/


		// 如果mbd有PropertyValues，就获取PropertyValues。
		// 其实也就是获取一下设置好的属性值，方便后续进行赋值操作
		/**
		 * 1、PropertyValues：包含一个或多个PropertyValue对象的容器，通常包括针对特定目标Bean的一次更新
		 *
		 * 2、PropertyValue：是
		 * <bean id="address" class="com.springstudy.mashibing.s_18.populateBean.Address">
		 * 		<property name="province" value="河北"></property>
		 * 		<property name="city" value="邯郸"></property>
		 * 		<property name="town" value="武安"></property>
		 * </bean>
		 * 中的<property>标签的属性值，代表当前bean定义信息里面，设置好的一些属性结果值，这里获取一下，方便后续进行赋值操作！
		 */
		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

		/*

		4、处理自动装配模式：byName、byType

		根据<bean autowire=""/>标签中的autowire属性值来决定使用名称注入还是类型注入

		注意：⚠️在自动装配的时候，并不会把找寻到的值设置到对象当中，而是保存自动装配所得到的属性值到pvs(PropertyValues)中，后续在applyPropertyValues()的时候，才会进行属性设置

		*/

		/**
		 * 1、当我在进行自动装配的时候，我怎么知道哪些属性是需要进行自动装配的？
		 * 2、resolvedAutowireMode是<bean autowire=""/>标签中的autowire属性值！
		 */
		// 获取自动装配模式
		int resolvedAutowireMode = mbd.getResolvedAutowireMode();
		// 自动装配模式判断：如果自动装配模式为按名称自动装配bean属性，或者按类型自动装配bean属性
		if (resolvedAutowireMode == AUTOWIRE_BY_NAME/* 按名称自动装配bean属性 */ || resolvedAutowireMode == AUTOWIRE_BY_TYPE/* 按类型自动装配bean属性 */) {
			// 是byName / byType 则if成立

			// MutablePropertyValues：PropertyValues接口的默认实现，允许对属性进行简单操作，并提供构造器来支持从映射 进行深度复制的构造
			// 存储自动注入找寻到的值！
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);

			// Add property values based on autowire by name if applicable. —— 如果适用，根据名称自动装配属性值。
			/* ️⚠️根据名称自动装配属性值 */
			if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
				// 通过bw的PropertyDescriptor属性名，找出对应的Bean对象，将其添加到newPvs中
				autowireByName(beanName, mbd, bw, newPvs);
			}
			// Add property values based on autowire by type if applicable. —— 如果适用，根据类型添加基于自动装配的属性值。
			/* ️⚠️根据类型自动装配属性值 */
			if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
				// 通过bw的PropertyDescriptor属性类型，找出对应的Bean对象，将其添加到newPvs中
				autowireByType(beanName, mbd, bw, newPvs);
			}

			// ⚠️让pvs重新引用newPvs，newPvs此时已经包含了pvs的属性值以及通过AUTOWIRE_BY_NAME. AUTOWIRE_BY_TYPE自动装配所得到的属性值
			pvs = newPvs;
		}

		// 工厂当中是否拥有InstantiationAwareBeanPostProcessor接口的实例
		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
		// 是否需要依赖检查
		// mbd.getDependencyCheck()：默认返回DEPENDENCY_CHECK_NONE，表示"不检查"
		boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);

		/*

		5、调用InstantiationAwareBeanPostProcessor#postProcessProperties()进行属性值的设置！

		主要是实现注解的属性值注入工作（当使用注解的时候，通过这个方法来完成注解属性的注入）
		例如常见的有：AutowiredAnnotationBeanPostProcessor实现了@Autowired的属性值注入、CommonAnnotationBeanPostProcessor处理@Resource、@PostConstruct的实现

		总结：注解属性值注入

		 */

		// 经过筛选的PropertyDescriptor数组,存放着排除忽略的依赖项，或忽略项上的定义的属性
		PropertyDescriptor[] filteredPds = null;
		// 如果工厂拥有InstantiationAwareBeanPostProcessor，那么调用postProcessProperties()，主要是对几个注解的赋值工作
		// 包含两个关键的子类是CommonAnnotationBeanPostProcessor、AutowiredAnnotationBeanPostProcessor
		if (hasInstAwareBpps/* true */) {
			if (pvs == null) {
				// 如果pvs为null，尝试获取mbd的PropertyValues
				pvs = mbd.getPropertyValues();
			}
			// 遍历工厂内的所有后置处理器
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					/**
					 * 1、ConfigurationClassPostProcessor$ImportAwareBeanPostProcessor
					 * 2、CommonAnnotationBeanPostProcessor处理@Resource、@PostConstruct的实现
					 * 3、AutowiredAnnotationBeanPostProcessor处理@Autowired的
					 * 注意：⚠️其中只有CommonAnnotationBeanPostProcessor、AutowiredAnnotationBeanPostProcessor有进行属性值设置，调用了metadata.inject()，内部解决了循环依赖
					 * 注意：⚠️循环依赖，并不是只在这里进行解决！后面的注入属性值也会解决！
					 */
					// 让ipb对pvs增加对bw的Bean对象的propertyValue，或编辑pvs的propertyValue
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					/**
					 * postProcessProperties()：在工厂将给定的属性值，应用到给定Bean之前，对它们进行后置处理，不需要任何属性扫描符。
					 * ⚠️当使用注解的时候，通过这个方法来完成属性的注入（当你实现某些注解的时候，可以通过该方法来设置具体的属性值），例如：实现@Autowired的注入
					 *
					 * 题外：该方法会在未来的版本中删除，取而代之的是postProcessPropertyValues()
					 */
					PropertyValues pvsToUse = ibp.postProcessProperties(pvs/* 属性值 */, bw.getWrappedInstance()/* bean */, beanName);
					// 如果pvsToUse为null
					if (pvsToUse == null) {
						// 如果filteredPds为null
						if (filteredPds == null) {
							// mbd.allowCaching：是否允许缓存，默认是允许的。
							// 缓存除了可以提高效率以外，还可以保证在并发的情况下，返回的PropertyDesciptor[]永远都是
							// 从bw提取一组经过筛选的PropertyDesciptor，排除忽略的依赖项或忽略项上定义的属性
							filteredPds = filterPropertyDescriptorsForDependencyCheck/* 过滤依赖检查的属性描述符 */(bw, mbd.allowCaching);
						}
						/**
						 * postProcessPropertyValues：一般进行检查是否所有依赖项都满足，例如基于"Require"注释在 bean属性setter,
						 * 替换要应用的属性值，通常是通过基于原始的PropertyValues创建一个新的MutablePropertyValue实例，添加或删除特定的值
						 * 返回的PropertyValues将应用于bw包装的bean实例的实际属性值（添加PropertyValues实例pvs或者设置为null以跳过属性填充）
						 */
						// ipd的postProcessPropertyValues()
						pvsToUse = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
						if (pvsToUse == null) {
							// 如果pvsToUse为null，将终止该方法精致，以跳过属性填充
							return;
						}
					}
					pvs = pvsToUse;
				}
			}
		}

		/* 6、检查一下我们依赖的属性值（默认不检查，一般用不到） */

		// 如果需要依赖检查（就是检查一下我们依赖的属性值）
		if (needsDepCheck) {
			// 如果filteredPds为null
			if (filteredPds == null) {
				// 从bw提取一组经过筛选的PropertyDescriptor，排除忽略的依赖项或忽略项上的定义属性
				filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			}
			// 检查依赖项：主要检查pd(PropertyDescriptor：属性描述符)的setter方法需要赋值时，pvs中没有满足其pd的需求的属性值可供其赋值
			checkDependencies(beanName, mbd, filteredPds, pvs); // 依赖检查，对应depends-on属性
		}

		/*

		7、设置<property>标签中定义的属性值

		根据<property>标签中定义的属性值，完成各种属性值的解析和赋值工作

		总结：标签属性值注入
		注意：只要上面没有退出属性设置，那么最终都会走这个逻辑！

		 */

		// 刚刚处理的是自动装配完成属性值的设置，配置文件中的
		// 具体属性值的填充和解析工作

		// 如果pvs不为null
		if (pvs != null) {
			// 应用给定的属性值，解决任何在这个bean工厂运行时其他bean的引用。必须使用深拷贝，所以我们不会永久地修改这个属性
			// ⚠️将属性应用到bean中
			applyPropertyValues/* 应用属性值（设置属性值） */(beanName, mbd, bw, pvs);
		}
	}

	/**
	 * 通过bw的PropertyDescriptor属性名，查找出对应的Bean对象，将其添加到pvs中
	 *
	 * Fill in any missing property values with references to
	 * other beans in this factory if autowire is set to "byName".
	 * @param beanName the name of the bean we're wiring up.
	 * Useful for debugging messages; not used functionally.
	 * @param mbd bean definition to update through autowiring
	 * @param bw the BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 */
	protected void autowireByName(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues/* 可变属性值 */ pvs) {

		/* 一、根据当前的名称，去匹配对应的引用值 */

		/*

		1、筛选出引用类型的属性名称进行返回

		当拿到这些引用类型之后，会判断一下能否进行自动装配工作

		⚠️注意：必须要有set方法

		*/
		// 寻找bw中需要依赖注入的属性：
		// >>> 获取bw中有setter方法 && 非简单类型属性 && mbd的PropertyValues中没有该pd的属性名的PropertyDescriptor属性名数组
		String[] propertyNames = unsatisfiedNonSimpleProperties/* 不满足的非简单属性，也就是不是简单属性的值，我把它过滤出来 */(mbd, bw);

		/*

		2、获取引用类型的属性对应的bean对象，没有就创建

		 */
		// 遍历属性名
		for (String propertyName : propertyNames) {
			// 如果该bean工厂有propertyName的bd，或外部注册的singleton实例
			if (containsBean(propertyName)) {
				// ⚠️从工厂中，获取propertyName的bean对象，没有就创建
				// ⚠️也就是在这里，完成了：往对象里面属性赋值的时候，也完成了属性的对象创建工作
				Object bean = getBean(propertyName);
				// 将propertyName，bean添加到pvs中
				pvs.add(propertyName, bean);
				// ⚠️存储依赖关系：注册propertyName与beanName的依赖关系
				/**
				 * 比如我现在创建Person对象，内部依赖Address对象，在属性注入过程中，我把Address对象创建好了，
				 * 它两具有依赖关系，既然有依赖关系，所以我把它两的依赖关系给存储起来
				 */
				registerDependentBean(propertyName, beanName);
				// 打印跟踪日志
				if (logger.isTraceEnabled()) {
					logger.trace("Added autowiring by name from bean name '" + beanName +
							"' via property '" + propertyName + "' to bean named '" + propertyName + "'");
				}
			}
			else {
				// 打印跟踪日志
				if (logger.isTraceEnabled()) {
					logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName +
							"' by name: no matching bean found");
				}
			}
		}
	}

	/**
	 * 定义"按类型自动装配”(按类型bean属性)行为的抽象方法
	 *
	 * Abstract method defining "autowire by type" (bean properties by type) behavior.
	 * <p>This is like PicoContainer default, in which there must be exactly one bean
	 * of the property type in the bean factory. This makes bean factories simple to
	 * configure for small namespaces, but doesn't work as well as standard Spring
	 * behavior for bigger applications.
	 * @param beanName the name of the bean to autowire by type
	 * @param mbd the merged bean definition to update through autowiring
	 * @param bw the BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 */
	protected void autowireByType(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

		// 获取工厂的自定义类型转换器（如果有类型转换器，意味着，可能要发生某些类型转换操作）
		TypeConverter converter = getCustomTypeConverter();
		// 如果没有配置自定义类型转换器，就把包装类赋给它，作为类型转换器
		if (converter == null) {
			// 使用bw作为类型转换器
			converter = bw;
		}

		// 存放所有候选Bean名的集合
		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);
		// 寻找bw中需要依赖注入的属性：
		// >>> 获取bw中有setter方法 && 非简单类型属性 && mbd的PropertyValues中没有该pd的属性名的PropertyDescriptor属性名数组
		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		// 遍历属性名
		for (String propertyName : propertyNames) {
			try {
				/**
				 * PropertyDescriptor：表示JavaBean类通过存储器导出一个属性
				 * ⚠️PropertyDescriptor：属性描述对象，里面存储的是属性描述信息 —— 每一个属性都有自己的一个描述信息的对象
				 */
				// 从bw中获取propertyName对应的PropertyDescriptor —— 获取属性描述信息
				PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
				// Don't try autowiring by type for type Object: never makes sense,
				// even if it technically is a unsatisfied, non-simple property.
				// 上面的翻译：不要尝试为Object类型，按类型自动装配（不要尝试按类型自动装配Object类型的对象）：永远没有意义，即使它在技术上是一个不令人满意的、不简单的属性。

				// 如果pd的属性值类型不是Object
				if (Object.class != pd.getPropertyType()) {
					// 因为Object是没有意义的，所以Object是不能够进来的！

					// 获取pd属性的Setter方法的方法参数包装对象 —— 获取set方法的一些参数
					MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
					// Do not allow eager init for type matching in case of a prioritized post-processor.
					// 上面的翻译：在优先后处理器的情况下，不允许使用 Eager init 进行类型匹配
					// 判断bean对象是否是PriorityOrder实例，如果不是就允许急于初始化来进行类型匹配 —— 也就是说，是否允许提前进行类型的匹配
					// eager为true时会导致初始化lazy-init单例，和由FactoryBeans(或带有"factory-bean"引用的工厂方法)创建的对象，以进行类型检查
					boolean eager = !(bw.getWrappedInstance() instanceof PriorityOrdered);
					// 将methodParam封装成AutowireByTypeDependencyDescriptor对象
					/**
					 * AutowireByTypeDependencyDescriptor：根据类型依赖自动注入的描述符，重写了getDependencyName()，使其永远返回null
					 */
					DependencyDescriptor/* 依赖描述符 */ desc = new AutowireByTypeDependencyDescriptor(methodParam, eager);

					// ⚠️根据desc(依赖的描述信息)里面所包装的依赖类型,解析出与之匹配的候选Bean对象
					Object autowiredArgument/* 自动装配参数值 */ = resolveDependency/* 解析依赖 */(desc, beanName, autowiredBeanNames, converter);

					if (autowiredArgument != null) {
						// ⚠️
						// 如果autowiredArgument不为null，将propertyName、autowiredArgument作为键值添加到pvs中
						pvs.add(propertyName, autowiredArgument);
					}

					// 遍历所有候选Bean名称集合
					for (String autowiredBeanName : autowiredBeanNames) {
						// ⚠️存储依赖关系：注册beanName与dependentBeanName的依赖关系
						registerDependentBean(autowiredBeanName, beanName);
						if (logger.isTraceEnabled()) {
							logger.trace("Autowiring by type from bean name '" + beanName + "' via property '" +
									propertyName + "' to bean named '" + autowiredBeanName + "'");
						}
					}
					// 将候选Bean名集合清空
					autowiredBeanNames.clear();
				}
			}
			catch (BeansException ex) {
				// 捕捉自动装配时抛出的Bean异常，重新抛出，不满足依赖异常
				throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
			}
		}
	}


	/**
	 * 返回一个不满足要求的非简单bean属性数组。这些可能是对工厂中其他bean的不满意的引用。不包括简单属性。如原始或字符串
	 *
	 * 获取bw中有setter方法 && 非简单属性 && mbd的PropertyValues中没有该pd的属性名的PropertyDescriptor 属性名数组
	 *
	 * Return an array of non-simple bean properties that are unsatisfied.
	 * These are probably unsatisfied references to other beans in the
	 * factory. Does not include simple properties like primitives or Strings.
	 * @param mbd the merged bean definition the bean was created with
	 * @param bw the BeanWrapper the bean was created with
	 * @return an array of bean property names
	 * @see org.springframework.beans.BeanUtils#isSimpleProperty
	 */
	protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
		/*

		该方法做的事情：筛选出引用类型的属性名称进行返回

		当拿到这些引用类型之后，会判断一下能否进行自动装配工作

		*/
		// TreeSet：底层是二叉树，可以对对象元素进行排序，但是自定义类需要实现comparable接口，重写comparaTo()方法
		// 存放的是引用类型的属性名称
		Set<String> result = new TreeSet<>();
		// 获取所有 <bean id="" class=""> <property name="province" value="河北"></property> </bean>
		// <property>标签的属性值
		PropertyValues pvs = mbd.getPropertyValues();
		// 获取bw的所有属性描述对象
		/**
		 * PropertyDescriptor：表示JavaBean类通过存储器导出一个属性
		 * ⚠️PropertyDescriptor：属性描述对象，里面存储的是属性描述信息 —— 每一个属性都有自己的一个描述信息的对象
		 */
		PropertyDescriptor[] pds = bw.getPropertyDescriptors();
		// 遍历属性描述对象
		for (PropertyDescriptor pd : pds) {
			// 如果pd有写入属性的set方法 && 该pd不是被排除在依赖项检查之外 && pvs没有该pd的属性名 && pd的属性类型不是"简单值类型"
			// pd = GenericTypeAwarePropertyDescriptor
			if (pd.getWriteMethod() != null/* 判断有没有set方法（要想赋值的还肯定要有set方法，所以这里做一个基本的判断） */
					// 当前的这个PropertyDescriptor有没有被排除在外(这里指定好忽略的一些东西)
					&& !isExcludedFromDependencyCheck/* 被排除在依赖检查之外 */(pd)
					&& !pvs.contains(pd.getName())
					// ⚠️最基本类型的判断（基本类型、数组、String都会返回true）
					&& !BeanUtils.isSimpleProperty/* 是简单属性 */(pd.getPropertyType()/* 获取一个类型 */)) {
				// 将pdd的属性名添加到result中
				result.add(pd.getName());
			}
		}

		// 将result装换成数组
		return StringUtils.toStringArray(result);
	}

	/**
	 * 过滤出需要依赖检查的属性
	 *
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @param cache whether to cache filtered PropertyDescriptors for the given bean Class
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 * @see #filterPropertyDescriptorsForDependencyCheck(org.springframework.beans.BeanWrapper)
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw, boolean cache) {
		PropertyDescriptor[] filtered = this.filteredPropertyDescriptorsCache.get(bw.getWrappedClass());
		if (filtered == null) {
			filtered = filterPropertyDescriptorsForDependencyCheck(bw);
			if (cache) {
				PropertyDescriptor[] existing =
						this.filteredPropertyDescriptorsCache.putIfAbsent(bw.getWrappedClass(), filtered);
				if (existing != null) {
					filtered = existing;
				}
			}
		}
		return filtered;
	}

	/**
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw) {
		List<PropertyDescriptor> pds = new ArrayList<>(Arrays.asList(bw.getPropertyDescriptors()));
		pds.removeIf(this::isExcludedFromDependencyCheck);
		return pds.toArray(new PropertyDescriptor[0]);
	}

	/**
	 * Determine whether the given bean property is excluded from dependency checks.
	 * <p>This implementation excludes properties defined by CGLIB and
	 * properties whose type matches an ignored dependency type or which
	 * are defined by an ignored dependency interface.
	 * @param pd the PropertyDescriptor of the bean property
	 * @return whether the bean property is excluded
	 * @see #ignoreDependencyType(Class)
	 * @see #ignoreDependencyInterface(Class)
	 */
	protected boolean isExcludedFromDependencyCheck(PropertyDescriptor pd) {
		// pd的属性是CGLIB定义的属性 || 该工厂的忽略依赖类型列表中包含该pd的属性类型 || pd的属性是ignoredDependencyInterfaces里面的接口定义的方法
		return (AutowireUtils.isExcludedFromDependencyCheck(pd) ||
				this.ignoredDependencyTypes.contains(pd.getPropertyType()) ||
				AutowireUtils.isSetterDefinedInInterface(pd, this.ignoredDependencyInterfaces));
	}

	/**
	 * Perform a dependency check that all properties exposed have been set,
	 * if desired. Dependency checks can be objects (collaborating beans),
	 * simple (primitives and String), or all (both).
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition the bean was created with
	 * @param pds the relevant property descriptors for the target bean
	 * @param pvs the property values to be applied to the bean
	 * @see #isExcludedFromDependencyCheck(java.beans.PropertyDescriptor)
	 */
	protected void checkDependencies(
			String beanName, AbstractBeanDefinition mbd, PropertyDescriptor[] pds, @Nullable PropertyValues pvs)
			throws UnsatisfiedDependencyException {

		int dependencyCheck = mbd.getDependencyCheck();
		for (PropertyDescriptor pd : pds) {
			if (pd.getWriteMethod() != null && (pvs == null || !pvs.contains(pd.getName()))) {
				boolean isSimple = BeanUtils.isSimpleProperty(pd.getPropertyType());
				boolean unsatisfied = (dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_ALL) ||
						(isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_SIMPLE) ||
						(!isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_OBJECTS);
				if (unsatisfied) {
					throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, pd.getName(),
							"Set this property value or disable dependency checking for this bean.");
				}
			}
		}
	}

	/**
	 * Apply the given property values, resolving any runtime references
	 * to other beans in this bean factory. Must use deep copy, so we
	 * don't permanently modify this property.
	 * @param beanName the bean name passed for better exception information
	 * @param mbd the merged bean definition
	 * @param bw the BeanWrapper wrapping the target object
	 * @param pvs the new property values
	 */
	protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
		// 为空，直接返回
		if (pvs.isEmpty()) {
			return;
		}

		// 如果有安全管理器，且bw是BeanWrapperImpl的实例
		if (System.getSecurityManager() != null && bw instanceof BeanWrapperImpl) {
			// 设置bw的安全上下文为工厂的访问控制上下文
			((BeanWrapperImpl) bw).setSecurityContext(getAccessControlContext());
		}

		// MutablePropertyValues：PropertyValues接口的默认实现，允许对属性进行简单操作，并提供构造函数来支持从映射 进行深度复制和构造
		MutablePropertyValues/* 可变属性值 */ mpvs = null;
		// 原始属性列表
		List<PropertyValue> original;

		// 如果pvs是MutablePropertyValues
		if (pvs instanceof MutablePropertyValues) {
			// 类型强制转换
			mpvs = (MutablePropertyValues) pvs;
			// isConverted()：返回该holder是否只包含转换后的值(true)，或者是否仍然需要转换这些值
			// 如果mpvs只包含转换后的值
			/**
			 * 判断mpvs是否需要被转换：
			 * 如果为true，代表之前处理过了，包含转换后的值，直接设置即可
			 * 如果为false，代表之前没有处理过，不包含转换后的值，这些值仍然可能需要转换，就把原始列表里面的值，直接赋给original
			 *
			 * 这块之前没有做过任何处理，结果是false；如果之前处理过了，会标记为true，直接把它进行设置就可以了，而不需要做额外的处理。
			 * 这相当于做了一个短路，如果我之前处理过了，我后面就不需要重复处理了。
			 */
			if (mpvs.isConverted()) { // 如果mpvs中的值已经被转换为对应的类型，那么可以直接设置到beanWapper中
				// Shortcut: use the pre-converted values as-is.
				try {
					// 已完成，直接返回
					bw.setPropertyValues(mpvs);
					return;
				}
				catch (BeansException ex) {
					// 捕捉Bean异常，重新抛出Bean创建异常
					throw new BeanCreationException(
							mbd.getResourceDescription(), beanName, "Error setting property values"/* 错误设置属性值 */, ex);
				}
			}
			// 获取mpvs的PropertyValue列表
			original = mpvs.getPropertyValueList();
		}
		else {
			// 获取pVs的PropertyValue对象数组，并将其转换成列表
			original = Arrays.asList(pvs.getPropertyValues()); // 如果pvs并不是使用mutablePropertyValue封装的类型，那么直接使用原始的属性获取
		}

		// 获取用户自定义的类型转换器
		TypeConverter converter = getCustomTypeConverter();
		// 如果转换器为空，则直接把包装类赋值给converter
		if (converter == null) {
			/**
			 * 为什么把bw给converter？
			 * 因为BeanWrapperImpl实现了TypeConverter接口，所以可以直接赋值，
			 * 并且会使用当前spring定好的，自动提供给我们的类型转换器，而不需要我们自己操作了
			 */
			converter = bw;
		}
		// BeanDefinitionValueResolver：在bean工厂实现中使用Helper类，它将bd对象中包含的值解析为应用于 目标bean实例的实际值
		// 题外：bd里面包含的value值是什么类型？不清楚，所以需要进行分析处理（判断是什么类型，根据当前具体的属性类型进行具体的对应处理）
		BeanDefinitionValueResolver/* bean定义信息的值处理器 */ valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter); // 获取对应的解析器

		// Create a deep copy, resolving any references for values. —— 创建一个深层副本，解析任何值的引用。
		// 创建一个深拷贝，解析任何值引用
		/**
		 * 之所以采用深拷贝是因为：
		 * 想着，后面的属性值不管是否进行类型转换，不管怎么变动，都不会影响到bd里面原来解析到的属性值
		 * 因为在整个spring里面，一个bean对象可能创建1个，也可能创建多个，这个时候就要区分开，
		 * 比如我之前对象创建完了，创建完之后，我把属性值修改了，会影响后续对象的创建。
		 * 所以不能这么干，用了一个深拷贝的方式
		 */
		List<PropertyValue> deepCopy = new ArrayList<>(original.size()); // 为解析的值创建一个副本，副本的数据将会被注入到bean中
		// 是否还需要解析标记
		boolean resolveNecessary = false;
		// 遍历属性，将属性转换为对应类的对应属性的类型

		/**
		 * 对值的处理工作
		 * 给我们PropertyValue里面，任何一种数据类型做匹配，
		 * 如果匹配成功了，就把对象属性的值都准备好，放到深拷贝对象里面，
		 * 目前还未填充到属性里面
		 */
		for (PropertyValue pv : original) { // ⚠️遍历属性，将属性转换为对应类型的对应属性的类型
			// 如果该属性已经解析过（是否已经转换过）
			if (pv.isConverted()) {
				// 已经转换过，就直接赋值
				deepCopy.add(pv);
			}
			// 如果属性没有被解析过（没有被转换过），就要经过下面的处理
			else {
				// 获取属性的名字
				String propertyName = pv.getName(); // 属性名称，例如：address
				// 获取未经类型转换的属性值
				/**
				 * RuntimeBeanReference：运行时的bean引用：也就是说，它不确定它具体是什么样的类型，所以在后面判断的时候，我需要根据我运行时输入的值，来判断一下说，
				 * 我到底是否需要创建当前的引用类型对象
				 */
				Object originalValue = pv.getValue(); // 原始属性值，例如：河北省_邯郸市_武安市
				// AutowiredPropertyMarker.INSTANCE：自动生成标记的规范实例
				/**
				 * 题外：AutowiredPropertyMarker：
				 * 对于独立的属性value值，我可以把它转换成AutowiredPropertyMarker对象，由当前的AutowiredPropertyMarker进行相关的属性值处理过程，就是一个包装的东西
				 */
				if (originalValue == AutowiredPropertyMarker.INSTANCE) {
					// 获取propertyName在bw中的setter方法
					Method writeMethod = bw.getPropertyDescriptor(propertyName).getWriteMethod();
					// 如果setter方法为null
					if (writeMethod == null) {
						// 抛出非法参数异常：自动装配标记属性没有写方法
						throw new IllegalArgumentException("Autowire marker for property without write method: " + pv);
					}
					// 将writerMethod封装到DependencyDescriptor对象
					originalValue = new DependencyDescriptor(new MethodParameter(writeMethod, 0), true);
				}


				/**
				 * 1、题外：里面会调用类型转换器，类型转换器会调用属性编辑器，对属性进行编辑！
				 */
				// ⚠️完成属性值的解析工作（里面判断归属于哪个类型，按照不同的类型来进行相关的解析工作）
				// 交由valueResolver，根据pv解析出originalValue所封装的对象
				// 原始属性值，resolvedValue = 河北省_邯郸市_武安市
				Object resolvedValue/* 解析到的值 */ = valueResolver.resolveValueIfNecessary(pv, originalValue);


				// 默认转换后的值是刚解析出来的值
				Object convertedValue = resolvedValue;
				// 可转换标记：propertyName是否bw中的可写属性 && propertyName不是表示索引属性或嵌套属性（如果propertyName中有'.'||'['就认为是索引属性或嵌套属性）
				boolean convertible = bw.isWritableProperty(propertyName) &&
						!PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName);
				// 如果可转换
				// 判断是否需要被进行类型转换
				if (convertible) {
					// ⚠️转换对应的属性值！—— 这里开始调用自定义编辑器进行属性转换！
					// 将resolvedValue转换为指定的目标属性对象
					convertedValue = convertForProperty(resolvedValue/* 河北省_邯郸市_武安市 */, propertyName/* address */, bw, converter);
				}
				// Possibly store converted value in merged bean definition,
				// in order to avoid re-conversion for every created bean instance.
				// 上面的翻译：可能将转换后的值存储在合并的 bean 定义中，以避免对每个创建的 bean 实例进行重新转换。
				// 如果resolvedValue与originalValue是同一个对象
				// 判断当前处理的值是否等于原始的值
				if (resolvedValue == originalValue) {
					// 如果可转换
					if (convertible) {
						// 将convertedValue设置到pv中
						pv.setConvertedValue(convertedValue);
					}
					// 将pv添加到deepCopy中
					/**
					 * ⚠️把所有的结果集全部放入深拷贝对象，之后对这个结果集进行整体的处理，就不需要来一个属性设置一次，来一个属性设置一次了，而是到后面进行统一处理
					 */
					deepCopy.add(pv);
				}
				// TypedStringValue：类型字符串的Holder，这个holder将只存储字符串值和目标类型。实际的转换由bean工厂执行
				else if (convertible && originalValue instanceof TypedStringValue &&
						!((TypedStringValue) originalValue).isDynamic() &&
						!(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
					// ⚠️设置转换的值
					pv.setConvertedValue(convertedValue);
					deepCopy.add(pv);
				}
				else {
					// 标记还需要解析
					resolveNecessary = true;
					// 根据pv,convertedValue构建PropertyValue对象，并添加到deepCopy中
					deepCopy.add(new PropertyValue(pv, convertedValue));
				}
			}
		}
		// mpvs不为null && 已经不需要解析
		if (mpvs != null && !resolveNecessary) {
			// 将此holder标记为只包含转换后的值
			// 设置类型转换标识，说，我是否已经转换过了
			mpvs.setConverted();
		}

		/* 赋值 */

		// Set our (possibly massaged) deep copy. —— 设置我们的（可能是经过按摩的）深拷贝。
		try {
			// ⚠️给bean对象设置属性值
			// 按原样使用deepCopy构造一个新的MutablePropertyValues对象，然后设置到bw中，以对bw的属性值更新
			bw.setPropertyValues(new MutablePropertyValues(deepCopy));
		}
		catch (BeansException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Error setting property values", ex);
		}
	}

	/**
	 * Convert the given value for the specified target property.
	 */
	@Nullable
	private Object convertForProperty(
			@Nullable Object value, String propertyName, BeanWrapper bw, TypeConverter converter) {

		if (converter instanceof BeanWrapperImpl) {
			// ⚠️转换对应的属性值！
			// 河北省_邯郸市_武安市 => Address{province='河北省', city='邯郸市', area='武安市'}
			return ((BeanWrapperImpl) converter).convertForProperty(value, propertyName);
		}
		else {
			PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
			MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
			return converter.convertIfNecessary(value, pd.getPropertyType(), methodParam);
		}
	}


	/**
	 * Initialize the given bean instance, applying factory callbacks
	 * as well as init methods and bean post processors.
	 * <p>Called from {@link #createBean} for traditionally defined beans,
	 * and from {@link #initializeBean} for existing bean instances.
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @return the initialized bean instance (potentially wrapped)
	 * @see BeanNameAware
	 * @see BeanClassLoaderAware
	 * @see BeanFactoryAware
	 * @see #applyBeanPostProcessorsBeforeInitialization
	 * @see #invokeInitMethods
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	protected Object initializeBean/* 初始化 Bean */(String beanName, Object bean, @Nullable RootBeanDefinition mbd) {
		/*

		1、执行Aware接口对应的方法

		⚠️如果bean实现了某些aware接口，就会进行对应的属性设置工作

		*/
		// 如果安全管理器不为空
		if (System.getSecurityManager() != null) {
			// 以特权的方式执行回调bean中的Aware接口方法
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				invokeAwareMethods(beanName, bean);
				return null;
			}, getAccessControlContext());
		}
		else {
			// Aware接口处理器，调用BeanNameAware、BeanClassLoaderAware、BeanFactoryAware
			invokeAwareMethods(beanName, bean);
		}

		/*

		2、执行所有的BeanPostProcessors#postProcessBeforeInitialization()。简称bean初始化的before方法

		总结：调用bean初始化的前置处理器

		*/
		Object wrappedBean = bean;
		/**
		 * synthetic：一般是指只有AOP相关的prointCut配置或者Advice配置才会将synthetic设置为true
		 * 也就是说，当这个对象需要被AOP进行代理的时候，synthetic属性才会为true，如果不是的话，不需要等于true
		 */
		// 如果mbd不为null || mbd不是"synthetic"
		if (mbd == null || !mbd.isSynthetic()) {
			/**
			 * 💡提示：🎈@PostConstruct修饰的初始化方法在这里面进行了调用
			 * >>> 由CommonAnnotationBeanPostProcessor#postProcessBeforeInitialization()里面
			 * >>> 执行了CommonAnnotationBeanPostProcessor#postProcessMergedBeanDefinition()里面解析出的标注了@PostConstruct的方法
			 * >>> 也就是执行了初始化方法
			 *
			 * 2、ApplicationContextAwareProcessor：对一堆Aware接口的处理！
			 */
			// 调用bean前置处理器
			// 返回的bean实例可能是原始Bean包装器
			wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
		}

		/* 3、执行InitializingBean#afterPropertiesSet()，和xml中的innit-method */
		try {
			// 调用初始化方法
			// 1、先调用InitializingBean+afterPropertiesSet()；
			// 2、后调用bean的自定义初始化方法 - 也就是xml中的innit-method（用户自定义的init方法）
			invokeInitMethods(beanName, wrappedBean, mbd);
		}
		catch (Throwable ex) {
			// 捕捉调用初始化方法时抛出的异常，重新抛出Bean创建异常
			throw new BeanCreationException(
					(mbd != null ? mbd.getResourceDescription() : null),
					beanName, "Invocation of init method failed"/* 调用初始化方法失败 */, ex);
		}

		/*

		4、️执行所有的BeanPostProcessors#postProcessAfterInitialization()。简称after方法

		总结：调用bean后置处理器

		主要实现的功能：AOP

		*/
		// 如果mbd为null || mbd不是"synthetic"
		if (mbd == null || !mbd.isSynthetic()) {
			/**
			 * 1、AOP：
			 * 注解方式：AnnotationAwareAspectJAutoProxyCreator
			 * xml方式：AspectJAwareAdvisorAutoProxyCreator
			 * 题外：如果是需要被AOP代理，执行完之后，wrappedBean变成了代理对象
			 */
			// 返回的Bean实例可能是原始Bean包装器
			wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
		}

		return wrappedBean;
	}

	/**
	 * 回调bean中Aware接口方法
	 *
	 * 三个扩展点：
	 * BeanNameAware+setBeanName()
	 * BeanClassLoaderAware+setBeanClassLoader()
	 * BeanFactoryAware+setBeanFactory()
	 * @param beanName
	 * @param bean
	 */
	private void invokeAwareMethods(String beanName, Object bean) {
		// 如果bean是Aware实例
		if (bean instanceof Aware) {
			if (bean instanceof BeanNameAware) {
				((BeanNameAware) bean).setBeanName(beanName);
			}
			if (bean instanceof BeanClassLoaderAware) {
				// 获取此工厂的类加载器以加载Bean类（即使无法使用系统ClassLoader，也只能为null）
				ClassLoader bcl = getBeanClassLoader();
				if (bcl != null) {
					((BeanClassLoaderAware) bean).setBeanClassLoader(bcl);
				}
			}
			if (bean instanceof BeanFactoryAware) {
				((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
			}
		}
	}

	/**
	 * Give a bean a chance to react now all its properties are set,
	 * and a chance to know about its owning bean factory (this object).
	 * This means checking whether the bean implements InitializingBean or defines
	 * a custom init method, and invoking the necessary callback(s) if it does.
	 * 现在设置了所有属性，使bean有机会做出反应，并有机会了解其拥有的bean工厂（此对象）。
	 * 这意味着检查bean是否实现InitializingBean或定义了一个自定义init方法，如果是，则调用必要的回调。
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the merged bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @throws Throwable if thrown by init methods or by the invocation process
	 * @see #invokeCustomInitMethod
	 */
	protected void invokeInitMethods(String beanName, Object bean, @Nullable RootBeanDefinition mbd)
			throws Throwable {
		/*

		1、执行InitializingBean#afterPropertiesSet()

		⚠️afterPropertiesSet()方法是在当前bean对象里面的，既然在bean对象里面，对象里面的所有属性都可以操作。
		一般用于做最后属性的验证和最后一次修改我们的属性值。

		*/

		/**
		 * InitializingBean：当Bean的所有属性都被BeanFactory设置好后，Bean需要执行相应的接口：例如执行自定义初始化，或者仅仅是检查所有强制属性是否已经设置好
		 */
		// bean是InitializingBean实例标记（判断是否实现了InitializingBean接口）
		boolean isInitializingBean = (bean instanceof InitializingBean);
		/**
		 * isExternallyManagedInitMethod()：是否外部受管理的Init方法名
		 */
		// ⚠️如果当前bean是InitializingBean实例 && (mbd为null || 'afterPropertiesSet'不是外部受管理的Init方法名)
		if (isInitializingBean && (mbd == null || !mbd.isExternallyManagedInitMethod/* 是外部管理的初始化方法 */("afterPropertiesSet"))) {
			// 如果是日志级别为跟踪模式
			if (logger.isTraceEnabled()) {
				logger.trace("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
			}
			// 如果安全贯流去不为null
			if (System.getSecurityManager() != null) {
				try {
					// 以特权方式调用bean的afterPropertiesSet()
					AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
						// ⚠️
						((InitializingBean) bean).afterPropertiesSet();
						return null;
					}, getAccessControlContext());
				}
				catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			}
			else {
				// ⚠️
				((InitializingBean) bean).afterPropertiesSet();
			}
		}

		/*

		2、调用<bean id="" init-method="">中init-method属性指定的初始化方法

		 */

		// 如果mbd不为null && bean不是NullBean类
		if (mbd != null && bean.getClass() != NullBean.class) {
			// 获取mbd指定的初始化方法名称 —— 也即是<bean id="" init-method="">中init-method属性指定的初始化方法
			String initMethodName = mbd.getInitMethodName();
			// 以下两个条件中的一个成立即可：
			// 1、如果存在初始化方法 && bean不是InitializingBean实例 && 初始化方法不是外部受管理的Init方法名
			// 2、如果存在初始化方法 && bean是InitializingBean实例 && 初始化方法不是afterPropertiesSet() && 初始化方法不是外部受管理的Init方法名
			if (StringUtils.hasLength(initMethodName) &&
					!(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
					!mbd.isExternallyManagedInitMethod(initMethodName)) {
				// ⚠️调用<bean id="" init-method="">中init-method属性指定的初始化方法
				// 在bean上调用指定的自定义init方法
				invokeCustomInitMethod(beanName, bean, mbd);
			}
		}
	}

	/**
	 * 获取bean的自定义初始化方法，如果自身或者父类是接口类型的话，就反射出接口方法来，最后调用
	 *
	 * Invoke the specified custom init method on the given bean.
	 * Called by invokeInitMethods.
	 * 在给定的bean上调用指定的自定义init方法。由invokeInitMethods调用
	 * <p>Can be overridden in subclasses for custom resolution of init
	 * methods with arguments.
	 * 可以在子类中覆盖<p>以使用参数自定义解析init方法。
	 * @see #invokeInitMethods
	 */
	protected void invokeCustomInitMethod(String beanName, Object bean, RootBeanDefinition mbd)
			throws Throwable {

		// 获取初始化方法名称
		String initMethodName = mbd.getInitMethodName();
		Assert.state(initMethodName != null, "No init method set");
		// 获取初始化方法
		Method initMethod = (mbd.isNonPublicAccessAllowed() ?
				BeanUtils.findMethod(bean.getClass(), initMethodName) :
				ClassUtils.getMethodIfAvailable(bean.getClass(), initMethodName));

		if (initMethod == null) {
			if (mbd.isEnforceInitMethod()) {
				throw new BeanDefinitionValidationException("Could not find an init method named '" +
						initMethodName + "' on bean with name '" + beanName + "'");
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("No default init method named '" + initMethodName +
							"' found on bean with name '" + beanName + "'");
				}
				// Ignore non-existent default lifecycle methods.
				return;
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Invoking init method  '" + initMethodName + "' on bean with name '" + beanName + "'");
		}
		Method methodToInvoke = ClassUtils.getInterfaceMethodIfPossible(initMethod);

		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				ReflectionUtils.makeAccessible(methodToInvoke);
				return null;
			});
			try {
				AccessController.doPrivileged((PrivilegedExceptionAction<Object>)
						() -> methodToInvoke.invoke(bean), getAccessControlContext());
			}
			catch (PrivilegedActionException pae) {
				InvocationTargetException ex = (InvocationTargetException) pae.getException();
				throw ex.getTargetException();
			}
		}
		else {
			try {
				ReflectionUtils.makeAccessible(methodToInvoke);
				// ⚠️xml init()
				// 反射执行(通过反射方式来调用自定义的初始化方法)
				methodToInvoke.invoke(bean);
			}
			catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}


	/**
	 * Applies the {@code postProcessAfterInitialization} callback of all
	 * registered BeanPostProcessors, giving them a chance to post-process the
	 * object obtained from FactoryBeans (for example, to auto-proxy them).
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	@Override
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) {
		return applyBeanPostProcessorsAfterInitialization(object, beanName);
	}

	/**
	 * Overridden to clear FactoryBean instance cache as well.
	 */
	@Override
	protected void removeSingleton(String beanName) {
		synchronized (getSingletonMutex()) {
			super.removeSingleton(beanName);
			this.factoryBeanInstanceCache.remove(beanName);
		}
	}

	/**
	 * Overridden to clear FactoryBean instance cache as well.
	 */
	@Override
	protected void clearSingletonCache() {
		synchronized (getSingletonMutex()) {
			super.clearSingletonCache();
			this.factoryBeanInstanceCache.clear();
		}
	}

	/**
	 * Expose the logger to collaborating delegates.
	 * @since 5.0.7
	 */
	Log getLogger() {
		return logger;
	}


	/**
	 * Special DependencyDescriptor variant for Spring's good old autowire="byType" mode.
	 * Always optional; never considering the parameter name for choosing a primary candidate.
	 */
	@SuppressWarnings("serial")
	private static class AutowireByTypeDependencyDescriptor extends DependencyDescriptor {

		public AutowireByTypeDependencyDescriptor(MethodParameter methodParameter, boolean eager) {
			super(methodParameter, false, eager);
		}

		@Override
		public String getDependencyName() {
			return null;
		}
	}


	/**
	 * {@link MethodCallback} used to find {@link FactoryBean} type information.
	 */
	private static class FactoryBeanMethodTypeFinder implements MethodCallback {

		private final String factoryMethodName;

		private ResolvableType result = ResolvableType.NONE;

		FactoryBeanMethodTypeFinder(String factoryMethodName) {
			this.factoryMethodName = factoryMethodName;
		}

		@Override
		public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
			if (isFactoryBeanMethod(method)) {
				ResolvableType returnType = ResolvableType.forMethodReturnType(method);
				ResolvableType candidate = returnType.as(FactoryBean.class).getGeneric();
				if (this.result == ResolvableType.NONE) {
					this.result = candidate;
				}
				else {
					Class<?> resolvedResult = this.result.resolve();
					Class<?> commonAncestor = ClassUtils.determineCommonAncestor(candidate.resolve(), resolvedResult);
					if (!ObjectUtils.nullSafeEquals(resolvedResult, commonAncestor)) {
						this.result = ResolvableType.forClass(commonAncestor);
					}
				}
			}
		}

		private boolean isFactoryBeanMethod(Method method) {
			return (method.getName().equals(this.factoryMethodName) &&
					FactoryBean.class.isAssignableFrom(method.getReturnType()));
		}

		ResolvableType getResult() {
			Class<?> resolved = this.result.resolve();
			boolean foundResult = resolved != null && resolved != Object.class;
			return (foundResult ? this.result : ResolvableType.NONE);
		}
	}

}
