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

import org.aopalliance.aop.Advice;
import org.aspectj.lang.annotation.*;
import org.springframework.aop.Advisor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.aspectj.*;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConvertingComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.comparator.InstanceComparator;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Factory that can create Spring AOP Advisors given AspectJ classes from
 * classes honoring AspectJ's annotation syntax, using reflection to invoke the
 * corresponding advice methods.
 *
 * @author Rod Johnson
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 2.0
 */
@SuppressWarnings("serial")
public class ReflectiveAspectJAdvisorFactory extends AbstractAspectJAdvisorFactory implements Serializable {

	private static final Comparator<Method> METHOD_COMPARATOR;

	static {
		// Note: although @After is ordered before @AfterReturning and @AfterThrowing,
		// an @After advice method will actually be invoked after @AfterReturning and
		// @AfterThrowing methods due to the fact that AspectJAfterAdvice.invoke(MethodInvocation)
		// invokes proceed() in a `try` block and only invokes the @After advice method
		// in a corresponding `finally` block.
		// 上面翻译：注意：虽然@After排序在@AfterReturning和@AfterThrowing之前，但实际上会在@AfterReturning和@AfterThrowing方法之后调用@After通知方法，
		// >>> 因为AspectJAfterAdvice.invoke(MethodInvocation)在`try`块中调用了proceed()并且仅在相应的“finally”块中调用@After通知方法。

		/**
		 * 题外：kind：翻译为：种类、类
		 */
		// 通知类比较器
		Comparator<Method> adviceKindComparator = new ConvertingComparator<>(
				new InstanceComparator<>(
						Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class),
				(Converter<Method, Annotation>) method -> {
					// 依次查找方法上存在的@Pointcut、@Around、@Before、@After、@AfterReturning、@AfterThrowing，先查到哪个注解，就返回哪个注解；如果一个都没有查询到，这返回null
					AspectJAnnotation<?> ann = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(method);
					return (ann != null ? ann.getAnnotation() : null);
				});

		// 方法名称比较器
		Comparator<Method> methodNameComparator = new ConvertingComparator<>(Method::getName);

		// 先采用adviceKindComparator比较器，如果adviceKindComparator比较器比较的结果相等，那么再采用methodNameComparator比较器
		METHOD_COMPARATOR = adviceKindComparator.thenComparing/* 然后比较 */(methodNameComparator);
	}


	@Nullable
	private final BeanFactory beanFactory;


	/**
	 * Create a new {@code ReflectiveAspectJAdvisorFactory}.
	 */
	public ReflectiveAspectJAdvisorFactory() {
		this(null);
	}

