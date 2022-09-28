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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.DeferredImportSelector.Group;
import org.springframework.core.NestedIOException;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.UnknownHostException;
import java.util.*;
import java.util.function.Predicate;

/**
 * Parses a {@link Configuration} class definition, populating a collection of
 * {@link ConfigurationClass} objects (parsing a single Configuration class may result in
 * any number of ConfigurationClass objects because one Configuration class may import
 * another using the {@link Import} annotation).
 *
 * <p>This class helps separate the concern of parsing the structure of a Configuration
 * class from the concern of registering BeanDefinition objects based on the content of
 * that model (with the exception of {@code @ComponentScan} annotations which need to be
 * registered immediately).
 *
 * <p>This ASM-based implementation avoids reflection and eager class loading in order to
 * interoperate effectively with lazy class loading in a Spring ApplicationContext.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @see ConfigurationClassBeanDefinitionReader
 * @since 3.0
 */
class ConfigurationClassParser {

	private static final PropertySourceFactory DEFAULT_PROPERTY_SOURCE_FACTORY = new DefaultPropertySourceFactory();

	// 默认排除过滤器：过滤掉【java.lang.annotation. || org.springframework.stereotype.】开头的类
	private static final Predicate<String> DEFAULT_EXCLUSION_FILTER/* 默认排除过滤器 */ = className ->
			(className.startsWith("java.lang.annotation.") || className.startsWith("org.springframework.stereotype."));

	private static final Comparator<DeferredImportSelectorHolder> DEFERRED_IMPORT_COMPARATOR/* 延迟导入比较器 */ =
			// 对@Ordered进行排序
			(o1, o2) -> AnnotationAwareOrderComparator.INSTANCE.compare(o1.getImportSelector(), o2.getImportSelector());


	private final Log logger = LogFactory.getLog(getClass());

	private final MetadataReaderFactory metadataReaderFactory;

	private final ProblemReporter problemReporter;

	private final Environment environment;

	private final ResourceLoader resourceLoader;

	private final BeanDefinitionRegistry registry;

	private final ComponentScanAnnotationParser componentScanParser;

	private final ConditionEvaluator conditionEvaluator;

	// 存放已经解析完成、解析过的配置类（只要标注了@Configuaration、@Component、@ComponentScan、@ComponentScans、@Import、@ImportResource、@Bean的类都是配置类）
	private final Map<ConfigurationClass, ConfigurationClass> configurationClasses = new LinkedHashMap<>();

	private final Map<String, ConfigurationClass> knownSuperclasses/* 已知的超类 */ = new HashMap<>();

	private final List<String> propertySourceNames = new ArrayList<>();

	private final ImportStack importStack = new ImportStack();

	// DeferredImportSelector处理器
	private final DeferredImportSelectorHandler deferredImportSelectorHandler = new DeferredImportSelectorHandler();

	private final SourceClass objectSourceClass = new SourceClass(Object.class);


	/**
	 * Create a new {@link ConfigurationClassParser} instance that will be used
	 * to populate the set of configuration classes.
	 */
	public ConfigurationClassParser(MetadataReaderFactory metadataReaderFactory,
									ProblemReporter problemReporter, Environment environment, ResourceLoader resourceLoader,
									BeanNameGenerator componentScanBeanNameGenerator, BeanDefinitionRegistry registry) {

		this.metadataReaderFactory = metadataReaderFactory;
		this.problemReporter = problemReporter;
		this.environment = environment;
		this.resourceLoader = resourceLoader;
		this.registry = registry;
		this.componentScanParser = new ComponentScanAnnotationParser(
				environment, resourceLoader, componentScanBeanNameGenerator, registry);
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}

