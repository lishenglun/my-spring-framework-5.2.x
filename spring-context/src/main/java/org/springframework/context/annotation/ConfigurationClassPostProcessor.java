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

package org.springframework.context.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.PassThroughSourceExtractor;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ConfigurationClassEnhancer.EnhancedConfiguration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.util.*;

/**
 *
 * ⚠️此类的作用：处理相关的注解解析工作
 *
 *
 * 此类是一个后置处理器的类，主要功能是参数BeanFactory的构建，主要功能如下：
 * 1、解析加了@Configuration的配置类
 * 2、解析@ComponentScan扫描的包
 * 3、解析@ComponentScans扫描的包
 * 4、解析@Import
 *
 *
 * {@link BeanFactoryPostProcessor} used for bootstrapping processing of
 * {@link Configuration @Configuration} classes.
 *
 * <p>Registered by default when using {@code <context:annotation-config/>} or
 * {@code <context:component-scan/>}. Otherwise, may be declared manually as
 * with any other BeanFactoryPostProcessor.
 *
 * <p>This post processor is priority-ordered as it is important that any
 * {@link Bean} methods declared in {@code @Configuration} classes have
 * their corresponding bean definitions registered before any other
 * {@link BeanFactoryPostProcessor} executes.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 3.0
 */
public class ConfigurationClassPostProcessor implements BeanDefinitionRegistryPostProcessor,
		PriorityOrdered, ResourceLoaderAware, BeanClassLoaderAware, EnvironmentAware {

	/**
	 *
	 * 使用类的全限定名作为bean的默认生成策略
	 *
	 * A {@code BeanNameGenerator} using fully qualified class names as default bean names.
	 * <p>This default for configuration-level import purposes may be overridden through
	 * {@link #setBeanNameGenerator}. Note that the default for component scanning purposes
	 * is a plain {@link AnnotationBeanNameGenerator#INSTANCE}, unless overridden through
	 * {@link #setBeanNameGenerator} with a unified user-level bean name generator.
	 *
	 * @see #setBeanNameGenerator
	 * @since 5.2
	 */
	public static final AnnotationBeanNameGenerator IMPORT_BEAN_NAME_GENERATOR =
			new FullyQualifiedAnnotationBeanNameGenerator();

	private static final String IMPORT_REGISTRY_BEAN_NAME =
			ConfigurationClassPostProcessor.class.getName() + ".importRegistry";


	private final Log logger = LogFactory.getLog(getClass());

	private SourceExtractor sourceExtractor = new PassThroughSourceExtractor();

	private ProblemReporter problemReporter = new FailFastProblemReporter();

	@Nullable
	private Environment environment;

	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	/**
	 * 元数据阅读器工厂
	 */
	private MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();

	private boolean setMetadataReaderFactoryCalled = false;

	private final Set<Integer> registriesPostProcessed = new HashSet<>();

	private final Set<Integer> factoriesPostProcessed = new HashSet<>();

	@Nullable
	private ConfigurationClassBeanDefinitionReader reader;

	// 本地Bean名称生成器集
	private boolean localBeanNameGeneratorSet = false;

	/* Using short class names as default bean names by default. */
	private BeanNameGenerator componentScanBeanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

	/* Using fully qualified class names as default bean names by default. */
	private BeanNameGenerator importBeanNameGenerator = IMPORT_BEAN_NAME_GENERATOR;


	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;  // within PriorityOrdered
	}

	/**
	 * Set the {@link SourceExtractor} to use for generated bean definitions
	 * that correspond to {@link Bean} factory methods.
	 */
	public void setSourceExtractor(@Nullable SourceExtractor sourceExtractor) {
		this.sourceExtractor = (sourceExtractor != null ? sourceExtractor : new PassThroughSourceExtractor());
	}

	/**
	 * Set the {@link ProblemReporter} to use.
	 * <p>Used to register any problems detected with {@link Configuration} or {@link Bean}
	 * declarations. For instance, an @Bean method marked as {@code final} is illegal
	 * and would be reported as a problem. Defaults to {@link FailFastProblemReporter}.
	 */
	public void setProblemReporter(@Nullable ProblemReporter problemReporter) {
		this.problemReporter = (problemReporter != null ? problemReporter : new FailFastProblemReporter());
	}

	/**
	 * Set the {@link MetadataReaderFactory} to use.
	 * <p>Default is a {@link CachingMetadataReaderFactory} for the specified
	 * {@linkplain #setBeanClassLoader bean class loader}.
	 */
	public void setMetadataReaderFactory(MetadataReaderFactory metadataReaderFactory) {
		Assert.notNull(metadataReaderFactory, "MetadataReaderFactory must not be null");
		this.metadataReaderFactory = metadataReaderFactory;
		this.setMetadataReaderFactoryCalled = true;
	}

	/**
	 * Set the {@link BeanNameGenerator} to be used when triggering component scanning
	 * from {@link Configuration} classes and when registering {@link Import}'ed
	 * configuration classes. The default is a standard {@link AnnotationBeanNameGenerator}
	 * for scanned components (compatible with the default in {@link ClassPathBeanDefinitionScanner})
	 * and a variant thereof for imported configuration classes (using unique fully-qualified
	 * class names instead of standard component overriding).
	 * <p>Note that this strategy does <em>not</em> apply to {@link Bean} methods.
	 * <p>This setter is typically only appropriate when configuring the post-processor as a
	 * standalone bean definition in XML, e.g. not using the dedicated {@code AnnotationConfig*}
	 * application contexts or the {@code <context:annotation-config>} element. Any bean name
	 * generator specified against the application context will take precedence over any set here.
	 *
	 * @see AnnotationConfigApplicationContext#setBeanNameGenerator(BeanNameGenerator)
	 * @see AnnotationConfigUtils#CONFIGURATION_BEAN_NAME_GENERATOR
	 * @since 3.1.1
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		Assert.notNull(beanNameGenerator, "BeanNameGenerator must not be null");
		this.localBeanNameGeneratorSet = true;
		this.componentScanBeanNameGenerator = beanNameGenerator;
		this.importBeanNameGenerator = beanNameGenerator;
	}

	@Override
	public void setEnvironment(Environment environment) {
		Assert.notNull(environment, "Environment must not be null");
		this.environment = environment;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
		if (!this.setMetadataReaderFactoryCalled) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
		}
	}

	@Override
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
		if (!this.setMetadataReaderFactoryCalled) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory(beanClassLoader);
		}
	}

	/**
	 * 定位、加载、解析、注册相关注解！
	 *
	 * Derive further bean definitions from the configuration classes in the registry. - 从注册表中的配置类派生更多的bean定义。
	 *
	 * ⚠️总体来说，只干了一件事：解析对应的被注解修饰的BeanDefinition，注册到BeanFactory中
	 *
	 * 这些注解的解析工作都在里面：
	 * @Configuaration
	 * @Bean
	 * @Import
	 * @Component
	 * @ComponentScan
	 * @ComponentScans
	 */
	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
		// 根据对应的registry对象生成hashcode值。此对象只会操作一次，如果之前处理过就抛出异常
		// （相当于在整个BeanFactory里面做了一个唯一标识，只会处理一次）
		int registryId = System.identityHashCode(registry);
		if (this.registriesPostProcessed.contains(registryId)) {
			// 如果包含就代表，我已经处理过了，抛出对应的异常
			throw new IllegalStateException(
					"postProcessBeanDefinitionRegistry already called on this post-processor against " + registry);
		}

		if (this.factoriesPostProcessed.contains(registryId)) {
			// 如果包含就代表，我已经处理过了，抛出对应的异常
			throw new IllegalStateException(
					"postProcessBeanFactory already called on this post-processor against " + registry);
		}

		// ⚠️加入到已经处理过的PostProcessor集合里面去
		// 将马上要进行处理的registry对象的id值放到已经处理的集合对象中，这样下次就可以判断是否被执行过，以达到防止重复执行的目的！
		this.registriesPostProcessed.add(registryId);

		// ⚠️处理配置类的bd信息
		processConfigBeanDefinitions(registry);
	}

	/**
	 * 添加cglib增强处理及ImportAwareBeanPostProcessor后置处理类
	 *
	 * Prepare the Configuration classes for servicing bean requests at runtime
	 * by replacing them with CGLIB-enhanced subclasses.
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		int factoryId = System.identityHashCode(beanFactory);
		if (this.factoriesPostProcessed.contains(factoryId)) {
			throw new IllegalStateException(
					"postProcessBeanFactory already called on this post-processor against " + beanFactory);
		}
		this.factoriesPostProcessed.add(factoryId);
		if (!this.registriesPostProcessed.contains(factoryId)) {
			// BeanDefinitionRegistryPostProcessor hook apparently not supported...
			// Simply call processConfigurationClasses lazily at this point then.
			processConfigBeanDefinitions((BeanDefinitionRegistry) beanFactory);
		}

		/**
		 * ⚠️重点！
		 * 产生cglib代理
		 * 为什么需要产生cglib代理？
		 */
		// 对配置类进行增强
		enhanceConfigurationClasses(beanFactory);

		// 向容器中添加BeanPostProcessor
		beanFactory.addBeanPostProcessor(new ImportAwareBeanPostProcessor(beanFactory));
	}

	/**
	 * 1、把包含如下某个注解的类当作配置类进行处理，做相关的解析工作：
	 * @Configuaration
	 * @Component
	 * @ComponentScan
	 * @ComponentScans
	 * @Import
	 * @ImportResource
	 * @Bean
	 *
	 * （1）如果包含@Configuration，同时包含proxyBeanMethods属性，那么设置BeanDefinition的configurationClass属性为full
	 * （2）如果包含@Component、@ComponentScan、@Import、@ImportSource、@Bean中的某一个，就往BD里面设置属性值了，将configurationClass属性设置为lite
	 *
	 * 题外：如果你对此方法了解清楚了，那么spring的自动装配原理就清楚了
	 *
	 * Build and validate a configuration model based on the registry of - 建立并验证基于注册表的配置模型
	 * {@link Configuration} classes.
	 */
	public void processConfigBeanDefinitions(BeanDefinitionRegistry registry/* registry = DefaultListableBeanFactory */) {

		// 🌹题外：BeanDefinitionHolder和BeanDefinition的关系：Holder相当于一个包装类，BeanDefinitionHolder是BeanDefinition的包装类，包含了BeanDefinition、beanName、aliases

		//
		// 创建存放BeanDefinitionHolder的对象集合，
		// 存放符合规则的bd，也就是包含了某一个@Configrution、@Component、@ComponentScan、@Import、@ImportResource，@Bean注解修饰的bd
		// 例如：beanDefinitionMap中的某一个bd包含了@Configrution，就会被放入到configCandidates中
		List<BeanDefinitionHolder> configCandidates = new ArrayList<>();

		// 获取所有的beanDefinitionNames
		String[] candidateNames/* 候选人姓名 */ = registry.getBeanDefinitionNames();

		/*

		1、遍历beanDefinitionMap中所有的bd，提取被@Configrution、@Component、@ComponentScan、@Import、@ImportResource，@Bean中的某一个注解修饰的bd，作为配置类
		放入到configCandidates集合中
		（1）如果存在@Configuration，则设置该BeanDefinition的configurationClass属性为full
		（2）如果不存在@Configuration，但存在@Component、@ComponentScan、@Import、@ImportResource，@Bean，则设置BeanDefinition的configurationClass属性为lite

		题外：被@Configrution、@Component、@ComponentScan、@Import、@ImportResource，@Bean中的某一个注解修饰的bd，简称BeanDefinition配置类

		*/
		for (String beanName : candidateNames) {
			// 从beanDefinitionMap中，通过beanName，获取指定名称的BeanDefinition对象
			BeanDefinition beanDef = registry.getBeanDefinition(beanName);
			/**
			 * CONFIGURATION_CLASS_ATTRIBUTE就是代表ConfigurationClassPostProcessor
			 *
			 * public static final String CONFIGURATION_CLASS_ATTRIBUTE = Conventions
			 * 			.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "configurationClass");
			 */
			// 判断BeanDefinition中有没有包含configurationClass属性值，如果有这个属性值，那么就意味着这个BeanDefinition已经处理过了，就不会添加到configCandidates中进行处理了，然后输出日志提示信息
			// ⚠️如果if条件成立，代表这个BeanDefinition已作为配置类被处理过了
			if (beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE) != null) {
				if (logger.isDebugEnabled()) {
					// 这个BeanDefinition已经作为配置类，被处理过了
					logger.debug("Bean definition has already been processed as a configuration class: " + beanDef);
				}
			}
			/**
			 * ⚠️checkConfigurationClassCandidate()：检查类是否包含了@Configrution、或者加了@Component、@ComponentScan、
			 * @Import、@ImportResource，@Bean，也就是是否属于配置类，如果是配置类就存入configCandidates集合
			 * >>> （1）如果存在@Configuration，则设置该BeanDefinition的configurationClass属性为full
			 * >>> （2）如果不存在@Configuration，但存在@Component、@ComponentScan、@Import、@ImportResource，@Bean，则设置BeanDefinition的configurationClass属性为lite
			 */
			// 🚩判断当前BeanDefinition是否加了@Configuration，或者加了@Component、@ComponentScan、@Import、@ImportSource、@Bean中的某一个注解
			// 🚩如果加了，就代表是配置类，就添加到configCandidates集合中
			else if (ConfigurationClassUtils.checkConfigurationClassCandidate/* 检查配置类候选 */(beanDef, this.metadataReaderFactory)) {
				configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
			}
		}

		/* 2、如果没有一个被刚刚说的注解修饰的bd，就退出方法的执行 */

		// Return immediately if no @Configuration classes were found - 如果未找到@Configuration类，则立即返回
		// 判断集合中是否有"配置类"，如果没有配置类，就直接返回
		if (configCandidates.isEmpty()) {
			// ⚠️如果程序员自定义的配置类为空就退出
			return;
		}

		/* 3、有的话，就排序 */

		// Sort by previously determined @Order value, if applicable —— 按先前确定的@Order 值排序（如果适用）
		// 如果适用，则按照先前确定的@Order的值排序
		configCandidates.sort((bd1, bd2) -> {
			int i1 = ConfigurationClassUtils.getOrder(bd1.getBeanDefinition());
			int i2 = ConfigurationClassUtils.getOrder(bd2.getBeanDefinition());
			return Integer.compare(i1, i2);
		});

		/*

		获取beanName生成器。
		题外：每次在加载bean对象的时候，都必须给bean对象生成一个名字。这里判断一下，是否有自定义的命名生成器，如果有的话就设置对应的beanName生成器，没有的话就按照默认的来

		*/

		// SingletonBeanRegistry：Singleton Bean注册中心类
		// Detect any custom bean name generation strategy supplied through the enclosing application context - 检测通过封闭的应用程序上下文提供的任何自定义bean名称生成策略
		// 判断当前类型是否是SingletonBeanRegistry类型
		SingletonBeanRegistry sbr = null;
		if (registry instanceof SingletonBeanRegistry/* 单例Bean注册表 */) {
			// 类型的强制转换
			sbr = (SingletonBeanRegistry) registry;
			// 判读是否有自定义的beanName生成器
			if (!this.localBeanNameGeneratorSet/* 本地Bean名称生成器集 */) {
				// 获取beanName生成器
				// 题外：从ioc容器中，获取自定义的beanName命名生成器（自己可以自定义一个，如果没有自定义，那么返回的是null），如果有的话就设置对应的beanName生成器，没有的话就按照默认的来
				// 题外：从ioc容器中获取bean，如果不为空直接返回，不再进行初始化工作
				BeanNameGenerator generator = (BeanNameGenerator) sbr.getSingleton(
						AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR/* org.springframework.context.annotation.internalConfigurationBeanNameGenerator */);
				// 如果有自定义的命名生成策略
				if (generator != null) {
					// 设置组件扫描的beanName生成策略
					// componentScanBeanNameGenerator与importBeanNameGenerator定义时就赋值了new AnnotationBeanNameGenerator()
					this.componentScanBeanNameGenerator/* 组件扫描Bean名称生成器 */ = generator;
					// 设置import bean name生成策略
					this.importBeanNameGenerator/* 导入Bean名称生成器 */ = generator;
				}
			}
		}

		// 检查有没有环境对象，如果环境对象为空，那么就重新创建新的环境对象
		if (this.environment == null) {
			this.environment = new StandardEnvironment/* 标准环境 */();
		}

		/* 4、创建"配置类解析器"，用于解析配置类 */

		// Parse each @Configuration class - 解析每个@Configuration类
		// ⚠️创建一个配置类的解析器，并初始化相关的参数，完成配置类的解析工作
		ConfigurationClassParser/* 配置类解析器 */ parser = new ConfigurationClassParser(
				this.metadataReaderFactory, this.problemReporter, this.environment,
				this.resourceLoader, this.componentScanBeanNameGenerator, registry);

		// 存放上面检测到的配置类bd，进行去重操作！
		Set<BeanDefinitionHolder> candidates/* 候选人 */ = new LinkedHashSet<>(configCandidates);
		// 存放已经解析过的bd配置类。用于防重，判断是否已经处理过
		Set<ConfigurationClass> alreadyParsed/* 已经解析 */ = new HashSet<>(configCandidates.size());

		do {

			/* 5、解析配置类，得到对应的bd */

			/**
			 * 1、解析所有的配置类，在此处会解析配置类上的注解(ComponentSan扫描的类，@Import注册的类，@Bean方法定义的类)
			 * 注意，这一步只会将添加了@Configuration注解以及通过@ComponentScan扫描的类才会加入到BeanDefinitionMap中
			 * 通过其他注解（@Import、@Bean）的方式在parse()这一步并不会将其解析为BeanDefinition，放到BeanDefinitionMap中
			 * 真正实现的方式是在this.reader.loadBeanDefinitions()中实现
			 *
			 * 2、candidates是一个配置类集合：包含所有的配置类
			 */
			// ⚠️开始解析，解析带有【@Configuration、@Component、@ComponentScan、@Import、@ImportSource、@Bean】的BeanDefinition
			parser.parse(candidates);
			// 将解析完成的Configuration配置类进行校验：1、配置类不能是final，2、@Bean修饰的方法必须可以重写以支持CGLIB
			parser.validate();

			/**
			 * parser.getConfigurationClasses();是获取加了配置注解的类，例如@Configuration、@Import之内的类是放在这里里面
			 */
			// 获取所有的bean，包括扫描的bean对象，@Import倒入的bean对象
			Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
			// 清除掉已经解析处理过的配置类
			configClasses.removeAll(alreadyParsed);

			// Read the model and create bean definitions based on its content
			// 判断读取器是否为空，如果为空的话，就创建完全填充好的ConfigurationClass实例的读取器
			if (this.reader == null) {
				this.reader = new ConfigurationClassBeanDefinitionReader(
						registry, this.sourceExtractor, this.resourceLoader, this.environment,
						this.importBeanNameGenerator, parser.getImportRegistry());
			}

			/* 6、加载bd */

			/**
			 * 1、把扫描出来的bean对应的beanDefinition添加到beanFactory的map当中
			 * 2、@Configuration配置类，加了static的@Bean与没加static的@Bean的实现区别，也在这里面
			 * 3、⚠️内部处理了ImportBeanDefinitionRegistrar的实现者
			 */
			// 将上一步parser()解析出的ConfigurationClass类加载成BeanDefinition，实际上经过上一步的parser()后，解析出来的bean已经放入到BeanDefinition中
			// 但是由于这些bean可能会引入新的bean（例如：实现了ImpartBeanDefinitionRegistrar或者ImportSelector接口的bean，或者bean中存在被@Bean注解的方法）
			// 因此需要执行一次loadBeanDefinition()，这样就会执行ImportBeanDefinitionRegistrar或者ImportSelector接口

			// reader = ConfigurationClassBeanDefinitionReader
			this.reader.loadBeanDefinitions(configClasses);

			// 添加已经解析的配置类
			alreadyParsed.addAll(configClasses);

			candidates.clear();
			// 这里判断registry.getBeanDefinitionCount() > candidateNames.length的目的是为了知道reader.loadBeanDefinitions(configClasses)这一步有没有
			// 实际上就是看配置类(例如AppConfig类会向BeanDefinitionMap中添加bean)
			// 如果有，registry.getBeanDefinitionCount()就会大于candidateNames.length
			// 这样就需要再次遍历新加入的BeanDefinition，并判断这些bean是否已经被解析过了，如果未解析，需要重新进行解析
			// 这里的AppConfig类向容器中添加的bean，实际上在parser.parse()这一步已经全部被解析了
			if (registry.getBeanDefinitionCount() > candidateNames.length) {

				// 重新获取bd，作为新的bd，因为在"解析配置类"的时候，会产生新的bd！
				String[] newCandidateNames = registry.getBeanDefinitionNames();

				// 原先处理过的bd，作为老的bd
				Set<String> oldCandidateNames = new HashSet<>(Arrays.asList(candidateNames));

				// 以及解析的配置类名称
				Set<String> alreadyParsedClasses = new HashSet<>();
				for (ConfigurationClass configurationClass : alreadyParsed) {
					alreadyParsedClasses.add(configurationClass.getMetadata().getClassName());
				}

				for (String candidateName : newCandidateNames) {
					/**
					 * 1、@Import、ImportSelector#selectImports()、DeferredImportSelector#selectImports()导入的没有实现ImportSelector、
					 * DeferredImportSelector、ImportBeanDefinitionRegistrar接口的类的bd，会在processImports()当中当作配置类被处理 —— 调用的是processConfigurationClass()；
					 * 同时将会在这里判定是否加了@Component、@ComponentScan、@Import、@ImportResource，@Bean中的某一个注解！
					 *
					 * 2、ImportBeanDefinitionRegistrar注入的bd，也会在这里进行判断
					 *
					 */
					// 获取解析配置类过程中新产生的bd（oldCandidateNames当中不包含的bd），
					// 判断其是不是加了@Configrution、@Component、@ComponentScan、@Import、@ImportResource，@Bean中的某一个注解
					// 以及判断是不是未解析的bd
					if (!oldCandidateNames.contains(candidateName)) {
						BeanDefinition bd = registry.getBeanDefinition(candidateName);
						/**
						 * 1、⚠️checkConfigurationClassCandidate()：检查类是否包含了@Configrution、或者加了@Component、@ComponentScan、
						 * @Import、@ImportResource，@Bean，也就是是否属于配置类，如果是配置类就存入configCandidates集合
						 * >>> （1）如果存在@Configuration，则设置该BeanDefinition的configurationClass属性为full
						 * >>> （2）如果不存在@Configuration，但存在@Component、@ComponentScan、@Import、@ImportResource，@Bean，则设置BeanDefinition的configurationClass属性为lite
						 *
						 * 2、!alreadyParsedClasses.contains(bd.getBeanClassName())：判断出未解析的bd
						 * （1）存在已经解析过的集合中，则返回true，取反为false —— 已解析
						 * （2）不存在已经解析过的集合中，则返回false，取反为true —— 未解析
						 * 提示：alreadyParsedClasses：已经解析过的配置类
						 *
						 */
						if (ConfigurationClassUtils.checkConfigurationClassCandidate(bd, this.metadataReaderFactory)
								&& !alreadyParsedClasses.contains(bd.getBeanClassName())) {

							// 如果有未解析的配置类，则将其添加到candidates中，
							// 这样candidates不为空，就会进入到下一次的while的循环中继续进行解析！
							candidates.add(new BeanDefinitionHolder(bd, candidateName));
						}
					}
				}

				// 刷新bdNames
				candidateNames = newCandidateNames;
			}
		}
		// candidates不为null，证明存在未解析的配置类，还需要进行解析
		while (!candidates.isEmpty());

		// Register the ImportRegistry as a bean in order to support ImportAware @Configuration classes
		// 上面的翻译：将ImportRegistry注册为Bean，以支持ImportAware @Configuration类
		if (sbr != null && !sbr.containsSingleton(IMPORT_REGISTRY_BEAN_NAME)) {
			sbr.registerSingleton(IMPORT_REGISTRY_BEAN_NAME, parser.getImportRegistry());
		}

		if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory) {
			// Clear cache in externally provided MetadataReaderFactory; this is a no-op
			// for a shared cache since it'll be cleared by the ApplicationContext.
			// 上面的翻译：清除外部提供的 MetadataReaderFactory 中的缓存；这是共享缓存的无操作，因为它将被 ApplicationContext 清除。
			((CachingMetadataReaderFactory) this.metadataReaderFactory).clearCache();
		}
	}

	/**
	 * Post-processes a BeanFactory in search of Configuration class BeanDefinitions;
	 * any candidates are then enhanced by a {@link ConfigurationClassEnhancer}.
	 * Candidate status is determined by BeanDefinition attribute metadata.
	 *
	 * @see ConfigurationClassEnhancer
	 */
	public void enhanceConfigurationClasses(ConfigurableListableBeanFactory beanFactory) {
		// 存放加了@Configuration的类
		Map<String, AbstractBeanDefinition> configBeanDefs = new LinkedHashMap<>();
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
			Object configClassAttr = beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE);
			MethodMetadata methodMetadata = null;
			if (beanDef instanceof AnnotatedBeanDefinition) {
				methodMetadata = ((AnnotatedBeanDefinition) beanDef).getFactoryMethodMetadata();
			}
			if ((configClassAttr != null || methodMetadata != null) && beanDef instanceof AbstractBeanDefinition) {
				// Configuration class (full or lite) or a configuration-derived @Bean method
				// -> resolve bean class at this point...
				AbstractBeanDefinition abd = (AbstractBeanDefinition) beanDef;
				if (!abd.hasBeanClass()) {
					try {
						abd.resolveBeanClass(this.beanClassLoader);
					} catch (Throwable ex) {
						throw new IllegalStateException(
								"Cannot load configuration class: " + beanDef.getBeanClassName(), ex);
					}
				}
			}
			// ConfigurationClassUtils.CONFIGURATION_CLASS_FULL=full
			if (ConfigurationClassUtils.CONFIGURATION_CLASS_FULL.equals(configClassAttr)) {
				if (!(beanDef instanceof AbstractBeanDefinition)) {
					throw new BeanDefinitionStoreException("Cannot enhance @Configuration bean definition '" +
							beanName + "' since it is not stored in an AbstractBeanDefinition subclass");
				} else if (logger.isInfoEnabled() && beanFactory.containsSingleton(beanName)) {
					logger.info("Cannot enhance @Configuration bean definition '" + beanName +
							"' since its singleton instance has been created too early. The typical cause " +
							"is a non-static @Bean method with a BeanDefinitionRegistryPostProcessor " +
							"return type: Consider declaring such methods as 'static'.");
				}
				configBeanDefs.put(beanName, (AbstractBeanDefinition) beanDef);
			}
		}
		if (configBeanDefs.isEmpty()) {
			/**
			 * 没加@Configuration，就会走这里
			 */
			// nothing to enhance -> return immediately - 没有什么可增强的->立即返回
			return;
		}

		/**
		 * 加了@Configuration，就会走这里
		 */
		// 用来生成具体的代理类
		ConfigurationClassEnhancer enhancer = new ConfigurationClassEnhancer();
		for (Map.Entry<String, AbstractBeanDefinition> entry : configBeanDefs.entrySet()) {
			AbstractBeanDefinition beanDef = entry.getValue();
			// If a @Configuration class gets proxied, always proxy the target class
			beanDef.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
			/**
			 * 完成对Full注解类的cglib代理
			 * 		enhancer.enhance()是具体方法
			 */
			// Set enhanced subclass of the user-specified bean class
			Class<?> configClass = beanDef.getBeanClass();
			// ⚠️重点方法
			// 生成的"动态代理的类"
			// 题外：这个"动态代理的类"最主要是为了解决@Bean的单例问题
			Class<?> enhancedClass = enhancer.enhance(configClass, this.beanClassLoader);
			if (configClass != enhancedClass) {
				if (logger.isTraceEnabled()) {
					logger.trace(String.format("Replacing bean definition '%s' existing class '%s' with " +
							"enhanced class '%s'", entry.getKey(), configClass.getName(), enhancedClass.getName()));
				}
				// ⚠️
				beanDef.setBeanClass(enhancedClass/* 动态代理的类 */);
			}
		}
	}


	private static class ImportAwareBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter {

		private final BeanFactory beanFactory;

		public ImportAwareBeanPostProcessor(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		/**
		 * 如果配置类上有@Configuration，会进行动态代理，会实现EnhancedConfiguration接口，里面有个setBeanFactory()接口
		 *
		 * @param pvs
		 * @param bean
		 * @param beanName
		 * @return
		 */
		@Override
		public PropertyValues postProcessProperties(@Nullable PropertyValues pvs, Object bean, String beanName) {
			// Inject the BeanFactory before AutowiredAnnotationBeanPostProcessor's
			// postProcessProperties method attempts to autowire other configuration beans.
			if (bean instanceof EnhancedConfiguration) {
				((EnhancedConfiguration) bean).setBeanFactory(this.beanFactory);
			}
			return pvs;
		}

		/**
		 * 如果是ImportAware类型的，就会设置bean的注解信息
		 *
		 * @param bean
		 * @param beanName
		 * @return
		 */
		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			if (bean instanceof ImportAware) {
				ImportRegistry ir = this.beanFactory.getBean(IMPORT_REGISTRY_BEAN_NAME, ImportRegistry.class);
				AnnotationMetadata importingClass = ir.getImportingClassFor(ClassUtils.getUserClass(bean).getName());
				if (importingClass != null) {
					((ImportAware) bean).setImportMetadata(importingClass);
				}
			}
			return bean;
		}
	}

}