	/**
	 * Create a new {@code ReflectiveAspectJAdvisorFactory}, propagating the given
	 * {@link BeanFactory} to the created {@link AspectJExpressionPointcut} instances,
	 * for bean pointcut handling as well as consistent {@link ClassLoader} resolution.
	 *
	 * @param beanFactory the BeanFactory to propagate (may be {@code null}}
	 * @see AspectJExpressionPointcut#setBeanFactory
	 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#getBeanClassLoader()
	 * @since 4.3.6
	 */
	public ReflectiveAspectJAdvisorFactory(@Nullable BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	/**
	 * 提取@AspectJ修饰类中的增强器
	 */
	@Override
	public List<Advisor> getAdvisors(MetadataAwareAspectInstanceFactory aspectInstanceFactory/* BeanFactoryAspectInstanceFactory */) {
		// 切面类型（标记@AspectJ类的Class）
		Class<?> aspectClass = aspectInstanceFactory.getAspectMetadata()/* AspectMetadata */.getAspectClass();
		// 切面名称（标记@AspectJ类的名称）
		String aspectName = aspectInstanceFactory.getAspectMetadata().getAspectName();
		// 验证
		validate(aspectClass);

		// We need to wrap the MetadataAwareAspectInstanceFactory with a decorator
		// so that it will only instantiate once.
		// 上面翻译：我们需要用装饰器包装MetadataAwareAspectInstanceFactory，这样它就只会实例化一次。
		MetadataAwareAspectInstanceFactory lazySingletonAspectInstanceFactory =
				new LazySingletonAspectInstanceFactoryDecorator(aspectInstanceFactory);

		List<Advisor> advisors = new ArrayList<>();

		/* 普通增强器的获取 */
		// ⚠️遍历切面类中未被@Pointcut修饰的方法
		for (Method method : getAdvisorMethods(aspectClass)) {
			// Prior to Spring Framework 5.2.7, advisors.size() was supplied as the declarationOrderInAspect
			// to getAdvisor(...) to represent the "current position" in the declared methods list.
			// However, since Java 7 the "current position" is not valid since the JDK no longer
			// returns declared methods in the order in which they are declared in the source code.
			// Thus, we now hard code the declarationOrderInAspect to 0 for all advice methods
			// discovered via reflection in order to support reliable advice ordering across JVM launches.
			// Specifically, a value of 0 aligns with the default value used in
			// AspectJPrecedenceComparator.getAspectDeclarationOrder(Advisor).
			// 上面翻译：在SpringFramework 5.2.7之前，advisors.size()作为声明OrderInAspect提供给getAdvisor(...)以表示声明的方法列表中的“当前位置”。
			// >>> 但是，从Java7开始，“当前位置”无效，因为JDK不再按照它们在源代码中声明的顺序返回声明的方法。
			// >>> 因此，我们现在将通过反射发现的所有Advice方法的declarationOrderInAspect硬编码为0，以支持跨JVM启动的可靠建议排序。
			// >>> 具体来说，值0与AspectJPrecedenceComparator.getAspectDeclarationOrder(Advisor)中使用的默认值一致。

			Advisor advisor = getAdvisor(method, lazySingletonAspectInstanceFactory, 0, aspectName);
			if (advisor != null) {
				advisors.add(advisor);
			}
		}

		/* 增加同步实例化增强器 */
		// If it's a per target aspect, emit the dummy instantiating aspect. —— 如果它是每个目标方面，则发出虚拟实例化方面

		// 如果【寻找到了Advisor && 配置了Advisor延迟初始化】，那么就在首位加入SyntheticInstantiationAdvisor("同步实例化增强器")，以保证Advisor使用之前的实例化
		// 题外:《Spring源码深度解析(第2版)》中的原话：如果寻找的增强都不为空而且又配置了增强延迟初始化，那么需要在首位加入同步实例化增强器，以保证增强使用之前的实例化
		if (!advisors.isEmpty() && lazySingletonAspectInstanceFactory.getAspectMetadata().isLazilyInstantiated()) {
			Advisor instantiationAdvisor = new SyntheticInstantiationAdvisor/* 同步实例化增强器 */(lazySingletonAspectInstanceFactory);
			// 在首位加入同步实例化增强器，以保证增强使用之前的实例化
			advisors.add(0, instantiationAdvisor);
		}

		/* 获取@DeclareParents */

		// Find introduction fields. —— 查找介绍字段
		// 获取@DeclareParents
		for (Field field : aspectClass.getDeclaredFields()) {
			// @DeclareParents主要用于引介增强的注解形式的实现，而其实现方式与普通增强很类似，只不过使用DeclareParentsAdvisor对功能进行封装
			Advisor advisor = getDeclareParentsAdvisor(field);
			if (advisor != null) {
				advisors.add(advisor);
			}
		}

		return advisors;
	}

	/**
	 * 获取切面类中没有被@Pointcut修饰的方法，暂时当作Advice方法（因为未被@Pointcut修饰的方法，有可能也不是一个Advice方法，它可以不写任何Advice注解）
	 *
	 * @param aspectClass 切面类
	 * @return
	 */
	private List<Method> getAdvisorMethods(Class<?> aspectClass) {
		final List<Method> methods = new ArrayList<>();
		// 获取切面类中没有被@Pointcut修饰的方法，暂时当作Advice方法（因为未被@Pointcut修饰的方法，有可能也不是一个Advice方法，它可以不写任何Advice注解）
		ReflectionUtils.doWithMethods(aspectClass, method -> {
			// Exclude pointcuts —— 排除切入点
			if (AnnotationUtils.getAnnotation(method, Pointcut.class) == null) {
				methods.add(method);
			}
		}, ReflectionUtils.USER_DECLARED_METHODS);

		// 排序Advice方法
		if (methods.size() > 1) {
			methods.sort(METHOD_COMPARATOR);
		}

		return methods;
	}

	/**
	 * Build a {@link org.springframework.aop.aspectj.DeclareParentsAdvisor}
	 * for the given introduction field.
	 * <p>Resulting Advisors will need to be evaluated for targets.
	 *
	 * @param introductionField the field to introspect
	 * @return the Advisor instance, or {@code null} if not an Advisor
	 */
	@Nullable
	private Advisor getDeclareParentsAdvisor(Field introductionField) {
		DeclareParents declareParents = introductionField.getAnnotation(DeclareParents.class);
		if (declareParents == null) {
			// Not an introduction field
			return null;
		}

		if (DeclareParents.class == declareParents.defaultImpl()) {
			throw new IllegalStateException("'defaultImpl' attribute must be set on DeclareParents");
		}

		return new DeclareParentsAdvisor(
				introductionField.getType(), declareParents.value(), declareParents.defaultImpl());
	}

	/**
	 *
	 * @param candidateAdviceMethod 		候选的Advice方法（因为只是初步排查了，它不是@Point方法）
	 * @param aspectInstanceFactory
	 * @param declarationOrderInAspect
	 * @param aspectName 					切面名称
	 * @return
	 */
	@Override
	@Nullable
	public Advisor getAdvisor(Method candidateAdviceMethod, MetadataAwareAspectInstanceFactory aspectInstanceFactory,
							  int declarationOrderInAspect, String aspectName) {

		validate(aspectInstanceFactory.getAspectMetadata().getAspectClass());

		/* 1、获取切点信息(获取方法上Advice注解中配置的切点信息。如果方法上没有Advice注解，则返回null) */

		// 获取切点信息，也就是获取通知注解上配置的切入点表达式，例如：@Before("test()")
		// 题外：通过通知注解上配置的切入点表达式，构建一个AspectJExpressionPointcut对象，里面存放着"通知注解上配置的切入点表达式"
		// 注意：⚠️里面就会识别出当前方法是不是Advice方法
		AspectJExpressionPointcut expressionPointcut = getPointcut(
				candidateAdviceMethod, aspectInstanceFactory.getAspectMetadata().getAspectClass());

		/**

		提示：⚠️调用当前方法之前，已经排除掉了@Pointcut方法，所以在这些方法上找不到@Pointcut，只找得到Advice注解，获取的也是Advice注解中的切点信息。
		所以如果切点信息为null，则代表当前方法上不存在Advice注解，当前方法不是通知方法（或者说，当前方法不是增强器），则返回null；
		如果有切点信息，则代表当前方法上存在Advice注解，当前方法是Advice方法，所以接下来可以直接生成Advisor对象（InstantiationModelAwarePointcutAdvisorImpl）

 		*/

		/* 2、如果切点信息为null，则代表当前方法上不存在Advice注解，当前方法不是Advice方法，则返回null */
		if (expressionPointcut == null) {
			return null;
		}

		/*

		3、如果有切点信息，则代表当前方法上存在Advice注解，当前方法是Advice方法，所以接下来创建一个Advisor对象(InstantiationModelAwarePointcutAdvisorImpl)。
		Advisor对象里面保存了切点信息，并且里面根据Advice方法上面的Advice注解类型，创建对应的Advice对象，Advice对象中也保存了切点信息，然后Advice对象保存在Advisor对象里面

		*/
		// 创建一个Advisor(增强器)，保存了切点信息。里面根据切面方法上的Advice注解类型，创建对应的Advice进行保存；Advice中也保存了切点信息
		// 题外：InstantiationModelAwarePointcutAdvisorImpl实现Advisor
		// 题外：在《spring源码深度解析(第2版)》中，Advisor称呼为增强，Advice称为增强器
		return new InstantiationModelAwarePointcutAdvisorImpl(expressionPointcut, candidateAdviceMethod,
				this, aspectInstanceFactory, declarationOrderInAspect, aspectName);
	}

	/**
	 * 获取方法上AspectJ相关注解中配置的切点信息。如果方法上没有AspectJ相关注解，则返回null
	 *
	 * AspectJ相关注解：@Pointcut、@Around、@Before、@After、@AfterReturning、@AfterThrowing
	 *
	 * @param candidateAdviceMethod 切面类中的方法
	 * @param candidateAspectClass  切面类型，例如：定义了@AspectJ注解类的Class
	 * @return
	 */
	@Nullable
	private AspectJExpressionPointcut getPointcut(Method candidateAdviceMethod, Class<?> candidateAspectClass) {
		/* 1、依次查找方法上存在的@Pointcut、@Around、@Before、@After、@AfterReturning、@AfterThrowing，先查到哪个注解，就返回哪个注解；如果一个都没有查询到，这返回null */
		AspectJAnnotation<?> aspectJAnnotation =
				AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);

		/* 2、如果方法上不存在AspectJ相关的注解，那么就返回null */
		if (aspectJAnnotation == null) {
			return null;
		}

		/* 3、创建AspectJExpressionPointcut对象封装"AspectJ相关注解"中的切入点表达式 */
		AspectJExpressionPointcut ajexp =
				new AspectJExpressionPointcut(candidateAspectClass, new String[0], new Class<?>[0]);

		/* 4、获取AspectJ注解上的切入点表达式，设置到AspectJExpressionPointcut中 */
		/**
		 * 1、aspectJAnnotation.getPointcutExpression()：获取AspectJ注解上的切入点表达式
		 * 所有@Pointcut、@Around、@Before、@After、@AfterReturning、@AfterThrowing都具备切入点表达式这一属性值
		 */
		// 获取AspectJ注解上的切入点表达式，然后设置切入点表达式
		ajexp.setExpression(aspectJAnnotation.getPointcutExpression());
		if (this.beanFactory != null) {
			ajexp.setBeanFactory(this.beanFactory);
		}

		return ajexp;
	}

