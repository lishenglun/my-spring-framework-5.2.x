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

import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.*;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.log.LogMessage;
import org.springframework.lang.Nullable;
import org.springframework.util.*;

import java.beans.PropertyEditor;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Abstract base class for {@link org.springframework.beans.factory.BeanFactory}
 * implementations, providing the full capabilities of the
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory} SPI.
 * Does <i>not</i> assume a listable bean factory: can therefore also be used
 * as base class for bean factory implementations which obtain bean definitions
 * from some backend resource (where bean definition access is an expensive operation).
 *
 * <p>This class provides a singleton cache (through its base class
 * {@link org.springframework.beans.factory.support.DefaultSingletonBeanRegistry},
 * singleton/prototype determination, {@link org.springframework.beans.factory.FactoryBean}
 * handling, aliases, bean definition merging for child bean definitions,
 * and bean destruction ({@link org.springframework.beans.factory.DisposableBean}
 * interface, custom destroy methods). Furthermore, it can manage a bean factory
 * hierarchy (delegating to the parent in case of an unknown bean), through implementing
 * the {@link org.springframework.beans.factory.HierarchicalBeanFactory} interface.
 *
 * <p>The main template methods to be implemented by subclasses are
 * {@link #getBeanDefinition} and {@link #createBean}, retrieving a bean definition
 * for a given bean name and creating a bean instance for a given bean definition,
 * respectively. Default implementations of those operations can be found in
 * {@link DefaultListableBeanFactory} and {@link AbstractAutowireCapableBeanFactory}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Chris Beams
 * @author Phillip Webb
 * @since 15 April 2001
 * @see #getBeanDefinition
 * @see #createBean
 * @see AbstractAutowireCapableBeanFactory#createBean
 * @see DefaultListableBeanFactory#getBeanDefinition
 */
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {

	/** Parent bean factory, for bean inheritance support. 父bean工厂，用于bean继承支持。 */
	@Nullable
	private BeanFactory parentBeanFactory;

	/** ClassLoader to resolve bean class names with, if necessary. */
	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	/** ClassLoader to temporarily resolve bean class names with, if necessary. */
	@Nullable
	private ClassLoader tempClassLoader;

	/** Whether to cache bean metadata or rather reobtain it for every access. */
	private boolean cacheBeanMetadata = true;

	/** Resolution strategy for expressions in bean definition values. */
	@Nullable
	private BeanExpressionResolver beanExpressionResolver;

	/** Spring ConversionService to use instead of PropertyEditors. */
	// 类型转换服务
	@Nullable
	private ConversionService conversionService;

	// 自定义属性编辑器的注册器集合
	/**
	 * 1、添加处：{@link CustomEditorConfigurer#postProcessBeanFactory(ConfigurableListableBeanFactory)}}
	 * Custom PropertyEditorRegistrars to apply to the beans of this factory.
	 * */
	private final Set<PropertyEditorRegistrar> propertyEditorRegistrars = new LinkedHashSet<>(4);

	// 自定义属性编辑器集合(Map<属性类型，属性编辑器>)
	/**
	 * 1、添加处：{@link CustomEditorConfigurer#postProcessBeanFactory(ConfigurableListableBeanFactory)}}
	 * Custom PropertyEditors to apply to the beans of this factory.
	 * */
	private final Map<Class<?>, Class<? extends PropertyEditor>> customEditors = new HashMap<>(4);

	/** A custom TypeConverter to use, overriding the default PropertyEditor mechanism. */
	@Nullable
	private TypeConverter typeConverter;

	/** String resolvers to apply e.g. to annotation attribute values. —— 要应用的字符串解析器，例如注释属性值。 */
	// 字符串值解析器：解析给定的字符串值，例如解析占位符
	private final List<StringValueResolver> embeddedValueResolvers = new CopyOnWriteArrayList<>();

	/**
	 * BeanPostProcessors to apply.
	 * ⚠️：要应用的BeanPostProcessors，BeanPostProcessor可以插手bean的实例化过程
	 * */
	// BeanPostProcessor集合
	private final List<BeanPostProcessor> beanPostProcessors = new CopyOnWriteArrayList<>();

	/** Indicates whether any InstantiationAwareBeanPostProcessors have been registered. —— 指示是否已注册任何InstantiationAwareBeanPostProcessor。 */
	// 如果有注册InstantiationAwareBeanPostProcessor bean，那么就为true；否则为false
	private volatile boolean hasInstantiationAwareBeanPostProcessors;

	/**
	 * 表明DestructionAwareBeanPostProcessors是否被注册
	 *
	 * Indicates whether any DestructionAwareBeanPostProcessors have been registered. */
	private volatile boolean hasDestructionAwareBeanPostProcessors;

	/**
	 * key：从作用域表示符String
	 * value：相应的作用域
	 *
	 * Map from scope identifier String to corresponding Scope. */
	private final Map<String, Scope> scopes = new LinkedHashMap<>(8);

	/**
	 * 与SecurityManager一起运行时使用的安全上下文
	 *
	 * Security context used when running with a SecurityManager. */
	@Nullable
	private SecurityContextProvider securityContextProvider;

	/**
	 * key：bean名称
	 * value：合并后的bd：RootBeanDefinition
	 *
	 * Map from bean name to merged RootBeanDefinition. */
	// 合并后的bd存放处：RootBeanDefinition
	private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);

	/**
	 * 至少创建一次的bean的名称
	 *
	 * Names of beans that have already been created at least once. —— 已至少创建一次的bean的名称。 */
	// 已经创建了的集合
	private final Set<String> alreadyCreated = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

	/** Names of beans that are currently in creation. */
	private final ThreadLocal<Object> prototypesCurrentlyInCreation =
			new NamedThreadLocal<>("Prototype beans currently in creation");


	/**
	 * Create a new AbstractBeanFactory.
	 */
	public AbstractBeanFactory() {
	}

	/**
	 * Create a new AbstractBeanFactory with the given parent.
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 * @see #getBean
	 */
	public AbstractBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this.parentBeanFactory = parentBeanFactory;
	}


	//---------------------------------------------------------------------
	// Implementation of BeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {
		return doGetBean(name, null, null, false);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		return doGetBean(name, requiredType, null, false);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		return doGetBean(name, null, args, false);
	}

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * @param name the name of the bean to retrieve
	 * @param requiredType the required type of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	public <T> T getBean(String name, @Nullable Class<T> requiredType, @Nullable Object... args)
			throws BeansException {
		// 此方法是实际获取bean的方法，也是触发依赖注入的方法
		return doGetBean(name, requiredType, args, false);
	}

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * @param name the name of the bean to retrieve
	 * @param requiredType the required type of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @param typeCheckOnly whether the instance is obtained for a type check,
	 * not for actual use
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	@SuppressWarnings("unchecked")
	protected <T> T doGetBean(
			String name, @Nullable Class<T> requiredType, @Nullable Object[] args, boolean typeCheckOnly)
			throws BeansException {

		/*

		1、去掉&
		name：是原始的beanName
		beanName：是去掉&的beanName

		*/

		// ⚠️transformedBeanName()：去掉&
		// 提取对应的beanName，很多同学可能会认为此处直接使用即可，为什么还要进行转换呢，原因在于bean对象实现FactoryBean接口之后就会变成&beanName，同时如果存在别名，
		String beanName = transformedBeanName(name);
		Object bean;

		/* 2、从一级缓存(容器)中获取beanName对应的bean对象 */

		/**
		 * ⚠️1：获取bean，从以下三个map当中获取
		 * 		singletonObjects
		 * 		earlySingletonObjects
		 * 		singletonFactories
		 *
		 * 初始化的时候，这里一定为null；
		 * 但是在context.getBean(MerchantInfo.class);也会走这个方法，就不为null，所以这里要做这样的判断
		 */
		// Eagerly check singleton cache for manually registered singletons. - 认真检查单例缓存中是否有手动注册的单例。
		// 提前检查单例缓存中是否有手动注册的单例对象，跟循环依赖有关系
		// 从当前容器中判断，是否有当前要实例化的bean对象
		Object sharedInstance = getSingleton(beanName);
		if (sharedInstance != null && args == null) {
			/* 2.1、如果容器中存在bean对象，那么就对bean对象做FactoryBean的处理，看是直接返回bean对象，还是返回Factory#getObject()对象 */

			if (logger.isTraceEnabled()) {
				/**
				 * isSingletonCurrentlyInCreation内部：this.singletonsCurrentlyInCreation.contains(beanName);
				 * 判断beanName是否是正在创建的beanName
				 */
				if (isSingletonCurrentlyInCreation(beanName) ) {
					logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
							"' that is not fully initialized yet - a consequence of a circular reference");
				}
				else {
					logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
				}
			}

			// 返回具体的对象实例。也就是：判断到底是返回哪个bean，是普通bean，还是FactoryBean，还是FactoryBean#getObject()
			// 总结：
			// 1、name是&开头，且name对应的bean是FactoryBean，就返回该FactoryBean
			// 2、name是&开头，但是name对应的bean不是FactoryBean，就抛出一个错误
			// 3、name不是&开头，且name对应的bean也不是FactoryBean，就返回该普通bean
			// 4、name不是&开头，但是name对应的bean是FactoryBean，就返回FactoryBean#getObject()
			// 题外：当实现了FactoryBean接口的对象，需要获取具体的对象的时候就需要此方法来进行获取了
			bean = getObjectForBeanInstance/* 获取Bean实例的对象 */(sharedInstance, name/* 原始beanName */, beanName/* 去掉&的beanName */, null);
		}
		/* 2.2、如果容器中不存在bean对象，那么就创建bean对象 */
		else {
			/* 2.2.1、⚠️当对象都是单例的时候会尝试解决循环依赖的问题，但是原型模式下如果存在循环依赖的情况，那么直接抛出异常 */
			// Fail if we're already creating this bean instance:
			// We're assumably within a circular reference.
			// 上面的翻译：如果我们已经在创建这个 bean 实例，则失败：我们大概在一个循环引用中。

			// 判断当前对象是不是正在被创建过程中
			// ⚠️当对象都是单例的时候会尝试解决循环依赖的问题，但是原型模式下如果存在循环依赖的情况，那么直接抛出异常
			if (isPrototypeCurrentlyInCreation/* 是目前正在创建的原型对象 */(beanName)) {
				// 循环依赖的一个检测：如果现在是原型bean，并且正在创建的过程中
				/**
				 * 如果是原型就不应该在初始化的时候创建，于是抛出异常
				 */
				throw new BeanCurrentlyInCreationException(beanName);
			}

			/* 2.2.2、如果存在父容器，并且当前子容器不包含要获取的beanName对应的bd，就去父容器中寻找对应的bean对象进行返回 */
			/**
			 * getParentBeanFactory()返回的是：父bean工厂，用于bean继承支持。
			 * BeanFactory父子容器的知识，参考：https://www.cnblogs.com/mayang2465/p/12163179.html
			 * 一般不会采用，所以不用管
			 */
			// Check if bean definition exists in this factory. - 检查该工厂中是否存在bean定义。
			// 获取父类容器 —— 当我拥有了父容器，我会去父容器里面查找相应的一些对象
			// 题外：先在当前容器中寻找，也就是先找子。如果子容器没有对应bean对象，那么就去找父容器里面找bean对象，父容器还没有，我就创建
			BeanFactory parentBeanFactory = getParentBeanFactory();
			// 如果beanDefinitionMap中也就是在所有已经加载的类中不包含beanName,那么就尝试从父容器中获取
			if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
				// Not found -> check parent. - 找不到->检查父项。
				String nameToLookup = originalBeanName(name);
				if (parentBeanFactory instanceof AbstractBeanFactory) {
					return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
							nameToLookup, requiredType, args, typeCheckOnly);
				}
				else if (args != null) {
					// Delegation to parent with explicit args. - 使用显式参数委派给父级。
					return (T) parentBeanFactory.getBean(nameToLookup, args);
				}
				else if (requiredType != null) {
					// No args -> delegate to standard getBean method. - 没有参数->委托给标准的getBean方法。
					return parentBeanFactory.getBean(nameToLookup, requiredType);
				}
				else {
					return (T) parentBeanFactory.getBean(nameToLookup);
				}
			}

			/*

			2.2.3、将beanName放入已经创建的集合(alreadyCreated)中做一个记录，标记当前bean为已创建，意味着当前bean要被创建了！

			⚠️这个已经创建的集合，️不是正在创建过程中的集合，不要搞混淆了！

			*/

			// 如果不是，做类型检查，那么表示要创建bean,此处在集合中做一个记录
			if (!typeCheckOnly/* 只检查类型 */) {
				// ⚠️第一次创建，将beanName放入alreadyCreated集合中做一个记录，标记当前bean为已创建，意味着当前bean要被创建了！
				markBeanAsCreated(beanName);
			}

			try {

				/*

				2.2.4、根据beanName获取完整的bd：RootBeanDefinition

				RootBeanDefinition是合并后的bd，为什么要合并呢？
				因为我们从配置文件读取过来的bd是GenericBeanDefinition、以注解方式读取过来的bd是ScannedGenericBeanDefinition，
				这些都是最基础的bd，是当前类自身的bd，不包含父类的一些信息；但是在实际创建bean的时候，是需要包含父类信息的；
				所以需要合并父类信息成为一个完整的bd，也就是成为一个RootBeanDefinition
				（如果有父类db，那么就会进行合并成为一个新的bd；没有的话就不需要合并，但最终返回的都是RootBeanDefinition）

				⚠️题外：这里面会判断，容器当中是否存在beanName的bd，如果不存在则会报错！

				*/

				// ⚠️获取合并的本地Bean定义
				// 题外：如果我们在getBean()一个对象的时候，容器中不存在对应的bd，就会报错；如果有对应的bd，就会创建对应的bean对象

				// 此处做了BeanDefinition对象的转换，当我们从xml文件中加载beanDefinition对象的时候，封装的对象是GenericBeanDefinition,
				// 此处要做类型转换，如果是子类bean的话，会合并父类的相关属性；根据beanName来获取到它的一个完整的描述信息
				// ⚠️我们在实际创建的时候，都要获取到RootBeanDefinition
				RootBeanDefinition mbd = getMergedLocalBeanDefinition/* 获取合并的本地bd */(beanName);
				// 检查BeanDefinition
				checkMergedBeanDefinition(mbd, beanName, args);

				/* 2.2.5、如果存在依赖的bean的话，那么则优先实例化依赖的bean */

				// Guarantee initialization of beans that the current bean depends on. - 确保当前bean依赖的bean的初始化。
				// 如果存在依赖的bean的话，那么则优先实例化依赖的bean
				String[] dependsOn = mbd.getDependsOn();
				if (dependsOn != null) {
					// 如果存在依赖，则优先递归实例化依赖的bean
					// <bean depends-on="">依赖的意思，正常情况下，bean的创建顺序没什么关系，但是，当我依赖一个bean的时候，那么就要先创建这个依赖的bean，然后我再根据依赖的bean来对当前bean做相应的处理工作
					// 被依赖的对象需要提前创建
					for (String dep : dependsOn) {
						if (isDependent(beanName, dep)) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
						}
						// 注册各个bean的依赖关系，方便进行销毁
						registerDependentBean(dep, beanName);
						try {
							// 实例化依赖的bean
							getBean(dep);
						}
						catch (NoSuchBeanDefinitionException ex) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
						}
					}
				}

				/* 2.2.6、创建bean的实例对象 */

				// Create bean instance.

				/* 2.2.6.1、单例模式的bean对象创建 */
				// 判断是不是单例的，单例模式的bean对象创建
				if (mbd.isSingleton()) {
					// ⚠️进入这里
					sharedInstance = getSingleton(beanName, () -> {
						try {
							// ⚠️⚠️⚠️️进入这里（注意，这个return是对getSingleton()进行返回！）
							// 由getSingleton()内部调用了「singletonFactory.getObject();」所以再走这里
							return createBean(beanName, mbd, args);
						}
						catch (BeansException ex) {
							// Explicitly remove instance from singleton cache: It might have been put there
							// eagerly by the creation process, to allow for circular reference resolution.
							// Also remove any beans that received a temporary reference to the bean.
							// 上面的翻译：从单例缓存中显式删除实例：它可能已被创建过程急切地放在那里，以允许循环引用解析。还要删除任何接收到对bean的临时引用的bean。

							// 显示地从单例缓存中删除实例，它可能是由创建过程急切地放在那里，以允许循环引用解析，还要删除接收到该Bean临时引用的任何Bean

							// 销毁给定的bean，如果找到对应的一次性Bean实例，则委托给destroyBean
							destroySingleton(beanName);
							// 重新抛出ex
							throw ex;
						}
					});
					// 🎈FactoryBean#getObject()的处理
					// 💡提示：name = &myFactoryBean，获取的是FactoryBean实例，而不是FactoryBean#getObject()

					// 从beanInstance中获取公开的Bean对象，主要处理beanInstance是FactoryBean对象的情况
					// 如果不是FactoryBean会直接返回beanInstance实例
					bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
				}
				/* 2.2.6.2、原型(多例)模式的bean对象创建 */
				// 原型模式的bean对象创建
				else if (mbd.isPrototype()) {
					// It's a prototype -> create a new instance.
					// 这是一个原型 -> 创建一个新实例。

					// 原型实例
					Object prototypeInstance = null;
					try {
						// 创建原型对象前的准备工作，默认实现将beanName添加到prototypesCurrentlyInCreation(目前正在创建的原型)中
						beforePrototypeCreation(beanName);
						// ⚠️
						prototypeInstance = createBean(beanName, mbd, args);
					}
					finally {
						// 创建完原型对象后的调用，默认是将beanName从prototypesCurrentlyInCreation(目前正在创建的原型)移除
						afterPrototypeCreation(beanName);
					}
					// 🎈FactoryBean
					bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
				}
				/* 2.2.6.3、其它scope模式(既不是单例，也不是原型)的bean对象创建 */
				else {
					// 指定的scope上实例化bean
					String scopeName = mbd.getScope();
					if (!StringUtils.hasLength(scopeName)) {

						throw new IllegalStateException("No scope name defined for bean ´" + beanName + "'");
					}
					// 从scopes中获取scopeName对应的Scope对象
					Scope scope = this.scopes.get(scopeName);
					if (scope == null) {
						// 如果scopeName为null，抛出非法状态异常
						throw new IllegalStateException("No Scope registered for scope name '" /* 没有名为'scopeName'的scope注册 */ + scopeName + "'");
					}
					try {
						// 从scope中获取beanName对应的实例对象
						Object scopedInstance = scope.get(beanName, () -> {
							// 创建原型对象前的准备工作，默认实现将beanName添加到prototypesCurrentlyInCreation(目前正在创建的原型)中
							beforePrototypeCreation(beanName);
							try {
								// ⚠️
								return createBean(beanName, mbd, args);
							}
							finally {
								// 创建完原型对象后的调用，默认是将beanName从prototypesCurrentlyInCreation(目前正在创建的原型)移除
								afterPrototypeCreation(beanName);
							}
						});
						// 🎈FactoryBean
						bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
					}
					// 捕捉非法状态异常
					catch (IllegalStateException ex) {
						// 抛出Bean创建异常：
						// 作用域"scopeName"对于当前线程是不活动；如果您打算从单个实例引用它，请考虑为此bd一个作用域代理
						throw new BeanCreationException(beanName,
								"Scope '" + scopeName + "' is not active for the current thread; consider " +
								"defining a scoped proxy for this bean if you intend to refer to it from a singleton",
								ex);
					}
				}
			}
			// 捕捉获取Bean对象抛出的Bean异常
			catch (BeansException ex) {
				// 在Bean创建失败后，对缓存的元数据执行适当的清理
				cleanupAfterBeanCreationFailure(beanName);
				// 重新抛出ex
				throw ex;
			}
		}

		/*

		3、看下我获取/创建好的bean对象，是不是我期望获取的类型的实例(requiredType类型)

		是一样的就直接强转为我需要的类型进行返回！

		不是则获取类型转换器，进行类型转换，将bean转换为我们所期望的类型(requiredType类型)，如果转换后的结果是null，也就是没有转换为我们所期望的结果

		题外：类型转换器底层调用的是属性编辑器，尝试获取期望类型对应的自定义属性编辑器(PropertyEditor#setAsText())，对bean对象进行编辑，编辑转换为我们所想要的类型

		*/

		// 判断我当前获取/创建到的bean对象，跟我需要的类型，是否一致

		// Check if required type matches the type of the actual bean instance. —— 检查所需类型是否与实际bean实例的类型匹配。
		// 检查需要的类型是否符合bean的实际类型

		// 如果requiredType不为null && bean不是requiredType的实例
		if (requiredType/* 所需类型 */ != null && !requiredType.isInstance(bean)/* bean是不是requiredType的实例 */) {
			try {
				/**
				 * 默认为：SimpleTypeConverter#convertIfNecessary()
				 *
				 * SimpleTypeConverter：简单的类型转换器
				 */
				// 获取类型转换器，进行类型转换，将bean转换为requiredType类型
				T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
				// 如果convertedBean为null
				if (convertedBean == null) {
					// 抛出Bean不是必要类型的异常
					throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
				}
				return convertedBean;
			}
			catch (TypeMismatchException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Failed to convert bean '" + name + "' to required type '" +
							ClassUtils.getQualifiedName(requiredType) + "'", ex);
				}
				throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
			}
		}
		// 强转成为我们所需要的类型进行返回！
		return (T) bean;
	}

	/**
	 * 所有容器中是否包含name的bean或者bd
	 */
	@Override
	public boolean containsBean(String name) {
		/* 1、检查当前容器中，是否有对应beanName的bean或者bd，有其中之一，就返回true */

		// 获取name最终的规范名称【最终别名称】—— 也就是去掉开头所有的&符号
		String beanName = transformedBeanName(name);
		// ⚠️beanName存在于singletonObjects中 || beanName存在于beanDefinitionMap中
		if (containsSingleton(beanName)/* 包含单例bean */ || containsBeanDefinition(beanName)/* 包含bd */) {
			// beanName不是以&符号开始 || 是一个FactoryBean
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(name));
		}

		/* 2、当前容器没有找到，就递归去父容器中查找是否有对应beanName的bean或者bd，有其中之一，就返回true */

		// Not found -> check parent.
		// 获取父工厂
		BeanFactory parentBeanFactory = getParentBeanFactory();
		// 如果父工厂不为null，则递归形式查询该name是否存在于父工厂，并返回执行结果：为null时直接返回false
		// 因为经过上面的步骤，已经确定当前工厂不存在该bean的bd对象以及singleton实例
		return (parentBeanFactory != null && parentBeanFactory.containsBean(originalBeanName(name)));
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			if (beanInstance instanceof FactoryBean) {
				return (BeanFactoryUtils.isFactoryDereference(name) || ((FactoryBean<?>) beanInstance).isSingleton());
			}
			else {
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isSingleton(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// In case of FactoryBean, return singleton status of created object if not a dereference.
		if (mbd.isSingleton()) {
			if (isFactoryBean(beanName, mbd)) {
				if (BeanFactoryUtils.isFactoryDereference(name)) {
					return true;
				}
				FactoryBean<?> factoryBean = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
				return factoryBean.isSingleton();
			}
			else {
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isPrototype(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isPrototype()) {
			// In case of FactoryBean, return singleton status of created object if not a dereference.
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName, mbd));
		}

		// Singleton or scoped - not a prototype.
		// However, FactoryBean may still produce a prototype object...
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			return false;
		}
		if (isFactoryBean(beanName, mbd)) {
			FactoryBean<?> fb = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged(
						(PrivilegedAction<Boolean>) () ->
								((fb instanceof SmartFactoryBean && ((SmartFactoryBean<?>) fb).isPrototype()) ||
										!fb.isSingleton()),
						getAccessControlContext());
			}
			else {
				return ((fb instanceof SmartFactoryBean && ((SmartFactoryBean<?>) fb).isPrototype()) ||
						!fb.isSingleton());
			}
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, typeToMatch, true);
	}

	/**
	 * Internal extended variant of {@link #isTypeMatch(String, ResolvableType)}
	 * to check whether the bean with the given name matches the specified type. Allow
	 * additional constraints to be applied to ensure that beans are not created early.
	 * @param name the name of the bean to query
	 * @param typeToMatch the type to match against (as a
	 * {@code ResolvableType})
	 * @return {@code true} if the bean type matches, {@code false} if it
	 * doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 5.2
	 * @see #getBean
	 * @see #getType
	 */
	protected boolean isTypeMatch(String name, ResolvableType typeToMatch, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {

		String beanName = transformedBeanName(name);
		boolean isFactoryDereference = BeanFactoryUtils.isFactoryDereference(name);

		// Check manually registered singletons. - 检查手动注册的单例。
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			if (beanInstance instanceof FactoryBean) {
				if (!isFactoryDereference) {
					Class<?> type = getTypeForFactoryBean((FactoryBean<?>) beanInstance);
					return (type != null && typeToMatch.isAssignableFrom(type));
				}
				else {
					return typeToMatch.isInstance(beanInstance);
				}
			}
			else if (!isFactoryDereference) {
				if (typeToMatch.isInstance(beanInstance)) {
					// Direct match for exposed instance?
					return true;
				}
				else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
					// Generics potentially only match on the target class, not on the proxy...
					RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
					Class<?> targetType = mbd.getTargetType();
					if (targetType != null && targetType != ClassUtils.getUserClass(beanInstance)) {
						// Check raw class match as well, making sure it's exposed on the proxy.
						Class<?> classToMatch = typeToMatch.resolve();
						if (classToMatch != null && !classToMatch.isInstance(beanInstance)) {
							return false;
						}
						if (typeToMatch.isAssignableFrom(targetType)) {
							return true;
						}
					}
					ResolvableType resolvableType = mbd.targetType;
					if (resolvableType == null) {
						resolvableType = mbd.factoryMethodReturnType;
					}
					return (resolvableType != null && typeToMatch.isAssignableFrom(resolvableType));
				}
			}
			return false;
		}
		else if (containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			// null instance registered
			return false;
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isTypeMatch(originalBeanName(name), typeToMatch);
		}

		// Retrieve corresponding bean definition.
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();

		// Setup the types that we want to match against
		Class<?> classToMatch = typeToMatch.resolve();
		if (classToMatch == null) {
			classToMatch = FactoryBean.class;
		}
		Class<?>[] typesToMatch = (FactoryBean.class == classToMatch ?
				new Class<?>[] {classToMatch} : new Class<?>[] {FactoryBean.class, classToMatch});


		// Attempt to predict the bean type
		Class<?> predictedType = null;

		// We're looking for a regular reference but we're a factory bean that has
		// a decorated bean definition. The target bean should be the same type
		// as FactoryBean would ultimately return.
		if (!isFactoryDereference && dbd != null && isFactoryBean(beanName, mbd)) {
			// We should only attempt if the user explicitly set lazy-init to true
			// and we know the merged bean definition is for a factory bean.
			if (!mbd.isLazyInit() || allowFactoryBeanInit) {
				RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
				Class<?> targetType = predictBeanType(dbd.getBeanName(), tbd, typesToMatch);
				if (targetType != null && !FactoryBean.class.isAssignableFrom(targetType)) {
					predictedType = targetType;
				}
			}
		}

		// If we couldn't use the target type, try regular prediction.
		if (predictedType == null) {
			predictedType = predictBeanType(beanName, mbd, typesToMatch);
			if (predictedType == null) {
				return false;
			}
		}

		// Attempt to get the actual ResolvableType for the bean.
		ResolvableType beanType = null;

		// If it's a FactoryBean, we want to look at what it creates, not the factory class.
		if (FactoryBean.class.isAssignableFrom(predictedType)) {
			if (beanInstance == null && !isFactoryDereference) {
				beanType = getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit);
				predictedType = beanType.resolve();
				if (predictedType == null) {
					return false;
				}
			}
		}
		else if (isFactoryDereference) {
			// Special case: A SmartInstantiationAwareBeanPostProcessor returned a non-FactoryBean
			// type but we nevertheless are being asked to dereference a FactoryBean...
			// Let's check the original bean class and proceed with it if it is a FactoryBean.
			predictedType = predictBeanType(beanName, mbd, FactoryBean.class);
			if (predictedType == null || !FactoryBean.class.isAssignableFrom(predictedType)) {
				return false;
			}
		}

		// We don't have an exact type but if bean definition target type or the factory
		// method return type matches the predicted type then we can use that.
		if (beanType == null) {
			ResolvableType definedType = mbd.targetType;
			if (definedType == null) {
				definedType = mbd.factoryMethodReturnType;
			}
			if (definedType != null && definedType.resolve() == predictedType) {
				beanType = definedType;
			}
		}

		// If we have a bean type use it so that generics are considered
		if (beanType != null) {
			return typeToMatch.isAssignableFrom(beanType);
		}

		// If we don't have a bean type, fallback to the predicted type
		return typeToMatch.isAssignableFrom(predictedType);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, ResolvableType.forRawClass(typeToMatch));
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		return getType(name, true);
	}

	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		// Check manually registered singletons.
		// ⚠️
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			if (beanInstance instanceof FactoryBean && !BeanFactoryUtils.isFactoryDereference(name)) {
				return getTypeForFactoryBean((FactoryBean<?>) beanInstance);
			}
			else {
				return beanInstance.getClass();
			}
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.getType(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// Check decorated bean definition, if any: We assume it'll be easier
		// to determine the decorated bean's type than the proxy's type.
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
		if (dbd != null && !BeanFactoryUtils.isFactoryDereference(name)) {
			RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
			Class<?> targetClass = predictBeanType(dbd.getBeanName(), tbd);
			if (targetClass != null && !FactoryBean.class.isAssignableFrom(targetClass)) {
				return targetClass;
			}
		}

		Class<?> beanClass = predictBeanType(beanName, mbd);

		// Check bean class whether we're dealing with a FactoryBean.
		if (beanClass != null && FactoryBean.class.isAssignableFrom(beanClass)) {
			if (!BeanFactoryUtils.isFactoryDereference(name)) {
				// If it's a FactoryBean, we want to look at what it creates, not at the factory class.
				return getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit).resolve();
			}
			else {
				return beanClass;
			}
		}
		else {
			return (!BeanFactoryUtils.isFactoryDereference(name) ? beanClass : null);
		}
	}

	@Override
	public String[] getAliases(String name) {
		String beanName = transformedBeanName(name);
		List<String> aliases = new ArrayList<>();
		boolean factoryPrefix = name.startsWith(FACTORY_BEAN_PREFIX);
		String fullBeanName = beanName;
		if (factoryPrefix) {
			fullBeanName = FACTORY_BEAN_PREFIX + beanName;
		}
		if (!fullBeanName.equals(name)) {
			aliases.add(fullBeanName);
		}
		String[] retrievedAliases = super.getAliases(beanName);
		String prefix = factoryPrefix ? FACTORY_BEAN_PREFIX : "";
		for (String retrievedAlias : retrievedAliases) {
			String alias = prefix + retrievedAlias;
			if (!alias.equals(name)) {
				aliases.add(alias);
			}
		}
		if (!containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null) {
				aliases.addAll(Arrays.asList(parentBeanFactory.getAliases(fullBeanName)));
			}
		}
		return StringUtils.toStringArray(aliases);
	}


	//---------------------------------------------------------------------
	// Implementation of HierarchicalBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return this.parentBeanFactory;
	}

	@Override
	public boolean containsLocalBean(String name) {
		String beanName = transformedBeanName(name);
		return ((containsSingleton(beanName) || containsBeanDefinition(beanName)) &&
				(!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName)));
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public void setParentBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		if (this.parentBeanFactory != null && this.parentBeanFactory != parentBeanFactory) {
			throw new IllegalStateException("Already associated with parent BeanFactory: " + this.parentBeanFactory);
		}
		if (this == parentBeanFactory) {
			throw new IllegalStateException("Cannot set parent bean factory to self");
		}
		this.parentBeanFactory = parentBeanFactory;
	}

	@Override
	public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
		this.beanClassLoader = (beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader());
	}

	@Override
	@Nullable
	public ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setTempClassLoader(@Nullable ClassLoader tempClassLoader) {
		this.tempClassLoader = tempClassLoader;
	}

	@Override
	@Nullable
	public ClassLoader getTempClassLoader() {
		return this.tempClassLoader;
	}

	@Override
	public void setCacheBeanMetadata(boolean cacheBeanMetadata) {
		this.cacheBeanMetadata = cacheBeanMetadata;
	}

	@Override
	public boolean isCacheBeanMetadata() {
		return this.cacheBeanMetadata;
	}

	@Override
	public void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver) {
		this.beanExpressionResolver = resolver;
	}

	@Override
	@Nullable
	public BeanExpressionResolver getBeanExpressionResolver() {
		return this.beanExpressionResolver;
	}

	@Override
	public void setConversionService(@Nullable ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	@Override
	@Nullable
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	@Override
	public void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar) {
		Assert.notNull(registrar, "PropertyEditorRegistrar must not be null");
		this.propertyEditorRegistrars.add(registrar);
	}

	/**
	 * Return the set of PropertyEditorRegistrars.
	 */
	public Set<PropertyEditorRegistrar> getPropertyEditorRegistrars() {
		return this.propertyEditorRegistrars;
	}

	@Override
	public void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass) {
		Assert.notNull(requiredType, "Required type must not be null");
		Assert.notNull(propertyEditorClass, "PropertyEditor class must not be null");
		this.customEditors.put(requiredType, propertyEditorClass);
	}

	@Override
	public void copyRegisteredEditorsTo(PropertyEditorRegistry registry) {
		registerCustomEditors(registry);
	}

	/**
	 * Return the map of custom editors, with Classes as keys and PropertyEditor classes as values.
	 */
	public Map<Class<?>, Class<? extends PropertyEditor>> getCustomEditors() {
		return this.customEditors;
	}

	@Override
	public void setTypeConverter(TypeConverter typeConverter) {
		this.typeConverter = typeConverter;
	}

	/**
	 * Return the custom TypeConverter to use, if any.
	 * @return the custom TypeConverter, or {@code null} if none specified
	 */
	@Nullable
	protected TypeConverter getCustomTypeConverter() {
		return this.typeConverter;
	}

	@Override
	public TypeConverter getTypeConverter() {
		TypeConverter customConverter = getCustomTypeConverter();
		if (customConverter != null) {
			return customConverter;
		}
		else {
			// Build default TypeConverter, registering custom editors.
			SimpleTypeConverter typeConverter = new SimpleTypeConverter();
			typeConverter.setConversionService(getConversionService());
			registerCustomEditors(typeConverter);
			return typeConverter;
		}
	}

	@Override
	public void addEmbeddedValueResolver(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		this.embeddedValueResolvers.add(valueResolver);
	}

	@Override
	public boolean hasEmbeddedValueResolver() {
		return !this.embeddedValueResolvers.isEmpty();
	}

	@Override
	@Nullable
	public String resolveEmbeddedValue(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String result = value;
		/**
		 * StringValueResolver：字符串值解析器
		 */
		// 遍历字符串值解析器
		for (StringValueResolver resolver : this.embeddedValueResolvers) {
			// 用字符串值解析器，解析字符串
			result = resolver.resolveStringValue(result);
			if (result == null) {
				return null;
			}
		}
		return result;
	}

	/**
	 * 添加后置处理器到末尾，如果之前存在就删除，为的是只添加到末尾
	 * @param beanPostProcessor the post-processor to register
	 */
	@Override
	public void  addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
		Assert.notNull(beanPostProcessor, "BeanPostProcessor must not be null");
		// Remove from old position, if any
		this.beanPostProcessors.remove(beanPostProcessor);

		// Track whether it is instantiation/destruction aware

		// ⚠️存在InstantiationAwareBeanPostProcessor，则把hasInstantiationAwareBeanPostProcessors设置为true
		if (beanPostProcessor instanceof InstantiationAwareBeanPostProcessor) {
			this.hasInstantiationAwareBeanPostProcessors = true;
		}
		// ⚠️存在DestructionAwareBeanPostProcessor，则把hasDestructionAwareBeanPostProcessors设置为true
		if (beanPostProcessor instanceof DestructionAwareBeanPostProcessor) {
			this.hasDestructionAwareBeanPostProcessors = true;
		}

		// Add to end of list - 添加到列表末尾
		// ⚠️添加后置处理器
		this.beanPostProcessors.add(beanPostProcessor);
	}

	@Override
	public int getBeanPostProcessorCount() {
		return this.beanPostProcessors.size();
	}

	/**
	 * Return the list of BeanPostProcessors that will get applied
	 * to beans created with this factory.
	 */
	public List<BeanPostProcessor> getBeanPostProcessors() {
		return this.beanPostProcessors;
	}

	/**
	 * 判断是否存在InstantiationAwareBeanPostProcessor bean，有就为true；否则为false
	 *
	 * Return whether this factory holds a InstantiationAwareBeanPostProcessor
	 * that will get applied to singleton beans on creation.
	 *
	 * 返回此工厂是否拥有一个将在创建时应用于单例bean的InstantiationAwareBeanPostProcessor。
	 *
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor
	 */
	protected boolean hasInstantiationAwareBeanPostProcessors() {
		// 如果有注册InstantiationAwareBeanPostProcessor bean，那么就为true；否则为false
		return this.hasInstantiationAwareBeanPostProcessors;
	}

	/**
	 * Return whether this factory holds a DestructionAwareBeanPostProcessor
	 * that will get applied to singleton beans on shutdown.
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean hasDestructionAwareBeanPostProcessors() {
		return this.hasDestructionAwareBeanPostProcessors;
	}

	@Override
	public void registerScope(String scopeName, Scope scope) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		Assert.notNull(scope, "Scope must not be null");
		if (SCOPE_SINGLETON.equals(scopeName) || SCOPE_PROTOTYPE.equals(scopeName)) {
			throw new IllegalArgumentException("Cannot replace existing scopes 'singleton' and 'prototype'");
		}
		Scope previous = this.scopes.put(scopeName, scope);
		if (previous != null && previous != scope) {
			if (logger.isDebugEnabled()) {
				logger.debug("Replacing scope '" + scopeName + "' from [" + previous + "] to [" + scope + "]");
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("Registering scope '" + scopeName + "' with implementation [" + scope + "]");
			}
		}
	}

	@Override
	public String[] getRegisteredScopeNames() {
		return StringUtils.toStringArray(this.scopes.keySet());
	}

	@Override
	@Nullable
	public Scope getRegisteredScope(String scopeName) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		return this.scopes.get(scopeName);
	}

	/**
	 * Set the security context provider for this bean factory. If a security manager
	 * is set, interaction with the user code will be executed using the privileged
	 * of the provided security context.
	 */
	public void setSecurityContextProvider(SecurityContextProvider securityProvider) {
		this.securityContextProvider = securityProvider;
	}

	/**
	 * Delegate the creation of the access control context to the
	 * {@link #setSecurityContextProvider SecurityContextProvider}.
	 */
	@Override
	public AccessControlContext getAccessControlContext() {
		return (this.securityContextProvider != null ?
				this.securityContextProvider.getAccessControlContext() :
				AccessController.getContext());
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		Assert.notNull(otherFactory, "BeanFactory must not be null");
		setBeanClassLoader(otherFactory.getBeanClassLoader());
		setCacheBeanMetadata(otherFactory.isCacheBeanMetadata());
		setBeanExpressionResolver(otherFactory.getBeanExpressionResolver());
		setConversionService(otherFactory.getConversionService());
		if (otherFactory instanceof AbstractBeanFactory) {
			AbstractBeanFactory otherAbstractFactory = (AbstractBeanFactory) otherFactory;
			this.propertyEditorRegistrars.addAll(otherAbstractFactory.propertyEditorRegistrars);
			this.customEditors.putAll(otherAbstractFactory.customEditors);
			this.typeConverter = otherAbstractFactory.typeConverter;
			this.beanPostProcessors.addAll(otherAbstractFactory.beanPostProcessors);
			this.hasInstantiationAwareBeanPostProcessors = this.hasInstantiationAwareBeanPostProcessors ||
					otherAbstractFactory.hasInstantiationAwareBeanPostProcessors;
			this.hasDestructionAwareBeanPostProcessors = this.hasDestructionAwareBeanPostProcessors ||
					otherAbstractFactory.hasDestructionAwareBeanPostProcessors;
			this.scopes.putAll(otherAbstractFactory.scopes);
			this.securityContextProvider = otherAbstractFactory.securityContextProvider;
		}
		else {
			setTypeConverter(otherFactory.getTypeConverter());
			String[] otherScopeNames = otherFactory.getRegisteredScopeNames();
			for (String scopeName : otherScopeNames) {
				this.scopes.put(scopeName, otherFactory.getRegisteredScope(scopeName));
			}
		}
	}

	/**
	 * 在实例化之前，要把所有的基础的beanDefinition对象转换成RootBeanDefinition对象，进行缓存，
	 * 后续在马上要实例化的时候，直接获取定义信息，而定义信息中如果包含了父类，那么必须要先创建父类才能有子类，父类如里没有的话，子类怎么创建?（🤔️我的理解：在实例化的时候，如果定义信息中包含了父类，那么就要先创建父类bean，才能创建子类bean，父类bean没有的话，子类bean无法创建）
	 *
	 * Return a 'merged' BeanDefinition for the given bean name,
	 * merging a child bean definition with its parent if necessary.
	 * <p>This {@code getMergedBeanDefinition} considers bean definition
	 * in ancestors as well.
	 * @param name the name of the bean to retrieve the merged definition for
	 * (may be an alias)
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	@Override
	public BeanDefinition getMergedBeanDefinition(String name) throws BeansException {
		// 获取真正的beanName
		String beanName = transformedBeanName(name);
		// Efficiently check whether bean definition exists in this factory.
		if (!containsBeanDefinition(beanName) && getParentBeanFactory()/* 获取父容器里面的东西，判断父容器里面有没有，然后再进行相关的调用操作 */ instanceof ConfigurableBeanFactory) {
			// 如果当前BeanFactory中不存在beanName的Bean定义 && 父beanFactory是ConfigurableBeanFactory
			// 则调用父BeanFactory去获取beanName的MergedBeanDefinition
			return ((ConfigurableBeanFactory) getParentBeanFactory()).getMergedBeanDefinition(beanName);
		}
		// Resolve merged bean definition locally.
		// 在当前BeanFactory中解析beanName的MergedBeanDefinition
		return getMergedLocalBeanDefinition(beanName);
	}

	@Override
	public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
		// 拿到真正的beanName
		String beanName = transformedBeanName(name);
		// 尝试从缓存中获取bean实例对象
		Object beanInstance = getSingleton(beanName, false);

		if (beanInstance != null) {
			// beanInstance存在，则直接判断类型是否为FactoryBean
			return (beanInstance instanceof FactoryBean);
		}

		// No singleton instance found -> check bean definition.
		// 如果缓存中不存在此beanName && 父beanFactory是ConfigurableBeanFactory,则调用父BeanFactory判断是否为FactoryBean
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
			// No bean definition found in this factory -> delegate to parent.
			return ((ConfigurableBeanFactory) getParentBeanFactory()).isFactoryBean(name);
		}

		// 通过MergedBeanDefinition来检查beanName对应的bean是否为FactoryBean
		return isFactoryBean(beanName, getMergedLocalBeanDefinition(beanName));
	}

	@Override
	public boolean isActuallyInCreation(String beanName) {
		return (isSingletonCurrentlyInCreation(beanName) || isPrototypeCurrentlyInCreation(beanName));
	}

	/**
	 * 返回特殊的bean是否正在被创建
	 *
	 * Return whether the specified prototype bean is currently in creation
	 *
	 * 返回指定的原型bean当前是否正在创建中
	 *
	 * (within the current thread).
	 * @param beanName the name of the bean
	 */
	protected boolean isPrototypeCurrentlyInCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		return (curVal != null &&
				(curVal.equals(beanName) || (curVal instanceof Set && ((Set<?>) curVal).contains(beanName))));
	}

	/**
	 * Callback before prototype creation.
	 * <p>The default implementation register the prototype as currently in creation.
	 * @param beanName the name of the prototype about to be created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void beforePrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal == null) {
			this.prototypesCurrentlyInCreation.set(beanName);
		}
		else if (curVal instanceof String) {
			Set<String> beanNameSet = new HashSet<>(2);
			beanNameSet.add((String) curVal);
			beanNameSet.add(beanName);
			this.prototypesCurrentlyInCreation.set(beanNameSet);
		}
		else {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.add(beanName);
		}
	}

	/**
	 * Callback after prototype creation.
	 * <p>The default implementation marks the prototype as not in creation anymore.
	 * @param beanName the name of the prototype that has been created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void afterPrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal instanceof String) {
			this.prototypesCurrentlyInCreation.remove();
		}
		else if (curVal instanceof Set) {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.remove(beanName);
			if (beanNameSet.isEmpty()) {
				this.prototypesCurrentlyInCreation.remove();
			}
		}
	}

	@Override
	public void destroyBean(String beanName, Object beanInstance) {
		destroyBean(beanName, beanInstance, getMergedLocalBeanDefinition(beanName));
	}

	/**
	 * Destroy the given bean instance (usually a prototype instance
	 * obtained from this factory) according to the given bean definition.
	 * @param beanName the name of the bean definition
	 * @param bean the bean instance to destroy
	 * @param mbd the merged bean definition
	 */
	protected void destroyBean(String beanName, Object bean, RootBeanDefinition mbd) {
		new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), getAccessControlContext()).destroy();
	}

	@Override
	public void destroyScopedBean(String beanName) {
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isSingleton() || mbd.isPrototype()) {
			throw new IllegalArgumentException(
					"Bean name '" + beanName + "' does not correspond to an object in a mutable scope");
		}
		String scopeName = mbd.getScope();
		Scope scope = this.scopes.get(scopeName);
		if (scope == null) {
			throw new IllegalStateException("No Scope SPI registered for scope name '" + scopeName + "'");
		}
		Object bean = scope.remove(beanName);
		if (bean != null) {
			destroyBean(beanName, bean, mbd);
		}
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * Return the bean name, stripping out the factory dereference prefix if necessary,
	 * and resolving aliases to canonical names.
	 * @param name the user-specified name
	 * @return the transformed bean name
	 */
	protected String transformedBeanName(String name) {
		return canonicalName(BeanFactoryUtils.transformedBeanName(name));
	}

	/**
	 * Determine the original bean name, resolving locally defined aliases to canonical names.
	 * @param name the user-specified name
	 * @return the original bean name
	 */
	protected String originalBeanName(String name) {
		String beanName = transformedBeanName(name);
		if (name.startsWith(FACTORY_BEAN_PREFIX)) {
			beanName = FACTORY_BEAN_PREFIX + beanName;
		}
		return beanName;
	}

	/**
	 * 初始化BeanWrapper
	 *
	 * Initialize the given BeanWrapper with the custom editors registered
	 * with this factory. To be called for BeanWrappers that will create
	 * and populate bean instances.
	 * <p>The default implementation delegates to {@link #registerCustomEditors}.
	 * Can be overridden in subclasses.
	 * @param bw the BeanWrapper to initialize
	 */
	protected void initBeanWrapper(BeanWrapper bw) {
		/* 前面设置的一些自定义属性编辑器，类型转换器，通过初始化之后，都包含在包装类里面了，有了这些功能 */

		/* 1、设置ConversionService(类型转换服务) */
		/**
		 *
		 */
		// 使用该工厂的ConversionService来作为bw的ConversionService，用于转换属性值，以替换JavaBeans PProperty
		bw.setConversionService(getConversionService());

		/* 2、注册自定义的编辑器 */
		/**
		 * ⚠️填充BeanWrapperImpl的overriddenDefaultEditors、customEditors属性，也就是：
		 * （1）将DefaultListableBeanFactory的propertyEditorRegistrars set集合中的属性编辑器放入BeanWrapperImpl的overriddenDefaultEditors set集合当中
		 * （2）将DefaultListableBeanFactory的customEditors map集合中的属性编辑器放入BeanWrapperImpl的customEditors map集合当中
		 */
		// 将BeanFactory中所有的PropertyEditor注册到bw中
		registerCustomEditors(bw);
	}

	/**
	 * 将工厂中所有的PropertyEditor注册到PropertyEditorRegistry中
	 *
	 * Initialize the given PropertyEditorRegistry with the custom editors
	 * that have been registered with this BeanFactory.
	 * <p>To be called for BeanWrappers that will create and populate bean
	 * instances, and for SimpleTypeConverter used for constructor argument
	 * and factory method type conversion.
	 * @param registry the PropertyEditorRegistry to initialize
	 */
	protected void registerCustomEditors(PropertyEditorRegistry registry/* registry = BeanWrapperImpl */) {
		/* 1、registry强转为PropertyEditorRegistrySupport */

		// 将registry强制转换为PropertyEditorRegistrySupport，如果不能转换，则registry为null
		// 题外：BeanWrapperImpl间接继承PropertyEditorRegistrySupport
		// 题外：PropertyEditorRegistrySupport是PropertyEditorRegistry接口的默认实现
		PropertyEditorRegistrySupport registrySupport =
				(registry instanceof PropertyEditorRegistrySupport ? (PropertyEditorRegistrySupport) registry : null);

		/* 2、设置【"是否开启配置值编辑器"的标识】为true，代表使用属性编辑器！ */
		if (registrySupport != null) {
			// 设置【"是否开启配置值编辑器"的标识】为true，代表使用属性编辑器！
			registrySupport.useConfigValueEditors/* 使用配置值编辑器 */();
		}

		/* 3、执行BeanFactory中所有的属性编辑器注册器，注册对应的属性编辑器到BeanWrapperImpl中 */
		// 注意：当前对象是BeanFactory
		if (!this.propertyEditorRegistrars.isEmpty()) {
			// 遍历BeanFactory中的自定义属性编辑器注册器集合，
			// this.propertyEditorRegistrars = "自定义属性编辑器的注册器"集合
			for (PropertyEditorRegistrar registrar : this.propertyEditorRegistrars) {
				try {
					/**
					 * this = DefaultListableBeanFactory
					 * ⚠️关键代码，调用DefaultListableBeanFactory.propertyEditorRegistrars set集合中的属性编辑器的注册器的注册自定义编辑器方法
					 * 在"属性编辑器的注册器的注册自定义编辑器方法"中，会把对应的属性编辑器注入到BeanWrapperImpl.overriddenDefaultEditors中
					 *
					 * 💡目前propertyEditorRegistrars只有ResourceEditorRegistrar这个对象，
					 * ResourceEditorRegistrar是在AbstractApplicationContext#prepareBeanFactory()中添加的
					 * 调用的是 beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment())); 进行添加的
					 */
					// registrar = ResourceEditorRegistrar
					// registry = BeanWrapperImpl
					// overriddenDefaultEditors
					registrar.registerCustomEditors/* 注册自定义编辑器 */(registry);
				}
				catch (BeanCreationException ex) {
					Throwable rootCause = ex.getMostSpecificCause();
					if (rootCause instanceof BeanCurrentlyInCreationException) {
						BeanCreationException bce = (BeanCreationException) rootCause;
						String bceBeanName = bce.getBeanName();
						if (bceBeanName != null && isCurrentlyInCreation(bceBeanName)) {
							if (logger.isDebugEnabled()) {
								logger.debug("PropertyEditorRegistrar [" + registrar.getClass().getName() +
										"] failed because it tried to obtain currently created bean '" +
										ex.getBeanName() + "': " + ex.getMessage());
							}
							onSuppressedException(ex);
							continue;
						}
					}
					throw ex;
				}
			}
		}

		/* 4、将BeanFactory中所有的属性编辑器，注册到BeanWrapperImpl中 */
		if (!this.customEditors.isEmpty()) {
			// this.customEditors = 属性编辑器(Map<属性类型，属性编辑器>)
			/**
			 * this = DefaultListableBeanFactory
			 * registry = BeanWrapperImpl
			 * ⚠️关键代码，将DefaultListableBeanFactory.customEditors map集合中的属性编辑器放入BeanWrapperImpl.customEditors map集合当中
			 */
			this.customEditors.forEach((requiredType, editorClass) ->
					// registry = BeanWrapperImpl
					// 调用的是BeanWrapperImpl.registerCustomEditor()
					registry.registerCustomEditor(requiredType, BeanUtils.instantiateClass(editorClass)));
		}
	}


	/**
	 * 根据beanName来获取到它的一个完整的描述信息
	 *
	 * 用来整合我们对应的一些父类里面也包含的一些BeanDefinition对象
	 *
	 * Return a merged RootBeanDefinition, traversing the parent bean definition
	 * if the specified bean corresponds to a child bean definition.
	 *
	 * 返回一个合并的 RootBeanDefinition，如果指定的 bean 对应于子 bean 定义，则遍历父 bean 定义。
	 *
	 * @param beanName the name of the bean to retrieve the merged definition for
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
		// Quick check on the concurrent map first, with minimal locking. —— 首先快速检查并发映射，锁定最少。

		// 检查beanName对应的mergedBeanDefinition（RootBeanDefinition）是否存在于缓存中

		// 题外：此缓存是在 invokeBeanFactoryPostProcess()中的第一个beanFactory.getBeanNamesForType()触发添加的
		RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
		if (mbd != null && !mbd.stale/* 陈旧 */) {
			// 如果缓存中存在，并且不是陈旧的数据，就直接返回
			/**
			 * stale作用：
			 * 判断bd是否需要进行重新合并，因为存放在缓存里面的数据，如果后续当前bean对象产生变动，那么缓存中的数据就不是一个新鲜的数据了，
			 * 所以此时需要重新合并，进行一个替换工作了，所以通过一个状态位来判断当前缓存是不是一个新鲜的值，是的话就拿过来直接用，不是的话就重新创建，然后进行替换
			 */
			return mbd;
		}

		// 如果不存在于缓存中，根据beanName和BeanDefinition, 获取mergedBeanDefinitions
		return getMergedBeanDefinition(beanName,
				// 从当前容器中获取beanName对应的bd
				// ⚠️题外：️这里面判断，容器当中是否存在beanName的bd，如果不存在则会报错！
				getBeanDefinition(beanName));
	}

	/**
	 * Return a RootBeanDefinition for the given top-level bean, by merging with
	 * the parent if the given bean's definition is a child bean definition.
	 * @param beanName the name of the bean definition
	 * @param bd the original bean definition (Root/ChildBeanDefinition)
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd)
			throws BeanDefinitionStoreException {

		return getMergedBeanDefinition(beanName, bd, null);
	}

	/**
	 * Return a RootBeanDefinition for the given bean, by merging with the
	 * parent if the given bean's definition is a child bean definition.
	 *
	 * 如果给定 bean 的定义是子 bean 定义，则通过与父合并返回给定 bean 的 RootBeanDefinition。
	 *
	 * @param beanName the name of the bean definition
	 * @param bd the original bean definition (Root/ChildBeanDefinition)
	 * @param containingBd the containing bean definition in case of inner bean,
	 * or {@code null} in case of a top-level bean
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(
			String beanName, BeanDefinition bd, @Nullable BeanDefinition containingBd)
			throws BeanDefinitionStoreException {

		// 无论如何，最终得到的对象都是RootBeanDefinition，它必须是包含，或者说合并完我们对应的一个父容器里面属性的一些值，才能拿过来算，要不然后面没办法进行相关的处理工作

		synchronized (this.mergedBeanDefinitions) {
			// 用于存储bd的MergedBeanDefinition
			RootBeanDefinition mbd = null;
			RootBeanDefinition previous/* 以前的 */ = null;

			// Check with full lock now in order to enforce the same merged instance.
			// 检查beanName对应的MergedBeanDefinition是否存在于缓存中
			if (containingBd == null) {
				mbd = this.mergedBeanDefinitions.get(beanName);
			}

			// 如果缓存中没有，或者缓存中的数据是陈旧的
			if (mbd == null || mbd.stale/* 陈旧 */) {
				previous = mbd;
				/**
				 * getParentName()获取的是<bean parent=""></bean>中的parent属性值
				 */
				// 判断有没有父类的名称
				// 判断当前bd里面是否已经包含父类，也就是说你到底有没有父类
				// 如果bd的parentName为空，代表bd没有父定义，无需与父定义进行合并操作
				if (bd.getParentName() == null) {
					// Use copy of given root bean definition.
					// 如果bd的类型为RootBeanDefinition,则bd的MergedBeanDefinition就是bd本身，则直接克隆一个副本
					if (bd instanceof RootBeanDefinition) {
						mbd = ((RootBeanDefinition) bd).cloneBeanDefinition();
					}
					else {
						// 否则，将bd作为参数，构建一个RootBeanDefinition
						// 正常使用下，BeanDefinition在被加载后是GenericBeanDefinition或ScannedGenericBeanDefinition
						mbd = new RootBeanDefinition(bd);
					}
				}
				else {

					// 判断一下说，到底有没有当前这样一个bd值，父容器里面到底有没有这样一个值，挨个进行相关的遍历操作，如果最终我都找到了，就创建一个RootBeanDefinition

					// Child bean definition: needs to be merged with parent. —— 子bean定义：需要与父合并。
					// bd存在父定义，需要与父定义合并
					BeanDefinition pbd;
					try {
						// 获取父bean的名称，并去掉&
						String parentBeanName = transformedBeanName(bd.getParentName());
						// 如果当前beanName和父beanName不相同，那么递归调用合并方法
						if (!beanName.equals(parentBeanName)) {
							// 递归，获取父定义对应的RootBeanDefinition(也是Merged好了的BeanDefinition)，并且会看父定义是否还有父定义，有的话就会再次合并父父定义，最终返回Merged好了的父定义
							pbd = getMergedBeanDefinition(parentBeanName);
						}
						// 如果父定义的beanName与bd的beanName相同，则拿到父BeanFactory.
						// 只有在存在父BeanFactory的情况下，才允许父定义beanName与自己相同，否则就是将自己设置为父定义
						else {
							BeanFactory parent = getParentBeanFactory();
							// 如果父BeanFactory是ConfigurableBeanFactory，则通过父BeanFactory获取父定义的MergedBeanDefinition
							if (parent instanceof ConfigurableBeanFactory) {
								pbd = ((ConfigurableBeanFactory) parent).getMergedBeanDefinition(parentBeanName);
							}
							else {
								// 如果父BeanFactory不是ConfigurableBeanFactory，则抛异常
								throw new NoSuchBeanDefinitionException(parentBeanName,
										"Parent name '" + parentBeanName + "' is equal to bean name '" + beanName +
										"': cannot be resolved without a ConfigurableBeanFactory parent");
							}
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName,
								"Could not resolve parent bean definition '" + bd.getParentName() + "'", ex);
					}
					// Deep copy with overridden values.
					// ⚠️使用父定义pbd构建一个新的RootBeanDefinition对象
					mbd = new RootBeanDefinition(pbd);
					// ⚠️使用当前bd覆盖父定义
					mbd.overrideFrom(bd);
				}

				// Set default singleton scope, if not configured before.
				// ⚠️如果没有指定scope，那么设置默认的scope为单例
				if (!StringUtils.hasLength(mbd.getScope())) {
					mbd.setScope(SCOPE_SINGLETON/* singleton */);
				}

				// A bean contained in a non-singleton bean cannot be a singleton itself.
				// Let's correct this on the fly here, since this might be the result of
				// parent-child merging for the outer bean, in which case the original inner bean
				// definition will not have inherited the merged outer bean's singleton status.
				// 如果containingBd为不空 && containingBd不为singleton && mbd为singleton，则将mbd的scope设置为containingBd的source
				if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
					mbd.setScope(containingBd.getScope());
				}

				// Cache the merged bean definition for the time being
				// (it might still get re-merged later on in order to pick up metadata changes)
				if (containingBd == null && isCacheBeanMetadata()) {
					// ⚠️将beanName与mbd放到mergedBeanDefinitions缓存，以便之后可以直接使用
					this.mergedBeanDefinitions.put(beanName, mbd);
				}
			}
			if (previous != null) {
				copyRelevantMergedBeanDefinitionCaches(previous, mbd);
			}
			// 返回MergedBeanDefinition
			return mbd;
		}
	}

	private void copyRelevantMergedBeanDefinitionCaches(RootBeanDefinition previous, RootBeanDefinition mbd) {
		if (ObjectUtils.nullSafeEquals(mbd.getBeanClassName(), previous.getBeanClassName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryBeanName(), previous.getFactoryBeanName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryMethodName(), previous.getFactoryMethodName())) {
			ResolvableType targetType = mbd.targetType;
			ResolvableType previousTargetType = previous.targetType;
			if (targetType == null || targetType.equals(previousTargetType)) {
				mbd.targetType = previousTargetType;
				mbd.isFactoryBean = previous.isFactoryBean;
				mbd.resolvedTargetType = previous.resolvedTargetType;
				mbd.factoryMethodReturnType = previous.factoryMethodReturnType;
				mbd.factoryMethodToIntrospect = previous.factoryMethodToIntrospect;
			}
		}
	}

	/**
	 * Check the given merged bean definition,
	 * potentially throwing validation exceptions.
	 * @param mbd the merged bean definition to check
	 * @param beanName the name of the bean
	 * @param args the arguments for bean creation, if any
	 * @throws BeanDefinitionStoreException in case of validation failure
	 */
	protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, @Nullable Object[] args)
			throws BeanDefinitionStoreException {

		if (mbd.isAbstract()) {
			// 判断一下是不是抽象的，是的话就抛出异常
			throw new BeanIsAbstractException(beanName);
		}
	}

	/**
	 * Remove the merged bean definition for the specified bean,
	 * recreating it on next access.
	 * @param beanName the bean name to clear the merged definition for
	 */
	protected void clearMergedBeanDefinition(String beanName) {
		RootBeanDefinition bd = this.mergedBeanDefinitions.get(beanName);
		if (bd != null) {
			bd.stale = true;
		}
	}

	/**
	 * Clear the merged bean definition cache, removing entries for beans
	 * which are not considered eligible for full metadata caching yet.
	 * <p>Typically triggered after changes to the original bean definitions,
	 * e.g. after applying a {@code BeanFactoryPostProcessor}. Note that metadata
	 * for beans which have already been created at this point will be kept around.
	 * @since 4.2
	 */
	public void clearMetadataCache() {
		this.mergedBeanDefinitions.forEach((beanName, bd) -> {
			if (!isBeanEligibleForMetadataCaching(beanName)) {
				bd.stale = true;
			}
		});
	}

	/**
	 * Resolve the bean class for the specified bean definition,
	 * resolving a bean class name into a Class reference (if necessary)
	 * and storing the resolved Class in the bean definition for further use.
	 * 为指定的bean定义解析bean类，将bean类名解析为Class引用（如果需要），并将解析后的Class存储在bean定义中以备将来使用。
	 * @param mbd the merged bean definition to determine the class for
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the resolved bean class (or {@code null} if none)
	 * @throws CannotLoadBeanClassException if we failed to load the class
	 */
	@Nullable
	protected Class<?> resolveBeanClass(RootBeanDefinition mbd, String beanName, Class<?>... typesToMatch)
			throws CannotLoadBeanClassException {

		try {
			// 判断mbd的定义信息中是否包含beanClass,并且是Class类型的，如果是直接返回，否则的话进行详细的解析
			// 判断当前bd的beanClass是不是Class对象，因为要进行反射创建对象，就需要Class对象！但是配置文件默认解析到的beanClass是一个字符串
			if (mbd.hasBeanClass()) {
				return mbd.getBeanClass();
			}
			// 判断是否有安全管理器
			// 安全管理器作用：用来赋权限用的，就是我们的代码在进行相关操作的时候，它会判断一下当前这个操作是不是安全的，避免在运行过程中发生比较大的一些问题
			// 本质上是用来进行相关的权限检查的，检查一下我要做某一个操作的时候有没有相关的安全检测，有安全管理器的话就要验证当前权限，没有的话就直接干了
			if (System.getSecurityManager() != null) {
				// 如果有安全警告机制的话，我就进行安全检查
				return AccessController.doPrivileged((PrivilegedExceptionAction<Class<?>>)
						// 进行详细的处理解析过程
						() -> doResolveBeanClass(mbd, typesToMatch), getAccessControlContext());
			}
			else {
				// 进行详细的处理解析过程
				return doResolveBeanClass(mbd, typesToMatch);
			}
		}
		catch (PrivilegedActionException pae) {
			ClassNotFoundException ex = (ClassNotFoundException) pae.getException();
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (ClassNotFoundException ex) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (LinkageError err) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), err);
		}
	}

	@Nullable
	private Class<?> doResolveBeanClass(RootBeanDefinition mbd, Class<?>... typesToMatch)
			throws ClassNotFoundException {

		// 获取bean的类加载器
		ClassLoader beanClassLoader = getBeanClassLoader();
		// 动态的类加载器
		ClassLoader dynamicLoader = beanClassLoader;
		boolean freshResolve = false;

		// 判断typesToMatch是否为空，如果不为空，那么使用临时加载器进行加载
		if (!ObjectUtils.isEmpty(typesToMatch)) {
			// When just doing type checks (i.e. not creating an actual instance yet),
			// use the specified temporary class loader (e.g. in a weaving scenario).
			// 上面的翻译：当只是进行类型检查（即尚未创建实际实例）时，请使用指定的临时类加载器（例如在编织场景中）。
			ClassLoader tempClassLoader = getTempClassLoader();
			if (tempClassLoader != null) {
				dynamicLoader = tempClassLoader;
				freshResolve = true;
				if (tempClassLoader instanceof DecoratingClassLoader) {
					DecoratingClassLoader dcl = (DecoratingClassLoader) tempClassLoader;
					for (Class<?> typeToMatch : typesToMatch) {
						dcl.excludeClass(typeToMatch.getName());
					}
				}
			}
		}

		String className = mbd.getBeanClassName();
		if (className != null) {
			// 读取BeanDefinition中对应的className
			Object evaluated = evaluateBeanDefinitionString(className, mbd);
			// 判断className是否等于计算出的表达式的结果，如果不等于，那么判断evaluated的类型
			if (!className.equals(evaluated)) {
				// A dynamically resolved expression, supported as of 4.2... —— 从4.2开始支持的动态解析表达式...
				// 如果是Class类型，直接返回
				if (evaluated instanceof Class) {
					return (Class<?>) evaluated;
				}
				// 如果是String类型，则设置freshResolve为true，并使用动态加载器进行加载
				else if (evaluated instanceof String) {
					className = (String) evaluated;
					freshResolve = true;
				}
				else {
					throw new IllegalStateException("Invalid class name expression result: " + evaluated);
				}
			}
			if (freshResolve) {
				// When resolving against a temporary class loader, exit early in order
				// to avoid storing the resolved Class in the bean definition.
				// 上面的翻译：当针对临时类加载器解析时，请提前退出以避免将解析的类存储在 bean 定义中。
				if (dynamicLoader != null) {
					try {
						return dynamicLoader.loadClass(className);
					}
					catch (ClassNotFoundException ex) {
						if (logger.isTraceEnabled()) {
							logger.trace("Could not load class [" + className + "] from " + dynamicLoader + ": " + ex);
						}
					}
				}
				return ClassUtils.forName(className, dynamicLoader);
			}
		}

		// Resolve regularly, caching the result in the BeanDefinition... —— 定期解析，将结果缓存在 BeanDefinition...
		// 定期检查，缓存bd的结果
		// ⚠️Class.from()在里面
		return mbd.resolveBeanClass(beanClassLoader);
	}

	/**
	 * Evaluate the given String as contained in a bean definition,
	 * potentially resolving it as an expression.
	 * @param value the value to check
	 * @param beanDefinition the bean definition that the value comes from
	 * @return the resolved value
	 * @see #setBeanExpressionResolver
	 */
	@Nullable
	protected Object evaluateBeanDefinitionString(@Nullable String value, @Nullable BeanDefinition beanDefinition) {
		/*

		1、判断一下"SpEL表达式处理器"是否为空

		beanExpressionResolver：SpEL表达式的处理器。在refresh() ——> prepareBeanFactory()中设置进去的！

		*/
		// 如果该工厂没有设置bean定义值中表达式的解析策略
		if (this.beanExpressionResolver == null) {
			// 直接返回要检查的值
			return value;
		}

		/* 2、确定一下当前的作用域：beanDefinition不为空，就获取一下作用域名称，然后通过名称获取作用域 */
		// 值所来自的bean定义的当前目标作用域
		Scope scope = null;
		// 如果有传入值所来自的bean定义
		if (beanDefinition != null) {
			// 获取值所来自的bean定义的当前目标作用域名称
			String scopeName = beanDefinition.getScope();
			if (scopeName != null) {
				// 获取scopeName对于的Scope对象
				scope = getRegisteredScope(scopeName);
			}
		}

		/* 3、⚠️利用"SpEL表达式的处理器"，对我们配置的值，进行属性替换工作 */

		// 评估value作为表达式（如果适用），否则按原样返回值
		// ⚠️利用"SpEL表达式的处理器"，对我们配置的值，进行属性替换工作
		return this.beanExpressionResolver.evaluate(value, new BeanExpressionContext(this, scope));
	}

	/**
	 * Predict the eventual bean type (of the processed bean instance) for the
	 * specified bean. Called by {@link #getType} and {@link #isTypeMatch}.
	 * Does not need to handle FactoryBeans specifically, since it is only
	 * supposed to operate on the raw bean type.
	 * <p>This implementation is simplistic in that it is not able to
	 * handle factory methods and InstantiationAwareBeanPostProcessors.
	 * It only predicts the bean type correctly for a standard bean.
	 * To be overridden in subclasses, applying more sophisticated type detection.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition to determine the type for
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type of the bean, or {@code null} if not predictable
	 */
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = mbd.getTargetType();
		if (targetType != null) {
			return targetType;
		}
		if (mbd.getFactoryMethodName() != null) {
			return null;
		}
		return resolveBeanClass(mbd, beanName, typesToMatch);
	}

	/**
	 * Check whether the given bean is defined as a {@link FactoryBean}.
	 * @param beanName the name of the bean
	 * @param mbd the corresponding bean definition
	 */
	protected boolean isFactoryBean(String beanName, RootBeanDefinition mbd) {
		// 获取RootBeanDefinition的isFactoryBean属性
		Boolean result = mbd.isFactoryBean;
		// 如果结果为空
		if (result == null) {
			// 拿到beanName对应的bean实例的类型
			Class<?> beanType = predictBeanType(beanName, mbd, FactoryBean.class);
			// 返回beanType是否为factoryBean本身、子类或子接口
			result = (beanType != null && FactoryBean.class.isAssignableFrom(beanType));
			mbd.isFactoryBean = result;
		}
		// 如果不为空，直接返回
		return result;
	}

	/**
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean
	 * already. The implementation is allowed to instantiate the target factory bean if
	 * {@code allowInit} is {@code true} and the type cannot be determined another way;
	 * otherwise it is restricted to introspecting signatures and related metadata.
	 * <p>If no {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} if set on the bean definition
	 * and {@code allowInit} is {@code true}, the default implementation will create
	 * the FactoryBean via {@code getBean} to call its {@code getObjectType} method.
	 * Subclasses are encouraged to optimize this, typically by inspecting the generic
	 * signature of the factory bean class or the factory method that creates it.
	 * If subclasses do instantiate the FactoryBean, they should consider trying the
	 * {@code getObjectType} method without fully populating the bean. If this fails,
	 * a full FactoryBean creation as performed by this implementation should be used
	 * as fallback.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param allowInit if initialization of the FactoryBean is permitted if the type
	 * cannot be determined another way
	 * @return the type for the bean if determinable, otherwise {@code ResolvableType.NONE}
	 * @since 5.2
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 */
	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
		if (result != ResolvableType.NONE) {
			return result;
		}

		if (allowInit && mbd.isSingleton()) {
			try {
				FactoryBean<?> factoryBean = doGetBean(FACTORY_BEAN_PREFIX + beanName, FactoryBean.class, null, true);
				Class<?> objectType = getTypeForFactoryBean(factoryBean);
				return (objectType != null ? ResolvableType.forClass(objectType) : ResolvableType.NONE);
			}
			catch (BeanCreationException ex) {
				if (ex.contains(BeanCurrentlyInCreationException.class)) {
					logger.trace(LogMessage.format("Bean currently in creation on FactoryBean type check: %s", ex));
				}
				else if (mbd.isLazyInit()) {
					logger.trace(LogMessage.format("Bean creation exception on lazy FactoryBean type check: %s", ex));
				}
				else {
					logger.debug(LogMessage.format("Bean creation exception on eager FactoryBean type check: %s", ex));
				}
				onSuppressedException(ex);
			}
		}
		return ResolvableType.NONE;
	}

	/**
	 * Determine the bean type for a FactoryBean by inspecting its attributes for a
	 * {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} value.
	 * @param attributes the attributes to inspect
	 * @return a {@link ResolvableType} extracted from the attributes or
	 * {@code ResolvableType.NONE}
	 * @since 5.2
	 */
	ResolvableType getTypeForFactoryBeanFromAttributes(AttributeAccessor attributes) {
		Object attribute = attributes.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
		if (attribute instanceof ResolvableType) {
			return (ResolvableType) attribute;
		}
		if (attribute instanceof Class) {
			return ResolvableType.forClass((Class<?>) attribute);
		}
		return ResolvableType.NONE;
	}

	/**
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean already.
	 * <p>The default implementation creates the FactoryBean via {@code getBean}
	 * to call its {@code getObjectType} method. Subclasses are encouraged to optimize
	 * this, typically by just instantiating the FactoryBean but not populating it yet,
	 * trying whether its {@code getObjectType} method already returns a type.
	 * If no type found, a full FactoryBean creation as performed by this implementation
	 * should be used as fallback.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 * @deprecated since 5.2 in favor of {@link #getTypeForFactoryBean(String, RootBeanDefinition, boolean)}
	 */
	@Nullable
	@Deprecated
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		return getTypeForFactoryBean(beanName, mbd, true).resolve();
	}

	/**
	 * Mark the specified bean as already created (or about to be created).
	 * <p>This allows the bean factory to optimize its caching for repeated
	 * creation of the specified bean.
	 * @param beanName the name of the bean
	 */
	protected void markBeanAsCreated(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			// alreadyCreated：已经创建的集合
			// 如果"已经创建的集合"不包含当前beanName，就可以进来
			synchronized (this.mergedBeanDefinitions) {
				if (!this.alreadyCreated.contains(beanName)) {
					// 如果"已经创建的集合"不包含当前beanName，就可以进来 —— DCL双重检测

					// Let the bean definition get re-merged now that we're actually creating
					// the bean... just in case some of its metadata changed in the meantime.
					// 当我们需要实际创建bean的时候，需要对当前bean来进行重新合并，以防止些元数据被修改
					clearMergedBeanDefinition(beanName);
					// 添加到alreadyCreated集合做一个记录，意味着当前bean要被创建了！
					/**
					 * 🤔️为什么不采用标识的方式来标识当前bean正在创建中呢，而是用集合呢？
					 * 用标识的方式也能实现，但是要获取到beanName对应的bd，然后再判断，而且这个标识与bd混合了，能把对应的一些属性，一些类型区分开，最好区分开，不要把所有的东西放在一块
					 */
					this.alreadyCreated.add(beanName);
				}
			}
		}
	}

	/**
	 * Perform appropriate cleanup of cached metadata after bean creation failed.
	 * @param beanName the name of the bean
	 */
	protected void cleanupAfterBeanCreationFailure(String beanName) {
		synchronized (this.mergedBeanDefinitions) {
			this.alreadyCreated.remove(beanName);
		}
	}

	/**
	 * Determine whether the specified bean is eligible for having
	 * its bean definition metadata cached.
	 * @param beanName the name of the bean
	 * @return {@code true} if the bean's metadata may be cached
	 * at this point already
	 */
	protected boolean isBeanEligibleForMetadataCaching(String beanName) {
		return this.alreadyCreated.contains(beanName);
	}

	/**
	 * Remove the singleton instance (if any) for the given bean name,
	 * but only if it hasn't been used for other purposes than type checking.
	 * 删除给定bean名称的单例实例（如果有的话），但前提是该类型仅用于类型检查以外的用途。
	 * @param beanName the name of the bean
	 * @return {@code true} if actually removed, {@code false} otherwise
	 */
	protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			/* 1、alreadyCreated当中没有包含 */
			removeSingleton(beanName);
			return true;
		}
		else {
			/* 2、alreadyCreated当中已经包含了 */
			// alreadyCreated当中包含beanName，为true，然后取反是false，所以走这里
			// 也就是说alreadyCreated当中已经包含了，就走这里
			return false;
		}
	}

	/**
	 * Check whether this factory's bean creation phase already started,
	 * i.e. whether any bean has been marked as created in the meantime.
	 * @since 4.2.2
	 * @see #markBeanAsCreated
	 */
	protected boolean hasBeanCreationStarted() {
		return !this.alreadyCreated.isEmpty();
	}

	/**
	 * Get the object for the given bean instance, either the bean
	 * instance itself or its created object in case of a FactoryBean.
	 * @param beanInstance the shared bean instance
	 * @param name the name that may include factory dereference prefix
	 * @param beanName the canonical bean name
	 * @param mbd the merged bean definition
	 * @return the object to expose for the bean
	 */
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name/* 原始beanName */, String beanName/* 去掉&的beanName */, @Nullable RootBeanDefinition mbd) {

		// 总结：
		// 1、name是&开头，且name对应的bean是FactoryBean，就返回该FactoryBean
		// 2、name是&开头，但是name对应的bean不是FactoryBean，就抛出一个错误
		// 3、name不是&开头，且name对应的bean也不是FactoryBean，就返回该普通bean
		// 4、name不是&开头，但是name对应的bean是FactoryBean，就返回FactoryBean#getObject()

		/*

		1、判断getBean(name)中传入的name是不是以&符号开头，

		name是&开头的话，并且name对应的bean是FactoryBean类型，就返回这个bean(也就是FactoryBean实例；注意，不是Factory#getObject()得到的对象)

		*/
		// Don't let calling code try to dereference the factory if the bean isn't a factory. - 如果Bean不是工厂，则不要让调用代码尝试取消引用工厂。

		// 判断name的开头是否包含&符号 —— 判断name是否有FactoryBean标识的前缀
		// 如果name包含&，就代表获取的是FactoryBean实例，而不是Factory#getObject()返回的对象。所以直接返回FactoryBean实例。
		if (BeanFactoryUtils.isFactoryDereference/* 是工厂取消引用 */(name)/* 判断name的开头是否包含&符号 */) {
			if (beanInstance instanceof NullBean) {
				return beanInstance;
			}
			if (!(beanInstance instanceof FactoryBean)) {
				// name以&开头，但是不是FactoryBean，那么就抛出一个错误
				throw new BeanIsNotAFactoryException/* Bean不是工厂例外 */(beanName, beanInstance.getClass());
			}
			if (mbd != null) {
				mbd.isFactoryBean = true;
			}

			// 如果name以&开头，那么就直接返回FactoryBean实例
			return beanInstance;
		}

		/* 2、name不是以&符号开头，并且name对应的bean不是FactoryBean类型，就直接返回 */

		// Now we have the bean instance, which may be a normal bean or a FactoryBean.
		// If it's a FactoryBean, we use it to create a bean instance, unless the
		// caller actually wants a reference to the factory.
		// 当我们有了bean的实例之后，这个实例可能是正常的bean,也可能是FactoryBean,如果是FactoryBean那么就直接创建实例，
		// 但是如果用户想要直接获取工厂实例而不是工厂的getObject方法对应的实例，那么传入的参数应该加&前缀
		if (!(beanInstance instanceof FactoryBean)) {
			// 如果不是FactoryBean就直接返回
			return beanInstance;
		}

		/* 3、name不是以&符号开头，并且name对应的bean是FactoryBean类型，那么就获取FactoryBean#getObject()对应的对象进行返回！ */

		Object object = null;
		if (mbd != null) {
			mbd.isFactoryBean = true;
		}
		else {
			/* 3.1、尝试从factoryBeanObjectCache map缓存中获取当前FactoryBean#getObject()对应的bean */
			object = getCachedObjectForFactoryBean(beanName);
		}
		/* 3.2、如果factoryBeanObjectCache map缓存中没有获取到当前FactoryBean#getObject()对应的bean，那么就直接调用当前FactoryBean#getObject()进行创建和获取 */
		if (object == null) {
			// Return bean instance from factory.
			// 将beanInstance转换为FactoryBean类型
			FactoryBean<?> factory = (FactoryBean<?>) beanInstance;
			// Caches object obtained from FactoryBean if it is a singleton.
			if (mbd == null && containsBeanDefinition(beanName)) {
				// 将存储xml配置文件的GenericBeanDefinition转换为RootBeanDefinition,如果指定BeanName是子Bean的话，同时会合并父类的相关属性
				mbd = getMergedLocalBeanDefinition(beanName);
			}
			// 判断当前bean是否是用户定义的，而不是应用程序本身定义的
			boolean synthetic = (mbd != null && mbd.isSynthetic());
			// ⚠️里面调用了FactoryBean#getObject()
			object = getObjectFromFactoryBean(factory, beanName, !synthetic);
		}
		return object;
	}

	/**
	 * Determine whether the given bean name is already in use within this factory,
	 * i.e. whether there is a local bean or alias registered under this name or
	 * an inner bean created with this name.
	 * @param beanName the name to check
	 */
	public boolean isBeanNameInUse(String beanName) {
		return isAlias(beanName) || containsLocalBean(beanName) || hasDependentBean(beanName);
	}

	/**
	 * Determine whether the given bean requires destruction on shutdown.
	 * <p>The default implementation checks the DisposableBean interface as well as
	 * a specified destroy method and registered DestructionAwareBeanPostProcessors.
	 *
	 * 确定给定的 bean 是否需要在关闭时销毁。 <p>默认实现检查 DisposableBean 接口以及指定的销毁方法和注册的 DestructionAwareBeanPostProcessors。
	 *
	 * @param bean the bean instance to check
	 * @param mbd the corresponding bean definition
	 * @see org.springframework.beans.factory.DisposableBean
	 * @see AbstractBeanDefinition#getDestroyMethodName()
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean requiresDestruction(Object bean, RootBeanDefinition mbd) {
		/**
		 * DestructionAwareBeanPostProcessors；该处理器将在容器关闭时(ad.close())去处理单例Bean
		 */
		// 以下两个条件之一成立：
		// 1、bean类不是NullBean && bean有destroy方法
		// 2、bean类不是NullBean && bean没有destroy方法 && 该工厂持有一个DestructionAwareBeanPostProcessor && Bean需要被毁灭
		return (// 如果bean类不是NullBean
				bean.getClass() != NullBean.class &&
				// 如果bean有destroy方法 || 该工厂持有一个DestructionAwareBeanPostProcessor
				(DisposableBeanAdapter.hasDestroyMethod(bean, mbd) || (hasDestructionAwareBeanPostProcessors() &&
						// Bean有应用于它的可识别销毁的后处理器（bean是否需要被毁灭）
						DisposableBeanAdapter.hasApplicableProcessors(bean, getBeanPostProcessors()))));
	}

	/**
	 * 将给定Bean添加到该工厂中的可丢弃Bean列表中，注册器可丢弃Bean接口和/或在工厂关闭时调用给定销毁方法(如果适用)。只适用单例
	 *
	 * Add the given bean to the list of disposable beans in this factory,
	 * registering its DisposableBean interface and/or the given destroy method
	 * to be called on factory shutdown (if applicable). Only applies to singletons.
	 *
	 * 将给定的 bean 添加到该工厂的一次性 bean 列表中，注册其 DisposableBean 接口和或在工厂关闭时调用的给定销毁方法（如果适用）。仅适用于单例。
	 *
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 * @param mbd the bean definition for the bean
	 * @see RootBeanDefinition#isSingleton
	 * @see RootBeanDefinition#getDependsOn
	 * @see #registerDisposableBean
	 * @see #registerDependentBean
	 */
	protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {

		/* 手动关系容器时：为了给我们的销毁来进行相关的使用的，如果需要销毁我就用，不需要销毁就不用 */

		// 如果有安全管理器，获取其访问控制上下文
		AccessControlContext acc = (System.getSecurityManager() != null ? getAccessControlContext() : null);
		// 如果mbd不是Prototype作用域 && bean在关闭时需要被销毁
		if (!mbd.isPrototype()/* 是不是原型的 */ && requiresDestruction(bean, mbd)/* bean在关闭时是否需要被销毁 */) {
			if (mbd.isSingleton()) {
				// Register a DisposableBean implementation that performs all destruction
				// work for the given bean: DestructionAwareBeanPostProcessors,
				// DisposableBean interface, custom destroy method.
				// 上面的翻译：注册一个为给定 bean 执行所有销毁工作的 DisposableBean 实现：DestructionAwareBeanPostProcessors、DisposableBean 接口、自定义销毁方法。

				// 注册一个一次性Bean实现来执行给定Bean的销毁工作：DestructionAwareBeanPostProcessors 一次性Bean接口，自定义销毁方法
				// DisposableBean：实际一次性Bean和可运行接口适配器，对给定Bean实例执行各种销毁步骤

				// ⚠️构建Bean对应的DisposableBeanAdapter对象，与beanName绑定到注册中心的一次性Bean列表中
				// 往某一个集合里面添加bean，在进行销毁的时候，可以把集合里面的每一个bean对象拿出来，拿出来进行垃圾回收也好，进行对象销毁也好，我把空间给释放掉
				registerDisposableBean/* 注册一次性Bean */(beanName,
						new DisposableBeanAdapter/* 一次性Bean适配器 */(bean, beanName, mbd, getBeanPostProcessors(), acc));
			}
			else {
				// A bean with a custom scope... —— 具有自定义范围的 bean...
				// 具有自定义作用域的Bean

				// 获取mbd的作用域
				Scope scope = this.scopes.get(mbd.getScope());
				// 如果作用域为null
				if (scope == null) {
					// 非法状态异常：无作用登记为作用名称'mbd.getScope'
					throw new IllegalStateException("No Scope registered for scope name '" /* 没有为作用域名称注册作用域 */+ mbd.getScope() + "'");
				}
				// 注册一个回调，在销毁作用域中将构建Bean对应的DisposableBeanAdapter对象指定
				// (或者在销毁整个作用域时执行，如果作用域没有销毁单个对象，而是全部终止)
				scope.registerDestructionCallback/* 注册销毁回调 */(beanName,
						new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), acc));
			}
		}
	}


	//---------------------------------------------------------------------
	// Abstract methods to be implemented by subclasses
	//---------------------------------------------------------------------

	/**
	 * Check if this bean factory contains a bean definition with the given name.
	 * Does not consider any hierarchy this factory may participate in.
	 * Invoked by {@code containsBean} when no cached singleton instance is found.
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * @param beanName the name of the bean to look for
	 * @return if this bean factory contains a bean definition with the given name
	 * @see #containsBean
	 * @see org.springframework.beans.factory.ListableBeanFactory#containsBeanDefinition
	 */
	protected abstract boolean containsBeanDefinition(String beanName);

	/**
	 * Return the bean definition for the given bean name.
	 * Subclasses should normally implement caching, as this method is invoked
	 * by this class every time bean definition metadata is needed.
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * @param beanName the name of the bean to find a definition for
	 * @return the BeanDefinition for this prototype name (never {@code null})
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException
	 * if the bean definition cannot be resolved
	 * @throws BeansException in case of errors
	 * @see RootBeanDefinition
	 * @see ChildBeanDefinition
	 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory#getBeanDefinition
	 */
	protected abstract BeanDefinition getBeanDefinition(String beanName) throws BeansException;

	/**
	 * Create a bean instance for the given merged bean definition (and arguments).
	 * The bean definition will already have been merged with the parent definition
	 * in case of a child definition.
	 * <p>All bean retrieval methods delegate to this method for actual bean creation.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 */
	protected abstract Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException;

}
