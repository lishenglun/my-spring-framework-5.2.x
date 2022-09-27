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

package org.springframework.aop.aspectj.annotation;

import org.aspectj.lang.reflect.PerClauseKind;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper for retrieving @AspectJ beans from a BeanFactory and building
 * Spring Advisors based on them, for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AnnotationAwareAspectJAutoProxyCreator
 */
public class BeanFactoryAspectJAdvisorsBuilder {

	private final ListableBeanFactory beanFactory;

	private final AspectJAdvisorFactory advisorFactory;

	@Nullable
	private volatile List<String> aspectBeanNames;

	private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>();

	private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();


	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
		this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
	}

	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 * @param advisorFactory the AspectJAdvisorFactory to build each Advisor with
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
		this.beanFactory = beanFactory;
		this.advisorFactory = advisorFactory;
	}


	/**
	 * 构建所有@AspectJ类中的Advisor
	 * 1、获取所有beanName（这一步骤中所有在beanFactory中注册的bean都会被提取出来）
	 * 2. 遍历所有beanName，找出@AspectJ修饰的类
	 * 3、然后提取@AspectJ修饰类中的增强器
	 * 4. 将提取结果加入缓存
	 *
	 * 寻找Aspect注解的面向对象，然后解析他的方法，通过注解来生成对应的通知器Advisor
	 *
	 * 我要去当前bean工厂里面看一下，有哪些是被@Aspect修饰的切面
	 *
	 * Look for AspectJ-annotated aspect beans in the current bean factory,
	 * and return to a list of Spring AOP Advisors representing them.
	 * <p>Creates a Spring Advisor for each AspectJ advice method.
	 *
	 * 在当前bean工厂中查找@AspectJ的切面bean，并返回代表它们的Spring AOP Advisor列表。
	 * <p>为每个 AspectJ 建议方法创建一个 Spring Advisor。
	 *
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	public List<Advisor> buildAspectJAdvisors/* 对@Aspect进行处理 */() {
		// 获取切面名字列表
		List<String> aspectNames = this.aspectBeanNames;

		// 缓存字段aspectNames没有值，注意实例化第一个单例bean的时候就会触发解析切面
		if (aspectNames == null) {
			synchronized (this) {
				aspectNames = this.aspectBeanNames;
				if (aspectNames == null) {
					// 用于保存所有解析出来的Advisor集合对象
					List<Advisor> advisors = new ArrayList<>();
					// 用于保存切面的名称的集合
					aspectNames = new ArrayList<>();

					/* 1、获取所有 beanName */
					/**
					 * AOP功能中在这里传入的是Object对象，代表去容器中获取到所有的组件的名称，然后再进行遍历，
					 * 这个过程是十分的消耗性能的，所以说Spring会在这里加入了保存切面信息的缓存，
					 * 但是事务功能不一样，事务模块的功能是直接去容器中获取Advisor类型的，选择范围小，且不消耗性能，
					 * 所以Spring在事务模块中没有加入缓存来保存我们的事务相关的advisor
					 */
					String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
							this.beanFactory, Object.class, true, false);

					// 遍历所有beanName，找出对应的增强方法
					for (String beanName : beanNames) {
						// 不合法的bean则略过，默认返回true，由子类定义规则
						// 题外：我们可以定义一个当前类的子类，然后重写isEligibleBean()，在里面决定过滤哪些bean
						if (!isEligibleBean/* 是合格的Bean */(beanName)) {
							continue;
						}

						// We must be careful not to instantiate beans eagerly as in this case they
						// would be cached by the Spring container but would not have been weaved.
						// 上面的翻译：我们必须注意不要急切地实例化bean，因为在这种情况下它们会被Spring容器缓存但不会被织入

						// 获取bean类型
						Class<?> beanType = this.beanFactory.getType(beanName, false);
						if (beanType == null) {
							continue;
						}

						/* 2、遍历所有beanName，并找出声明@AspectJ的类，进行进一步的处理 */
						// 如果当前bean存在@Aspect
						if (this.advisorFactory.isAspect(beanType)) {

							aspectNames.add(beanName);
							// 对于使用了@Aspect标注的bean，将其封装为一个AspectMetadata类型，
							// 这里在封装的过程中会解析@Aspect上的参数指定的切面类型，如perthis
							// 和pertarget等。这些被解析的注解都会被封装到其perClausePointcut属性中
							AspectMetadata amd = new AspectMetadata(beanType, beanName);

							// 判断@Aspect中标注的是否为singleton类型，默认的切面类都是singleton类型
							if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON/* 单身人士 */) {
								// 将BeanFactory和当前bean封装为MetadataAwareAspectInstanceFactory对象，
								// 这里会再次将@Aspect中的参数都封装为一个AspectMetadata，并且保存在该factory中
								MetadataAwareAspectInstanceFactory factory =
										new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);

								/* 3、提取@AspectJ修饰类中的增强器 */
								// 通过封装的bean获取其Advice，如@Before、@After等等，并且将这些Advice都解析并且封装为一个个的Advisor
								List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);


								/*

								4、将提取结果加入缓存

								如果切面类是singleton类型，则将解析得到的Advisor进行缓存，
								否则将当前的factory进行缓存，以便再次获取时可以通过factory直接获取

								*/
								if (this.beanFactory.isSingleton(beanName)) {
									this.advisorsCache.put(beanName, classAdvisors);
								}
								else {
									this.aspectFactoryCache.put(beanName, factory);
								}

								advisors.addAll(classAdvisors);
							}
							else {
								// Per target or per this. —— 按目标或按此。
								// 如果@Aspect标注的是perthis和pertarget类型，说明当前切面不可能是单例的，
								// 因而这里判断其如果是单例的则抛出异常
								if (this.beanFactory.isSingleton(beanName)) {
									throw new IllegalArgumentException("Bean with name '" + beanName +
											"' is a singleton, but aspect instantiation model is not singleton");
								}
								// 将当前BeanFactory和切面bean封装为一个多例类型的Factory
								MetadataAwareAspectInstanceFactory factory =
										new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
								// 对当前bean和factory进行缓存
								this.aspectFactoryCache.put(beanName, factory);
								advisors.addAll(this.advisorFactory.getAdvisors(factory));
							}
						}
					}
					this.aspectBeanNames = aspectNames;
					return advisors;
				}
			}
		}

		if (aspectNames.isEmpty()) {
			return Collections.emptyList();
		}

		/* 从缓存中提取所有注解修饰的增强器（Advisor bean） */

		// 通过所有aspectNames在缓存中获取切面对应的Advisor，这里如果是单例的，则直接从advisorsCache获取，
		// 如果是多例类型的，则通过MetadataAwareAspectInstanceFactory立即生成一个
		List<Advisor> advisors = new ArrayList<>();

		for (String aspectName : aspectNames) {
			// 从缓存中获取当前类中的增强器（Advisor bean）
			List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
			// 如果是单例的Advisor bean，则直接添加到返回值列表中(默认是单例的)
			if (cachedAdvisors != null) {
				advisors.addAll(cachedAdvisors);
			}
			else {
				// 如果是多例的Advisor bean，则通过MetadataAwareAspectInstanceFactory生成
				MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
				advisors.addAll(this.advisorFactory.getAdvisors(factory)/* ⚠️ */);
			}
		}

		return advisors;
	}

	/**
	 * Return whether the aspect bean with the given name is eligible.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