	/**
	 * 处理所有的配置类
	 *
	 * @param configCandidates 所有的配置类
	 */
	public void parse(Set<BeanDefinitionHolder> configCandidates) {
		for (BeanDefinitionHolder holder : configCandidates) {
			BeanDefinition bd = holder.getBeanDefinition();
			try {
				/*

				1、解析配置类

				判断bd的归属类型，根据bd类型的不同，调用不同的parse()重载方法（最终都是调用processConfigurationClass()），来解析配置类

				💡提示：直接从配置文件读取过来的BeanDefinition名称叫GenericBeanDefinition；以注解的方式读取到的BD名称是ScannedGenericBeanDefinition

				*/
				/**
				 * 配置类在register()时注入beanDefinitionMap中
				 * 是以BeanDefinitionHolder包含了AnnotatedGenericBeanDefinition对象的形式存放的;
				 * 而AnnotatedGenericBeanDefinition implements AnnotatedBeanDefinition;
				 * 所以配置类bd instanceof AnnotatedBeanDefinition成立
				 */
				// bd是注解类型的
				if (bd instanceof AnnotatedBeanDefinition) {
					// ⚠️
					// 题外：getMetadata()是获取配置类上所有的注解的元数据
					parse(((AnnotatedBeanDefinition) bd).getMetadata()/* 注解元数据 */, holder.getBeanName());
				}
				// 普通的
				else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()/* 有class对象的 */) {
					parse(((AbstractBeanDefinition) bd).getBeanClass()/* Class */, holder.getBeanName());
				}
				// 什么都不是
				else {
					parse(bd.getBeanClassName()/* Class name */, holder.getBeanName());
				}

			} catch (BeanDefinitionStoreException ex) {
				throw ex;
			} catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
			}
		}

		/* 2、所有其它的的配置类都已经处理完了，再处理所有的DeferredImportSelector(延迟导入选择器) */
		// 所有其它的的配置类都已经处理完了，再处理所有的DeferredImportSelector
		// 有的时候，需求是，等所有配置类都加载完了，再从容器中取到某个东西做相关的处理，决定是否导入一些类
		this.deferredImportSelectorHandler/* 延迟导入选择器处理程序 */.process();
	}

	// 根据className和beanName解析配置文件，读取元数据
	protected final void parse(@Nullable String className, String beanName) throws IOException {
		Assert.notNull(className, "No bean class name for configuration class bean definition");
		MetadataReader reader = this.metadataReaderFactory.getMetadataReader(className);
		processConfigurationClass(new ConfigurationClass(reader, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	// 根据Class和beanName解析配置文件，有Class对象
	protected final void parse(Class<?> clazz, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(clazz, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	/**
	 * @param metadata 类上的所有注解的元数据
	 * @param beanName
	 * @throws IOException
	 */
	// 根据注解元数据和beanName解析配置文件，有注解元数据
	protected final void parse(AnnotationMetadata metadata/* 注解元数据 */, String beanName) throws IOException {
		// 当前类ConfigurationClass，是对metadata和beanName的再一层包装，并加入了resource
		processConfigurationClass(new ConfigurationClass(metadata, beanName), DEFAULT_EXCLUSION_FILTER/* 默认排除过滤器 */);
	}

	/**
	 * Validate each {@link ConfigurationClass} object.
	 *
	 * @see ConfigurationClass#validate
	 */
	public void validate() {
		for (ConfigurationClass configClass : this.configurationClasses.keySet()) {
			configClass.validate(this.problemReporter);
		}
	}

	public Set<ConfigurationClass> getConfigurationClasses() {
		return this.configurationClasses.keySet();
	}

	/**
	 * 处理配置类
	 *
	 * 注意：当前是处理配置类最底层的通用的方法！无论是注解声明的，还是xml声明的，或者其它方式，最终都走这个方法！
	 *
	 * @param configClass			配置类（只要标注了@Configuaration、@Component、@ComponentScan、@ComponentScans、@Import、@ImportResource、@Bean的类都是配置类）
	 * @param filter				过滤器
	 */
	protected void processConfigurationClass(ConfigurationClass configClass/* BeanDefinition */, Predicate<String> filter/* 默认空对象 */) throws IOException {
		/* 一、通过条件计算器来判断，是否要跳过当前配置类的解析 */
		if (this.conditionEvaluator/* 条件计算器 */.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION/* 解析配置 */)) {
			return;
		}

		/* 二、处理重复引入一个配置类的情况。如果之前处理过了，而且此时是@Import进来的，就不会进行处理了！ */

		/**
		 * ⚠️configurationClasses：是存放已经解析过的配置类
		 *
		 * 第一次进入的时候，configurationClasses的size=0，所以existingClass肯定为null，如果重复引入了相应的配置类则进行不同的处理：
		 *
		 * 例如：刚开始有一个配置类，PersonService，已经处理过了；后面又通过@Import(PersonService.class)方式引入进来这个配置类。
		 * 由于已经处理过PersonService了，后面再@Import(PersonService.class)进来 ，没必要对PersonService进行两遍处理，所以要根据对应情况进行处理：
		 * （1）之前解析过PersonService，此次解析PersonService时，是@Import(PersonService.class)进来的，直接返回，不解析了！
		 * （2）之前解析过PersonService，此次解析PersonService时，是@Import(PersonService.class)进来的，并且之前解析PersonService时，
		 *  >>> PersonService也是被@Import(PersonService.class)进来的，那么就合并两者的importBy属性！再返回！
		 * （3）之前解析过PersonService，此次解析PersonService时，不@Import(PersonService.class)进来的，就删除掉之前解析过的PersonService，采用此次的PersonService进行解析
		 */
		ConfigurationClass existingClass/* 现有类 */ = this.configurationClasses/* 💡️ */.get(configClass);
		// existingClass==null，代表之前没有解析过这个配置类
		// existingClass！=null，代表之前解析过这个配置类
		if (existingClass != null) {
			if (configClass.isImported()) { // 判断当前解析的配置类，是不是被import的

				/* 1、之前解析过这个配置类，并且又解析这个配置类时；这个配置类目前是被Import进来的，就直接返回，不解析了！ */

				if (existingClass.isImported()) { // 判断当前解析的配置类，之前被解析的时候，是不是被import
					/*

					2、之前解析过这个配置类，并且又解析这个配置类时；且这个配置类目前是被Import进来的，并且原先解析这个配置类时，也是被Import进来的，
					也就是说同一个配置类，两次都是被Import进来的，那么就进行合并两者的importBy属性！再返回！

					*/
					existingClass.mergeImportedBy(configClass);
				}
				// Otherwise ignore new imported config class; existing non-imported class overrides it. —— 否则忽略新导入的配置类；现有的非导入类会覆盖它。
				return;
			} else {

				/* 3、之前解析过这个配置类，并且又解析这个配置类时；这个配置类，不是被import进来的；就把原先解析过的配置类给删除掉，移除旧的，使用新的配置类 */

				// Explicit bean definition found, probably replacing an import. —— 找到显式 bean definition，可能替换导入。
				// Let's remove the old one and go with the new one. —— 让我们删除旧的并使用新的。
				this.configurationClasses/* 💡️ */.remove(configClass);
				this.knownSuperclasses.values().removeIf(configClass::equals);
			}
		}

		/* 三、递归处理配置类及其超类层次结构，也就是解析类上的各种注解 */

		// Recursively process the configuration class and its superclass hierarchy. - 递归处理配置类及其超类层次结构。
		SourceClass sourceClass = asSourceClass/* 作为资源类 */(configClass, filter);
		do {
			/**
			 * 里面判断是否有对应的父类
			 *
			 * 处理配置类，由于配置类可能存在父类（若父类的全类名是以java开头的，则除外），所有需要将configClass变成sourceClass去解析，然后返回sourceClass的父类。
			 * 如果此时父类为空，则不会进行while循环去解析；如果父类不为空，则会循环的去解析父类，把所有包含的注解的东西都给解析到
			 *
			 * 例如：PersonService有可能有自己的父类，父类也要进行相关的处理工作，包含父类这样的继承关系的时候，都要进行处理！
			 * PersonService的父类是Object，Object是java.lang.Object，所以asSourceClass()后，返回的还是当前PersonService这个对象！
			 *
			 *  (SourceClass的意义：简单的包装类，目的是为了以统一的方式去处理带有注解的类，不管这些类是如何加载的（如果无法理解，可以把它当成一个黑盒，不会影响看Spring源码的主流程）)
			 */
			// ⚠️解析各种注解（对类上包含的注解进行解析）
			// ⚠️返回的sourceClass，是父类，如果父类不为空，那么就会进行解析父类，否则不解析，退出while()循环
			sourceClass = doProcessConfigurationClass/* 处理配置类 */(configClass, sourceClass, filter);
		}
		// 看下有没有父类，对父类里面的东西进行解析
		while (sourceClass != null);

		/*

		四、存放解析完成的配置类，后续会注册这些配置类bd到容器中

		在ConfigurationClassPostProcessor#processConfigBeanDefinitions()中处理完毕parser.parse(candidates) = ConfigurationClassParser#parse()
		之后的this.reader.loadBeanDefinitions(configClasses)会进行处理！

		*/
		// 将解析的配置类存储起来，这样回到parse()时，能取到值
		this.configurationClasses/* 💡️ */.put(configClass, configClass);
	}

	/**
	 * Apply processing and build a complete {@link ConfigurationClass} by reading the
	 * annotations, members and methods from the source class. This method can be called
	 * multiple times as relevant sources are discovered.
	 *
	 * @param configClass the configuration class being build —— 正在构建的配置类
	 * @param sourceClass a source class —— 源类
	 * @return the superclass, or {@code null} if none found or previously processed
	 */
	@Nullable
	protected final SourceClass doProcessConfigurationClass(
			ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter)
			throws IOException {

		/* 1、如果有被@Component修饰，就先看有没有内部类配置类，有的话就递归处理内部配置类，因为内部类也可以是一个配置类 */

		/**
		 * 注意：@Configuration上面有@Component修饰，也就是继承了@Component，所以如果是一个只用@Configuration修饰的类，是能识别到上面的@Component。这里的if判断也为true。
		 * 同理，从这里可以推断出，如果我自己定义一个@MyComponent，上面也用@Component修饰，那么在这里也能被识别到。
		 */
		// 判断是否被@Component修饰，有的就递归处理内部类（⚠️重点是处理内部类！）
		if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
			// Recursively process any member (nested) classes first —— 首先递归处理任何成员（嵌套）类
			// 先处理内部类 —— 递归处理内部类（递归处理成员嵌套类），因为内部类也可以是一个配置类（一般不会写内部类）；处理内部类时，最终还是调用doProcessConfigurationClass()
			processMemberClasses/* MemberClasses：内部类 */(configClass, sourceClass, filter);
		}

		/* 2、处理@PropertySource：处理属性资源文件 */

		// Process any @PropertySource annotations —— 处理所有@PropertySource注解
		// 处理属性资源文件，加了@PropertySource

		// 如果配置类上加了@PropertySource，那么就加载和解析对应的属性资源文件(properties文件)(把配置文件的值，读取进来，注入到属性里面去)，并将属性添加到spring上下文中
		for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), PropertySources.class,
				org.springframework.context.annotation.PropertySource.class)) {
			if (this.environment instanceof ConfigurableEnvironment/* StandarEnvironment */) {
				// 加载外部的配置文件进来
				/**
				 * ⚠️当这里完成处理之后，只是把外部文件给加过来了，但是没有对里面的值进行相关的处理工作
				 * 只有在实例化操作的时候，才会给属性赋值！所以这里只是把外部文件给加载进来
				 */
				processPropertySource(propertySource);
			} else {
				logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
						"]. Reason: Environment must implement ConfigurableEnvironment");
			}
		}

		/* 3、处理@ComponentScans、@ComponentScan */
		/**
		 * @ComponentScans、@ComponentScan作用：扫描指定的路径下有哪些是被注解修饰的类，把这些东西全部都取出来
		 */

		// Process any @ComponentScan annotations
		// 处理@ComponentScan或者@ComponentScans，并将扫描包下的所有bean转换成填充后的ConfigurationClass
		// 此处就是将自定义的bean加载到IOC容器，因为扫描到的了可能也添加了@ComponentScan和@ComponentScans，因此需要进行递归解析

		// 首先找出类上的@CompoentScan和@ComponentScans的所有属性
		Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable/* 可重复的属性 */(
				sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
		if (!componentScans.isEmpty() &&
				!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			for (AnnotationAttributes componentScan : componentScans) {

				// The config class is annotated with @ComponentScan -> perform the scan immediately
				// 解析@CompoentScan和@ComponentScans配置的扫描包所包含的类
				// 比如 basePackages = com.mashibing，那么在这一步会扫描出这个包以及子包下的class，然后将其解析成BeanDefinition
				// (⚠️BeanDefinition可以理解为等价于BeanDefinitionHolder)

				// ⚠️进入这里
				/* 3.1、扫描包中的普通类了，并且把这些普通类放入beanDefinitionMap当中，并返回扫描的结果 */
				Set<BeanDefinitionHolder> scannedBeanDefinitions = this.componentScanParser/* 组件扫描解析器 */
						.parse(componentScan, sourceClass.getMetadata().getClassName());

				// Check the set of scanned definitions for any further config classes and parse recursively if needed
				// 通过上一步扫描包com.mashibing，有可能扫描出来的bean中，可能也添加了ComponentScan或者@CompoentScans注解
				// 所以这里需要循环遍历一次，进行递归(parse)，继续解析，直到解析出的类上没有ComponentScan和@ComponentScans
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition/* 获取原始Bean定义 */();
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition();
					}
					/**
					 * 1、ConfigurationClassUtils.checkConfigurationClassCandidate()：
					 * 检查类是否包含了@Configrution，或者加了@Component、@ComponentScan、@Import、@ImportResource、@Bean，
					 * 也就是，是否属于配置类，如果是配置类就存入configCandidates集合
					 * （1）如果存在@Configuration注解，则为BeanDefinition设置configurationClass属性为full
					 * （2）如果不存在存在@Configuration注解，但加了@Component、@ComponentScan、@Import、@ImportResource、@Bean，
					 * >>> 则为BeanDefinition设置configurationClass属性为lite
					 *
					 */
					/* 3.2、挨个判断，扫描到的BD是不是配置类。是的话就设置full或lite属性，并且调用parse()进行解析（递归调用）。 */
					// 判断是不是一个配置类，并设置full或lite属性
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
						/* 3.3、如果扫描出来的bd，属于配置类，就调用parse()对其进行解析 */
						parse(bdCand.getBeanClassName(), holder.getBeanName());
					}
				}
			}
		}

		/* 4、处理所有@Import：导入了额外的配置类，同时完成了具体类的实例化工作 */

		/**
		 * 重点⚠️
		 * 处理所有@Import注解，内部三种import情况：
		 * 1、ImportSelector、
		 * 2、ImportBeanDefinitionRegistrar、
		 * 3、普通类(没有实现ImportSelector、ImportBeanDefinitionRegistrar接口，但加了@Import的类) —— 作为@Configuration类处理
		 *
		 * configClass是加了@Import的类
		 * sourceClass  = configClass
		 * getImports(sourceClass)获取的是@Import(类)中的类，例如：@Import(A.class)，那么得到的就是A.class
		 */
		// Process any @Import annotations - 处理所有@Import注解
		// 处理@Import注解注册的bean,这一步只会将import注册的bean变为ConfigurationClass,不会变成BeanDefinition
		// 而是在loadBeanDefinitions()方法中变成BeanDefinition，再放入到BeanDefinitionMap中
		// Import类包含实现ImportSelector的类或者ImportBeanDefinitionRegistry类或者普通类
		processImports(configClass, sourceClass,
				getImports(sourceClass)/* 递归获取到类上@Import(类)中的类(获取当前有哪些类，需要被进行相关的导入工作) */,
				filter, true);

		/* 5、处理所有的@ImportResource */

		// Process any @ImportResource annotations
		// 处理@ImportResource引入的配置文（加载某些spring的配置文件）
		AnnotationAttributes importResource =
				AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
		if (importResource != null) {
			String[] resources = importResource.getStringArray("locations");
			Class<? extends BeanDefinitionReader> readerClass/* 阅读器类 */ = importResource.getClass("reader");
			for (String resource : resources) {
				String resolvedResource = this.environment.resolveRequiredPlaceholders/* 解决所需的占位符 */(resource);
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}

		/* 6、获取出加了@Bean注解的方法 */

		// Process individual @Bean methods —— 处理单个@Bean方法
		// 处理类当中加了@Bean修饰的方法，将@Bean方法转化为BeanMethod对象，保存在集合中
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata/* 检索@Bean方法元数据：看一下方法上面有没有包含@Bean */(sourceClass);
		for (MethodMetadata methodMetadata : beanMethods) {
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
		}

		/* 7、处理接口上的默认方法实现（因为从jdk8开始，接口中的方法也可以有自己的默认实现了，因此如果这个接口的方法加了@Bean，也需要被解析） */
		// Process default methods on interfaces —— 处理接口上的默认方法
		processInterfaces(configClass, sourceClass);

		/*

		8、返回父类，如果父类不为空，那么在上层函数当中就会进行解析父类，否则不解析
		（如果被解析的配置类继承了某个类，那么配置的父类也会被进行解析）
		⚠️提示：这里返回了对应的父类，证明父类不为空，存在父类，那么就会进行对应的解析，如果父类为空，就代表不存在对应的父类，那么就不会再进行解析

		*/

		// Process superclass, if any —— 处理超类（如果有）
		if (sourceClass.getMetadata().hasSuperClass()) {
			// 获取父类的全限定类名
			String superclass = sourceClass.getMetadata().getSuperClassName();
			if (superclass != null &&
					// 排除掉不是java开头的父类
					// 如果父类是Object，就会是java开头，所以要排除。
					!superclass.startsWith("java") &&
					// 已知的超类中不包含当前父类
					!this.knownSuperclasses/* 已知的超类 */.containsKey(superclass)) {
				// 放入"已知的超类"集合中
				this.knownSuperclasses.put(superclass, configClass);
				// Superclass found, return its annotation metadata and recurse —— 找到超类，返回其注解元数据并递归
				return sourceClass.getSuperClass();
			}
		}

		// No superclass -> processing is complete —— 没有超类 -> 处理完成
		return null;
	}

	/**
	 * Register member (nested) classes that happen to be configuration classes themselves.
	 */
	private void processMemberClasses(ConfigurationClass configClass, SourceClass sourceClass,
									  Predicate<String> filter) throws IOException {
		// 找到内部类，内部类中也可能是一个配置类
		Collection<SourceClass> memberClasses = sourceClass.getMemberClasses();
		// 如果不等于空的话，就代表存在内部类，就对其进行处理
		if (!memberClasses.isEmpty()) {
			// ⚠️如果内部类也是配置类，就存放进来
			List<SourceClass> candidates = new ArrayList<>(memberClasses.size());
			/* 1、⚠️循环判断，内部类，是不是配置类。如果内部类也是配置类，就放入candidates集合。 */
			for (SourceClass memberClass : memberClasses) {
				// 判断内部类是不是一个配置类，是的话就加入到candidates集合中
				// 也就是检查是不是包含@Component、@ComponentScan、@Import、@ImportResource、@Bean中的一个。注意的一点是，这里面没有@Configuration的判断！
				if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata()) &&
						// 内部类的名称，不等于当前配置类的名称
						!memberClass.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
					candidates.add(memberClass);
				}
			}
			/* 2、对配置类进行排序操作 */
			OrderComparator.sort(candidates);

			/* 3、对类当中的内部类配置类，进行解析 */
			// 遍历配置类（内部类是配置类的类）
			for (SourceClass candidate : candidates) {
				if (this.importStack.contains(configClass)) {
					// 出现配置类循环导入，则直接报错
					this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
				} else {
					// 将配置类入栈
					this.importStack.push(configClass);
					try {
						// ⚠️调用processConfigurationClass()，对类当中的内部类配置类，进行解析
						// （因为内部类中可能还包含内部类，所以需要再做循环解析）
						processConfigurationClass(candidate.asConfigClass(configClass), filter);
					} finally {
						// 解析完，出栈
						this.importStack.pop();
					}
				}
			}
		}
	}

	/**
	 * Register default methods on interfaces implemented by the configuration class.
	 *
	 * 在配置类实现的接口上注册默认方法。
	 */
	private void processInterfaces(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
		for (SourceClass ifc : sourceClass.getInterfaces()) {
			Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata/* 检索Bean方法元数据 */(ifc);
			for (MethodMetadata methodMetadata : beanMethods) {
				if (!methodMetadata.isAbstract()) {
					// A default method or other concrete method on a Java 8+ interface... —— Java 8+ 接口上的默认方法或其他具体方法...
					configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
				}
			}
			processInterfaces(configClass, ifc);
		}
	}

	/**
	 * Retrieve the metadata for all <code>@Bean</code> methods.
	 */
	private Set<MethodMetadata> retrieveBeanMethodMetadata(SourceClass sourceClass) {
		// 获取当前类的注解元数据信息
		AnnotationMetadata original = sourceClass.getMetadata();
		// 获取方法上标注了@Bean的所有"方法元数据"对象集合
		Set<MethodMetadata> beanMethods = original.getAnnotatedMethods(Bean.class.getName());
		if (beanMethods.size() > 1 && original instanceof StandardAnnotationMetadata) {
			// Try reading the class file via ASM for deterministic declaration order...
			// Unfortunately, the JVM's standard reflection returns methods in arbitrary
			// order, even between different runs of the same application on the same JVM.
			// 上面的翻译：尝试通过ASM读取类文件以获得确定的声明顺序...不幸的是，JVM的标准反射以任意顺序返回方法，即使在同一JVM上，同一应用程序的不同运行之间也是如此。
			try {
				// ⚠️ASM
				AnnotationMetadata asm =
						this.metadataReaderFactory.getMetadataReader(original.getClassName()).getAnnotationMetadata();
				Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Bean.class.getName());
				/* 获取对应的一些元数据信息 */
				if (asmMethods.size() >= beanMethods.size()) {
					Set<MethodMetadata> selectedMethods = new LinkedHashSet<>(asmMethods.size());
					for (MethodMetadata asmMethod : asmMethods) {
						for (MethodMetadata beanMethod : beanMethods) {
							if (beanMethod.getMethodName().equals(asmMethod.getMethodName())) {
								selectedMethods.add(beanMethod);
								break;
							}
						}
					}
					if (selectedMethods.size() == beanMethods.size()) {
						// All reflection-detected methods found in ASM method set -> proceed —— 在 ASM 方法集中找到的所有反射检测方法 -> 继续
						beanMethods = selectedMethods;
					}
				}
			} catch (IOException ex) {
				logger.debug("Failed to read class file via ASM for determining @Bean method order", ex);
				// No worries, let's continue with the reflection metadata we started with... —— 不用担心，让我们继续我们开始的反射元数据......
			}
		}
		return beanMethods;
	}


	/**
	 * Process the given <code>@PropertySource</code> annotation metadata.
	 *
	 * @param propertySource metadata for the <code>@PropertySource</code> annotation found
	 * @throws IOException if loading a property source failed
	 */
	private void processPropertySource(AnnotationAttributes propertySource) throws IOException {

		/* 1、解析@PropertySource注解所包含的属性 */

		// 获取name属性
		String name = propertySource.getString("name");
		if (!StringUtils.hasLength(name)) {
			name = null;
		}
		// 获取encoding属性
		String encoding = propertySource.getString("encoding");
		if (!StringUtils.hasLength(encoding)) {
			encoding = null;
		}
		// 获取value属性
		String[] locations/* 地点 */ = propertySource.getStringArray("value");
		Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
		// 获取ignoreResourceNotFound属性
		boolean ignoreResourceNotFound = propertySource.getBoolean("ignoreResourceNotFound");
		// 获取factory属性
		Class<? extends PropertySourceFactory> factoryClass = propertySource.getClass("factory");
		PropertySourceFactory factory = (factoryClass == PropertySourceFactory.class ?
				DEFAULT_PROPERTY_SOURCE_FACTORY : BeanUtils.instantiateClass(factoryClass));

		// 循环处理资源文件路径
		for (String location : locations) {
			// location = classpath:myconfig2.properties
			try {
				// 处理属性值的占位符
				// 没有占位符，所以不解析，所以resolvedLocation还是等于classpath:myconfig2.properties
				String resolvedLocation = this.environment.resolveRequiredPlaceholders/* 解决所需的占位符 */(location);
				// 将指定位置的资源转换成resource对象
				// 把当前路径里面的东西变成一个Resource对象
				Resource resource = this.resourceLoader.getResource(resolvedLocation);
				// ⚠️添加resource对象为属性资源
				/**
				 * ⚠️当这里完成处理之后，只是把外部文件给加过来了，但是没有对里面的值进行相关的处理工作
				 * 只有在实例化操作的时候，才会给属性赋值！所以这里只是把外部文件给加载进来
				 */
				addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
			} catch (IllegalArgumentException | FileNotFoundException | UnknownHostException ex) {
				// Placeholders not resolvable or resource not found when trying to open it
				if (ignoreResourceNotFound) {
					if (logger.isInfoEnabled()) {
						logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
					}
				} else {
					throw ex;
				}
			}
		}
	}

	private void addPropertySource(PropertySource<?> propertySource) {
		// 获取名称
		String name = propertySource.getName();
		// 获取属性资源
		MutablePropertySources propertySources = ((ConfigurableEnvironment) this.environment).getPropertySources();

		if (this.propertySourceNames.contains(name)) {
			// We've already added a version, we need to extend it
			PropertySource<?> existing = propertySources.get(name);
			if (existing != null) {
				PropertySource<?> newSource = (propertySource instanceof ResourcePropertySource ?
						((ResourcePropertySource) propertySource).withResourceName() : propertySource);
				if (existing instanceof CompositePropertySource) {
					((CompositePropertySource) existing).addFirstPropertySource(newSource);
				} else {
					if (existing instanceof ResourcePropertySource) {
						existing = ((ResourcePropertySource) existing).withResourceName();
					}
					CompositePropertySource composite = new CompositePropertySource(name);
					composite.addPropertySource(newSource);
					composite.addPropertySource(existing);
					propertySources.replace(name, composite);
				}
				return;
			}
		}

		if (this.propertySourceNames.isEmpty()) {
			// ⚠️当这里完成处理之后，只是把外部文件给加过来了，但是没有对里面的值进行相关的处理工作
			// 只有在实例化操作的时候，才会给属性赋值！所以这里只是把外部文件给加载进来
			propertySources.addLast(propertySource);
		} else {
			String firstProcessed = this.propertySourceNames.get(this.propertySourceNames.size() - 1);
			propertySources.addBefore(firstProcessed, propertySource);
		}
		this.propertySourceNames.add(name);
	}


	/**
	 * Returns {@code @Import} class, considering all meta-annotations.
	 */
	private Set<SourceClass> getImports(SourceClass sourceClass) throws IOException {
		// 存放@Import导入的类
		Set<SourceClass> imports = new LinkedHashSet<>();
		// 存储解析过的类/注解，作用：因为会递归调用解析，为了避免递归调用的时候，重复解析某一个注解，所以把之前解析过的注解给存储起来
		Set<SourceClass> visited = new LinkedHashSet<>();
		// ⚠️收集@Import注解所导入的类
		// ⚠️这里面很关键的一点是，会遍历类上面的所有注解，以及遍历注解上面的所有注解，这样就使得我们自定义注解上的@Import(A.class)里面的内容能够被识别到
		collectImports/* 收集进口 */(sourceClass, imports, visited);
		return imports;
	}

	/**
	 * Recursively collect all declared {@code @Import} values. Unlike most
	 * meta-annotations it is valid to have several {@code @Import}s declared with
	 * different values; the usual process of returning values from the first
	 * meta-annotation on a class is not sufficient.
	 * <p>For example, it is common for a {@code @Configuration} class to declare direct
	 * {@code @Import}s in addition to meta-imports originating from an {@code @Enable}
	 * annotation.
	 *
	 * @param sourceClass the class to search
	 * @param imports     the imports collected so far
	 * @param visited     used to track visited classes to prevent infinite recursion
	 * @throws IOException if there is any problem reading metadata from the named class
	 */
	private void collectImports(SourceClass sourceClass, Set<SourceClass> imports, Set<SourceClass> visited)
			throws IOException {

		/* 遍历类上的注解，以及递归遍历注解上的注解，找到@Import，获取里面对应的要导入的类 */

		/**
		 * visited.add()是存储访问过的类。
		 * >>> 如果可以存储成功，则返回true，也代表之前没有访问过，所以才可以访问，所以执行对应的过滤逻辑
		 * >>> 如果存储失败，则返回false，也代表之前访问过了，就不再执行对应的过滤逻辑
		 */
		if (visited.add(sourceClass)) {
			/**
			 * 以@SpringBootApplication为例，则sourceClass.getAnnotations()获取到的是：
			 * org.springframework.boot.autoconfigure.SpringBootApplication
			 */
			for (SourceClass annotation : sourceClass.getAnnotations()/* 获取类上的所有注解（题外：注解其实是接口，也有对应的Class对象） */) {
				// 注解的全限定类名
				String annName = annotation.getMetadata().getClassName();
				/**
				 * 判断"注解的全限定类名"是不是等于"@Import注解的全限定类名"，也就是判断是不是@Import
				 * >>> 如果是，就不再递归，而是进入下一个for循环。
				 * 当所有的for循环结束后，如果包含@Import，那么sourceClass.getAnnotationAttributes(Import.class.getName(), "value")就可以获取到@Import的vaue属性值
				 * 如果说for循环结束后，不包含@Import，那么sourceClass.getAnnotationAttributes(Import.class.getName(), "value")则为null
				 * >>> 如果不是，则继续递归当前注解，看当前注解上的注解有没有是@Import的
				 */
				// 题外：一个类/注解上只能加一个@Import
				if (!annName.equals(Import.class.getName())) {
					// ⚠️递归当前注解，看当前注解上的注解有没有是@Import的
					collectImports(annotation, imports, visited);
				}
			}
			/**
			 * 当所有的for循环结束后，如果包含@Import，那么sourceClass.getAnnotationAttributes(Import.class.getName(), "value")就可以获取到@Import的vaue属性值
			 * 如果说for循环结束后，不包含@Import，那么sourceClass.getAnnotationAttributes(Import.class.getName(), "value")则为null
			 */
			// 获取@Import的value属性值（@Import的value属性值也就是要导入的类）
			imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
		}
	}

	/**
	 * 一、处理@Import导入的类 —— {@link Import}
	 *
	 * 有@Import(A.class)、ImportSelector#selectImports()、DeferredImportSelector#selectImports()这三种方式可以导入类。
	 * 会对这三种方式导入的类，继续进行processImports()处理，直到导入的类被当成一个ImportBeanDefinitionRegistrar、或者是@Configuration一样的配置类进行处理！
	 *
	 * 1、注意：只有当@Import(A.class)、ImportSelector#selectImports()、DeferredImportSelector#selectImports()导入的类，
	 * 是没有实现ImportSelector、DeferredImportSelector、ImportBeanDefinitionRegistrar这三个接口的类，才算是导入的配置类！
	 *
	 * 例如：@Import(A.class)导入的A没有实现ImportSelector、DeferredImportSelector、ImportBeanDefinitionRegistrar等接口，则A是作为配置类被处理
	 *
	 * 例如：@Import(A.class)导入的A implements ImportSelector，A是导入的类，但是A不是导入的配置类，会继续对A进行processImports()处理，
	 * 获取A#selectImports()，假设A#selectImports()获取到的是B.class.getName()，B是导入的类，那么继续对B进行processImports()处理，
	 * （1）如果B没有实现ImportSelector、DeferredImportSelector、ImportBeanDefinitionRegistrar这3个接口，那就当作普通配置类处理掉！
	 * （2）如果B没有实现ImportSelector、DeferredImportSelector这2个接口，但是实现了ImportBeanDefinitionRegistrar，那么就当作ImportBeanDefinitionRegistrar处理掉；
	 * （3）如果B没有实现DeferredImportSelector接口，实现了ImportSelector或者DeferredImportSelector接口，那么B不会作为配置类被处理，
	 * 而是会继续执行B#selectImports()，假设B#selectImports()获取到的是C.class.getName，那么继续对C进行processImports()处理，
	 * 如此循环往复下去，直到selectImports()所导入的类没有实现ImportSelector、DeferredImportSelector这2个接口，才算结束
	 *
	 * 总结：最终一定是要得到一个没有实现ImportSelector、DeferredImportSelector这2个接口的类，然后进行处理：
	 * （1）要么是实现了ImportBeanDefinitionRegistrar接口，当作ImportBeanDefinitionRegistrar处理；
	 * （2）要是么也没有ImportBeanDefinitionRegistrar接口，当作普通配置类进行处理。
	 * 只有这样才能终止processImports()递归调用！
	 *
	 * 2、注意：@Import导入的类中，如果导入的是配置类，则会注册"导入的配置类 bd"到容器中，后面进行实例化；
	 * 导入的不是配置类，则不会注册对应bd。也就是说，导入的配置类将来会有其bean对象存在容器中，而导入的不是配置类，则不会有！
	 *
	 * 源码验证：
	 * （1）{@link ConfigurationClassParser#processConfigurationClass}处理导入的配置类，其中this.configurationClasses.put(configClass, configClass)，是存放所有解析完成的配置类
	 * （2）然后在{@link ConfigurationClassPostProcessor#processConfigBeanDefinitions}中，处理完毕parser.parse(candidates) = ConfigurationClassParser#parse()
	 * 之后的this.reader.loadBeanDefinitions(configClasses) = {@link ConfigurationClassBeanDefinitionReader#loadBeanDefinitions}里面，会注册所有"配置类 bd"，里面就包括"@Import导入进来的配置类 bd"
	 *
	 * 3、题外：一个配置类上只能写一个@Import，不过一个@Import可以导入多个类
	 *
	 * 4、DeferredImportSelector和ImportSelector的区别：
	 * (1)如果只实现了ImportSelector接口就立即处理
	 * (2)实现了DeferredImportSelector则延迟处理，等到所有配置类都处理完毕之后，再处理它
	 *
	 * 5、疑问：既然@Import可以直接导入配置类，为什么还要有ImportSelector来导入配置类呢？
	 * 因为ImportSelector的比@Import具备灵活性。@Import是直接写死导入的配置类，而ImportSelector#selectImports()可以写if..else，根据不同的条件来动态选择要导入的配置类！
	 *
	 * @param configClass					一般来说，configClass和currentSourceClass是同一个
	 * @param currentSourceClass
	 * @param importCandidates				被导入的类
	 * @param exclusionFilter
	 * @param checkForCircularImports
	 */
	private void processImports(ConfigurationClass configClass/* 配置类 */, SourceClass currentSourceClass/* 当前配置类 */,
								Collection<SourceClass> importCandidates/* 导入候选人：被导入的类 */, Predicate<String> exclusionFilter,
								boolean checkForCircularImports) {

		// 如果使用@Import修饰的类集合为空，那么直接返回
		if (importCandidates.isEmpty()) {
			return;
		}
		// 通过一个栈结构解决循环引入（或者链式引入的问题）
		if (checkForCircularImports && isChainedImportOnStack(configClass)) {
			this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
		} else {
			// 添加到栈中，用于处理循环引用的问题
			this.importStack.push(configClass);
			try {
				// 遍历每一个@Import的类
				for (SourceClass candidate : importCandidates) {
					/**
					 * 三个if情况：
					 * 		加了@Import的类属于ImportSelector.class
					 * 		加了@Import的类属于ImportBeanDefinitionRegistrar.class
					 * 		加了@Import的类,不属于ImportSelector.class，也不属于ImportBeanDefinitionRegistrar.class，而是程序员自己定义的普通类
					 */
					/* 1、如果是实现了ImportSelector接口的bd */
					// 检查配置类Import引入的类是否是ImportSelector的子类
					if (candidate.isAssignable(ImportSelector.class/* 导入选择器 */)) {
						// Candidate class is an ImportSelector -> delegate to it to determine imports - 候选类是一个ImportSelector->委托给它以确定导入
						// 候选类是一个导入选择器 —> 委托来确定是否进行导入
						Class<?> candidateClass = candidate.loadClass();

						// ⚠️通过反射实例化@Import的类，也就是通过反射实例化一个ImportSelect对象
						ImportSelector selector = ParserStrategyUtils.instantiateClass(candidateClass, ImportSelector.class,
								this.environment, this.resourceLoader, this.registry);

						// 🍭️执行ImportSelector#getExclusionFilter()
						// 获取选择器的额外过滤器
						Predicate<String> selectorFilter = selector.getExclusionFilter/* 获取排除的过滤器 */();
						if (selectorFilter != null) {
							exclusionFilter = exclusionFilter.or(selectorFilter);
						}

						/*

						1.1、如果实现了DeferredImportSelector接口，则添加到deferredImportSelectorHandler实例中，等到所有的配置类加载完成后，再处理它。
						也是调用processImports()，处理DeferredImportSelector#selectImports()导入的类！
						如果导入的类没有实现ImportSelector、DeferredImportSelector、ImportBeanDefinitionRegistrar接口，就会被当作配置类一样进行处理！

						*/
						/**
						 * DeferredImportSelector和ImportSelector的区别：
						 * 1、如果只实现了ImportSelector接口就立即处理
						 * 2、实现了DeferredImportSelector则延迟处理，等到所有配置类都处理完毕之后，再处理它
						 */
						// 判断是不是实现了DeferredImportSelector，如果实现了就放入deferredImportSelectorHandler实例当中，进行延迟处理（将会在所有的配置类都加载完毕后，再加载）
						// >>> 如果没有就执行ImportSelector#selectImports()方法，将类全名称形成资源类集合，然后再递归processImports()
						if (selector instanceof DeferredImportSelector  /* 延迟导入选择器、延时加载ImportSelector */ /* DeferredImportSelector extends ImportSelector */ ) {
							// 将选择器添加到deferredImportSelectorHandler实例中，等到所有的配置类加载完成后，统一处理自动化配置类
							this.deferredImportSelectorHandler/* 延迟导入选择器的处理程序 */.handle(configClass, (DeferredImportSelector) selector);
						}
						/* 1.2、没有实现DeferredImportSelector接口，则立即进行处理，获取导入的类，然后对导入的类进行processImports()处理 */
						else {
							// 🍭执行ImportSelector#selectImports()，获取到导入的类
							// 题外：currentSourceClass.getMetadata()：标注@Import(ImportBeanDefinitionRegistrar实现类.class)注解的配置类的注解元数据
							String[] importClassNames/* 导入的类名 */ = selector.selectImports/* 选择进口 */(currentSourceClass.getMetadata());

							// 因为ImportSelector#selectImports()也是导入进来的类，所以对ImportSelector#selectImports()导入进来的类进行处理！
							// 所以importSourceClasses = ImportSelector#selectImports()导入进来的类
							Collection<SourceClass> importSourceClasses/* 导入的资源类 */ = asSourceClasses(importClassNames, exclusionFilter);

							// ⚠️递归处理
							// 处理ImportSelector#selectImports()导入进来的类
							// >>> 因为ImportSelector#selectImports()也是导入进来的类，所以对ImportSelector#selectImports()导入进来的类进行处理！
							// >>> 且ImportSelector#selectImports()导入的类也有可能是实现了ImportSelector、DeferredImportSelector、ImportBeanDefinitionRegistrar等接口
							// >>> 如果ImportSelector#selectImports()导入的类没有实现上诉中的任何一个接口，那么就当作配置类来进行处理
							processImports(configClass/* 从始至终没变过 */, currentSourceClass,
									importSourceClasses, exclusionFilter, false);
						}
					}
					/*

					2、如果是实现了ImportBeanDefinitionRegistrar接口的bd，放入importBeanDefinitionRegistrars map当中

					题外：ImportBeanDefinitionRegistrar：用于将bd注册到beanDefinitionMap当中
					题外：importBeanDefinitionRegistrars map在ConfigurationClassPostProcessor#processConfigBeanDefinitions ——>
					this.reader.loadBeanDefinitions(configClasses); = ConfigurationClassParser#loadBeanDefinitions() 里面进行处理的

					*/
					else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
						// Candidate class is an ImportBeanDefinitionRegistrar ->
						// delegate to it to register additional bean definitions
						// 上面的翻译：候选类是一个ImportBeanDefinitionRegistrar -> 委托给它，注册额外的bd
						Class<?> candidateClass = candidate.loadClass();

						// 把导入的类进行实例化操作
						ImportBeanDefinitionRegistrar registrar =
								ParserStrategyUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class,
										this.environment, this.resourceLoader, this.registry);

						/**
						 * 1、题外：这个map集合是在{@link ConfigurationClassPostProcessor#processConfigBeanDefinitions} ——>
						 * this.reader.loadBeanDefinitions(configClasses) = {@link ConfigurationClassBeanDefinitionReader#loadBeanDefinitions} 里面进行处理的
						 */
						// 添加到一个map集合，为的是在后续处理！
						configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
					}
					/*

					3、普通类(没有实现ImportSelector、DeferredImportSelector、ImportBeanDefinitionRegistrar接口，
					但是被@Import(A.class)或者ImportSelector#selectImports()导入的类)，作为配置类进行处理

					题外：就是@Import注解，可以导入任意类。导入的类，不一定要实现ImportSelector、ImportBeanDefinitionRegistrar接口。
					如果导入的类实现了ImportSelector、ImportBeanDefinitionRegistrar接口，那么会做相应的处理；
					但是如果导入的类没有实现ImportSelector、ImportBeanDefinitionRegistrar接口，那么就会当成一个配置类进行处理！

					*/
					else {
						// Candidate class not an ImportSelector or ImportBeanDefinitionRegistrar ->
						// process it as an @Configuration class
						// 上面的翻译：候选类不是ImportSelector或ImportBeanDefinitionRegistrar->将其作为@Configuration类处理

						this.importStack.registerImport(
								currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());

						/**
						 * processConfigurationClass()里面主要就是把类放到configurationClasses，
						 * configurationClasses是一个集合，会在后面拿出来解析成bd，注册到beanFactory
						 * 从这里可以看出，普通的类在扫描出来的时候就被注册了；但如果是importSelector，会先放到configurationClass，后面再进行注册
						 */
						// 作为配置类进行处理

						// 注意：@Import、ImportSelector#selectImports()、DeferredImportSelector#selectImports()引入的，
						// >>> 但未实现ImportSelector、DeferredImportSelector、ImportBeanDefinitionRegistrar接口的类，都是在这里作为配置类进行处理！
						processConfigurationClass/* 处理配置类 */(candidate.asConfigClass(configClass), exclusionFilter);
					}
				}
			} catch (BeanDefinitionStoreException ex) {
				throw ex;
			} catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to process import candidates for configuration class [" +
								configClass.getMetadata().getClassName() + "]", ex);
			} finally {
				this.importStack.pop();
			}
		}
	}

	private boolean isChainedImportOnStack(ConfigurationClass configClass) {
		if (this.importStack.contains(configClass)) {
			String configClassName = configClass.getMetadata().getClassName();
			AnnotationMetadata importingClass = this.importStack.getImportingClassFor(configClassName);
			while (importingClass != null) {
				if (configClassName.equals(importingClass.getClassName())) {
					return true;
				}
				importingClass = this.importStack.getImportingClassFor(importingClass.getClassName());
			}
		}
		return false;
	}

	ImportRegistry getImportRegistry() {
		return this.importStack;
	}


	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link ConfigurationClass}.
	 * —— 从 {@link ConfigurationClass} 获取 {@link SourceClass} 的工厂方法。
	 */
	private SourceClass asSourceClass(ConfigurationClass configurationClass, Predicate<String> filter) throws IOException {
		// 标注了@Import的配置类元数据
		AnnotationMetadata metadata = configurationClass.getMetadata();

		if (metadata instanceof StandardAnnotationMetadata/* 标准注解元数据 */) {
			return asSourceClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass()/* 获取父类 */, filter);
		}

		return asSourceClass(metadata.getClassName(), filter);
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link Class}. —— 从 {@link Class} 获取 {@link SourceClass} 的工厂方法。
	 */
	SourceClass asSourceClass(@Nullable Class<?> classType, Predicate<String> filter) throws IOException {
		if (classType == null || filter.test(classType.getName())) {
			return this.objectSourceClass;
		}
		try {
			// Sanity test that we can reflectively read annotations,
			// including Class attributes; if not -> fall back to ASM —— 健全性测试，我们可以反射性地读取注释，包括类属性；如果不是 -> 回退到 ASM
			for (Annotation ann : classType.getDeclaredAnnotations()) {
				AnnotationUtils.validateAnnotation(ann);
			}
			return new SourceClass(classType);
		} catch (Throwable ex) {
			// Enforce ASM via class name resolution
			return asSourceClass(classType.getName(), filter);
		}
	}

	/**
	 * Factory method to obtain {@link SourceClass SourceClasss} from class names.
	 */
	private Collection<SourceClass> asSourceClasses(String[] classNames, Predicate<String> filter) throws IOException {
		List<SourceClass> annotatedClasses = new ArrayList<>(classNames.length);
		for (String className : classNames) {
			annotatedClasses.add(asSourceClass(className, filter));
		}
		return annotatedClasses;
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a class name.
	 *
	 * 从类名中获取 {@link SourceClass} 的工厂方法。
	 */
	SourceClass asSourceClass(@Nullable String className, Predicate<String> filter) throws IOException {
		if (className == null || filter.test(className)) {
			return this.objectSourceClass;
		}
		if (className.startsWith("java")) {
			// Never use ASM for core java types —— 永远不要将 ASM 用于核心 java 类型
			try {
				return new SourceClass(ClassUtils.forName(className, this.resourceLoader.getClassLoader()));
			} catch (ClassNotFoundException ex) {
				throw new NestedIOException("Failed to load class [" + className + "]", ex);
			}
		}
		return new SourceClass(this.metadataReaderFactory.getMetadataReader(className)/* SimpleMetadataReader */ );
	}


	@SuppressWarnings("serial")
	private static class ImportStack extends ArrayDeque/* 数组双端队列 */<ConfigurationClass> implements ImportRegistry {

		// key：导入的类的全限定类名称，例如：com.springstudy.importspring.ImportSelector.UserImportSelectOne.UserOne；
		// value：导入的类的注解元数据，例如：UserOne类的注解元数据
		private final MultiValueMap<String, AnnotationMetadata> imports = new LinkedMultiValueMap<>();

		public void registerImport(AnnotationMetadata importingClass, String importedClass) {
			this.imports.add(importedClass, importingClass);
		}

		@Override
		@Nullable
		public AnnotationMetadata getImportingClassFor(String importedClass) {
			return CollectionUtils.lastElement(this.imports.get(importedClass));
		}

		@Override
		public void removeImportingClass(String importingClass) {
			for (List<AnnotationMetadata> list : this.imports.values()) {
				for (Iterator<AnnotationMetadata> iterator = list.iterator(); iterator.hasNext(); ) {
					if (iterator.next().getClassName().equals(importingClass)) {
						iterator.remove();
						break;
					}
				}
			}
		}

		/**
		 * Given a stack containing (in order)
		 * <ul>
		 * <li>com.acme.Foo</li>
		 * <li>com.acme.Bar</li>
		 * <li>com.acme.Baz</li>
		 * </ul>
		 * return "[Foo->Bar->Baz]".
		 */
		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner("->", "[", "]");
			for (ConfigurationClass configurationClass : this) {
				joiner.add(configurationClass.getSimpleName());
			}
			return joiner.toString();
		}
	}


	private class DeferredImportSelectorHandler {

		@Nullable
		private List<DeferredImportSelectorHolder> deferredImportSelectors = new ArrayList<>();

		/**
		 * Handle the specified {@link DeferredImportSelector}. If deferred import
		 * selectors are being collected, this registers this instance to the list. If
		 * they are being processed, the {@link DeferredImportSelector} is also processed
		 * immediately according to its {@link DeferredImportSelector.Group}.
		 *
		 * @param configClass    the source configuration class —— 源配置类
		 * @param importSelector the selector to handle
		 */
		public void handle(ConfigurationClass configClass/* 配置类，也就是标注@Import注解的类 */,
						   DeferredImportSelector importSelector/* @Import导入的DeferredImportSelector实例 */) {

			// DeferredImportSelector持有器，就包含了两个东西：（1）标注@Import的类；（2）DeferredImportSelector
			DeferredImportSelectorHolder holder = new DeferredImportSelectorHolder(configClass, importSelector);

			/* 1、deferredImportSelectors == null，立即处理DeferredImportSelector */
			// 注意：这里基本不会走
			if (this.deferredImportSelectors == null) {
				/* (1)创建DeferredImportSelectorGroupingHandler处理DeferredImportSelector */
				DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();

				/*

				(2)按分组方式，注册当前的DeferredImportSelector到handler中

				注意：⚠️里面会执行DeferredImportSelector#getImportGroup()，获取Group类型

				*/
				handler.register(holder);

				/* (3)处理当前分组的DeferredImportSelector，当前分组也就只有当前这一个DeferredImportSelector，其实也就是处理当前的DeferredImportSelector */
				handler.processGroupImports();
			}
			/* 2、deferredImportSelectors != null，添加DeferredImportSelectorHolder到deferredImportSelectors中，进行延迟处理 */
			else {
				/**
				 * ⚠️1、️几乎走的都是这里，因为在创建DeferredImportSelectorHandler时，deferredImportSelectors就初始化了，所以不可能为空；
				 * 只有当在{@link ConfigurationClassParser#parse(Set<BeanDefinitionHolder> configCandidates)} ——>
				 * this.deferredImportSelectorHandler.process() = {@link DeferredImportSelectorHandler#process()} 处理DeferredImportSelector时，
				 * 才会清空deferredImportSelectors
				 */
				this.deferredImportSelectors.add(holder);
			}

		}

		/**
		 * 延迟处理deferredImportSelectors中所有的DeferredImportSelector
		 */
		public void process() {
			List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
			// 置为空
			this.deferredImportSelectors = null;
			try {
				/* 1、处理deferredImportSelectors中所有的DeferredImportSelector */
				if (deferredImports != null) {

					/* (1)创建DeferredImportSelectorGroupingHandler处理DeferredImportSelector */
					DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();

					/* (2)排序 */
					// 排序（@Ordered）
					deferredImports.sort(DEFERRED_IMPORT_COMPARATOR/* 延迟导入比较器 */);

					/*

					(3)按分组方式，注册deferredImports中所有的DeferredImportSelector到handler中

					注意：⚠️里面会执行DeferredImportSelector#getImportGroup()，获取Group类型

					*/
					// 循环deferredImports，按分组方式，注册所有的DeferredImportSelector到handler中
					deferredImports.forEach(handler::register);

					/* (4)以分组为粒度，处理所有的DeferredImportSelector */
					handler.processGroupImports/* 处理组导入 */();

				}
			} finally {
				// 重新初始化，令其不为null
				this.deferredImportSelectors = new ArrayList<>();
			}
		}
	}


	private class DeferredImportSelectorGroupingHandler {

		/**
		 * 1、DeferredImportSelectorGrouping里面包含了：
		 * （1）分组实例：如果Group不为null，就是创建Group实例；否则是创建DefaultDeferredImportSelectorGroup实例
		 * （2）DeferredImportSelectorHolder对象
		 *
		 * 2、DeferredImportSelectorHolder里面包含了：（1）配置类，也就是标注@Import的类；（2）DeferredImportSelector对象
		 */
		// ⚠️以分组的形式，存储DeferredImportSelector
		// key：存在Group，就是Group class；如果不存在Group，就用DeferredImportSelectorHolder对象
		// value：DeferredImportSelectorGrouping
		private final Map<Object, DeferredImportSelectorGrouping> groupings = new LinkedHashMap<>();

		// 源配置类，也就是标注@Import注解的类
		// key：源配置类的注解元数据
		// value：源配置类
		private final Map<AnnotationMetadata, ConfigurationClass> configurationClasses = new HashMap<>();

		/**
		 * 1、按分组方式，注册DeferredImportSelector
		 * 注意：⚠️会执行DeferredImportSelector#getImportGroup()，获取Group类型
		 *
		 * 2、存放配置类（也就是直接标注@Import的类作为配置类）
		 *
		 * @param deferredImport
		 */
		public void register(DeferredImportSelectorHolder deferredImport) {
			/* 1、按分组方式，注册DeferredImportSelector */

			// 对不同的导入选择器进行分组，默认为null，就采用自身对象进行分组
			// 因为有多个不同的导入选择器，所以有分组的概念！

			/* (1)执行DeferredImportSelector#getImportGroup()，获取Group类型 */
			/**
			 * 1、deferredImport.getImportSelector()：获取DeferredImportSelector
			 *
			 * 2、deferredImport.getImportSelector().getImportGroup()：执行DeferredImportSelector#getImportGroup()，获取Group类型
			 */
			// 执行DeferredImportSelector#getImportGroup()，获取Group类型
			// 题外：需要根据Group来分组
			Class<? extends Group> group = deferredImport.getImportSelector().getImportGroup();

			/* (2)往DeferredImportSelectorGrouping中放入Group实例和DeferredImportSelector */
			// 获取"延迟导入选择器"的分组，也就是DeferredImportSelectorGrouping对象
			// DeferredImportSelectorGrouping里面存放了DeferredImportSelectorHolder集合
			DeferredImportSelectorGrouping grouping = this.groupings.computeIfAbsent/* 计算如果不存在 */(
					// key值：存在Group，就用Group class进行分组；如果不存在Group，就用DeferredImportSelectorHolder对象作为分组
					(group != null ? group : deferredImport),
					/**
					 * 1、createGroup(group)：创建分组实例。
					 * 如果Group不为null，就是创建Group实例；否则是创建DefaultDeferredImportSelectorGroup实例
					 */
					// value值：DeferredImportSelectorGrouping
					// 创建DeferredImportSelectorGrouping，并往里面放入分组实例
					key -> new DeferredImportSelectorGrouping(createGroup(group)));

			// ⚠️往DeferredImportSelectorGrouping里面添加DeferredImportSelectorHolder
			grouping.add(deferredImport);

			/* 2、存放配置类（也就是直接标注@Import的类作为配置类）*/
			/**
			 * 题外：在ConfigurationClassPostProcessor#processConfigBeanDefinitions()中处理完毕parser.parse(candidates) = ConfigurationClassParser#parse()
			 * 之后的this.reader.loadBeanDefinitions(configClasses)会进行处理！
			 */
			// deferredImport.getConfigurationClass()：获取的是配置类，也就是标注@Import注解的类
			this.configurationClasses.put(deferredImport.getConfigurationClass().getMetadata()/* 源配置类的注解元数据 */,
					deferredImport.getConfigurationClass()/* 源配置类 */);
		}

		/**
		 * 以分组为粒度，处理所有的DeferredImportSelector
		 */
		public void processGroupImports() {
			/* 1、遍历处理每个分组中所有的DeferredImportSelector */
			// 遍历groupings中的DeferredImportSelectorGrouping
			// DeferredImportSelectorGrouping：代表一个分组，内部存放了一个分组内所有的DeferredImportSelector
			// 所以这里的含义其实是：以分组为粒度进行遍历，遍历处理每个分组中所有的DeferredImportSelector
			for (DeferredImportSelectorGrouping grouping : this.groupings.values()) {
				/* (1)执行DeferredImportSelector#getExclusionFilter()，获取Predicate */
				// 里面会执行DeferredImportSelector#getExclusionFilter()，获取Predicate
				Predicate<String> exclusionFilter = grouping.getCandidateFilter();
				/*

				(2)grouping.getImports()：执行分组内所有的Group#process()、Group#selectImports()，返回导入的类的全限定类名

				题外：在执行Group#process()时，如果Group为null，那么执行的是DefaultDeferredImportSelectorGroup#process()，里面执行了⚠️DeferredImportSelector#selectImports()，构建Entry集合；
				⚠️也就是从这里可以得知，如果是Group为null的情况下，才会执行️DeferredImportSelector#selectImports()；如果Group不为null，就根据自定义的Group#process()逻辑而言，决定是否执行DeferredImportSelector#selectImports()

				 */
				// grouping.getImports()：执行分组内所有的Group#process()、Group#selectImports()，返回导入的类的全限定类名
				grouping.getImports().forEach(entry/* 里面包含了要导入的类的全限定类名 */ -> {
					// 通过"标注了@Import的配置类元数据"获取"标注了@Import的配置类"
					ConfigurationClass configurationClass = this.configurationClasses.get(entry.getMetadata()/* 标注了@Import的配置类元数据 */);
					try {
						/*

						(3)处理导入的类
						>>> (1)如果Group为null，那么就是执行DeferredImportSelector#selectImports()获取要导入的类；
						>>> (2)如果Group不为null，那么就是执行DeferredImportSelector.Group#selectImports()获取要导入的类

						*/
						processImports(configurationClass/* 配置类 */,
								asSourceClass(configurationClass, exclusionFilter),
								Collections.singleton(asSourceClass(entry.getImportClassName()/* 获取导入的类 */, exclusionFilter)),
								exclusionFilter, false);
					} catch (BeanDefinitionStoreException ex) {
						throw ex;
					} catch (Throwable ex) {
						throw new BeanDefinitionStoreException(
								"Failed to process import candidates for configuration class [" +
										configurationClass.getMetadata().getClassName() + "]", ex);
					}
				});
			}
		}

		/**
		 * 创建分组实例。如果Group不为null，就是创建Group实例；否则是创建DefaultDeferredImportSelectorGroup实例
		 *
		 * @param type	分组类型
		 */
		private Group createGroup(@Nullable Class<? extends Group> type) {
			// 1、获取分组的类型
			// group不为null，就是group；否则是DefaultDeferredImportSelectorGroup
			Class<? extends Group> effectiveType/* 分组的有效类型 */ = (type != null ? type : DefaultDeferredImportSelectorGroup.class);

			// 2、根据分组类型，实例化分组实例
			return ParserStrategyUtils.instantiateClass(effectiveType/* 实例化的Class */, Group.class,
					ConfigurationClassParser.this.environment,
					ConfigurationClassParser.this.resourceLoader,
					ConfigurationClassParser.this.registry);
		}
	}


	/**
	 * DeferredImportSelector包装器，主要包含：
	 * 1、配置类，也就是标注@Import注解的类
	 * 2、@Import导入的DeferredImportSelector实例
	 */
	private static class DeferredImportSelectorHolder {

		// 配置类（也就是直接标注@Import的类）
		private final ConfigurationClass configurationClass;

		// DeferredImportSelector实例（有可能是@Import、ImportSelector#selectImports()导入、以及DeferredImportSelector导入的）
		private final DeferredImportSelector importSelector;

		public DeferredImportSelectorHolder(ConfigurationClass configClass, DeferredImportSelector selector) {
			this.configurationClass = configClass;
			this.importSelector = selector;
		}

		public ConfigurationClass getConfigurationClass() {
			return this.configurationClass;
		}

		public DeferredImportSelector getImportSelector() {
			return this.importSelector;
		}
	}


	/**
	 * 代表一个分组，内部存放了一个分组内所有的DeferredImportSelector
	 */
	private static class DeferredImportSelectorGrouping {

		// 分组实例
		// 如果Group不为null，就是Group实例；否则是DefaultDeferredImportSelectorGroup实例
		private final DeferredImportSelector.Group group;

		// 存放当前分组内所有的DeferredImportSelectorHolder
		private final List<DeferredImportSelectorHolder> deferredImports = new ArrayList<>();

		DeferredImportSelectorGrouping(Group group) {
			this.group = group;
		}

		public void add(DeferredImportSelectorHolder deferredImport) {
			this.deferredImports.add(deferredImport);
		}

		/**
		 * 执行分组内所有的Group#process()、Group#selectImports()
		 *
		 *
		 * Return the imports defined by the group. —— 返回组定义的导入
		 *
		 * @return each import with its associated configuration class
		 */
		public Iterable<Group.Entry> getImports() {
			/*

			1、执行分组内所有的Group#process()
			如果Group为null，那么执行的是DefaultDeferredImportSelectorGroup#process()，里面执行了⚠️DeferredImportSelector#selectImports()，构建Entry集合；
			⚠️也就是从这里可以得知，如果是Group为null的情况下，必然会执行️DeferredImportSelector#selectImports()；如果Group不为null，就根据自定义的Group#process()逻辑而言，决定是否执行DeferredImportSelector#selectImports()

			*/
			// 遍历当前分组内所有的DeferredImportSelectorHolder
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				/**
				 * 1、this.group：分组实例 —— 如果Group不为null，就是Group实例；否则是DefaultDeferredImportSelectorGroup实例
				 * 题外：经典的有SpringBoot的AutoConfigurationImportSelector
				 */
				// 执行Group#process()
				// 如果Group为null，那么执行的是DefaultDeferredImportSelectorGroup#process()，里面执行了⚠️DeferredImportSelector#selectImports()，构建Entry集合
				this.group.process(deferredImport.getConfigurationClass().getMetadata()/* 直接标注@Import的类的元数据 */,
						deferredImport.getImportSelector()/* DeferredImportSelector */);
			}

			/* 2、执行分组内所有的Group#selectImports() */
			// ⚠️获取导入的类名称（全限定类名）
			return this.group.selectImports();
		}

		/**
		 * 获取Predicate
		 */
		public Predicate<String> getCandidateFilter() {
			// 默认排除过滤器：过滤掉【java.lang.annotation. || org.springframework.stereotype.】开头的类
			Predicate<String> mergedFilter = DEFAULT_EXCLUSION_FILTER;

			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				// 执行DeferredImportSelector#getExclusionFilter()，获取Predicate
				Predicate<String> selectorFilter = deferredImport.getImportSelector().getExclusionFilter();

				if (selectorFilter != null) {
					// 组合两个Predicate的逻辑，变为一个新的Predicate
					mergedFilter = mergedFilter.or(selectorFilter);
				}
			}

			return mergedFilter;
		}
	}


	private static class DefaultDeferredImportSelectorGroup implements Group {

		// 存储着所有导入的类的全限定类名
		private final List<Entry> imports = new ArrayList<>();

		/**
		 * 执行DeferredImportSelector#selectImports()，构建Entry集合
		 *
		 * @param metadata						直接标注@Import的类的注解元数据
		 * @param selector						DeferredImportSelector
		 */
		@Override
		public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
			for (String importClassName : selector.selectImports(metadata)/* DeferredImportSelector#selectImports() */) {
				this.imports.add(new Entry(metadata, importClassName));
			}
		}

		@Override
		public Iterable<Entry> selectImports() {
			return this.imports;
		}

	}


	/**
	 * Simple wrapper that allows annotated source classes to be dealt with
	 * in a uniform manner, regardless of how they are loaded.
	 */
	private class SourceClass implements Ordered {

		private final Object source;  // Class or MetadataReader —— 类或元数据读取器

		private final AnnotationMetadata metadata;

		public SourceClass(Object source) {
			this.source = source;
			if (source instanceof Class) {
				this.metadata = AnnotationMetadata.introspect((Class<?>) source);
			} else {
				this.metadata = ((MetadataReader) source).getAnnotationMetadata();
			}
		}

		public final AnnotationMetadata getMetadata() {
			return this.metadata;
		}

		@Override
		public int getOrder() {
			Integer order = ConfigurationClassUtils.getOrder(this.metadata);
			return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
		}

		public Class<?> loadClass() throws ClassNotFoundException {
			if (this.source instanceof Class) {
				return (Class<?>) this.source;
			}
			String className = ((MetadataReader) this.source).getClassMetadata().getClassName();
			return ClassUtils.forName(className, resourceLoader.getClassLoader());
		}

		public boolean isAssignable(Class<?> clazz) throws IOException {
			if (this.source instanceof Class) {
				return clazz.isAssignableFrom((Class<?>) this.source);
			}
			return new AssignableTypeFilter(clazz).match((MetadataReader) this.source, metadataReaderFactory);
		}

		public ConfigurationClass asConfigClass(ConfigurationClass importedBy) {
			if (this.source instanceof Class) {
				return new ConfigurationClass((Class<?>) this.source, importedBy);
			}
			return new ConfigurationClass((MetadataReader) this.source, importedBy);
		}

		public Collection<SourceClass> getMemberClasses() throws IOException {
			Object sourceToProcess = this.source;
			if (sourceToProcess instanceof Class) {
				Class<?> sourceClass = (Class<?>) sourceToProcess;
				try {
					Class<?>[] declaredClasses = sourceClass.getDeclaredClasses();
					List<SourceClass> members = new ArrayList<>(declaredClasses.length);
					for (Class<?> declaredClass : declaredClasses) {
						members.add(asSourceClass(declaredClass, DEFAULT_EXCLUSION_FILTER));
					}
					return members;
				} catch (NoClassDefFoundError err) {
					// getDeclaredClasses() failed because of non-resolvable dependencies
					// -> fall back to ASM below
					sourceToProcess = metadataReaderFactory.getMetadataReader(sourceClass.getName());
				}
			}

			// ASM-based resolution - safe for non-resolvable classes as well
			MetadataReader sourceReader = (MetadataReader) sourceToProcess;
			String[] memberClassNames = sourceReader.getClassMetadata().getMemberClassNames();
			List<SourceClass> members = new ArrayList<>(memberClassNames.length);
			for (String memberClassName : memberClassNames) {
				try {
					members.add(asSourceClass(memberClassName, DEFAULT_EXCLUSION_FILTER));
				} catch (IOException ex) {
					// Let's skip it if it's not resolvable - we're just looking for candidates
					if (logger.isDebugEnabled()) {
						logger.debug("Failed to resolve member class [" + memberClassName +
								"] - not considering it as a configuration class candidate");
					}
				}
			}
			return members;
		}

		public SourceClass getSuperClass() throws IOException {
			if (this.source instanceof Class) {
				return asSourceClass(((Class<?>) this.source).getSuperclass(), DEFAULT_EXCLUSION_FILTER);
			}
			return asSourceClass(
					((MetadataReader) this.source).getClassMetadata().getSuperClassName(), DEFAULT_EXCLUSION_FILTER);
		}

		public Set<SourceClass> getInterfaces() throws IOException {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Class<?> ifcClass : sourceClass.getInterfaces()) {
					result.add(asSourceClass(ifcClass, DEFAULT_EXCLUSION_FILTER));
				}
			} else {
				for (String className : this.metadata.getInterfaceNames()) {
					result.add(asSourceClass(className, DEFAULT_EXCLUSION_FILTER));
				}
			}
			return result;
		}

		public Set<SourceClass> getAnnotations() {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Annotation ann : sourceClass.getDeclaredAnnotations()) {
					Class<?> annType = ann.annotationType();
					if (!annType.getName().startsWith("java")) {
						try {
							result.add(asSourceClass(annType, DEFAULT_EXCLUSION_FILTER));
						} catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			} else {
				for (String className : this.metadata.getAnnotationTypes()) {
					if (!className.startsWith("java")) {
						try {
							result.add(getRelated(className));
						} catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			return result;
		}

		public Collection<SourceClass> getAnnotationAttributes(String annType, String attribute) throws IOException {
			Map<String, Object> annotationAttributes = this.metadata.getAnnotationAttributes(annType, true);
			if (annotationAttributes == null || !annotationAttributes.containsKey(attribute)) {
				return Collections.emptySet();
			}
			String[] classNames = (String[]) annotationAttributes.get(attribute);
			Set<SourceClass> result = new LinkedHashSet<>();
			for (String className : classNames) {
				result.add(getRelated(className));
			}
			return result;
		}

		private SourceClass getRelated(String className) throws IOException {
			if (this.source instanceof Class) {
				try {
					Class<?> clazz = ClassUtils.forName(className, ((Class<?>) this.source).getClassLoader());
					return asSourceClass(clazz, DEFAULT_EXCLUSION_FILTER);
				} catch (ClassNotFoundException ex) {
					// Ignore -> fall back to ASM next, except for core java types.
					if (className.startsWith("java")) {
						throw new NestedIOException("Failed to load class [" + className + "]", ex);
					}
					return new SourceClass(metadataReaderFactory.getMetadataReader(className));
				}
			}
			return asSourceClass(className, DEFAULT_EXCLUSION_FILTER);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other || (other instanceof SourceClass &&
					this.metadata.getClassName().equals(((SourceClass) other).metadata.getClassName())));
		}

		@Override
		public int hashCode() {
			return this.metadata.getClassName().hashCode();
		}

		@Override
		public String toString() {
			return this.metadata.getClassName();
		}
	}


	/**
	 * {@link Problem} registered upon detection of a circular {@link Import}.
	 */
	private static class CircularImportProblem extends Problem {

		public CircularImportProblem(ConfigurationClass attemptedImport, Deque<ConfigurationClass> importStack) {
			super(String.format("A circular @Import has been detected: " +
							"Illegal attempt by @Configuration class '%s' to import class '%s' as '%s' is " +
							"already present in the current import stack %s", importStack.element().getSimpleName(),
					attemptedImport.getSimpleName(), attemptedImport.getSimpleName(), importStack),
					new Location(importStack.element().getResource(), attemptedImport.getMetadata()));
		}
	}

}