	/**
	 * 根据Advice方法上的Advice注解类型，创建对应的Advice返回
	 *
	 * @param candidateAdviceMethod 	切面方法
	 * @param expressionPointcut 		切点信息
	 * @param aspectInstanceFactory 	切面实例工厂
	 * @param declarationOrder
	 * @param aspectName 				切面名称
	 * @return
	 */
	@Override
	@Nullable
	public Advice getAdvice(Method candidateAdviceMethod, AspectJExpressionPointcut expressionPointcut,
							MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrder, String aspectName) {
		// 切面类型
		Class<?> candidateAspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
		validate(candidateAspectClass);

		/* 1、依次查找方法上的advice注解，先查到哪个注解，就返回哪个注解 */
		// 依次查找方法上存在的@Pointcut、@Around、@Before、@After、@AfterReturning、@AfterThrowing，先查到哪个注解，就返回哪个注解
		AspectJAnnotation<?> aspectJAnnotation =
				AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
		if (aspectJAnnotation == null) {
			return null;
		}

		// If we get here, we know we have an AspectJ method.
		// Check that it's an AspectJ-annotated class
		// 上面翻译：如果我们到达这里，我们知道我们有一个AspectJ方法。检查它是否是@AspectJ标注的类

		// 如果不是切面，则报错
		if (!isAspect(candidateAspectClass)) {
			// 建议必须在切面类型中声明：" + "违规方法 '" + CandidateAdviceMethod + "' 在类 [" + CandidateAspectClass.getName() + "]
			throw new AopConfigException("Advice must be declared inside an aspect type: " +
					"Offending method '" + candidateAdviceMethod + "' in class [" +
					candidateAspectClass.getName() + "]");
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Found AspectJ method: " + candidateAdviceMethod);
		}

		/* 2、根据不同的Advice注解类型，创建对应的Advice */
		// 根据Advice注解类型，创建对应的Advice，例如：@Before，那么就创建AspectJMethodBeforeAdvice
		AbstractAspectJAdvice springAdvice;
		switch (aspectJAnnotation.getAnnotationType()) {
			case AtPointcut/* pointcut */:
				if (logger.isDebugEnabled()) {
					logger.debug("Processing pointcut '" + candidateAdviceMethod.getName() + "'");
				}
				return null;
			case AtAround/* around */:
				springAdvice = new AspectJAroundAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			case AtBefore/* before */:
				springAdvice = new AspectJMethodBeforeAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			case AtAfter/* after */:
				springAdvice = new AspectJAfterAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				break;
			case AtAfterReturning/* afterReturning */:
				springAdvice = new AspectJAfterReturningAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				AfterReturning afterReturningAnnotation = (AfterReturning) aspectJAnnotation.getAnnotation();
				if (StringUtils.hasText(afterReturningAnnotation.returning())) {
					springAdvice.setReturningName(afterReturningAnnotation.returning());
				}
				break;
			case AtAfterThrowing/* afterThrowing */:
				springAdvice = new AspectJAfterThrowingAdvice(
						candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
				AfterThrowing afterThrowingAnnotation = (AfterThrowing) aspectJAnnotation.getAnnotation();
				if (StringUtils.hasText(afterThrowingAnnotation.throwing())) {
					springAdvice.setThrowingName(afterThrowingAnnotation.throwing());
				}
				break;
			default:
				throw new UnsupportedOperationException(
						"Unsupported advice type on method: " + candidateAdviceMethod);
		}

		/* 3、配置advice */

		// Now to configure the advice... —— 现在配置advice...

		springAdvice.setAspectName(aspectName);
		springAdvice.setDeclarationOrder(declarationOrder);
		String[] argNames = this.parameterNameDiscoverer.getParameterNames(candidateAdviceMethod);
		if (argNames != null) {
			springAdvice.setArgumentNamesFromStringArray(argNames);
		}
		springAdvice.calculateArgumentBindings();

		return springAdvice;
	}


	/**
	 * Synthetic advisor that instantiates the aspect.
	 * Triggered by per-clause pointcut on non-singleton aspect.
	 * The advice has no effect.
	 */
	@SuppressWarnings("serial")
	protected static class SyntheticInstantiationAdvisor extends DefaultPointcutAdvisor {

		public SyntheticInstantiationAdvisor(final MetadataAwareAspectInstanceFactory aif) {
			super(aif.getAspectMetadata().getPerClausePointcut(),
					(MethodBeforeAdvice)(method, args, target) -> aif.getAspectInstance());
		}
	}

}
