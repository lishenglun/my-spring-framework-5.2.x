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

package org.springframework.context.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

/**
 * Internal class used to evaluate {@link Conditional} annotations.
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @since 4.0
 */
class ConditionEvaluator {

	private final ConditionContextImpl context;


	/**
	 * Create a new {@link ConditionEvaluator} instance.
	 */
	public ConditionEvaluator(@Nullable BeanDefinitionRegistry registry,
			@Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

		this.context = new ConditionContextImpl(registry, environment, resourceLoader);
	}


	/**
	 * Determine if an item should be skipped based on {@code @Conditional} annotations.
	 * The {@link ConfigurationPhase} will be deduced from the type of item (i.e. a
	 * {@code @Configuration} class will be {@link ConfigurationPhase#PARSE_CONFIGURATION})
	 * @param metadata the meta data
	 * @return if the item should be skipped
	 */
	public boolean shouldSkip(AnnotatedTypeMetadata metadata) {
		return shouldSkip(metadata, null);
	}

	/**
	 *
	 * 根据 {@code @Conditional} 注解，确定是否应跳过某个项目。
	 *
	 * Determine if an item should be skipped based on {@code @Conditional} annotations.
	 * @param metadata the meta data
	 * @param phase the phase of the call
	 * @return if the item should be skipped
	 *
	 * 🚩️true：跳过！什么都不做，也就是不会进行解析
	 * 🚩false：不跳过！会进行解析
	 *
	 */
	public boolean shouldSkip(@Nullable AnnotatedTypeMetadata metadata, @Nullable ConfigurationPhase phase) {

		/* 1、不存在@Conditional，就直接不跳过 */

		if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
			// 元数据为空，或者元数据不为空，且配置类中"不存在@Conditional"，则返回false
			// false在上层函数中代表不跳过！true代表跳过！
			return false;
		}

		/*

		2、存在@Conditional，就获取@Conditional上面的value属性值，是一个数组，也就是条件类数组；
		然后遍历条件类数组，获取每一个类的全限定类名，通过全限定类名获取对应的Class对象，然后通过Class对象创建对应的条件类实例；
		最后调用条件类实例的matches()方法，进行判断，是否跳过（由于会取反，所以matches()返回true代表匹配，所以不跳过；返回false代表不匹配，所以跳过）

		*/

