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
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.*;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Reads a given fully-populated set of ConfigurationClass instances, registering bean
 * definitions with the given {@link BeanDefinitionRegistry} based on its contents.
 *
 * <p>This class was modeled after the {@link BeanDefinitionReader} hierarchy, but does
 * not implement/extend any of its artifacts as a set of configuration classes is not a
 * {@link Resource}.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 3.0
 * @see ConfigurationClassParser
 */
class ConfigurationClassBeanDefinitionReader {

	private static final Log logger = LogFactory.getLog(ConfigurationClassBeanDefinitionReader.class);

	private static final ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	private final BeanDefinitionRegistry registry;

	private final SourceExtractor sourceExtractor;

	private final ResourceLoader resourceLoader;

	private final Environment environment;

	private final BeanNameGenerator importBeanNameGenerator;

	private final ImportRegistry importRegistry;

	private final ConditionEvaluator conditionEvaluator;


	/**
	 * Create a new {@link ConfigurationClassBeanDefinitionReader} instance
	 * that will be used to populate the given {@link BeanDefinitionRegistry}.
	 */
	ConfigurationClassBeanDefinitionReader(BeanDefinitionRegistry registry, SourceExtractor sourceExtractor,
			ResourceLoader resourceLoader, Environment environment, BeanNameGenerator importBeanNameGenerator,
			ImportRegistry importRegistry) {

		this.registry = registry;
		this.sourceExtractor = sourceExtractor;
		this.resourceLoader = resourceLoader;
		this.environment = environment;
		this.importBeanNameGenerator = importBeanNameGenerator;
		this.importRegistry = importRegistry;
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}


	/**
	 * Read {@code configurationModel}, registering bean definitions
	 * with the registry based on its contents.
	 * 阅读{@code configurationModel}，根据其内容在注册表中注册bean定义。
	 * @param configurationModel 普通类，加了注解的类，已经放入beanDefinitionMap
	 */
	public void loadBeanDefinitions(Set<ConfigurationClass> configurationModel) {
		TrackedConditionEvaluator trackedConditionEvaluator = new TrackedConditionEvaluator();
		// 遍历所有配置类
		for (ConfigurationClass configClass : configurationModel) {
			// 加载配置类的bd
			loadBeanDefinitionsForConfigurationClass(configClass, trackedConditionEvaluator);
		}
	}

