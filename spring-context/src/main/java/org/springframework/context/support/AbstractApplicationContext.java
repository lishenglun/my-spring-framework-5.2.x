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

package org.springframework.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.support.ResourceEditorRegistrar;
import org.springframework.context.*;
import org.springframework.context.event.*;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.context.weaving.LoadTimeWeaverAware;
import org.springframework.context.weaving.LoadTimeWeaverAwareProcessor;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract implementation of the {@link org.springframework.context.ApplicationContext}
 * interface. Doesn't mandate the type of storage used for configuration; simply
 * implements common context functionality. Uses the Template Method design pattern,
 * requiring concrete subclasses to implement abstract methods.
 *
 * <p>In contrast to a plain BeanFactory, an ApplicationContext is supposed
 * to detect special beans defined in its internal bean factory:
 * Therefore, this class automatically registers
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor BeanFactoryPostProcessors},
 * {@link org.springframework.beans.factory.config.BeanPostProcessor BeanPostProcessors},
 * and {@link org.springframework.context.ApplicationListener ApplicationListeners}
 * which are defined as beans in the context.
 *
 * <p>A {@link org.springframework.context.MessageSource} may also be supplied
 * as a bean in the context, with the name "messageSource"; otherwise, message
 * resolution is delegated to the parent context. Furthermore, a multicaster
 * for application events can be supplied as an "applicationEventMulticaster" bean
 * of type {@link org.springframework.context.event.ApplicationEventMulticaster}
 * in the context; otherwise, a default multicaster of type
 * {@link org.springframework.context.event.SimpleApplicationEventMulticaster} will be used.
 *
 * <p>Implements resource loading by extending
 * {@link org.springframework.core.io.DefaultResourceLoader}.
 * Consequently treats non-URL resource paths as class path resources
 * (supporting full class path resource names that include the package path,
 * e.g. "mypackage/myresource.dat"), unless the {@link #getResourceByPath}
 * method is overridden in a subclass.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @see #refreshBeanFactory
 * @see #getBeanFactory
 * @see org.springframework.beans.factory.config.BeanFactoryPostProcessor
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @see org.springframework.context.event.ApplicationEventMulticaster
 * @see org.springframework.context.ApplicationListener
 * @see org.springframework.context.MessageSource
 * @since January 21, 2001
 */
public abstract class AbstractApplicationContext extends DefaultResourceLoader
		implements ConfigurableApplicationContext {

	/**
	 * Name of the MessageSource bean in the factory.
	 * If none is supplied, message resolution is delegated to the parent.
	 *
	 * @see MessageSource
	 */
	public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";

	/**
	 * Name of the LifecycleProcessor bean in the factory.
	 * If none is supplied, a DefaultLifecycleProcessor is used.
	 *
	 * @see org.springframework.context.LifecycleProcessor
	 * @see org.springframework.context.support.DefaultLifecycleProcessor
	 */
	public static final String LIFECYCLE_PROCESSOR_BEAN_NAME = "lifecycleProcessor";

	/**
	 * Name of the ApplicationEventMulticaster bean in the factory.
	 * If none is supplied, a default SimpleApplicationEventMulticaster is used.
	 *
	 * @see org.springframework.context.event.ApplicationEventMulticaster
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 */
	// 广播器beanName
	public static final String APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster"/* 应用事件多播 */;


	static {
		// 优先加载上下文关闭事件来防止奇怪的类加载问题，在应用程序关闭的时候
		// Eagerly load the ContextClosedEvent class to avoid weird classloader issues
		// on application shutdown in WebLogic 8.1. (Reported by Dustin Woods.)
		ContextClosedEvent.class.getName();
	}


	/**
	 * Logger used by this class. Available to subclasses.
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * Unique id for this context, if any.
	 */
	private String id = ObjectUtils.identityToString(this);

	/**
	 * Display name.
	 */
	private String displayName = ObjectUtils.identityToString(this);

	/**
	 * Parent context.
	 */
	@Nullable
	private ApplicationContext parent;

	/**
	 * Environment used by this context.
	 */
	@Nullable
	private ConfigurableEnvironment environment;

	/**
	 * BeanFactoryPostProcessors to apply on refresh. - BeanFactoryPostProcessors应用于refresh()。
	 */
	private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors = new ArrayList<>();

	/**
	 * System time in milliseconds when this context started.
	 */
	private long startupDate;

	/**
	 * Flag that indicates whether this context is currently active.
	 */
	private final AtomicBoolean active = new AtomicBoolean();

	/**
	 * Flag that indicates whether this context has been closed already.
	 */
	private final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * Synchronization monitor for the "refresh" and "destroy". - 同步监视器的“刷新”和“销毁”。
	 */
	private final Object startupShutdownMonitor = new Object();

	/**
	 * Reference to the JVM shutdown hook, if registered.
	 */
	@Nullable
	private Thread shutdownHook;

	/**
	 * ResourcePatternResolver used by this context.
	 */
	private ResourcePatternResolver resourcePatternResolver;

	/**
	 * LifecycleProcessor for managing the lifecycle of beans within this context.
	 */
	@Nullable
	private LifecycleProcessor lifecycleProcessor;

	/**
	 * MessageSource we delegate our implementation of this interface to.
	 */
	@Nullable
	private MessageSource messageSource;

	/**
	 * Helper class used in event publishing. —— 事件发布中使用的助手类
	 */
	// 广播器
	// 在容器中没有自定义的广播器时，spring默认使用的广播器是SimpleApplicationEventMulticaster
	@Nullable
	private ApplicationEventMulticaster applicationEventMulticaster;

	/**
	 * 用来存放ApplicationListener的集合对象
	 * <p>
	 * Statically specified listeners.
	 */
	private final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();

	/**
	 * 刷新前注册的本地侦听器。
	 * Local listeners registered before refresh.
	 */
	@Nullable
	private Set<ApplicationListener<?>> earlyApplicationListeners;

	/**
	 * 在多播器设置之前发布的ApplicationEvents
	 * ApplicationEvents published before the multicaster setup.
	 */
	@Nullable
	private Set<ApplicationEvent> earlyApplicationEvents;


	/**
	 * Create a new AbstractApplicationContext with no parent.
	 */
	public AbstractApplicationContext() {
		// 创建一个资源模式解析器（其实就是用来解析xml配置文件的）PathMatchingResourcePatternResolver
		this.resourcePatternResolver = getResourcePatternResolver();
	}

	/**
	 * Create a new AbstractApplicationContext with the given parent context.
	 *
	 * @param parent the parent context
	 */
	public AbstractApplicationContext(@Nullable ApplicationContext parent) {
		this();
		setParent(parent);
	}


	//---------------------------------------------------------------------
	// Implementation of ApplicationContext interface
	//---------------------------------------------------------------------

	/**
	 * Set the unique id of this application context.
	 * <p>Default is the object id of the context instance, or the name
	 * of the context bean if the context is itself defined as a bean.
	 *
	 * @param id the unique id of the context
	 */
	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public String getApplicationName() {
		return "";
	}

	/**
	 * Set a friendly name for this context.
	 * Typically done during initialization of concrete context implementations.
	 * <p>Default is the object id of the context instance.
	 */
	public void setDisplayName(String displayName) {
		Assert.hasLength(displayName, "Display name must not be empty");
		this.displayName = displayName;
	}

	/**
	 * Return a friendly name for this context.
	 *
	 * @return a display name for this context (never {@code null})
	 */
	@Override
	public String getDisplayName() {
		return this.displayName;
	}

	/**
	 * Return the parent context, or {@code null} if there is no parent
	 * (that is, this context is the root of the context hierarchy).
	 */
	@Override
	@Nullable
	public ApplicationContext getParent() {
		return this.parent;
	}

	/**
	 * Set the {@code Environment} for this application context.
	 * <p>Default value is determined by {@link #createEnvironment()}. Replacing the
	 * default with this method is one option but configuration through {@link
	 * #getEnvironment()} should also be considered. In either case, such modifications
	 * should be performed <em>before</em> {@link #refresh()}.
	 *
	 * @see org.springframework.context.support.AbstractApplicationContext#createEnvironment
	 */
	@Override
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	/**
	 * 获取系统环境对象！没有就创建
	 * <p>
	 * Return the {@code Environment} for this application context in configurable
	 * form, allowing for further customization.
	 * <p>If none specified, a default environment will be initialized via
	 * {@link #createEnvironment()}.
	 */
	@Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			// ⚠️创建系统环境对象！
			this.environment = createEnvironment();
		}
		return this.environment;
	}

	/**
	 * Create and return a new {@link StandardEnvironment}.
	 * <p>Subclasses may override this method in order to supply
	 * a custom {@link ConfigurableEnvironment} implementation.
	 */
	protected ConfigurableEnvironment createEnvironment() {
		// StandardEnvironment：标准的环境对象
		return new StandardEnvironment();
	}

	/**
	 * Return this context's internal bean factory as AutowireCapableBeanFactory,
	 * if already available.
	 *
	 * @see #getBeanFactory()
	 */
	@Override
	public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
		return getBeanFactory();
	}

	/**
	 * Return the timestamp (ms) when this context was first loaded.
	 */
	@Override
	public long getStartupDate() {
		return this.startupDate;
	}

	/**
	 * 将给定事件发布到所有监听器
	 * <p>
	 * Publish the given event to all listeners.
	 * <p>Note: Listeners get initialized after the MessageSource, to be able
	 * to access it within listener implementations. Thus, MessageSource
	 * implementations cannot publish events.
	 *
	 * @param event the event to publish (may be application-specific or a
	 *              standard framework event)
	 */
	@Override
	public void publishEvent(ApplicationEvent event) {
		publishEvent(event, null);
	}

	/**
	 * 将给定事件发布到所有监听器
	 * <p>
	 * Publish the given event to all listeners.
	 * <p>Note: Listeners get initialized after the MessageSource, to be able
	 * to access it within listener implementations. Thus, MessageSource
	 * implementations cannot publish events.
	 *
	 * @param event the event to publish (may be an {@link ApplicationEvent}
	 *              or a payload object to be turned into a {@link PayloadApplicationEvent})
	 */
	@Override
	public void publishEvent(Object event) {
		publishEvent(event, null);
	}

	/**
	 * 将给定事件发布到所有监听器
	 * <p>
	 * Publish the given event to all listeners.
	 *
	 * @param event     the event to publish (may be an {@link ApplicationEvent}
	 *                  or a payload object to be turned into a {@link PayloadApplicationEvent})
	 * @param eventType the resolved event type, if known
	 * @since 4.2
	 */
	protected void publishEvent(Object event, @Nullable ResolvableType eventType) {
		// 如果时间为null，则抛出异常
		Assert.notNull(event, "Event must not be null");


		/* 1、先转换为ApplicationEvent */

		// Decorate event as an ApplicationEvent if necessary —— 如有必要，将事件装饰为ApplicationEvent

		ApplicationEvent applicationEvent;
		// 判断是不是一个ApplicationEvent，是的话就转换为ApplicationEvent类型
		if (event instanceof ApplicationEvent) {
			applicationEvent = (ApplicationEvent) event;
		}
		// 不是事件就转为事件
		else {
			// PayloadApplicationEvent：携带任意有效负载的ApplicationEvent。
			// 创建一个新的PayloadApplicationEvent
			applicationEvent = new PayloadApplicationEvent<>(this, event);
			// 如果eventType为null，则eventType = PayloadApplicationEvent类型
			if (eventType == null) {
				// 将applicationEvent转换为PayloadApplicationEvent象，引用其ResolvableType对象
				eventType = ((PayloadApplicationEvent<?>) applicationEvent).getResolvableType();
			}
		}

		// Multicast right now if possible - or lazily once the multicaster is initialized
		// 上面的翻译：如果可能，现在就进行广播 - 或者在广播器初始化后懒惰地进行

		/**
		 * 1、⚠️题外：earlyApplicationEvents：在多播程序设置之前发布的ApplicationEvent
		 */
		// 如果earlyApplicationEvents不为 null（这种情况只在上下文的多播器还没有初始化的情况下才会成立），
		// >>> 则会将applicationEvent添加到earlyApplicationEvents保存起来，待广播器初始化后才继续进行广播到适当的监听器
		if (this.earlyApplicationEvents != null) {
			//将applicationEvent添加到 earlyApplicationEvents
			this.earlyApplicationEvents.add(applicationEvent);
		}
		// 获取事件广播器（多播器），发布事件
		else {
			// ⚠️发布事件
			// 多播applicationEvent到适当的监听器
			// 题外：每次在进行广播的时候，看起来好像是发布了事件，但最终在进行事件广播的时候，是由广播器来进行广播的
			getApplicationEventMulticaster()/* 获取之前创建好的广播器 */.multicastEvent/* 广播事件 */(applicationEvent, eventType);
		}

		// Publish event via parent context as well... —— 也通过父上下文发布事件......
		// 如果存在父容器，那么父容器也发布事件 —— 通过父容器发布事件
		if (this.parent != null) {
			// 如果parent是AbstractApplicationContext的实例
			if (this.parent instanceof AbstractApplicationContext) {
				// 将event多播到所有适合的监听器。如果event不是ApplicationEvent实例，会将其封装成PayloadApplicationEvent对象再进行多播
				((AbstractApplicationContext) this.parent).publishEvent(event, eventType);
			} else {
				// 通知与event事件应用程序注册的所有匹配的监听器
				this.parent.publishEvent(event);
			}
		}
	}

	/**
	 * Return the internal ApplicationEventMulticaster used by the context.
	 *
	 * @return the internal ApplicationEventMulticaster (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	ApplicationEventMulticaster getApplicationEventMulticaster() throws IllegalStateException {
		if (this.applicationEventMulticaster == null) {
			throw new IllegalStateException("ApplicationEventMulticaster not initialized - " +
					"call 'refresh' before multicasting events via the context: " + this);
		}
		return this.applicationEventMulticaster;
	}

	/**
	 * Return the internal LifecycleProcessor used by the context.
	 *
	 * @return the internal LifecycleProcessor (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	LifecycleProcessor getLifecycleProcessor() throws IllegalStateException {
		if (this.lifecycleProcessor == null) {
			throw new IllegalStateException("LifecycleProcessor not initialized - " +
					"call 'refresh' before invoking lifecycle methods via the context: " + this);
		}
		return this.lifecycleProcessor;
	}

	/**
	 * Return the ResourcePatternResolver to use for resolving location patterns
	 * into Resource instances. Default is a
	 * {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver},
	 * supporting Ant-style location patterns.
	 * <p>Can be overridden in subclasses, for extended resolution strategies,
	 * for example in a web environment.
	 * <p><b>Do not call this when needing to resolve a location pattern.</b>
	 * Call the context's {@code getResources} method instead, which
	 * will delegate to the ResourcePatternResolver.
	 *
	 * @return the ResourcePatternResolver for this context
	 * @see #getResources
	 * @see org.springframework.core.io.support.PathMatchingResourcePatternResolver
	 */
	protected ResourcePatternResolver getResourcePatternResolver() {
		// 路径匹配资源模式解析器
		return new PathMatchingResourcePatternResolver(this);
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableApplicationContext interface
	//---------------------------------------------------------------------

	/**
	 * Set the parent of this application context.
	 * <p>The parent {@linkplain ApplicationContext#getEnvironment() environment} is
	 * {@linkplain ConfigurableEnvironment#merge(ConfigurableEnvironment) merged} with
	 * this (child) application context environment if the parent is non-{@code null} and
	 * its environment is an instance of {@link ConfigurableEnvironment}.
	 *
	 * @see ConfigurableEnvironment#merge(ConfigurableEnvironment)
	 */
	@Override
	public void setParent(@Nullable ApplicationContext parent) {
		this.parent = parent;
		if (parent != null) {
			Environment parentEnvironment = parent.getEnvironment();
			if (parentEnvironment instanceof ConfigurableEnvironment) {
				getEnvironment().merge((ConfigurableEnvironment) parentEnvironment);
			}
		}
	}

	@Override
	public void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor) {
		Assert.notNull(postProcessor, "BeanFactoryPostProcessor must not be null");
		this.beanFactoryPostProcessors.add(postProcessor);
	}

	/**
	 * Return the list of BeanFactoryPostProcessors that will get applied
	 * to the internal BeanFactory.
	 */
	public List<BeanFactoryPostProcessor> getBeanFactoryPostProcessors() {
		return this.beanFactoryPostProcessors;
	}

	@Override
	public void addApplicationListener(ApplicationListener<?> listener) {
		Assert.notNull(listener, "ApplicationListener must not be null");
		if (this.applicationEventMulticaster != null) {
			this.applicationEventMulticaster.addApplicationListener(listener);
		}
		this.applicationListeners.add(listener);
	}

	/**
	 * Return the list of statically specified ApplicationListeners. —— 返回静态指定的 ApplicationListener 列表。
	 */
	public Collection<ApplicationListener<?>> getApplicationListeners() {
		return this.applicationListeners;
	}

	@Override
	public void refresh() throws BeansException, IllegalStateException {
		synchronized (this.startupShutdownMonitor) {
			/* 一、实例化前的准备工作（为实例化做准备） */

			/* 1、容器刷新前的准备工作 —— 在整个容器刷新之前，做一些准备工作（前戏，容器刷新前的准备工作） */
			/**
			 * 准备工作:
			 * 1、设置当前容器的启动时间
			 * 2、设置当前容器关闭状态为false，也就是代表"未关闭"
			 * 3、设置当前容器的活跃状态为true，也就是代表活跃
			 * 4、获取当前系统的环境对象Environment，并加载当前系统的属性值到Environment对象中
			 * ⚠️在️创建系统环境对象的时候，加载了系统属性值(systemProperties)和系统环境值(systemEnvironment)
			 * 5、准备监听器和事件的集合对象，默认为空集合
			 * 建立集合是因为，我一个事件发布之后，可能有n多个监听器。遍历集合中的监听器来匹配事件，匹配得上就处理
			 */
			// Prepare this context for refreshing. - 准备此上下文，以进行刷新。
			prepareRefresh/* 准备刷新 */();

			/* 2、创建BeanFactory(容器对象)、和加载、解析配置文件，注册bd */

			/**
			 * 1、创建一个BeanFactory：DefaultListableBeanFactory
			 * 2、以及解析xml配置文件，得到BeanDefinition（识别xml文件里面配置的BeanDefinition），放入DefaultListableBeanFactory的beanDefinitionMap当中！！！
			 * ⚠️⚠️⚠️注意：读取和解析配置文件，形成BeanDefinition，放入beanDefinitionMap的工作，是在这里完成的！！！
			 *
			 * 题外：如果是ClassPathXmlApplicationContext，读取xml配置文件的方式，那么当配置文件中有配置<context:component-scan></context:component-scan>标签
			 * >>> 那么就会加载到6个BeanDefinition存在于beanDefinitionMap和beanDefinitionNames中：
			 * >>> org.springframework.context.annotation.internalConfigurationAnnotationProcessor
			 * >>> org.springframework.context.annotation.internalAutowiredAnnotationProcessor
			 * >>> org.springframework.context.annotation.internalCommonAnnotationProcessor
			 * >>> org.springframework.context.event.internalEventListenerProcessor
			 * >>> org.springframework.context.event.internalEventListenerFactory
			 * >>> com.springstudy.mashibing.s_08.MyBeanFactoryPostProcessor#0
			 * 题外：如果是AnnotationConfigApplicationContext的方式，那么在其
			 * >>> AnnotationConfigApplicationContext(Class<?>... componentClasses) ——> this() ——> new AnnotatedBeanDefinitionReader(this); ——> this(registry, getOrCreateEnvironment(registry)) ——> AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry)
			 * >>> 中会加载这6个BeanDefinition存在于beanDefinitionMap和beanDefinitionNames中
			 */
			// Tell the subclass to refresh the internal bean factory. - 告诉子类刷新内部bean工厂
			// 创建容器对象：DefaultListableBeanFactory
			// 加载xml配置文件的属性值到当前工厂中，最重要的就是BeanDefinition
			ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();/* 获得鲜豆工厂 */

			/* 3、对容器对象做一些准备工作：给容器对象设置一些属性值 */

			/**
			 * 初始化beanFactory：也就是，往beanFactory里面设置基本的属性值！
			 *
			 * 准备bean工厂的一些属性，例如上下文的ClassLoader和后处理器。
			 *
			 * 里面一共三个后置处理器：
			 * 		ApplicationContextAwareProcessor implements BeanPostProcessor
			 * 		ApplicationListenerDetector implements MergedBeanDefinitionPostProcessor 间接实现BeanPostProcessor
			 * 		LoadTimeWeaverAwareProcessor implements BeanPostProcessor
			 *
			 * 只添加了两个：ApplicationContextAwareProcessor、ApplicationListenerDetector
			 */
			// Prepare the bean factory for use in this context. - 准备在此上下文中使用的bean工厂。
			prepareBeanFactory/* 准备bean工厂 */(beanFactory);

			try {
				/*

				4、后置处理BeanFactory

				一个钩子方法。上面容器创建完成了，也初始化完成了，这里就留一个方法，对容器进行后置处理的扩展操作

				*/
				/**
				 * 1、spring中是空实现，但是springmvc中有具体的实现：XmlWebApplicationContext
				 * XmlWebApplicationContext extends AbstractRefreshableWebApplicationContext，
				 * 所以走的是AbstractRefreshableWebApplicationContext#postProcessBeanFactory()
				 */
				// Allows post-processing of the bean factory in context subclasses. - 允许在上下文子类中对bean工厂进行后处理。

				// 后置处理BeanFactory
				// >>> 上面容器创建完成了，也初始化完成了，这里就留一个方法，对容器进行后置处理的扩展操作
				// 题外：钩子方法、模版方法。
				postProcessBeanFactory(beanFactory);

				/*

				5、调用BeanDefinitionRegistryPostProcessor(BDRPP)、BeanFactoryPostProcessor(BFPP)

				题外：BDRPP是操作BeanDefinitionRegister的
				题外：BFPP是操作BeanFactory，对其进行扩展，可以修饰BeanFactory里面的一些属性
				>>> 占位符替换的

				 */

				/**
				 * ⚠️1、执行所有BeanFactoryPostProcessor/BeanDefinitionRegistryPostProcessor(先执行BeanDefinitionRegistryPostProcessor) bean
				 * 		Spring有两个实现了BeanFactoryPostProcessors
				 * 			ConfigurationClassPostProcessor implements BeanDefinitionRegistryPostProcessor extends BeanFactoryPostProcessor
				 * 			EventListenerMethodProcessor implements BeanFactoryPostProcessor
				 * 2、解析配置类，得到所有的beanDefinition，放入beanDefinitionMap当中
				 * 3、处理所有的@Import注解
				 * 4、为@Configuration类生成cglib代理对象
				 */
				// 💡内部将CustomEditorConfigurer的customEditors，转移到DefaultListableBeanFactory的customEditors里面了！！！
				// Invoke factory processors registered as beans in the context. - 调用在上下文中注册为bean的工厂处理器。
				// BeanFactoryPostProcessor：核心点是用来操作beanFactory对象的
				invokeBeanFactoryPostProcessors(beanFactory);

				/* 下面在finishBeanFactoryInitialization()之前的方法，都是做bean实例化之前的准备工作 */

				/*

				6、实例化并注册所有BeanPostProcessor

				题外：BPP是操作Bean对象的，用于对Bean的扩展
				>>> AOP就是通过BPP扩展实现的

				 */

				/**
				 * 实例化并注册所有 BeanPostProcessor bean
				 *
				 * ⚠️invokeBeanFactoryPostProcessors()针对的是BeanFactory，registerBeanPostProcessors()针对的是Bean，操作对象不一样；
				 * >>> 操作方式也不一样，invoke是执行的意思，register是注册的意思，注册并没有执行，在实例化bean完成之后，才会进行执行
				 */
				/**
				 * AOP的自动动态代理创建器是在这里完成创建的，例如：xml方式的AspectJAwareAdvisorAutoProxyCreator
				 */
				// Register bean processors that intercept bean creation.
				// 注册bean处理器，这里只是注册功能，真正调用的是getBean()
				registerBeanPostProcessors(beanFactory);

				/*

				7、国际化处理

				 */

				// Initialize message source for this context. - 为此上下文初始化消息源。
				// 为上下文初始化message源，即不同语言的消息体，国际化处理
				initMessageSource();

				/*

				8、初始化事件广播器（或者叫：多播器、事件监听器，多路广播器）
				（1）先看下容器(一级缓存)中是否存在自定义的多播器
				（2）如果不存在，则创建一个spring提供的默认的多播器，然后注册到容器（一级缓存）当中

				 */

				/**
				 * ⚠️initApplicationEventMulticaster()和registerListeners()牵扯到spring非常重要的设计模式：观察者模式。
				 * 在传统的观察者模式上做了改动：
				 * 在原先的观察者模式基础之上，加了一个事件驱动，做了一个更细腻度的划分。当做了一个更细腻度的划分，相当于做了一次解耦操作。
				 *
				 * spring观察者模式分为这几个东西：
				 * 1、事件(监听事件)：被观察者具体要执行的动作
				 * 		ApplicationContextEvent extends ApplicationEvent
				 * 		ApplicationEvent extends EventObject
				 * 2、监听器：也就是观察者。可能存在多个，接受不同的事件，来做不同的处理工作
				 * 3、多播器：通知的方式。把"被观察者遍历观察者通知消息"的操作拿出来，委托给一个多播器来进行消息通知，或者说通过观察者进行不同的操作
				 * 		ApplicationEventMulticaster
				 * 4、事件源(监听源)：谁来调用或者执行，去发布具体的事件。谁发起的事件。
				 * 例如这里的事件源就是应用程序（this）
				 *
				 * ⚠️以事件来驱动。事件源发布(触发)事件后，然后多播器才能广播事件；多播器广播事件后，然后监听器才能接收到事件，开始执行！
				 * 其实也相当于，本身就是发布/订阅模式。
				 *
				 * 逻辑执行过程:
				 * 1、事件源来发布不同的事件
				 * 2、当发布事件之后会调用多播器的方法来进行事件广播，由多播器去触发具体的监听器去执行操作
				 * 3、监听器接收到具体的事件之后，可以验证匹配是否能处理当前事件，如果可以，直接处理，不行，不做任何操作
				 *
				 * 题外：为什么要提前初始化好监听器对象，而不是在用的时候再创建？答：分块、解耦。提前把事情做好，后面用的时候直接用，不在其它地方进行创建，来干扰其它地方的逻辑！
				 */
				// Initialize event multicaster for this context. - 为此上下文初始化事件多播器。
				// 初始化事件广播器（或者叫：多播器、事件监听器，多路广播器）
				initApplicationEventMulticaster/* 初始化应用程序的事件多播器 */();

				/*

				9、onRefresh()：空壳方法，用于子类实现，初始化其它特殊bean。

				题外：spring中没有任何实现，模版方法，但是在spring boot中启动了web容器。spring boot中的嵌入和启动tomcat是在这里面实现的

				 */

				/**
				 * 1、spring中是空实现，但是springmvc中有具体的实现：XmlWebApplicationContext
				 * XmlWebApplicationContext extends AbstractRefreshableWebApplicationContext，
				 * 所以走的是AbstractRefreshableWebApplicationContext#onRefresh()
				 */
				// Initialize other special beans in specific context subclasses. - 在特定上下文子类中初始化其他特殊bean。
				// 空壳方法，留给子类来初始化其它的bean
				onRefresh();

				/* 10、注册监听器到广播器中 */

				// Check for listener beans and register them. —— 检查侦听器 bean 并注册它们。
				// 注册监听器到多播器中（在所有注册的bean中查找listener bean，注册到多播器中），为了接受广播的事件！
				// 题外：上面已经初始化好了多播器，有了多播器之后，就要发布事件了，发布的事件，需要有人来监听，所以要注册监听器，为了接受广播的事件
				registerListeners();

				/* 上面相当于做了一堆的准备工作，下面开始真正的实例化bean！ */

				/* 二、实例化 */

				/* 11、️实例化所有剩余的非懒加载的单例bean */

				// AnnotationAutoProxyCreator
				// Instantiate all remaining (non-lazy-init) singletons. - 实例化所有剩余的（非延迟初始化）单例。
				// ⚠️实例化所有剩余的单例（非懒加载的）
				finishBeanFactoryInitialization(beanFactory);

				/* 12、完成刷新 */

				// Last step: publish corresponding event.
				// 完成刷新过程，通知生命周期处理器lifecycleProcessor刷新过程，同时发出ContextRefreshEvent通知别人
				finishRefresh/* 完成刷新 */();
			} catch (BeansException ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Exception encountered during context initialization - " +
							"cancelling refresh attempt: " + ex);
				}

				/* 销毁已经创建的bean */
				// Destroy already created singletons to avoid dangling resources. —— 销毁已经创建的单例以避免悬空资源。
				// 销毁已经创建的bean（为防止bean占用资源，在异常处理中，销毁已经在前面创建的单例bean）
				destroyBeans();

				/* 取消刷新操作，重置容器的active标识为不是活跃的  */
				// Reset 'active' flag. —— 重置active标识
				// 重置当前容器的active标识，标识说已经不是活跃的了
				cancelRefresh(ex);

				// Propagate exception to caller.
				// 抛出异常
				throw ex;
			} finally {

				/* 13、清空运行过程中产生的缓存 */

				// 把缓冲进行一些重置！
				// Reset common introspection caches in Spring's core, since we
				// might not ever need metadata for singleton beans anymore...
				resetCommonCaches();
			}
		}
	}

	/**
	 * Prepare this context for refreshing, setting its startup date and
	 * active flag as well as performing any initialization of property sources.
	 */
	protected void prepareRefresh() {
		// Switch to active. spring容器当前启动的时间
		this.startupDate = System.currentTimeMillis();
		// 容器的关闭标识位
		this.closed.set(false);
		// 容器的激活标志位
		this.active.set(true);

		// 记录日志
		if (logger.isDebugEnabled()) {
			if (logger.isTraceEnabled()) {
				logger.trace("Refreshing " + this);
			} else {
				logger.debug("Refreshing " + getDisplayName());
			}
		}

		// 留给子类覆盖，初始化属性资源 - 在上下文环境中初始化任何占位符属性源【${}里面的属性资源】
		// Initialize any placeholder property sources in the context environment. - 在上下文环境中初始化任何占位符属性源。
		// 初始化某些对应的属性资源
		initPropertySources();

		// 创建并获取环境对象，验证需要的属性文件是否都已经放入环境中，不存在就会报错！
		// >>> org.springframework.core.env.MissingRequiredPropertiesException: The following properties were declared as required but could not be resolved: [abc]
		// Validate that all properties marked as required are resolvable:
		// see ConfigurablePropertyResolver#setRequiredProperties
		getEnvironment().validateRequiredProperties/* 验证必需的属性 */();

		/* 梳理监听器集合 */

		// Store pre-refresh ApplicationListeners...
		// 判断刷新前的应用程序监听器集合是否为空
		if (this.earlyApplicationListeners/* 早期的应用程序监听器 */ == null) {
			// 如果"早期的监听器集合"为空，就创建早期的监听器集合，并把"监听器集合"中的监听器全部放入进去
			// ⚠️这个地方会使得earlyApplicationListeners中有值
			// 题外：在spring mvc中，this.applicationListeners就有值！
			// 题外：在spring boot中，this.applicationListeners就有值！
			this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
		} else {
			// Reset local application listeners to pre-refresh state.
			// 如果"早期的监听器集合"不为空，则清空"监听器集合"里面的东西，然后把"早期的监听器集合"中的所有监听器放入到"监听器集合"中！
			this.applicationListeners/* 应用程序监听器 */.clear();
			this.applicationListeners.addAll(this.earlyApplicationListeners);
		}

		/* 创建事件集合 - 空集合 */

		// Allow for the collection of early ApplicationEvents,
		// to be published once the multicaster is available...
		// 创建刷新前的监听事件集合
		this.earlyApplicationEvents/* 早期申请事件 */ = new LinkedHashSet<>();
	}

	/**
	 * <p>Replace any stub property sources with actual instances.
	 *
	 * @see org.springframework.core.env.PropertySource.StubPropertySource
	 * @see org.springframework.web.context.support.WebApplicationContextUtils#initServletPropertySources
	 */
	protected void initPropertySources() {
		// For subclasses: do nothing by default.
	}

	/**
	 * Tell the subclass to refresh the internal bean factory. —— 告诉子类刷新内部 bean 工厂。
	 *
	 * @return the fresh BeanFactory instance
	 * @see #refreshBeanFactory()
	 * @see #getBeanFactory()
	 */
	protected ConfigurableListableBeanFactory obtainFreshBeanFactory()/* 获取刷新的BeanFactory */ {
		// 初始化BeanFactory，并进行XML文件读取，并将得到的BeanFactory记录在当前实体的属性中！
		// AbstractRefreshableApplicationContext
		refreshBeanFactory();

		// 返回当前实体的BeanFactory属性
		return getBeanFactory();
	}

	/**
	 * 做了beanFactory的基本属性设置工作！
	 * <p>
	 * Configure the factory's standard context characteristics,such as the context's ClassLoader and post-processors. - 配置工厂的标准上下文特征，例如上下文的ClassLoader和后处理器。
	 *
	 * @param beanFactory the BeanFactory to configure
	 */
	protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		/* 1、设置bean的类加载器 */

		// Tell the internal bean factory to use the context's class loader etc. - 告诉内部bean工厂使用上下文的类加载器等。
		beanFactory.setBeanClassLoader(getClassLoader());

		/* 2、设置SpEL表达式的处理器 */

		// 设置一个bean表达解释器，为了能够让我们的beanFactory去解析bean表达式
		beanFactory.setBeanExpressionResolver/* 设置 Bean 表达式解析器 */(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));

		/* 3、添加一个属性编辑器的注册器 */
		/**
		 * 为beanFactory增加一个默认的propertyEditor（属性编辑器），这个主要是对bean的属性等设置管理的一个工具类
		 * 参数的解析工具类，把它放入到DefaultListableBeanFactory的propertyEditorRegistrars set集合里面去
		 */
		// ⚠️通过beanFactory.addPropertyEditorRegistrar()都是放入到DefaultListableBeanFactory的propertyEditorRegistrars set集合里面去
		// 后续会放入每个BeanWrapperImpl的overriddenDefaultEditors set集合里面去，这样BeanWrapperImpl的overriddenDefaultEditors set集合就有了属性编辑器
		beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment())/* 添加一个属性编辑器的注册器 */);

		/*

		4、添加一个BeanPostProcessor：ApplicationContextAwareProcessor
		⚠️题外：Aware接口：设置一些属性值的

		*/

		// Configure the bean factory with context callbacks.
		// 添加beanPostProcessor，ApplicationContextAwareProcessor用来完成某些Aware对象的注入
		beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this)/* 添加一个后置处理器 */);

		/* 4.1、设置自动装配时，要忽略的一些依赖。因为上面添加的ApplicationContextAwareProcessor，就是往bean里面设置属性值的！会对这些接口进行处理！ */

		/**
		 *
		 * ⚠️在自动装配时，忽略接口的实现类中，有和接口setter方法入参类型相同的依赖(属性) —— 也就是忽略接口实现类中，外部的依赖，而不是忽略接口本身！
		 *
		 * ⚠️之所以忽略，是因为后面有地方会处理；所以忽略，不做任何处理！
		 * 比如：ignoreDependencyInterface(EnvironmentAware.class) 忽略的是，自动装配时，在EnvironmentAware的实现类中，
		 * >>> 与EnvironmentAware#setEnvironment(Environment environment)方法参数的Environment bean，不再注入，
		 * >>> 因为只要实现了EnvironmentAware，后面会通过setEnvironment(Environment environment)进行注入！
		 *
		 * 题外：💡下面的这几个接口，都是由上面的 ApplicationContextAwareProcessor 内部进行处理的！
		 * 题外：⚠️自动装配和@Autowired注解的装配不是同一回事
		 * 题外：在创建DefaultListableBeanFactory的时候，它的父类构造方法里面也会忽略一些接口。
		 * 之所以忽略的地方不一样的，是因为，BeanFactory、ApplicationContext是两套东西，所以在两个不同地方进行忽略操作
		 */
		// 忽略某些对应的接口
		beanFactory.ignoreDependencyInterface/* 忽略依赖接口 */(EnvironmentAware.class);
		beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
		beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
		beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);

		/* 5、设置如果存在多个匹配对象的话，应该哪个对象优先 */

		/**
		 * 预先设置要进行主要注入的对象，也就是说：如果有多个匹配项的话，我要把哪个进行注入！
		 *
		 * 例如：@Autowire private BeanFactory beanFactory，我找到了几个BeanFactory对象，那么我就把这里设置的BeanFactory接口对应的实现进行注入！
		 */
		// 设置几个自动装配的特殊规则，当在进行ioc初始化的如果有多个实现，那么就使用指定的对象进行注入
		// BeanFactory interface not registered as resolvable type in a plain factory. —— BeanFactory 接口未在普通工厂中注册为可解析类型。
		// MessageSource registered (and found for autowiring) as a bean. —— MessageSource 作为 bean 注册（并为自动装配找到）。
		beanFactory.registerResolvableDependency/* 注册可解析的依赖项 */(BeanFactory.class, beanFactory);
		beanFactory.registerResolvableDependency(ResourceLoader.class, this);
		beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
		beanFactory.registerResolvableDependency(ApplicationContext.class, this);


		/* 6、添加一个BeanPostProcessor：ApplicationListenerDetector */
		// Register early post-processor for detecting inner beans as ApplicationListeners.
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this)/* 应用程序侦听器的检测器 */);

		/* 7、增加对AspectJ的支持：使用AspectJ提供的类加载期织入 */

		// Detect a LoadTimeWeaver and prepare for weaving, if found. —— 检测LoadTimeWeaver并准备编织（如果找到）

		/**
		 * 1、在java中切面织入方式分为三种：编译期织入，类加载期织入，运行期织入
		 * （1）编译期织入(编译时)：指在Java编译期，采用特殊的编译器，将切面织入到java类中
		 * 例如：AspectJ编译器
		 * （2）类加载期织入(加载时)：指在类加载期，通过特殊的类加载器，在类字节码加载到JVM时，织入切面，
		 * 例如：AspectJ的LoadTimeWeaving(LTW)
		 * （3）运行期织入(加载时)：采用cglib和jdk动态代理，进行切面的织入
		 * 题外：Spring AOP默认方式
		 *
		 * 2、Aspectj提供了两种织入方式
		 * （1）编译期织入：通过AspectJ编译器，在编译期，将aspectj语言编写的切面类，织入到java类中
		 * （2）类加载期织入：AspectJ的LoadTimeWeaving(LTW)
		 *
		 * 3、AspectJ的LTW原理
		 *
		 * 在类加载期，通过字节码编辑技术，对类字节码进行转换，将切面织入目标类，这种方式叫做LTW（Load Time Weaving）。
		 *
		 * 题外：使用JDK5新增的java.lang.instrument包，在类加载时，对类字节码进行转换，从而实现AOP功能。
		 * 题外：spring-instrument模块，用于类加载时修改字节码
		 * 参考：《详解 Spring AOP LoadTimeWeaving (LTW)》：https://blog.csdn.net/c39660570/article/details/106791365/
		 *
		 * 3、Spring中的静态AOP
		 * （1）Spring中的静态AOP直接使用了AspectJ提供的方法(LTW)，也就是将动态代理的任务直接委托给了AspectJ；
		 * （2）而AspectJ又是在Instrument基础上进行的封装（Instrument指java.lang.instrument包）；
		 * （3）使用JDK5新增的java.lang.instrument包，在类加载时对字节码进行转换，从而实现AOP功能。
		 *
		 * 题外：java.lang.instrument包：JDK5新增的。它类似一种更低级、更松耦合的AOP，可以在类加载时对字节码进行转换，来改变一个类的行为，从而实现AOP功能。相当于在JVM层面做了AOP支持。
		 * >>> 通过java.lang.instrument包实现agent，使得"监控代码"和"应用代码"完全隔离了。
		 *
		 * 4、Spring中关于loadTimeWeaver名称bean的注册地方：{@link org.springframework.context.config.LoadTimeWeaverBeanDefinitionParser}，
		 * bean = {@link org.springframework.context.weaving.DefaultContextLoadTimeWeaver}
		 *
		 * 题外：我们也可以注册一个自己的。
		 * 题外：AspectJWeavingEnabler
		 */
		// loadTimeWeaver：AspectJ提供的类加载期织入
		if (beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME/* loadTimeWeaver */)) {
			// 注册LoadTimeWeaverAwareProcessor，
			// 注意：⚠️只有注册了LoadTimeWeaverAwareProcessor才会激活整个AspectJ的功能
			// 题外：这也是一个后置处理器
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			// Set a temporary ClassLoader for type matching. - 设置一个临时的ClassLoader以进行类型匹配。
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}

		/* 8、注册默认的系统环境bean到一级缓存中 */

		/**
		 * 意思是如果自定义的Bean中没有名为"systemProperties"和"systemEnvironment"的Bean，
		 * 则注册两个Bean，Key为"systemProperties"和"systemEnvironment"，Value为Map
		 * ⚠️这两个Bean就是一些系统配置和系统环境信息
		 */
		// Register default environment beans. - 注册默认环境bean。
		if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
		}
	}

	/**
	 * Modify the application context's internal bean factory after its standard
	 * initialization. All bean definitions will have been loaded, but no beans
	 * will have been instantiated yet. This allows for registering special
	 * BeanPostProcessors etc in certain ApplicationContext implementations.
	 *
	 * @param beanFactory the bean factory used by the application context
	 */
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
	}

	/**
	 * Instantiate and invoke all registered BeanFactoryPostProcessor beans,
	 * respecting explicit order if given.
	 * <p>
	 * <p>
	 * 实例化并调用所有注册的 BeanFactoryPostProcessor bean，并且按照给定的顺序进行执行
	 *
	 * <p>Must be called before singleton instantiation.
	 */
	protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		// 这里面主要做了两件事，处理BeanFactoryPostProcessor和BeanDefinitionRegistryPostProcessor(先执行的BeanDefinitionRegistryPostProcessor)
		// BeanFactoryPostProcessor所针对的点是BeanFactory
		// BeanDefinitionRegistryPostProcessor所针对的点是BeanDefinition，另外BeanDefinitionRegistryPostProcessor extends BeanFactoryPostProcessor

		// 题外：BeanDefinitionMap、BeanDifinitionNames

		/*
		只是在spring的beanFactory初始化的过程中去做一些事情：把实现了BeanFactoryPostProcessor/BeanDefinitionRegistryPostProcessor接口的类调用一下
		（先执行的BeanDefinitionRegistryPostProcessor）
		 */

		/**
		 * getBeanFactoryPostProcessors()：获取自定义的BeanFactoryPostProcessor的Bean
		 * 		这个自定义的也只限于「annotationConfigApplicationContext.addBeanFactoryPostProcessor(new AddBeanFactoryPostProcessorTest());」这样的形式添加的才算自定义，才能获取得到；
		 * 		如果不是这样的形式，而是定义了一个实现了BeanFactoryPostProcessor的类，并注入该类对象，则在这里面获取不到
		 */
		/**
		 * 如果自定义实现了BeanFactoryPostProcessor接口，那么想让spring识别到的话，有两种方式：
		 * 1、定义在spring的配置文件中，让spring自动识别
		 * 2、调用具体的addBeanFactoryPostProcessor()
		 */
		PostProcessorRegistrationDelegate/* Delegate:委托类 */.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

		// Detect a LoadTimeWeaver and prepare for weaving, if found in the meantime —— 检测 LoadTimeWeaver 并准备编织（如果同时发现）
		// (e.g. through an @Bean method registered by ConfigurationClassPostProcessor)
		if (beanFactory.getTempClassLoader() == null && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}
	}

	/**
	 * Instantiate and register all BeanPostProcessor beans,
	 * respecting explicit order if given.
	 * <p>Must be called before any instantiation of application beans.
	 */
	protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		PostProcessorRegistrationDelegate.registerBeanPostProcessors(beanFactory, this);
	}

	/**
	 * Initialize the MessageSource.
	 * Use parent's if none defined in this context.
	 */
	protected void initMessageSource() {
		// 获取bean工厂，一般是DefaultListableBeanFactory
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();

		/* 1、先从beanFactory里面判断，里面有没有我们自定义的MessageSource BD(BD id需要为messageSource)，有的话就用自己的 */

		// 首先判断beanFactory里面，是否包含了一个id为messageSource的BeanDefinition
		if (beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME/* messageSource */)) {
			// 如果有，则从BeanFactory中创建并获取这个id为messageSource的MessageSource bean对象
			/**
			 * ⚠️如果我们定义的id为messageSource的bean，并未实现MessageSource接口，则会报错：Method threw 'org.springframework.beans.factory.BeanNotOfRequiredTypeException' exception.
			 * 所以我们定义的id为messageSource的bean，必须实现MessageSource接口
			 */
			this.messageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
			// Make MessageSource aware of parent MessageSource.
			// 当父类bean工厂不为空，并且这个bean对象是HierarchicalMessageSource类型
			if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource) {
				// 类型强制转换，转换为HierarchicalMessageSource的类型
				HierarchicalMessageSource hms = (HierarchicalMessageSource) this.messageSource;
				// 判断父类的messageSource是否为空，如果等于空，则设置父类的messageSource
				if (hms.getParentMessageSource() == null) {
					// Only set parent context as parent MessageSource if no parent MessageSource
					// registered already.
					// 上面的翻译：如果尚未注册父消息源，则仅将父上下文设置为父消息源。
					hms.setParentMessageSource(getInternalParentMessageSource());
				}
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Using MessageSource [" + this.messageSource + "]");
			}
		}
		/* 2、如果没有我们自定义的MessageSource，那么spring就提供一个默认的 */
		else {
			// Use empty MessageSource to be able to accept getMessage calls.
			// 如果没有messageSource bean对象，新建DelegatingMessageSource类作为messageSource的bean
			// DelegatingMessageSource：spring自动提供的消息处理类
			DelegatingMessageSource dms = new DelegatingMessageSource();
			// 给这个DelegatingMessageSource添加父类消息源
			dms.setParentMessageSource(getInternalParentMessageSource());
			this.messageSource = dms;
			// 将这个messageSource实例注册到bean工厂
			// 如果后面要进行某些国际化的设置的时候，就需要用到工厂里面的这个单例对象
			beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + MESSAGE_SOURCE_BEAN_NAME + "' bean, using [" + this.messageSource + "]");
			}
		}
	}

	/**
	 * Initialize the ApplicationEventMulticaster.
	 * Uses SimpleApplicationEventMulticaster if none defined in the context.
	 *
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 */
	protected void initApplicationEventMulticaster() {
		// 获取当前bean工厂, 一般是DefaultListableBeanFactory
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		/* 1、先判断容器中是否已经有自定义的广播器 */
		// 判断容器中是否存在bdName为applicationEventMulticaster的bd,也就是说自定义的事件监听多路广播器，必须实现ApplicationEventMulticaster接口
		if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME /* applicationEventMulticaster */)) {

			this.applicationEventMulticaster =
					beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME/* applicationEventMulticaster */, ApplicationEventMulticaster.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Using ApplicationEventMulticaster [" + this.applicationEventMulticaster + "]");
			}
		}
		/* 2、如果容器中没有自定义的广播器，那么就创建使用spring默认提供的广播器(SimpleApplicationEventMulticaster)，然后放入一级缓存中 */
		else {
			// 创建spring默认的广播器
			this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
			// 注册广播器到一级缓存中
			beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME/* applicationEventMulticaster */, this.applicationEventMulticaster);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + APPLICATION_EVENT_MULTICASTER_BEAN_NAME + "' bean, using " +
						"[" + this.applicationEventMulticaster.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * 初始化 LifecycleProcessor.如果上下文中没有定义，则使用DefaultLifecycleProcessor
	 * <p>
	 * Initialize the LifecycleProcessor.
	 * Uses DefaultLifecycleProcessor if none defined in the context.
	 *
	 * @see org.springframework.context.support.DefaultLifecycleProcessor
	 */
	protected void initLifecycleProcessor() {
		// 获取当前上下文的BeanFactory对象
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		// 如果beanFactory包含'lifecycleProcessor'的bean，忽略父工厂
		if (beanFactory.containsLocalBean(LIFECYCLE_PROCESSOR_BEAN_NAME)) {
			// 让当前上下文引用从beanFactory中获取名为'lifecycleProcessor'的LifecycleProcessor类型的Bean对象
			this.lifecycleProcessor =
					beanFactory.getBean(LIFECYCLE_PROCESSOR_BEAN_NAME, LifecycleProcessor.class);
			// 如果当前日志级级别是跟踪
			if (logger.isTraceEnabled()) {
				logger.trace("Using LifecycleProcessor [" + this.lifecycleProcessor + "]");
			}
		} else {
			// 创建一个DefaultLifecycleProcessor实例
			DefaultLifecycleProcessor defaultProcessor = new DefaultLifecycleProcessor();
			// 为Bean实例提供所属工厂的回调函数
			defaultProcessor.setBeanFactory(beanFactory);
			// 让当前上下文引用defaultProcessor
			this.lifecycleProcessor = defaultProcessor;
			// 将lifecycleProcessor与lifecycleProcessor注册到beanFactory中
			beanFactory.registerSingleton(LIFECYCLE_PROCESSOR_BEAN_NAME, this.lifecycleProcessor);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + LIFECYCLE_PROCESSOR_BEAN_NAME + "' bean, using " +
						"[" + this.lifecycleProcessor.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Template method which can be overridden to add context-specific refresh work.
	 * Called on initialization of special beans, before instantiation of singletons.
	 * <p>This implementation is empty.
	 *
	 * @throws BeansException in case of errors
	 * @see #refresh()
	 */
	protected void onRefresh() throws BeansException {
		// For subclasses: do nothing by default.
	}

	/**
	 * 注册监听器到多播器当中
	 * <p>
	 * Add beans that implement ApplicationListener as listeners.
	 * Doesn't affect other listeners, which can be added without being beans.
	 */
	protected void registerListeners() {
		/* 1、获取当前应用程序的监听器，添加到广播器当中 */

		// Register statically specified listeners first.
		// 遍历应用程序中(AbstractApplicationContext)存在的监听器集合，并将对应的"监听器"添加到"监听器的多路广播器"中
		for (ApplicationListener<?> listener : getApplicationListeners()) {
			/**
			 *  💡getApplicationEventMulticaster()：获取refresh() ——> initApplicationEventMulticaster()中初始化的多播器对象
			 */
			// 注册监听器
			getApplicationEventMulticaster().addApplicationListener(listener);
		}

		/* 2、看下BeanFactory里面的bd，有没有其它监听器，有就添加到广播器当中 */

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let post-processors apply to them!
		// 从容器中获取所有实现了ApplicationListener接口的bd的bdName，放入ApplicationListenerBeans集合中
		String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
		for (String listenerBeanName : listenerBeanNames) {
			getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
		}

		/* 3、广播earlyApplicationEvents集合里面的事件 —— 发布早期的应用程序事件 */

		// Publish early application events now that we finally have a multicaster... —— 发布早期应用程序事件，因为我们终于有了一个多播器......
		/**
		 * 💡earlyApplicationEvents在 refresh()——>prepareRefresh() 中有初始化过
		 */
		// 发布早期的应用程序事件
		Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
		this.earlyApplicationEvents = null;
		if (!CollectionUtils.isEmpty(earlyEventsToProcess)) {
			for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
				// 广播事件
				getApplicationEventMulticaster().multicastEvent(earlyEvent);
			}
		}
	}

	/**
	 * Finish the initialization of this context's bean factory,initializing all remaining singleton beans. - 完成此上下文的bean工厂的初始化，初始化所有剩余的单例bean。
	 */
	protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
		/* 1、判断beanFactory中有没有ConversionService，有的话就设置ConversionService(类型转换服务)到AbstractBeanFactory.conversionService变量上 */

		/**
		 * 1、ConversionService小知识点，参考：https://www.cnblogs.com/diyunpeng/p/6366386.html
		 *
		 * 题外：⚠️Service的含义：提供的是一整个服务，服务里面包含了各种各样的转换器，由转换器来完成具体的操作
		 * 题外：具体的转换器，最终都是要放入到ConversionService里面添加
		 *
		 * 2、对应的转换器接口有：
		 * （1）Converter：1对1的转换；只支持从一个原类型转换为一个目标类型
		 *
		 * （2）ConverterFactory：1对多的转换；支持一个原始类型转换为某个类型的整个层次结构中的所有类型，
		 * 也就是从一个原类型转换为一个目标类型，或者目标类型对应的子类型
		 *
		 * （3）GenericConverter：多对多的转换；
		 * 支持转换多个不同的"原始类型和目标类型的键值对组合"
		 */

		// Initialize conversion service for this context. - 为此上下文初始化转换服务。
		// 为上下文初始化类型转换服务

		// 判断是否有"beanName=conversionService 且类型等于ConversionService"的bean对象。有的话就设置ConversionService(类型转换服务)
		if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME)/* conversionService */ &&
				beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME/* conversionService */, ConversionService.class)) {
			beanFactory.setConversionService/* 设置转换服务 */(beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
		}

		/*

		2、设置值处理器

		判断beanFactory之前有没有注册【"嵌入值"解析器】(内嵌的值解析器、内嵌的处理器)，没有，则注册默认的【"嵌入值"解析器】，主要用于注解属性值的解析，占位符的处理工作

		*/

		// Register a default embedded value resolver if no bean post-processor
		// (such as a PropertyPlaceholderConfigurer bean) registered any before:
		// at this point, primarily for resolution in annotation attribute values.
		// 上面的翻译：如果之前没有注册过任何bean后置处理器（例如 PropertyPlaceholderConfigurer bean），则注册一个默认的嵌入值解析器：此时，主要用于解析注解属性值。
		// 判断beanFactory之前有没有注册【"嵌入值"解析器】(内嵌的值解析器)，没有，则注册默认的【"嵌入值"解析器】，主要用于注解属性值的解析，占位符的处理工作
		if (!beanFactory.hasEmbeddedValueResolver()) {    // 是否存在内置的value转换器
			beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
		}

		/* 3、设置织入的方式（Aop） */

		/**
		 * Aop
		 *
		 * Aspectj的静态织入的过程
		 *
		 * Spring Aop：不论jdk、cglib都是动态织入的！
		 * Aspectj是编译过程织入的，即静态织入
		 */
		// Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early. - 尽早初始化LoadTimeWeaverAware Bean，以便尽早注册其转换器。
		// 尽早初始化LoadTimeWeaverAware Bean，以便尽早注册它们的转换器
		String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
		for (String weaverAwareName : weaverAwareNames) {
			getBean(weaverAwareName);
		}

		/*

		4、设置一个临时的类加载器

		这里设置为null，代表禁止使用临时类加载器进行类型匹配

		*/

		// Stop using the temporary ClassLoader for type matching.
		// 设置一个临时的类加载器，这里设置为null，代表禁止使用临时类加载器进行类型匹配
		beanFactory.setTempClassLoader(null);

		/*

		5、冻结我们当前已经定义好的bd，就是说之后不能对其进行相关的修改工作了：

		因为下面要开始进行实例化了，直接拿取bd进行实例化，所以bd不允许再修改了，所以放入一个冻结集合里面去，代表不会再修改了

		题外：之前如果要对bd进行修改，也改得差不多了

		*/

		// Allow for caching all bean definition metadata, not expecting further changes. —— 允许缓存所有bean定义元数据，不希望任何的修改了。
		// 冻结所有的bd，说明注册的bd将不被修改或任何进一步的处理
		// (因为不要再修改bd了，所以放入一个冰冻集合里面去，代表我的bd不会再修改了，后面在进行实例化的时候，就按照这个bd完成实例化操作就搞定了，其它东西不用再考虑了  )
		beanFactory.freezeConfiguration();

		/* 6、实例化所有剩余的（非懒加载的）单例 */

		// Instantiate all remaining (non-lazy-init) singletons. - 实例化所有剩余的（非延迟初始化）单例。
		// ⚠️实例化所有剩下的（非懒加载的）单例对象
		// 题外：当然有些类在这之前已经被实例化了，因为可以put
		beanFactory.preInstantiateSingletons/* 预实例化单例 */();
	}

	/**
	 * 完成刷新
	 * <p>
	 * Finish the refresh of this context, invoking the LifecycleProcessor's
	 * onRefresh() method and publishing the
	 * {@link org.springframework.context.event.ContextRefreshedEvent}.
	 */
	protected void finishRefresh() {

		/* 1、清除资源缓存 */
		// Clear context-level resource caches (such as ASM metadata from scanning). —— 清除上下文级资源缓存（例如来自扫描的 ASM 元数据）。
		clearResourceCaches();

		/* 2、初始化生命周期的一些处理器 */
		// Initialize lifecycle processor for this context. —— 为此上下文初始化生命周期处理器。
		// 将刷新完毕时间传播到生命周期处理器
		initLifecycleProcessor();

		/* 3、把bean对象关联上生命周期 */
		// Propagate refresh to lifecycle processor first. —— 首先将刷新传播到生命周期处理器。
		// 传播，刷新生命周期处理器
		// 上下文刷新的通知，例如自动启动的组件
		getLifecycleProcessor().onRefresh();

		/* 4、发送"容器刷新完成"事件 */
		// Publish the final event. —— 发布最终事件。
		/**
		 * 当前的AbstractApplicationContext是事件源；
		 * 事件源发布事件，发布事件之后会调用多播器的方法来进行广播事件；
		 * 广播事件也就是，多播器调用多个具体的监听器，传入事件
		 * 监听器收到事件后，判断是否能处理，能处理就处理，不能就不处理！
		 */
		publishEvent(new ContextRefreshedEvent/* 上下文刷新事件（事件对象） */(this));

		/* 5、注册应用程序上下文 */
		// Participate in LiveBeansView MBean, if active. —— 参与LiveBeansView MBean（如果处于活动状态）。
		LiveBeansView.registerApplicationContext(this);
	}

	/**
	 * Cancel this context's refresh attempt, resetting the {@code active} flag
	 * after an exception got thrown.
	 *
	 * @param ex the exception that led to the cancellation
	 */
	protected void cancelRefresh(BeansException ex) {
		this.active.set(false);
	}

	/**
	 * Reset Spring's common reflection metadata caches, in particular the
	 * {@link ReflectionUtils}, {@link AnnotationUtils}, {@link ResolvableType}
	 * and {@link CachedIntrospectionResults} caches.
	 *
	 * @see ReflectionUtils#clearCache()
	 * @see AnnotationUtils#clearCache()
	 * @see ResolvableType#clearCache()
	 * @see CachedIntrospectionResults#clearClassLoader(ClassLoader)
	 * @since 4.2
	 */
	protected void resetCommonCaches() {
		ReflectionUtils.clearCache();
		AnnotationUtils.clearCache();
		ResolvableType.clearCache();
		CachedIntrospectionResults.clearClassLoader(getClassLoader());
	}


	/**
	 * Register a shutdown hook {@linkplain Thread#getName() named}
	 * {@code SpringContextShutdownHook} with the JVM runtime, closing this
	 * context on JVM shutdown unless it has already been closed at that time.
	 * <p>Delegates to {@code doClose()} for the actual closing procedure.
	 *
	 * @see Runtime#addShutdownHook
	 * @see ConfigurableApplicationContext#SHUTDOWN_HOOK_THREAD_NAME
	 * @see #close()
	 * @see #doClose()
	 */
	@Override
	public void registerShutdownHook() {
		if (this.shutdownHook == null) {
			// No shutdown hook registered yet.
			this.shutdownHook = new Thread(SHUTDOWN_HOOK_THREAD_NAME) {
				@Override
				public void run() {
					synchronized (startupShutdownMonitor) {
						doClose();
					}
				}
			};
			Runtime.getRuntime().addShutdownHook(this.shutdownHook);
		}
	}

	/**
	 * Callback for destruction of this instance, originally attached
	 * to a {@code DisposableBean} implementation (not anymore in 5.0).
	 * <p>The {@link #close()} method is the native way to shut down
	 * an ApplicationContext, which this method simply delegates to.
	 *
	 * @deprecated as of Spring Framework 5.0, in favor of {@link #close()}
	 */
	@Deprecated
	public void destroy() {
		close();
	}

	/**
	 * Close this application context, destroying all beans in its bean factory.
	 * <p>Delegates to {@code doClose()} for the actual closing procedure.
	 * Also removes a JVM shutdown hook, if registered, as it's not needed anymore.
	 *
	 * @see #doClose()
	 * @see #registerShutdownHook()
	 */
	@Override
	public void close() {
		synchronized (this.startupShutdownMonitor) {
			doClose();
			// If we registered a JVM shutdown hook, we don't need it anymore now:
			// We've already explicitly closed the context.
			if (this.shutdownHook != null) {
				try {
					Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
				} catch (IllegalStateException ex) {
					// ignore - VM is already shutting down
				}
			}
		}
	}

	/**
	 * Actually performs context closing: publishes a ContextClosedEvent and
	 * destroys the singletons in the bean factory of this application context.
	 * <p>Called by both {@code close()} and a JVM shutdown hook, if any.
	 *
	 * @see org.springframework.context.event.ContextClosedEvent
	 * @see #destroyBeans()
	 * @see #close()
	 * @see #registerShutdownHook()
	 */
	protected void doClose() {
		// Check whether an actual close attempt is necessary...
		if (this.active.get() && this.closed.compareAndSet(false, true)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing " + this);
			}

			LiveBeansView.unregisterApplicationContext(this);

			try {
				// Publish shutdown event.
				publishEvent(new ContextClosedEvent(this));
			} catch (Throwable ex) {
				logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex);
			}

			// Stop all Lifecycle beans, to avoid delays during individual destruction.
			if (this.lifecycleProcessor != null) {
				try {
					this.lifecycleProcessor.onClose();
				} catch (Throwable ex) {
					logger.warn("Exception thrown from LifecycleProcessor on context close", ex);
				}
			}

			// Destroy all cached singletons in the context's BeanFactory. —— 销毁上下文的BeanFactory中所有缓存的单例。
			destroyBeans();

			// Close the state of this context itself.
			closeBeanFactory();

			// Let subclasses do some final clean-up if they wish...
			onClose();

			// Reset local application listeners to pre-refresh state.
			if (this.earlyApplicationListeners != null) {
				this.applicationListeners.clear();
				this.applicationListeners.addAll(this.earlyApplicationListeners);
			}

			// Switch to inactive.
			this.active.set(false);
		}
	}

	/**
	 * Template method for destroying all beans that this context manages.
	 * The default implementation destroy all cached singletons in this context,
	 * invoking {@code DisposableBean.destroy()} and/or the specified
	 * "destroy-method".
	 * <p>Can be overridden to add context-specific bean destruction steps
	 * right before or right after standard singleton destruction,
	 * while the context's BeanFactory is still active.
	 *
	 * @see #getBeanFactory()
	 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#destroySingletons()
	 */
	protected void destroyBeans() {
		getBeanFactory().destroySingletons();
	}

	/**
	 * Template method which can be overridden to add context-specific shutdown work.
	 * The default implementation is empty.
	 * <p>Called at the end of {@link #doClose}'s shutdown procedure, after
	 * this context's BeanFactory has been closed. If custom shutdown logic
	 * needs to execute while the BeanFactory is still active, override
	 * the {@link #destroyBeans()} method instead.
	 */
	protected void onClose() {
		// For subclasses: do nothing by default.
	}

	@Override
	public boolean isActive() {
		return this.active.get();
	}

	/**
	 * Assert that this context's BeanFactory is currently active,
	 * throwing an {@link IllegalStateException} if it isn't.
	 * <p>Invoked by all {@link BeanFactory} delegation methods that depend
	 * on an active context, i.e. in particular all bean accessor methods.
	 * <p>The default implementation checks the {@link #isActive() 'active'} status
	 * of this context overall. May be overridden for more specific checks, or for a
	 * no-op if {@link #getBeanFactory()} itself throws an exception in such a case.
	 */
	protected void assertBeanFactoryActive() {
		// active = true，代表容器激活了，或者说是刷新了
		// active = false，代表容器未激活
		if (!this.active.get()) {
			/* 如果容器未激活 */

			if (this.closed.get()) {
				throw new IllegalStateException(getDisplayName() + " has been closed already"/* 已经关闭了 */);
			} else {
				throw new IllegalStateException(getDisplayName() + " has not been refreshed yet"/* 还没有刷新 */);
			}
		}
	}


	//---------------------------------------------------------------------
	// Implementation of BeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		// 判断BeanFactory容器是否激活了
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, requiredType);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, args);
	}

	@Override
	public <T> T getBean(Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType);
	}

	@Override
	public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType, args);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public boolean containsBean(String name) {
		return getBeanFactory().containsBean(name);
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isSingleton(name);
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isPrototype(name);
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name);
	}

	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name, allowFactoryBeanInit);
	}

	@Override
	public String[] getAliases(String name) {
		return getBeanFactory().getAliases(name);
	}


	//---------------------------------------------------------------------
	// Implementation of ListableBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public boolean containsBeanDefinition(String beanName) {
		return getBeanFactory().containsBeanDefinition(beanName);
	}

	@Override
	public int getBeanDefinitionCount() {
		return getBeanFactory().getBeanDefinitionCount();
	}

	@Override
	public String[] getBeanDefinitionNames() {
		return getBeanFactory().getBeanDefinitionNames();
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForAnnotation(annotationType);
	}

	@Override
	public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansWithAnnotation(annotationType);
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException {

		assertBeanFactoryActive();
		return getBeanFactory().findAnnotationOnBean(beanName, annotationType);
	}


	//---------------------------------------------------------------------
	// Implementation of HierarchicalBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return getParent();
	}

	@Override
	public boolean containsLocalBean(String name) {
		return getBeanFactory().containsLocalBean(name);
	}

	/**
	 * Return the internal bean factory of the parent context if it implements
	 * ConfigurableApplicationContext; else, return the parent context itself.
	 *
	 * @see org.springframework.context.ConfigurableApplicationContext#getBeanFactory
	 */
	@Nullable
	protected BeanFactory getInternalParentBeanFactory() {
		return (getParent() instanceof ConfigurableApplicationContext ?
				((ConfigurableApplicationContext) getParent()).getBeanFactory() : getParent());
	}


	//---------------------------------------------------------------------
	// Implementation of MessageSource interface
	//---------------------------------------------------------------------

	@Override
	public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, Locale locale) {
		return getMessageSource().getMessage(code, args, defaultMessage, locale);
	}

	@Override
	public String getMessage(String code, @Nullable Object[] args, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(code, args, locale);
	}

	@Override
	public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(resolvable, locale);
	}

	/**
	 * Return the internal MessageSource used by the context.
	 *
	 * @return the internal MessageSource (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	private MessageSource getMessageSource() throws IllegalStateException {
		if (this.messageSource == null) {
			throw new IllegalStateException("MessageSource not initialized - " +
					"call 'refresh' before accessing messages via the context: " + this);
		}
		return this.messageSource;
	}

	/**
	 * Return the internal message source of the parent context if it is an
	 * AbstractApplicationContext too; else, return the parent context itself.
	 */
	@Nullable
	protected MessageSource getInternalParentMessageSource() {
		return (getParent() instanceof AbstractApplicationContext ?
				((AbstractApplicationContext) getParent()).messageSource : getParent());
	}


	//---------------------------------------------------------------------
	// Implementation of ResourcePatternResolver interface
	//---------------------------------------------------------------------

	@Override
	public Resource[] getResources(String locationPattern) throws IOException {
		// PathMatchingResourcePatternResolver#getResources()
		return this.resourcePatternResolver.getResources(locationPattern);
	}


	//---------------------------------------------------------------------
	// Implementation of Lifecycle interface
	//---------------------------------------------------------------------

	@Override
	public void start() {
		getLifecycleProcessor().start();
		publishEvent(new ContextStartedEvent(this));
	}

	@Override
	public void stop() {
		getLifecycleProcessor().stop();
		publishEvent(new ContextStoppedEvent(this));
	}

	@Override
	public boolean isRunning() {
		return (this.lifecycleProcessor != null && this.lifecycleProcessor.isRunning());
	}


	//---------------------------------------------------------------------
	// Abstract methods that must be implemented by subclasses
	//---------------------------------------------------------------------

	/**
	 * Subclasses must implement this method to perform the actual configuration load.
	 * The method is invoked by {@link #refresh()} before any other initialization work.
	 * <p>A subclass will either create a new bean factory and hold a reference to it,
	 * or return a single BeanFactory instance that it holds. In the latter case, it will
	 * usually throw an IllegalStateException if refreshing the context more than once.
	 *
	 * @throws BeansException        if initialization of the bean factory failed
	 * @throws IllegalStateException if already initialized and multiple refresh
	 *                               attempts are not supported
	 */
	protected abstract void refreshBeanFactory() throws BeansException, IllegalStateException;

	/**
	 * Subclasses must implement this method to release their internal bean factory.
	 * This method gets invoked by {@link #close()} after all other shutdown work.
	 * <p>Should never throw an exception but rather log shutdown failures.
	 */
	protected abstract void closeBeanFactory();

	/**
	 * Subclasses must return their internal bean factory here. They should implement the
	 * lookup efficiently, so that it can be called repeatedly without a performance penalty.
	 * <p>Note: Subclasses should check whether the context is still active before
	 * returning the internal bean factory. The internal factory should generally be
	 * considered unavailable once the context has been closed.
	 *
	 * @return this application context's internal bean factory (never {@code null})
	 * @throws IllegalStateException if the context does not hold an internal bean factory yet
	 *                               (usually if {@link #refresh()} has never been called) or if the context has been
	 *                               closed already
	 * @see #refreshBeanFactory()
	 * @see #closeBeanFactory()
	 */
	@Override
	public abstract ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;


	/**
	 * Return information about this context.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getDisplayName());
		sb.append(", started on ").append(new Date(getStartupDate()));
		ApplicationContext parent = getParent();
		if (parent != null) {
			sb.append(", parent: ").append(parent.getDisplayName());
		}
		return sb.toString();
	}

}