		// 采用递归的方式进行判断，第一次执行的时候phase为空，向下执行
		if (phase == null/* phase值，第一次都等于空 */) {
			// 1、判断metadata是否是AnnotationMetadata类的一个实例
			// 2、ConfigurationClassUtils.isConfigurationCandidate()，主要逻辑如下：
			// >>> 1、检查bean是不是一个接口，如果是，返回false
			// >>> 2、检查bean中是否包含@Component、@ComponentScan、@Import、@ImportResource中的任意一个
			// >>> 3、检查bean中是否有@Bean
			// 只要满足其中1,2或者1,3或者1,4或者1,5，就会继续递归
			if (metadata instanceof AnnotationMetadata &&
					ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {
				// 递归调用，目的还是为了解析
				return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION/* 解析配置 */);
			}
			// 递归调用，目的还是为了解析
			return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN/* 注册Bean */);
		}

		/*

		2.1、获取@Conditional上面的value属性值，是一个数组，也就是条件类数组；
		然后遍历条件类数组，获取每一个类的全限定类名，通过全限定类名获取对应的Class对象，然后通过Class对象创建对应的条件类实例，放入conditions集合中；

		*/

		List<Condition> conditions = new ArrayList<>();
		// ⚠️获取到@Conditional注解的value属性数组，也就是对应的条件类数组！
		// ⚠️题外：一个类上只能写一个@Conditional注解
		for (String[] conditionClasses : getConditionClasses(metadata)) {
			for (String conditionClass : conditionClasses) {
				// ⚠️⚠️会直接通过反射，创建@Conditional里面value属性对应的类的对象（条件对象）
				Condition condition = getCondition(conditionClass/* 条件类的全限定类名，用于反射创建条件对象 */, this.context.getClassLoader());
				conditions.add(condition);
			}
		}

		// 对相关的条目进行排序操作
		AnnotationAwareOrderComparator.sort(conditions);

		/*

		2.2、遍历条件类实例，调用条件类实例的matches()，
		只要其中有一个条件类实例的matches()返回false，也就是不匹配，那么当前函数方法就返回true，跳过此bd的解析！
		只有所有的条件类实例的matches()都返回true，也就是都匹配，当前函数方法才会返回false，也就是不跳过，进行解析！

		(判断当前这个类是否满足条件。如果满足，就留下来；不满足，就跳过。相当于做了一些最基本的验证规则)

		*/

		for (Condition condition : conditions) {
			ConfigurationPhase requiredPhase = null;
			// 判断条件对象是不是ConfigurationCondition的实例
			if (condition instanceof ConfigurationCondition) {
				requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase/* 获取配置阶段 */();
			}
			// requiredPhase只可能是null或者是ConfigurationCondition的一个实例对象
			/**
			 * condition.matches(this.context, metadata)：调用条件类实例的matches()
			 * >>> true：代表匹配，代表满足条件，就留下来，不跳过，接着往下进行解析
			 * >>> false：代表不匹配，代表不满足条件，就跳过，不往下进行解析了
			 */
			if ((requiredPhase == null || requiredPhase == phase) && !condition.matches(this.context, metadata)) {
				// 一共有的逻辑是：
				// 1、requiredPhase不是ConfigurationCondition的实例
				// 2、phase==requiredPhase，从上述的递归可知：phase可为ConfigurationPhase.PARSE_CONFIGURATION或者ConfigurationPhase.REGISTER_BEAN
				// 3、condition.matches(this.context,metadata)返回false
				// ⚠️如果1、2或者1、3成立，则在此函数的上层将阻断bean注入Spring容器
				return true;
			}
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	private List<String[]> getConditionClasses/* 获取条件类 */(AnnotatedTypeMetadata metadata) {
		// ⚠️获取到@Conditional注解的value属性数组，也就是对应的条件类数组！
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes/* 获取所有注解属性 */(Conditional.class.getName(), true);
		Object values = (attributes != null ? attributes.get("value") : null);
		return (List<String[]>) (values != null ? values : Collections.emptyList());
	}

	private Condition getCondition(String conditionClassName/* 条件类的全限定类名 */, @Nullable ClassLoader classloader) {
		// ⚠️通过全限定类名称得到对应的Class对象
		Class<?> conditionClass = ClassUtils.resolveClassName/* 解析类名 */(conditionClassName, classloader);
		// ⚠️通过Class对象，反射创建实例
		return (Condition) BeanUtils.instantiateClass/* 实例化类 */(conditionClass);
	}


	/**
	 * Implementation of a {@link ConditionContext}.
	 */
	private static class ConditionContextImpl implements ConditionContext {

		@Nullable
		private final BeanDefinitionRegistry registry;

		@Nullable
		private final ConfigurableListableBeanFactory beanFactory;

		private final Environment environment;

		private final ResourceLoader resourceLoader;

		@Nullable
		private final ClassLoader classLoader;

		public ConditionContextImpl(@Nullable BeanDefinitionRegistry registry,
				@Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

			this.registry = registry;
			this.beanFactory = deduceBeanFactory(registry);
			this.environment = (environment != null ? environment : deduceEnvironment(registry));
			this.resourceLoader = (resourceLoader != null ? resourceLoader : deduceResourceLoader(registry));
			this.classLoader = deduceClassLoader(resourceLoader, this.beanFactory);
		}

		@Nullable
		private ConfigurableListableBeanFactory deduceBeanFactory(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ConfigurableListableBeanFactory) {
				return (ConfigurableListableBeanFactory) source;
			}
			if (source instanceof ConfigurableApplicationContext) {
				return (((ConfigurableApplicationContext) source).getBeanFactory());
			}
			return null;
		}

		private Environment deduceEnvironment(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof EnvironmentCapable) {
				return ((EnvironmentCapable) source).getEnvironment();
			}
			return new StandardEnvironment();
		}

		private ResourceLoader deduceResourceLoader(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ResourceLoader) {
				return (ResourceLoader) source;
			}
			return new DefaultResourceLoader();
		}

		@Nullable
		private ClassLoader deduceClassLoader(@Nullable ResourceLoader resourceLoader,
				@Nullable ConfigurableListableBeanFactory beanFactory) {

			if (resourceLoader != null) {
				ClassLoader classLoader = resourceLoader.getClassLoader();
				if (classLoader != null) {
					return classLoader;
				}
			}
			if (beanFactory != null) {
				return beanFactory.getBeanClassLoader();
			}
			return ClassUtils.getDefaultClassLoader();
		}

		@Override
		public BeanDefinitionRegistry getRegistry() {
			Assert.state(this.registry != null, "No BeanDefinitionRegistry available");
			return this.registry;
		}

		@Override
		@Nullable
		public ConfigurableListableBeanFactory getBeanFactory() {
			return this.beanFactory;
		}

		@Override
		public Environment getEnvironment() {
			return this.environment;
		}

		@Override
		public ResourceLoader getResourceLoader() {
			return this.resourceLoader;
		}

		@Override
		@Nullable
		public ClassLoader getClassLoader() {
			return this.classLoader;
		}
	}

}