	/**
	 * Read a particular {@link ConfigurationClass}, registering bean definitions
	 * for the class itself and all of its {@link Bean} methods.
	 *
	 * 阅读特定的{@link ConfigurationClass}，为类本身及其所有{@link Bean}方法注册bean定义。
	 *
	 * 加载配置类的Bean定义
	 */
	private void loadBeanDefinitionsForConfigurationClass(
			ConfigurationClass configClass, TrackedConditionEvaluator trackedConditionEvaluator) {

		/* 1、判断配置类是否要跳过 */

		if (trackedConditionEvaluator/* 跟踪状态评估器 */.shouldSkip(configClass)) {
			String beanName = configClass.getBeanName();
			if (StringUtils.hasLength(beanName) && this.registry.containsBeanDefinition(beanName)) {
				this.registry.removeBeanDefinition(beanName);
			}
			this.importRegistry.removeImportingClass(configClass.getMetadata().getClassName());
			return;
		}

		/*

		2、注册配置类bd（其中包括@Import导入进来的配置类 bd）

		注意：只有@Import、ImportSelector#selectImports()、DeferredImportSelector#selectImports()导入进来的，
		但是未实现ImportSelector、DeferredImportSelector、ImportBeanDefinitionRegistrar接口的类，才算是真正导入的类，也就是当作普通配置类进行处理！

		例如：UserOne就是被{@link com.springstudy.importspring.ImportSelector.UserImportSelectOne.UserImportSelectOne#selectImports}给import导入进来的，
		>>> 那么UserOne会被当成一个配置类被处理，然后在这里进行UserOne bd的注册！

		 */
		// 如果一个类是被import进来的，会被spring标注，然后在这里完成对应bd的注册。
		if (configClass.isImported()) {
			registerBeanDefinitionForImportedConfigurationClass/* 为导入的配置类注册 Bean 定义 */(configClass);
		}

		/* 3、为@Bean方法加载bd —— 解析和注册@Bean方法的bd */

		// 题外：static @Bean与普通@Bean的实现区别处理
		// 题外：⚠️@Configuration配置类，加了static的@Bean与没加static的@Bean的实现区别，在这里面
		for (BeanMethod beanMethod : configClass.getBeanMethods()) {
			loadBeanDefinitionsForBeanMethod(beanMethod);
		}

		/* 4、xml */

		loadBeanDefinitionsFromImportedResources(configClass.getImportedResources());

		/*

		5、处理importBeanDefinitionRegistrars集合当中的【ImportBeanDefinitionRegistrar#registerBeanDefinitions()】,可将bean定义注册到beanDefinitionMap当中

		*/

		/**
		 * 处理importBeanDefinitionRegistrars的，其是在{@link ConfigurationClassParser#processImports}ConfigurationClassParser#processImports中,ImportBeanDefinitionRegistrar实现者被放入其中
		 * 		(「configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());」添加的)
		 */
		loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars()/* importBeanDefinitionRegistrars */);
	}

	/**
	 * 注册配置类 bd
	 *
	 * Register the {@link Configuration} class itself as a bean definition. —— 将{@link Configuration}类本身注册为bd
	 */
	private void registerBeanDefinitionForImportedConfigurationClass(ConfigurationClass configClass) {
		AnnotationMetadata metadata = configClass.getMetadata();
		AnnotatedGenericBeanDefinition configBeanDef = new AnnotatedGenericBeanDefinition(metadata);

		ScopeMetadata scopeMetadata = scopeMetadataResolver.resolveScopeMetadata(configBeanDef);
		configBeanDef.setScope(scopeMetadata.getScopeName());
		String configBeanName = this.importBeanNameGenerator.generateBeanName(configBeanDef, this.registry);
		AnnotationConfigUtils.processCommonDefinitionAnnotations(configBeanDef, metadata);

		BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(configBeanDef, configBeanName);
		definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
		this.registry.registerBeanDefinition(definitionHolder.getBeanName(), definitionHolder.getBeanDefinition());
		configClass.setBeanName(configBeanName);

		if (logger.isTraceEnabled()) {
			logger.trace("Registered bean definition for imported class '" + configBeanName + "'");
		}
	}

	/**
	 * Read the given {@link BeanMethod}, registering bean definitions
	 * with the BeanDefinitionRegistry based on its contents.
	 */
	@SuppressWarnings("deprecation")  // for RequiredAnnotationBeanPostProcessor.SKIP_REQUIRED_CHECK_ATTRIBUTE
	private void loadBeanDefinitionsForBeanMethod(BeanMethod beanMethod) {
		// 获取配置类
		ConfigurationClass configClass = beanMethod.getConfigurationClass();
		// 获取标注@Bean方法的元数据
		MethodMetadata metadata = beanMethod.getMetadata();
		// 获取标注@Bean方法的名称
		String methodName = metadata.getMethodName();

		/**
		 * 根据 {@code @Conditional} 注解，确定是否应跳过某个项目。
		 * 🚩️true：跳过！什么都不做，也就是不会进行解析
		 * 🚩false：不跳过！会进行解析
		 */
		// Do we need to mark the bean as skipped by its condition? —— 我们是否需要将 bean 标记为被其条件跳过？
		if (this.conditionEvaluator.shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN)) {
			// 需要跳过的标注了@Bean的方法
			configClass.skippedBeanMethods.add(methodName);
			return;
		}

		if (configClass.skippedBeanMethods.contains(methodName)) {
			return;
		}

		// 获取@Bean注解的属性对象
		AnnotationAttributes bean = AnnotationConfigUtils.attributesFor(metadata, Bean.class);
		Assert.state(bean != null, "No @Bean annotation attributes"/* 没有@Bean注解属性 */);

		// Consider name and any aliases —— 考虑名称和任何别名

		// 获取@Bean注解的name属性
		List<String> names = new ArrayList<>(Arrays.asList(bean.getStringArray("name")));
		// 如果@Bean注解的name属性不为空，就获取name属性的第一个值，作为beanName
		// （之所以names.remove(0)，是用其它的name属性值作为别名）；
		// 如果@Bean注解的name属性为空，就用标注@Bean方法的方法名称作为beanName
		String beanName = (!names.isEmpty() ? names.remove(0) : methodName);

		// 用name属性值，第一值除外的names属性值作为别名
		// Register aliases even when overridden —— 即使被覆盖也注册别名
		for (String alias : names) {
			this.registry.registerAlias(beanName, alias);
		}

		// Has this effectively been overridden before (e.g. via XML)? —— 这之前是否被有效地覆盖（例如通过 XML）？
		if (isOverriddenByExistingDefinition/* 被现有定义覆盖 */(beanMethod, beanName)) {
			// @Bean的beanName与配置类的beanName，相同了，发生冲突了，就报错！
			if (beanName.equals(beanMethod.getConfigurationClass().getBeanName())) {
				throw new BeanDefinitionStoreException(beanMethod.getConfigurationClass().getResource().getDescription(),
						beanName, "Bean name derived from @Bean method '" + beanMethod.getMetadata().getMethodName() +
						"' clashes with bean name for containing configuration class; please make those names unique!");
			}
			return;
		}

		/* 构建@Bean方法的bd */

		ConfigurationClassBeanDefinition beanDef/* 配置类bd */ = new ConfigurationClassBeanDefinition(configClass, metadata/* @Bean方法的元数据 */);
		beanDef.setSource(this.sourceExtractor.extractSource/* 提取来源 */(metadata, configClass.getResource()));

		// 如果@Bean方法是静态的，那么就无法对其进行代理，也就不会走代理类的过滤方法，所以会打印两遍
		if (metadata.isStatic()) {
			// static @Bean method
			if (configClass.getMetadata() instanceof StandardAnnotationMetadata) {
				beanDef.setBeanClass(((StandardAnnotationMetadata) configClass.getMetadata()).getIntrospectedClass());
			}
			else {
				beanDef.setBeanClassName(configClass.getMetadata().getClassName());
			}
			beanDef.setUniqueFactoryMethodName/* 设置唯的一工厂方法名称 */(methodName);
		}
		// 如果@Bean方法不是静态的
		else {
			// instance @Bean method
			beanDef.setFactoryBeanName(configClass.getBeanName());
			beanDef.setUniqueFactoryMethodName/* 设置唯的一工厂方法名称 */(methodName);
		}

		if (metadata instanceof StandardMethodMetadata) {
			beanDef.setResolvedFactoryMethod/* 设置已解析的工厂方法 */(((StandardMethodMetadata) metadata).getIntrospectedMethod()/* 获取自省方法 */);
		}

		// 设置自动装配模式为constructor："按照构造器自动装配"
		beanDef.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
		beanDef.setAttribute(org.springframework.beans.factory.annotation.RequiredAnnotationBeanPostProcessor.
				SKIP_REQUIRED_CHECK_ATTRIBUTE, Boolean.TRUE);

		// ⚠️处理可通用定义的注解：@Lazy、@Primary、@Role、@Description
		// 获取这些注解的属性，给设置到bd中
		AnnotationConfigUtils.processCommonDefinitionAnnotations/* 处理通用定义的注解 */(beanDef, metadata/* @Bean方法的元数据 */);

		// 获取@Bean的autowire属性
		Autowire autowire = bean.getEnum("autowire");
		if (autowire.isAutowire()) {
			beanDef.setAutowireMode(autowire.value());
		}

		// 获取@Bean的autowireCandidate属性
		boolean autowireCandidate = bean.getBoolean("autowireCandidate");
		if (!autowireCandidate) {
			beanDef.setAutowireCandidate(false);
		}

		// 获取@Bean的initMethod属性
		String initMethodName = bean.getString("initMethod");
		if (StringUtils.hasText(initMethodName)) {
			beanDef.setInitMethodName(initMethodName);
		}

		// 获取@Bean的destroyMethod属性
		String destroyMethodName = bean.getString("destroyMethod");
		beanDef.setDestroyMethodName(destroyMethodName);

		// Consider scoping —— 考虑范围
		ScopedProxyMode proxyMode = ScopedProxyMode.NO;

		// 获取"标注@Bean的方法"上的@Scope注解的属性对象
		AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(metadata, Scope.class);
		if (attributes != null) {
			// 获取@Scope的value属性值
			beanDef.setScope(attributes.getString("value"));
			// 获取@Scope的proxyMode属性值
			proxyMode = attributes.getEnum("proxyMode"/* 代理模式 */);
			if (proxyMode == ScopedProxyMode.DEFAULT) {
				proxyMode = ScopedProxyMode.NO;
			}
		}

		// Replace the original bean definition with the target one, if necessary —— 如有必要，将原始bd替换为目标
		BeanDefinition beanDefToRegister/* 注册的bd */ = beanDef;

		// 如果代理模式，不是"不要创建范围代理"，也就是代表要创建范围代理模式！
		if (proxyMode != ScopedProxyMode.NO/* 不要创建范围代理 */) {
			BeanDefinitionHolder proxyDef = ScopedProxyCreator.createScopedProxy(
					// bd包装类
					new BeanDefinitionHolder(beanDef, beanName),
					// BeanDefinitionRegistry
					this.registry,
					// proxyTargetClass：是否需要代理目标类
					// 如果代理模式是"创建基于类的代理"，那么就代表需要代理目标类！
					proxyMode == ScopedProxyMode.TARGET_CLASS);

			beanDefToRegister = new ConfigurationClassBeanDefinition(
					(RootBeanDefinition) proxyDef.getBeanDefinition(), configClass/* 配置类 */, metadata/* @Bean方法的元数据 */);
		}

		if (logger.isTraceEnabled()) {
			logger.trace(String.format("Registering bean definition for @Bean method %s.%s()"/* 为@Bean方法注册bd */,
					configClass.getMetadata().getClassName(), beanName));
		}

		/* ⚠️注册@Bean方法的bd */
		this.registry.registerBeanDefinition(beanName, beanDefToRegister);
	}

	protected boolean isOverriddenByExistingDefinition(BeanMethod beanMethod, String beanName) {
		// 如果不包含当前beanName的bd,就返回false
		if (!this.registry.containsBeanDefinition(beanName)) {
			return false;
		}

		// 如果包含当前beanName的bd，就获取该bd
		BeanDefinition existingBeanDef = this.registry.getBeanDefinition(beanName);

		// Is the existing bean definition one that was created from a configuration class?
		// -> allow the current bean method to override, since both are at second-pass level.
		// However, if the bean method is an overloaded case on the same configuration class,
		// preserve the existing bean definition.
		// 上面的翻译：现有的bd是从配置类创建的吗？ -> 允许覆盖当前的bean方法，因为两者都处于第二阶段。
		// >>> 但是，如果bean方法是同一配置类上的重载案例，请保留现有的 bean 定义。
		if (existingBeanDef instanceof ConfigurationClassBeanDefinition) {
			ConfigurationClassBeanDefinition ccbd = (ConfigurationClassBeanDefinition) existingBeanDef;
			if (ccbd.getMetadata().getClassName().equals(
					beanMethod.getConfigurationClass().getMetadata().getClassName())) {
				if (ccbd.getFactoryMethodMetadata().getMethodName().equals(ccbd.getFactoryMethodName())) {
					ccbd.setNonUniqueFactoryMethodName(ccbd.getFactoryMethodMetadata().getMethodName());
				}
				return true;
			}
			else {
				return false;
			}
		}

		// A bean definition resulting from a component scan can be silently overridden
		// by an @Bean method, as of 4.2...
		if (existingBeanDef instanceof ScannedGenericBeanDefinition) {
			return false;
		}

		// Has the existing bean definition bean marked as a framework-generated bean?
		// -> allow the current bean method to override it, since it is application-level
		if (existingBeanDef.getRole() > BeanDefinition.ROLE_APPLICATION) {
			return false;
		}

		// At this point, it's a top-level override (probably XML), just having been parsed
		// before configuration class processing kicks in...
		if (this.registry instanceof DefaultListableBeanFactory &&
				!((DefaultListableBeanFactory) this.registry).isAllowBeanDefinitionOverriding()) {
			throw new BeanDefinitionStoreException(beanMethod.getConfigurationClass().getResource().getDescription(),
					beanName, "@Bean definition illegally overridden by existing bean definition: " + existingBeanDef);
		}
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Skipping bean definition for %s: a definition for bean '%s' " +
					"already exists. This top-level bean definition is considered as an override.",
					beanMethod, beanName));
		}
		return true;
	}

	private void loadBeanDefinitionsFromImportedResources(
			Map<String, Class<? extends BeanDefinitionReader>> importedResources) {

		Map<Class<?>, BeanDefinitionReader> readerInstanceCache = new HashMap<>();

		importedResources.forEach((resource, readerClass) -> {
			// Default reader selection necessary?
			if (BeanDefinitionReader.class == readerClass) {
				if (StringUtils.endsWithIgnoreCase(resource, ".groovy")) {
					// When clearly asking for Groovy, that's what they'll get...
					readerClass = GroovyBeanDefinitionReader.class;
				}
				else {
					// Primarily ".xml" files but for any other extension as well
					readerClass = XmlBeanDefinitionReader.class;
				}
			}

			BeanDefinitionReader reader = readerInstanceCache.get(readerClass);
			if (reader == null) {
				try {
					// Instantiate the specified BeanDefinitionReader
					reader = readerClass.getConstructor(BeanDefinitionRegistry.class).newInstance(this.registry);
					// Delegate the current ResourceLoader to it if possible
					if (reader instanceof AbstractBeanDefinitionReader) {
						AbstractBeanDefinitionReader abdr = ((AbstractBeanDefinitionReader) reader);
						abdr.setResourceLoader(this.resourceLoader);
						abdr.setEnvironment(this.environment);
					}
					readerInstanceCache.put(readerClass, reader);
				}
				catch (Throwable ex) {
					throw new IllegalStateException(
							"Could not instantiate BeanDefinitionReader class [" + readerClass.getName() + "]");
				}
			}

			// TODO SPR-6310: qualify relative path locations as done in AbstractContextLoader.modifyLocations
			reader.loadBeanDefinitions(resource);
		});
	}

	/**
	 * registrars是：importBeanDefinitionRegistrars
	 * @param registrars
	 */
	private void loadBeanDefinitionsFromRegistrars(Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> registrars) {
		registrars.forEach((registrar, metadata) ->
				registrar.registerBeanDefinitions(metadata, this.registry, this.importBeanNameGenerator));
	}


	/**
	 * {@link RootBeanDefinition} marker subclass used to signify that a bean definition
	 * was created from a configuration class as opposed to any other configuration source.
	 * Used in bean overriding cases where it's necessary to determine whether the bean
	 * definition was created externally.
	 */
	@SuppressWarnings("serial")
	private static class ConfigurationClassBeanDefinition extends RootBeanDefinition implements AnnotatedBeanDefinition {

		// 配置类的注解元数据
		private final AnnotationMetadata annotationMetadata/* 注解元数据 */;

		// @Bean方法的元数据
		private final MethodMetadata factoryMethodMetadata/* 工厂方法元数据 */;

		public ConfigurationClassBeanDefinition(ConfigurationClass configClass, MethodMetadata beanMethodMetadata) {
			this.annotationMetadata = configClass.getMetadata();
			this.factoryMethodMetadata = beanMethodMetadata;
			setResource(configClass.getResource());
			setLenientConstructorResolution(false);
		}

		public ConfigurationClassBeanDefinition(
				RootBeanDefinition original, ConfigurationClass configClass, MethodMetadata beanMethodMetadata) {
			super(original);
			this.annotationMetadata = configClass.getMetadata();
			this.factoryMethodMetadata = beanMethodMetadata;
		}

		private ConfigurationClassBeanDefinition(ConfigurationClassBeanDefinition original) {
			super(original);
			this.annotationMetadata = original.annotationMetadata;
			this.factoryMethodMetadata = original.factoryMethodMetadata;
		}

		@Override
		public AnnotationMetadata getMetadata() {
			return this.annotationMetadata;
		}

		@Override
		@NonNull
		public MethodMetadata getFactoryMethodMetadata() {
			return this.factoryMethodMetadata;
		}

		@Override
		public boolean isFactoryMethod(Method candidate) {
			return (super.isFactoryMethod(candidate) && BeanAnnotationHelper.isBeanAnnotated(candidate));
		}

		@Override
		public ConfigurationClassBeanDefinition cloneBeanDefinition() {
			return new ConfigurationClassBeanDefinition(this);
		}
	}


	/**
	 * Evaluate {@code @Conditional} annotations, tracking results and taking into
	 * account 'imported by'.
	 *
	 * 评估{@code @Conditional}批注，跟踪结果并考虑“导入人”。
	 */
	private class TrackedConditionEvaluator {

		// key：配置类
		// value：是否要跳过的结果
		private final Map<ConfigurationClass, Boolean> skipped = new HashMap<>();

		public boolean shouldSkip(ConfigurationClass configClass) {
			Boolean skip = this.skipped.get(configClass);
			if (skip == null) {
				// 判断当前这个配置类，是不是被import导入进来的！
				if (configClass.isImported()) {
					boolean allSkipped = true;
					for (ConfigurationClass importedBy : configClass.getImportedBy()) {
						if (!shouldSkip(importedBy)) {
							allSkipped = false;
							break;
						}
					}
					if (allSkipped) {
						// The config classes that imported this one were all skipped, therefore we are skipped...
						// 导入这个的配置类都被跳过了，因此我们被跳过了......
						skip = true;
					}
				}

				if (skip == null) {
					// 根据 {@code @Conditional} 注解，确定是否应跳过某个项目。
					// >>> true：跳过！什么都不做，也就是不会进行解析
					// >>> false：不跳过！会进行解析
					// conditionEvaluator = ConditionEvaluator
					skip = conditionEvaluator.shouldSkip(configClass.getMetadata()/* 获取配置类的注解元数据 */, ConfigurationPhase.REGISTER_BEAN);
				}

				// 将配置类是否要跳过的结果进行缓存！
				this.skipped.put(configClass, skip);
			}
			return skip;
		}
	}

}
