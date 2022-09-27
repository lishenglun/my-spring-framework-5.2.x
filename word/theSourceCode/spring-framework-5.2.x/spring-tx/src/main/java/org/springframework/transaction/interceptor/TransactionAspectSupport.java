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

package org.springframework.transaction.interceptor;

import io.vavr.control.Try;
import kotlin.reflect.KFunction;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.lang.Nullable;
import org.springframework.transaction.*;
import org.springframework.transaction.reactive.TransactionContextManager;
import org.springframework.transaction.support.CallbackPreferringPlatformTransactionManager;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;

/**
 * Base class for transactional aspects, such as the {@link TransactionInterceptor}
 * or an AspectJ aspect.
 *
 * <p>This enables the underlying Spring transaction infrastructure to be used easily
 * to implement an aspect for any aspect system.
 *
 * <p>Subclasses are responsible for calling methods in this class in the correct order.
 *
 * <p>If no transaction name has been specified in the {@link TransactionAttribute},
 * the exposed name will be the {@code fully-qualified class name + "." + method name}
 * (by default).
 *
 * <p>Uses the <b>Strategy</b> design pattern. A {@link PlatformTransactionManager} or
 * {@link ReactiveTransactionManager} implementation will perform the actual transaction
 * management, and a {@link TransactionAttributeSource} (e.g. annotation-based) is used
 * for determining transaction definitions for a particular class or method.
 *
 * <p>A transaction aspect is serializable if its {@code TransactionManager} and
 * {@code TransactionAttributeSource} are serializable.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Stéphane Nicoll
 * @author Sam Brannen
 * @author Mark Paluch
 * @since 1.1
 * @see PlatformTransactionManager
 * @see ReactiveTransactionManager
 * @see #setTransactionManager
 * @see #setTransactionAttributes
 * @see #setTransactionAttributeSource
 */
public abstract class TransactionAspectSupport implements BeanFactoryAware, InitializingBean {

	// NOTE: This class must not implement Serializable because it serves as base
	// class for AspectJ aspects (which are not allowed to implement Serializable)!


	/**
	 * Key to use to store the default transaction manager.
	 */
	private static final Object DEFAULT_TRANSACTION_MANAGER_KEY = new Object();

	/**
	 * Vavr library present on the classpath?
	 */
	private static final boolean vavrPresent = ClassUtils.isPresent(
			"io.vavr.control.Try", TransactionAspectSupport.class.getClassLoader());

	/**
	 * Reactive Streams API present on the classpath?
	 */
	private static final boolean reactiveStreamsPresent =
			ClassUtils.isPresent("org.reactivestreams.Publisher", TransactionAspectSupport.class.getClassLoader());

	/**
	 * Holder to support the {@code currentTransactionStatus()} method,
	 * and to support communication between different cooperating advices
	 * (e.g. before and after advice) if the aspect involves more than a
	 * single method (as will be the case for around advice).
	 */
	private static final ThreadLocal<TransactionInfo> transactionInfoHolder =
			new NamedThreadLocal<>("Current aspect-driven transaction");


	/**
	 * Subclasses can use this to return the current TransactionInfo.
	 * Only subclasses that cannot handle all operations in one method,
	 * such as an AspectJ aspect involving distinct before and after advice,
	 * need to use this mechanism to get at the current TransactionInfo.
	 * An around advice such as an AOP Alliance MethodInterceptor can hold a
	 * reference to the TransactionInfo throughout the aspect method.
	 * <p>A TransactionInfo will be returned even if no transaction was created.
	 * The {@code TransactionInfo.hasTransaction()} method can be used to query this.
	 * <p>To find out about specific transaction characteristics, consider using
	 * TransactionSynchronizationManager's {@code isSynchronizationActive()}
	 * and/or {@code isActualTransactionActive()} methods.
	 * @return the TransactionInfo bound to this thread, or {@code null} if none
	 * @see TransactionInfo#hasTransaction()
	 * @see org.springframework.transaction.support.TransactionSynchronizationManager#isSynchronizationActive()
	 * @see org.springframework.transaction.support.TransactionSynchronizationManager#isActualTransactionActive()
	 */
	@Nullable
	protected static TransactionInfo currentTransactionInfo() throws NoTransactionException {
		return transactionInfoHolder.get();
	}

	/**
	 * Return the transaction status of the current method invocation.
	 * Mainly intended for code that wants to set the current transaction
	 * rollback-only but not throw an application exception.
	 * @throws NoTransactionException if the transaction info cannot be found,
	 * because the method was invoked outside an AOP invocation context
	 */
	public static TransactionStatus currentTransactionStatus() throws NoTransactionException {
		TransactionInfo info = currentTransactionInfo();
		if (info == null || info.transactionStatus == null) {
			throw new NoTransactionException("No transaction aspect-managed TransactionStatus in scope");
		}
		return info.transactionStatus;
	}


	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private final ReactiveAdapterRegistry reactiveAdapterRegistry;

	// 事务管理器名称
	@Nullable
	private String transactionManagerBeanName;

	/**
	 * 这个事务管理器是配置时指定引用的事务管理器，例如：其中transaction-manager指定的就是事务管理器。
	 * <tx:advice>标签对应的是TransactionInterceptor对象，TransactionInterceptor extends TransactionAspectSupport，
	 * 所以当前transactionManager属性所对应的TransactionManager，是配置<tx:advice>标签时指定的事务管理器
	 *
	 * 	<tx:advice id="txAdvice" transaction-manager="transactionManager">
	 * 		<tx:attributes>
	 * 			<tx:method name="updateBalanceInService" propagation="REQUIRED"/>
	 * 			<tx:method name="updateBalanceInDao" propagation="REQUIRED"/>
	 * 		</tx:attributes>
	 * 	</tx:advice>
	 */
	// 配置时指定引用的事务管理器，简称：引用的事务管理器
	@Nullable
	private TransactionManager transactionManager;

	@Nullable
	private TransactionAttributeSource transactionAttributeSource;

	@Nullable
	private BeanFactory beanFactory;

	// 事务管理器缓存
	// key：beanName
	// value：TransactionManager
	private final ConcurrentMap<Object, TransactionManager> transactionManagerCache =
			new ConcurrentReferenceHashMap<>(4);

	private final ConcurrentMap<Method, ReactiveTransactionSupport> transactionSupportCache =
			new ConcurrentReferenceHashMap<>(1024);


	protected TransactionAspectSupport() {
		if (reactiveStreamsPresent) {
			this.reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();
		}
		else {
			this.reactiveAdapterRegistry = null;
		}
	}


	/**
	 * Specify the name of the default transaction manager bean.
	 * <p>This can either point to a traditional {@link PlatformTransactionManager} or a
	 * {@link ReactiveTransactionManager} for reactive transaction management.
	 */
	public void setTransactionManagerBeanName(@Nullable String transactionManagerBeanName) {
		this.transactionManagerBeanName = transactionManagerBeanName;
	}

	/**
	 * Return the name of the default transaction manager bean.
	 */
	@Nullable
	protected final String getTransactionManagerBeanName() {
		return this.transactionManagerBeanName;
	}

	/**
	 * Specify the <em>default</em> transaction manager to use to drive transactions.
	 * <p>This can either be a traditional {@link PlatformTransactionManager} or a
	 * {@link ReactiveTransactionManager} for reactive transaction management.
	 * <p>The default transaction manager will be used if a <em>qualifier</em>
	 * has not been declared for a given transaction or if an explicit name for the
	 * default transaction manager bean has not been specified.
	 * @see #setTransactionManagerBeanName
	 */
	public void setTransactionManager(@Nullable TransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	/**
	 * Return the default transaction manager, or {@code null} if unknown.
	 * <p>This can either be a traditional {@link PlatformTransactionManager} or a
	 * {@link ReactiveTransactionManager} for reactive transaction management.
	 */
	@Nullable
	public TransactionManager getTransactionManager() {
		return this.transactionManager;
	}

	/**
	 * Set properties with method names as keys and transaction attribute
	 * descriptors (parsed via TransactionAttributeEditor) as values:
	 * e.g. key = "myMethod", value = "PROPAGATION_REQUIRED,readOnly".
	 * <p>Note: Method names are always applied to the target class,
	 * no matter if defined in an interface or the class itself.
	 * <p>Internally, a NameMatchTransactionAttributeSource will be
	 * created from the given properties.
	 * @see #setTransactionAttributeSource
	 * @see TransactionAttributeEditor
	 * @see NameMatchTransactionAttributeSource
	 */
	public void setTransactionAttributes(Properties transactionAttributes) {
		NameMatchTransactionAttributeSource tas = new NameMatchTransactionAttributeSource();
		tas.setProperties(transactionAttributes);
		this.transactionAttributeSource = tas;
	}

	/**
	 * Set multiple transaction attribute sources which are used to find transaction
	 * attributes. Will build a CompositeTransactionAttributeSource for the given sources.
	 * @see CompositeTransactionAttributeSource
	 * @see MethodMapTransactionAttributeSource
	 * @see NameMatchTransactionAttributeSource
	 * @see org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
	 */
	public void setTransactionAttributeSources(TransactionAttributeSource... transactionAttributeSources) {
		this.transactionAttributeSource = new CompositeTransactionAttributeSource(transactionAttributeSources);
	}

	/**
	 * Set the transaction attribute source which is used to find transaction
	 * attributes. If specifying a String property value, a PropertyEditor
	 * will create a MethodMapTransactionAttributeSource from the value.
	 * @see TransactionAttributeSourceEditor
	 * @see MethodMapTransactionAttributeSource
	 * @see NameMatchTransactionAttributeSource
	 * @see org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
	 */
	public void setTransactionAttributeSource(@Nullable TransactionAttributeSource transactionAttributeSource) {
		this.transactionAttributeSource = transactionAttributeSource;
	}

	/**
	 * Return the transaction attribute source.
	 */
	@Nullable
	public TransactionAttributeSource getTransactionAttributeSource() {
		return this.transactionAttributeSource;
	}

	/**
	 * Set the BeanFactory to use for retrieving {@code TransactionManager} beans.
	 */
	@Override
	public void setBeanFactory(@Nullable BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	/**
	 * Return the BeanFactory to use for retrieving {@code TransactionManager} beans.
	 */
	@Nullable
	protected final BeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	/**
	 * Check that required properties were set.
	 */
	@Override
	public void afterPropertiesSet() {
		if (getTransactionManager() == null && this.beanFactory == null) {
			throw new IllegalStateException(
					"Set the 'transactionManager' property or make sure to run within a BeanFactory " +
					"containing a TransactionManager bean!");
		}
		if (getTransactionAttributeSource() == null) {
			throw new IllegalStateException(
					"Either 'transactionAttributeSource' or 'transactionAttributes' is required: " +
					"If there are no transactional methods, then don't use a transaction aspect.");
		}
	}


	/**
	 * General delegate for around-advice-based subclasses, delegating to several other template
	 * methods on this class. Able to handle {@link CallbackPreferringPlatformTransactionManager}
	 * as well as regular {@link PlatformTransactionManager} implementations and
	 * {@link ReactiveTransactionManager} implementations for reactive return types.
	 *
	 * 用于围绕建议的子类的一般委托，委托给此类上的其他几个模板方法。能够处理 {@link CallbackPreferringPlatformTransactionManager}
	 * 以及常规的 {@link PlatformTransactionManager} 实现和 {@link ReactiveTransactionManager} 实现响应式返回类型。
	 *
	 * @param method the Method being invoked
	 * @param targetClass the target class that we're invoking the method on
	 * @param invocation the callback to use for proceeding with the target invocation
	 * @return the return value of the method, if any
	 * @throws Throwable propagated from the target invocation
	 */
	@Nullable
	protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
			final InvocationCallback invocation) throws Throwable {

		/* 1、从事务属性源中，获取当前方法的事务属性 */
		// If the transaction attribute is null, the method is non-transactional. —— 如果事务属性为空，则该方法是非事务性的
		/**
		 * 1、例如：配置了几个<tx:method>标签就有几个TransactionAttribute，然后将所有的TransactionAttribute构成一个TransactionAttributeSource，
		 * TransactionAttributeSource里面存放了所有的TransactionAttribute（题外：TransactionAttributeSource对应了<tx:attributes>）
		 *
		 * 	<tx:advice id="txAdvice" transaction-manager="transactionManager">
		 * 		<tx:attributes>
		 * 			<tx:method name="updateBalanceInService" propagation="REQUIRED"/>
		 * 			<tx:method name="updateBalanceInDao" propagation="REQUIRED"/>
		 * 		</tx:attributes>
		 * 	</tx:advice>
		 *
		 * 	2、开启注解事务支持时，tas = {@link AnnotationTransactionAttributeSource}
		 */
		// (1)获取事务属性源：NameMatchTransactionAttributeSource
		// 题外：NameMatchTransactionAttributeSource事务属性源，对应一个<tx:attributes>标签
		TransactionAttributeSource tas = getTransactionAttributeSource();

		/**
		 * 1、NameMatchTransactionAttributeSource#getTransactionAttribute(method, targetClass)逻辑：
		 * 根据方法名称匹配TransactionAttribute（先根据方法名精准匹配，后模糊匹配）
		 *
		 * 2、AnnotationTransactionAttributeSource#getTransactionAttribute()逻辑：
		 * 寻找和解析方法上的事务注解，构建一个TransactionAttribute返回。最典型的就是寻找和解析方法上的@Transaction，构建一个TransactionAttribute进行缓存和返回。
		 *
		 * 题外：AnnotationTransactionAttributeSource extends AbstractFallbackTransactionAttributeSource，所以走的是AbstractFallbackTransactionAttributeSource
		 */
		// (2)从事务属性源中，获取匹配当前方法的事务属性：RuleBasedTransactionAttribute
		// 题外：RuleBasedTransactionAttribute事务属性，对应一个<tx:method>标签
		final TransactionAttribute txAttr/* 当前方法对应的事务属性 */ = (tas != null ? tas.getTransactionAttribute(method, targetClass) : null);

		/* 2、获取事务管理器 */
		/**
		 * 1、一般获取的都是引用的事务管理器，例如：
		 * <tx:advice id="txAdvice" transaction-manager="transactionManager">，其中的transaction-manager属性引用的事务管理器
		 *
		 * 2、题外：事务属性中指定beanName的事务管理器优先级最高
		 */
		// 获取事务管理器
		final TransactionManager tm = determineTransactionManager/* 确定事务管理器 */(txAttr);

		if (this.reactiveAdapterRegistry != null && tm instanceof ReactiveTransactionManager) {
			ReactiveTransactionSupport txSupport = this.transactionSupportCache.computeIfAbsent(method, key -> {
				if (KotlinDetector.isKotlinType(method.getDeclaringClass()) && KotlinDelegate.isSuspend(method)) {
					throw new TransactionUsageException(
							"Unsupported annotated transaction on suspending function detected: " + method +
							". Use TransactionalOperator.transactional extensions instead.");
				}
				ReactiveAdapter adapter = this.reactiveAdapterRegistry.getAdapter(method.getReturnType());
				if (adapter == null) {
					throw new IllegalStateException("Cannot apply reactive transaction to non-reactive return type: " +
							method.getReturnType());
				}
				return new ReactiveTransactionSupport(adapter);
			});
			return txSupport.invokeWithinTransaction(
					method, targetClass, invocation, txAttr, (ReactiveTransactionManager) tm);
		}

		// 对事务管理器进行类型判断和转换：
		// 判断一下，事务管理器是不是PlatformTransactionManager类型，是的话就转换为PlatformTransactionManager，不是的话就报错，
		// 也就是说，⚠️要求事务管理器必须是PlatformTransactionManager类型
		PlatformTransactionManager ptm = asPlatformTransactionManager(tm);

		/* 3、获取方法签名：类名+方法名 */
		// 例如：com.springstudy.msb.s_27.tx_xml.service.BookServiceImpl.updateBalanceInService
		final String joinpointIdentification/* 连接点的唯一标识 */ = methodIdentification(method, targetClass, txAttr);

		/* 4、声明式事务处理 */
		/**
		 * 对于声明式事务的处理与编程式事务的处理的区别：
		 * （1）第一点区别在于事务属性上，编程式的事务处理必须要有事务属性，
		 * （2）第二点区别就是在TransactionManager上，编程式事务，事务管理器必须是CallbackPreferringPlatformTransactionManager类型
		 * >>> 题外：CallbackPreferringPlatformTransactionManager实现PlatformTransactionManager接口，暴露出一个方法用于执行事务处理中的回调。
		 * 所以，这两种方式都可以用作事务处理方式的判断。
		 *
		 * 注意：⚠️这里所说的编程式事务，不是去处理我们写业务逻辑时所使用的编程式事务，跟那个没有半毛钱关系！
		 */
		// txAttr==null || ptm!=CallbackPreferringPlatformTransactionManager
		if (txAttr == null || !(ptm instanceof CallbackPreferringPlatformTransactionManager)) {
			/*

			(1)开启事务(️创建事务️）：在目标方法执行前获取事务并收集事务信息TransactionAttribute，
			TransactionInfo与TransactionAttribute并不相同：TransactionInfo中包含TransactionAttribute信息，但是，除了TransactionAttribute外，还有其他事务信息，例如PlatformTransactionManager以及TransactionStatus相关信息。

			*/
			// Standard transaction demarcation with getTransaction and commit/rollback calls. —— 使用 getTransaction 和 commit/rollback 调用进行"标准事务"划分。
			/**
			 * 1、题外：创建得到TransactionInfo后，自此，跟事务相关的所有信息都准备好了！
			 *
			 * 2、题外：status表示的是事务状态，info表示的是整个事务的状态信息
			 */
			TransactionInfo txInfo = createTransactionIfNecessary/* 如果需要的话，创建事务 */(ptm, txAttr, joinpointIdentification);

			Object retVal;
			try {

				/* (2)执行被增强的方法 */
				// This is an around advice: Invoke the next interceptor in the chain. —— 这是一个环绕建议：调用链中的下一个拦截器。
				// This will normally result in a target object being invoked. —— 这通常会导致调用目标对象。
				retVal = invocation.proceedWithInvocation();
			}
			catch (Throwable ex) {

				/* (3)异常，回滚事务 */
				// target invocation exception
				completeTransactionAfterThrowing(txInfo, ex);

				// 该抛异常还是会抛出异常，但是回滚标识可能是false，也就是当前事务不需要回滚，例如：
				// methodA()调用MethodB()，methodA()传播特性是required，methodB()的传播特性是nested，
				// 那么当methodB()内部报错的话，它只会回滚methodB()这个保存点！并且methodB()内部没有捕获异常的话，而是往外抛出异常，
				// 那么这里也会把异常往外抛，抛到methodA里面去，但是回滚的标识是false，也就是说methodA()里面不需要因为methodB()的异常而回滚！
				throw ex;
			}
			finally {
				/* (4)清除事务信息 */
				// 清除当前事务信息，恢复线程私有的老的事务信息（恢复外层的事务信息！）
				cleanupTransactionInfo(txInfo);
			}

			if (retVal != null && vavrPresent && VavrDelegate.isVavrTry(retVal)) {
				// Set rollback-only in case of Vavr failure matching our rollback rules...
				TransactionStatus status = txInfo.getTransactionStatus();
				if (status != null && txAttr != null) {
					retVal = VavrDelegate.evaluateTryFailure(retVal, txAttr, status);
				}
			}
			/* (5)提交事务 */
			// 成功后提交事务，会进行资源储量，连接释放，恢复挂起事务等操作
			commitTransactionAfterReturning(txInfo);
			return retVal;
		}
		/* 5、编程式事务处理 */
		// 注意：⚠️这里所说的编程式事务，不是去处理我们写业务逻辑时所使用的编程式事务，跟那个没有半毛钱关系！
		// txAttr!=null && ptm==CallbackPreferringPlatformTransactionManager
		else {
			Object result;
			final ThrowableHolder throwableHolder = new ThrowableHolder();

			// It's a CallbackPreferringPlatformTransactionManager: pass a TransactionCallback in.
			// 上面翻译：这是一个CallbackPreferringPlatformTransactionManager：传入一个 TransactionCallback。

			try {
				result = ((CallbackPreferringPlatformTransactionManager) ptm).execute(txAttr, status -> {
					// 准备事务信息(TransactionInfo)
					TransactionInfo txInfo = prepareTransactionInfo(ptm, txAttr, joinpointIdentification, status);
					try {
						// 执行目标方法
						Object retVal = invocation.proceedWithInvocation();
						if (retVal != null && vavrPresent && VavrDelegate.isVavrTry(retVal)) {
							// Set rollback-only in case of Vavr failure matching our rollback rules...
							retVal = VavrDelegate.evaluateTryFailure(retVal, txAttr, status);
						}
						return retVal;
					}
					catch (Throwable ex) {
						// 异常回滚
						if (txAttr.rollbackOn(ex)) {
							// A RuntimeException: will lead to a rollback.
							if (ex instanceof RuntimeException) {
								throw (RuntimeException) ex;
							}
							else {
								throw new ThrowableHolderException(ex);
							}
						}
						else {
							// A normal return value: will lead to a commit.
							throwableHolder.throwable = ex;
							return null;
						}
					}
					finally {
						// 清除事务信息
						cleanupTransactionInfo(txInfo);
					}
				});
			}
			catch (ThrowableHolderException ex) {
				throw ex.getCause();
			}
			catch (TransactionSystemException ex2) {
				if (throwableHolder.throwable != null) {
					logger.error("Application exception overridden by commit exception", throwableHolder.throwable);
					ex2.initApplicationException(throwableHolder.throwable);
				}
				throw ex2;
			}
			catch (Throwable ex2) {
				if (throwableHolder.throwable != null) {
					logger.error("Application exception overridden by commit exception", throwableHolder.throwable);
				}
				throw ex2;
			}

			// Check result state: It might indicate a Throwable to rethrow.
			if (throwableHolder.throwable != null) {
				throw throwableHolder.throwable;
			}

			return result;
		}
	}

	/**
	 * Clear the transaction manager cache.
	 */
	protected void clearTransactionManagerCache() {
		this.transactionManagerCache.clear();
		this.beanFactory = null;
	}

	/**
	 * 通过事务属性，获取给定事务对应的事务管理器
	 *
	 * Determine the specific transaction manager to use for the given transaction. —— 确定用于给定事务的特定事务管理器。
	 *
	 * @param txAttr			事务属性（RuleBasedTransactionAttribute）
	 */
	@Nullable
	protected TransactionManager determineTransactionManager(@Nullable TransactionAttribute txAttr) {
		/* 1、如果事务属性为null || beanFactory为null，则直接获取"引用的事务管理器" */
		// Do not attempt to lookup tx manager if no tx attributes are set —— 如果未设置tx属性，请勿尝试查找tx manager
		if (txAttr == null || this.beanFactory == null) {
			return getTransactionManager();
		}

		/* 2、存在事务属性，或者存在beanFactory */

		/* (1)获取TransactionAttribute中指定beanName的事务管理器 */
		// 获取TransactionAttribute中指定的事务管理器beanName
		// 题外：qualifier作为beanName
		// 题外：RuleBasedTransactionAttribute extends DefaultTransactionAttribute，所以走的是DefaultTransactionAttribute
		String qualifier = txAttr.getQualifier();
		// 通过TransactionAttribute中指定的事务管理器beanName，获取对应的TransactionManager
		if (StringUtils.hasText(qualifier)) {
			// 获取指定beanName的TransactionManager
			return determineQualifiedTransactionManager(this.beanFactory, qualifier);
		}
		/* (2)获取指定事务管理器beanName对应的事务管理器 */
		// 通过指定的事务管理器beanName，确定事务管理器
		else if (StringUtils.hasText(this.transactionManagerBeanName)) {
			// 获取指定beanName的TransactionManager
			return determineQualifiedTransactionManager(this.beanFactory, this.transactionManagerBeanName);
		}
		else {
			/* (3)获取"引用的事务管理器" */
			TransactionManager defaultTransactionManager = getTransactionManager();

			/*

			(4)获取默认的事务管理器
			默认事务管理器：benFactory中TransactionManager类型的bean，作为默认的事务管理器

			*/
			if (defaultTransactionManager == null) {
				// 获取默认事务管理器
				defaultTransactionManager = this.transactionManagerCache.get(DEFAULT_TRANSACTION_MANAGER_KEY/* 默认事务管理器key */);
				if (defaultTransactionManager == null) {
					// 去benFactory中获取TransactionManager类型的bean，作为默认的事务管理器
					defaultTransactionManager = this.beanFactory.getBean(TransactionManager.class);
					// 放入默认事务管理器
					this.transactionManagerCache.putIfAbsent(
							DEFAULT_TRANSACTION_MANAGER_KEY, defaultTransactionManager);
				}
			}
			return defaultTransactionManager;
		}
	}

	/**
	 * 获取指定beanName的TransactionManager
	 *
	 * @param beanFactory
	 * @param qualifier				beanName
	 * @return
	 */
	private TransactionManager determineQualifiedTransactionManager(BeanFactory beanFactory, String qualifier/* 作为beanName */) {
		// 先从transactionManagerCache中获取，对应beanName的TransactionManager
		TransactionManager txManager = this.transactionManagerCache.get(qualifier);

		// 缓存中没有
		if (txManager == null) {

			// 获取beanName为qualifier参数值的TransactionManager
			txManager = BeanFactoryAnnotationUtils.qualifiedBeanOfType/* 合格的Bean类型 */(beanFactory, TransactionManager.class, qualifier);

			// 放入缓存
			this.transactionManagerCache.putIfAbsent(qualifier, txManager);
		}
		return txManager;
	}

	/**
	 * 判断一下，事务管理器是不是PlatformTransactionManager类型，是的话就转换为PlatformTransactionManager，不是的话就报错，
	 * 也就是说，要求事务管理器必须是PlatformTransactionManager类型
	 */
	@Nullable
	private PlatformTransactionManager asPlatformTransactionManager(@Nullable Object transactionManager) {
		/* 1、如果事务管理器是PlatformTransactionManager类型，则转换为PlatformTransactionManager */
		// 如果事务管理器为空 || 事务管理器不为空，但是PlatformTransactionManager类型，都转换为PlatformTransactionManager类型
		// 题外：如果事务管理器为空转换为PlatformTransactionManager类型，不会报错，而是返回一个null，
		// >>> 所以实际意义上来说，这里就是判断事务管理器是不是一个PlatformTransactionManager实例，如果不是PlatformTransactionManager实例则报错，
		// >>> 所以就要求事务管理器必须是PlatformTransactionManager的子类！
		if (transactionManager == null || transactionManager instanceof PlatformTransactionManager) {
			return (PlatformTransactionManager) transactionManager;
		}
		/* 2、如果事务管理器不是PlatformTransactionManager类型，则报错。所以要求要求事务管理器必须是PlatformTransactionManager的子类 */
		else {
			throw new IllegalStateException(
					"Specified transaction manager is not a PlatformTransactionManager: " + transactionManager);
		}
	}

	private String methodIdentification(Method method, @Nullable Class<?> targetClass,
			@Nullable TransactionAttribute txAttr) {

		String methodIdentification = methodIdentification(method, targetClass);
		if (methodIdentification == null) {
			if (txAttr instanceof DefaultTransactionAttribute) {
				methodIdentification = ((DefaultTransactionAttribute) txAttr).getDescriptor();
			}
			if (methodIdentification == null) {
				methodIdentification = ClassUtils.getQualifiedMethodName(method, targetClass);
			}
		}
		return methodIdentification;
	}

	/**
	 * Convenience method to return a String representation of this Method
	 * for use in logging. Can be overridden in subclasses to provide a
	 * different identifier for the given method.
	 * <p>The default implementation returns {@code null}, indicating the
	 * use of {@link DefaultTransactionAttribute#getDescriptor()} instead,
	 * ending up as {@link ClassUtils#getQualifiedMethodName(Method, Class)}.
	 * @param method the method we're interested in
	 * @param targetClass the class that the method is being invoked on
	 * @return a String representation identifying this method
	 * @see org.springframework.util.ClassUtils#getQualifiedMethodName
	 */
	@Nullable
	protected String methodIdentification(Method method, @Nullable Class<?> targetClass) {
		return null;
	}

	/**
	 * 如果需要的话，创建事务
	 *
	 * Create a transaction if necessary based on the given TransactionAttribute.
	 * <p>Allows callers to perform custom TransactionAttribute lookups through
	 * the TransactionAttributeSource.
	 * @param txAttr the TransactionAttribute (may be {@code null})
	 * @param joinpointIdentification the fully qualified method name
	 * (used for monitoring and logging purposes)
	 * @return a TransactionInfo object, whether or not a transaction was created.
	 * The {@code hasTransaction()} method on TransactionInfo can be used to
	 * tell if there was a transaction created.
	 * @see #getTransactionAttributeSource()
	 */
	@SuppressWarnings("serial")
	protected TransactionInfo createTransactionIfNecessary(@Nullable PlatformTransactionManager tm,
			@Nullable TransactionAttribute txAttr/* RuleBasedTransactionAttribute */, final String joinpointIdentification) {

		/*

		1、如果TransactionAttribute中没有指定事务名称，则使用方法签名作为事务名称，具体做法是：
		则创建一个委托对象(DelegatingTransactionAttribute)，把事务属性封装到委托对象中，通过委托对象获取方法签名作为事务名称

		题外：DelegatingTransactionAttribute包装事务属性的目的就是为了返回方法签名作为事务名称。
		题外：一般TransactionAttribute中都没有配置事务名称，所以这里一般都成立

		*/
		// If no name specified, apply method identification as transaction name. —— 如果未指定名称，则应用方法标识作为事务名称。
		// 如果TransactionAttribute中没有指定事务名称，则使用方法签名作为事务名称。具体实现：创建一个DelegatingTransactionAttribute，委托TransactionAttribute，然后getName()返回方法签名作为事务名称。
		// 题外：RuleBasedTransactionAttribute extends DefaultTransactionAttribute，所以走走DefaultTransactionDefinition
		if (txAttr != null && txAttr.getName() == null) {
			// 创建一个DelegatingTransactionAttribute，委托TransactionAttribute，然后getName()返回方法签名作为事务名称
			txAttr = new DelegatingTransactionAttribute/* 委托的事务属性 */(txAttr) {
				@Override
				public String getName() {
					// 使用方法签名作为事务名称
					return joinpointIdentification;
				}
			};
		}

		/* 2、获取TransactionStatus，里面创建了事务对象，和开启了事务 */
		TransactionStatus status = null;
		if (txAttr != null) {
			if (tm != null) {
				// 获取TransactionStatus
				// ⚠️通过事务管理器，获取TransactionStatus（事务状态信息）
				// AbstractPlatformTransactionManager
				status = tm.getTransaction(txAttr);
			}
			else {
				if (logger.isDebugEnabled()) {
					logger.debug("Skipping transactional joinpoint [" + joinpointIdentification +
							"] because no transaction manager has been configured");
				}
			}
		}

		/*

		3、准备事务信息(TransactionInfo)

		当已经建立事务连接并完成了事务信息的提取后，我们需要将所有的事务信息统一记录在TransactionInfo类型的实例中，
	 	这个实例包含了目标方法开始前的所有状态信息，一旦事务执行失败，Spring会通过TransactionInfo类型的实例中的信息来进行回滚等后续工作。

	 	目标方法运行前的事务准备工作，最大的目的是为了，对于程序没有按照我们期待的那样进行，也就是出现特定的错误时，对数据进行恢复，回滚。

		*/
		// 根据指定的属性与status准备一个TransactionInfo(事务信息) —— 准备事务信息
		// 题外：无论传播特性是咋样的，每个事务方法都会对应一个新的TransactionInfo，只是里面的连接持有器可能是一样的！
		return prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);
	}

	/**
	 * 创建事务信息对象(TransactionInfo)，然后把事务管理器，事务注解属性，方法标识符，事务状态设置进入，然后绑定到当前线程私有变量里。
	 *
	 * 当已经建立事务连接并完成了事务信息的提取后，我们需要将所有的事务信息统一记录在TransactionInfo类型的实例中，
	 * 这个实例包含了目标方法开始前的所有状态信息，一旦事务执行失败，Spring会通过TransactionInfo类型的实例中的信息来进行回滚等后续工作。
	 *
	 * Prepare a TransactionInfo for the given attribute and status object.
	 * @param txAttr the TransactionAttribute (may be {@code null})
	 * @param joinpointIdentification the fully qualified method name
	 *                                方法签名
	 * (used for monitoring and logging purposes)
	 * @param status the TransactionStatus for the current transaction
	 * @return the prepared TransactionInfo object
	 */
	protected TransactionInfo prepareTransactionInfo(@Nullable PlatformTransactionManager tm,
			@Nullable TransactionAttribute txAttr, String joinpointIdentification/* 方法签名 */,
			@Nullable TransactionStatus status) {

		// 创建事务信息
		TransactionInfo txInfo = new TransactionInfo(tm, txAttr, joinpointIdentification);
		if (txAttr != null) {
			// We need a transaction for this method...
			if (logger.isTraceEnabled()) {
				logger.trace("Getting transaction for [" + txInfo.getJoinpointIdentification() + "]");
			}
			// The transaction manager will flag an error if an incompatible tx already exists.
			// 设置新事务状态
			txInfo.newTransactionStatus(status);
		}
		else {
			// The TransactionInfo.hasTransaction() method will return false. We created it only
			// to preserve the integrity of the ThreadLocal stack maintained in this class.
			if (logger.isTraceEnabled()) {
				logger.trace("No need to create transaction for [" + joinpointIdentification +
						"]: This method is not transactional.");
			}
		}

		// We always bind the TransactionInfo to the thread, even if we didn't create
		// a new transaction here. This guarantees that the TransactionInfo stack
		// will be managed correctly even if no transaction was created by this aspect.
		// ⚠️事务信息绑定到当前线程
		// 里面会将当前事务信息TransactionInfo存放到了ThreadLocal里面，然后将老的TransactionInfo保存到了当前TransactionInfo.oldTransactionInfo变量里面，方便后续的恢复！
		txInfo.bindToThread/* 跟当前线程进行绑定 */();
		return txInfo;
	}

	/**
	 * 提交事务
	 *
	 * Execute after successful completion of call, but not after an exception was handled.
	 * Do nothing if we didn't create a transaction.
	 * @param txInfo information about the current transaction
	 */
	protected void commitTransactionAfterReturning(@Nullable TransactionInfo txInfo) {
		if (txInfo != null && txInfo.getTransactionStatus() != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "]");
			}
			txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
		}
	}

	/**
	 * 如果支持回滚的话就进行回滚，否则就处理提交，提交里面，如果TransactionStatus.isRollbackOnly()=true的话，也会进行回滚处理！
	 *
	 * Handle a throwable, completing the transaction.
	 * We may commit or roll back, depending on the configuration.
	 * @param txInfo information about the current transaction
	 * @param ex throwable encountered
	 */
	protected void completeTransactionAfterThrowing(@Nullable TransactionInfo txInfo, Throwable ex) {
		// 当抛出异常时，首先判断当前是否存在事务，这是基础依据
		// 只有存在事务，才要进行回滚
		if (txInfo != null && txInfo.getTransactionStatus() != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() +
						"] after exception: " + ex);
			}
			/**
			 * 1、txInfo.transactionAttribute.rollbackOn(ex)
			 * 在对目标方法的执行过程中，一旦出现 Throwable 就会被引导至此方法处理，
			 * 但是并不代表所有的 Throwable 都会被回滚处理，比如我们常用的 Exception，默认是不会被处理的。
			 * ⚠️默认情况下，即使出现异常，数据也会被正常提交，而这个关键的地方就是在 txInfo.transaction Attribute.rollbackOn(ex)这个两数
			 */
			// ⚠️这里判断是否回滚默认的依据是：抛出的异常是否是RuntimeException或者是Error的类型
			if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)/* ⚠️ */) {
				try {
					// ⚠️根据TransactionStatus信息，进行回滚处理
					txInfo.getTransactionManager().rollback(txInfo.getTransactionStatus());
				}
				catch (TransactionSystemException ex2) {
					logger.error("Application exception overridden by rollback exception", ex);
					ex2.initApplicationException(ex);
					throw ex2;
				}
				catch (RuntimeException | Error ex2) {
					logger.error("Application exception overridden by rollback exception", ex);
					throw ex2;
				}
			}
			// 如果不满足回滚条件，即使抛出异常也同样会提交
			else {
				// We don't roll back on this exception.
				// Will still roll back if TransactionStatus.isRollbackOnly() is true.
				try {
					txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
				}
				catch (TransactionSystemException ex2) {
					logger.error("Application exception overridden by commit exception", ex);
					ex2.initApplicationException(ex);
					throw ex2;
				}
				catch (RuntimeException | Error ex2) {
					logger.error("Application exception overridden by commit exception", ex);
					throw ex2;
				}
			}
		}
	}

	/**
	 * 清除事务信息
	 *
	 * Reset the TransactionInfo ThreadLocal.
	 * <p>Call this in all cases: exception or normal return!
	 * @param txInfo information about the current transaction (may be {@code null})
	 */
	protected void cleanupTransactionInfo(@Nullable TransactionInfo txInfo) {
		if (txInfo != null) {
			// 恢复transactionInfoHolder ThreadLocal里面的状态值
			txInfo.restoreThreadLocalStatus();
		}
	}


	/**
	 * Opaque object used to hold transaction information. Subclasses
	 * must pass it back to methods on this class, but not see its internals.
	 *
	 * 用于保存事务信息的不透明对象。子类必须将其传递回此类上的方法，但看不到其内部。
	 *
	 */
	protected static final class TransactionInfo {

		// 事务的管理器
		@Nullable
		private final PlatformTransactionManager transactionManager;

		// 事务的属性
		@Nullable
		private final TransactionAttribute transactionAttribute;

		// 连接点（方法的全限定名称）
		private final String joinpointIdentification;

		// 事务状态
		@Nullable
		private TransactionStatus transactionStatus;

		// 老的事务信息
		@Nullable
		private TransactionInfo oldTransactionInfo;

		public TransactionInfo(@Nullable PlatformTransactionManager transactionManager,
				@Nullable TransactionAttribute transactionAttribute, String joinpointIdentification) {

			this.transactionManager = transactionManager;
			this.transactionAttribute = transactionAttribute;
			this.joinpointIdentification = joinpointIdentification;
		}

		public PlatformTransactionManager getTransactionManager() {
			Assert.state(this.transactionManager != null, "No PlatformTransactionManager set");
			return this.transactionManager;
		}

		@Nullable
		public TransactionAttribute getTransactionAttribute() {
			return this.transactionAttribute;
		}

		/**
		 * Return a String representation of this joinpoint (usually a Method call)
		 * for use in logging.
		 */
		public String getJoinpointIdentification() {
			return this.joinpointIdentification;
		}

		public void newTransactionStatus(@Nullable TransactionStatus status) {
			this.transactionStatus = status;
		}

		@Nullable
		public TransactionStatus getTransactionStatus() {
			return this.transactionStatus;
		}

		/**
		 * Return whether a transaction was created by this aspect,
		 * or whether we just have a placeholder to keep ThreadLocal stack integrity.
		 */
		public boolean hasTransaction() {
			return (this.transactionStatus != null);
		}

		private void bindToThread() {
			// Expose current TransactionStatus, preserving any existing TransactionStatus
			// for restoration after this transaction is complete.
			// 上面的翻译：暴露当前的TransactionStatus，保留任何现有的TransactionStatus以便在此事务完成后恢复。

			// 保存老的事务信息（也就是外层方法的事务信息）
			/**
			 * 例如：MethodA调用MethodB，MethodA是required，MethodB是required_new，
			 * required_new是无论如何都会新创建一个事务，所以当在调用MethodB的时候，会创建一个新的事务来执行，MethodA的事务就作为一个老事务了
			 * 但是要保存MethodA的事务，方便恢复，要不然执行完毕MethodB后，MethodA再接着往下执行的时候，MethodA对应的事务就无法恢复了
			 */
			this.oldTransactionInfo = transactionInfoHolder.get();
			transactionInfoHolder.set(this);
		}

		private void restoreThreadLocalStatus() {
			// Use stack to restore old transaction TransactionInfo.
			// Will be null if none was set.
			// 恢复老的状态
			transactionInfoHolder.set(this.oldTransactionInfo);
		}

		@Override
		public String toString() {
			return (this.transactionAttribute != null ? this.transactionAttribute.toString() : "No transaction");
		}
	}


	/**
	 * Simple callback interface for proceeding with the target invocation.
	 * Concrete interceptors/aspects adapt this to their invocation mechanism.
	 */
	@FunctionalInterface
	protected interface InvocationCallback {

		@Nullable
		Object proceedWithInvocation() throws Throwable;
	}


	/**
	 * Internal holder class for a Throwable in a callback transaction model.
	 */
	private static class ThrowableHolder {

		@Nullable
		public Throwable throwable;
	}


	/**
	 * Internal holder class for a Throwable, used as a RuntimeException to be
	 * thrown from a TransactionCallback (and subsequently unwrapped again).
	 */
	@SuppressWarnings("serial")
	private static class ThrowableHolderException extends RuntimeException {

		public ThrowableHolderException(Throwable throwable) {
			super(throwable);
		}

		@Override
		public String toString() {
			return getCause().toString();
		}
	}


	/**
	 * Inner class to avoid a hard dependency on the Vavr library at runtime.
	 */
	private static class VavrDelegate {

		public static boolean isVavrTry(Object retVal) {
			return (retVal instanceof Try);
		}

		public static Object evaluateTryFailure(Object retVal, TransactionAttribute txAttr, TransactionStatus status) {
			return ((Try<?>) retVal).onFailure(ex -> {
				if (txAttr.rollbackOn(ex)) {
					status.setRollbackOnly();
				}
			});
		}
	}

	/**
	 * Inner class to avoid a hard dependency on Kotlin at runtime.
	 */
	private static class KotlinDelegate {

		static private boolean isSuspend(Method method) {
			KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
			return function != null && function.isSuspend();
		}
	}


	/**
	 * Delegate for Reactor-based management of transactional methods with a
	 * reactive return type.
	 */
	private class ReactiveTransactionSupport {

		private final ReactiveAdapter adapter;

		public ReactiveTransactionSupport(ReactiveAdapter adapter) {
			this.adapter = adapter;
		}

		public Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
				InvocationCallback invocation, @Nullable TransactionAttribute txAttr, ReactiveTransactionManager rtm) {

			String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

			// Optimize for Mono
			if (Mono.class.isAssignableFrom(method.getReturnType())) {
				return TransactionContextManager.currentContext().flatMap(context ->
						createTransactionIfNecessary(rtm, txAttr, joinpointIdentification).flatMap(it -> {
							try {
								// Need re-wrapping until we get hold of the exception through usingWhen.
								return Mono.<Object, ReactiveTransactionInfo>usingWhen(
										Mono.just(it),
										txInfo -> {
											try {
												return (Mono<?>) invocation.proceedWithInvocation();
											}
											catch (Throwable ex) {
												return Mono.error(ex);
											}
										},
										this::commitTransactionAfterReturning,
										(txInfo, err) -> Mono.empty(),
										this::commitTransactionAfterReturning)
										.onErrorResume(ex ->
												completeTransactionAfterThrowing(it, ex).then(Mono.error(ex)));
							}
							catch (Throwable ex) {
								// target invocation exception
								return completeTransactionAfterThrowing(it, ex).then(Mono.error(ex));
							}
						})).subscriberContext(TransactionContextManager.getOrCreateContext())
						.subscriberContext(TransactionContextManager.getOrCreateContextHolder());
			}

			// Any other reactive type, typically a Flux
			return this.adapter.fromPublisher(TransactionContextManager.currentContext().flatMapMany(context ->
					createTransactionIfNecessary(rtm, txAttr, joinpointIdentification).flatMapMany(it -> {
						try {
							// Need re-wrapping until we get hold of the exception through usingWhen.
							return Flux
									.usingWhen(
											Mono.just(it),
											txInfo -> {
												try {
													return this.adapter.toPublisher(invocation.proceedWithInvocation());
												}
												catch (Throwable ex) {
													return Mono.error(ex);
												}
											},
											this::commitTransactionAfterReturning,
											(txInfo, ex) -> Mono.empty(),
											this::commitTransactionAfterReturning)
									.onErrorResume(ex ->
											completeTransactionAfterThrowing(it, ex).then(Mono.error(ex)));
						}
						catch (Throwable ex) {
							// target invocation exception
							return completeTransactionAfterThrowing(it, ex).then(Mono.error(ex));
						}
					})).subscriberContext(TransactionContextManager.getOrCreateContext())
					.subscriberContext(TransactionContextManager.getOrCreateContextHolder()));
		}

		@SuppressWarnings("serial")
		private Mono<ReactiveTransactionInfo> createTransactionIfNecessary(ReactiveTransactionManager tm,
				@Nullable TransactionAttribute txAttr, final String joinpointIdentification) {

			// If no name specified, apply method identification as transaction name.
			if (txAttr != null && txAttr.getName() == null) {
				txAttr = new DelegatingTransactionAttribute(txAttr) {
					@Override
					public String getName() {
						return joinpointIdentification;
					}
				};
			}

			final TransactionAttribute attrToUse = txAttr;
			Mono<ReactiveTransaction> tx = (attrToUse != null ? tm.getReactiveTransaction(attrToUse) : Mono.empty());
			return tx.map(it -> prepareTransactionInfo(tm, attrToUse, joinpointIdentification, it)).switchIfEmpty(
					Mono.defer(() -> Mono.just(prepareTransactionInfo(tm, attrToUse, joinpointIdentification, null))));
		}

		private ReactiveTransactionInfo prepareTransactionInfo(@Nullable ReactiveTransactionManager tm,
				@Nullable TransactionAttribute txAttr, String joinpointIdentification,
				@Nullable ReactiveTransaction transaction) {

			ReactiveTransactionInfo txInfo = new ReactiveTransactionInfo(tm, txAttr, joinpointIdentification);
			if (txAttr != null) {
				// We need a transaction for this method...
				if (logger.isTraceEnabled()) {
					logger.trace("Getting transaction for [" + txInfo.getJoinpointIdentification() + "]");
				}
				// The transaction manager will flag an error if an incompatible tx already exists.
				txInfo.newReactiveTransaction(transaction);
			}
			else {
				// The TransactionInfo.hasTransaction() method will return false. We created it only
				// to preserve the integrity of the ThreadLocal stack maintained in this class.
				if (logger.isTraceEnabled()) {
					logger.trace("Don't need to create transaction for [" + joinpointIdentification +
							"]: This method isn't transactional.");
				}
			}

			return txInfo;
		}

		private Mono<Void> commitTransactionAfterReturning(@Nullable ReactiveTransactionInfo txInfo) {
			if (txInfo != null && txInfo.getReactiveTransaction() != null) {
				if (logger.isTraceEnabled()) {
					logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "]");
				}
				return txInfo.getTransactionManager().commit(txInfo.getReactiveTransaction());
			}
			return Mono.empty();
		}

		private Mono<Void> completeTransactionAfterThrowing(@Nullable ReactiveTransactionInfo txInfo, Throwable ex) {
			if (txInfo != null && txInfo.getReactiveTransaction() != null) {
				if (logger.isTraceEnabled()) {
					logger.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() +
							"] after exception: " + ex);
				}
				if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)) {
					return txInfo.getTransactionManager().rollback(txInfo.getReactiveTransaction()).onErrorMap(ex2 -> {
								logger.error("Application exception overridden by rollback exception", ex);
								if (ex2 instanceof TransactionSystemException) {
									((TransactionSystemException) ex2).initApplicationException(ex);
								}
								return ex2;
							}
					);
				}
				else {
					// We don't roll back on this exception.
					// Will still roll back if TransactionStatus.isRollbackOnly() is true.
					return txInfo.getTransactionManager().commit(txInfo.getReactiveTransaction()).onErrorMap(ex2 -> {
								logger.error("Application exception overridden by commit exception", ex);
								if (ex2 instanceof TransactionSystemException) {
									((TransactionSystemException) ex2).initApplicationException(ex);
								}
								return ex2;
							}
					);
				}
			}
			return Mono.empty();
		}
	}


	/**
	 * Opaque object used to hold transaction information for reactive methods.
	 */
	private static final class ReactiveTransactionInfo {

		@Nullable
		private final ReactiveTransactionManager transactionManager;

		@Nullable
		private final TransactionAttribute transactionAttribute;

		private final String joinpointIdentification;

		@Nullable
		private ReactiveTransaction reactiveTransaction;

		public ReactiveTransactionInfo(@Nullable ReactiveTransactionManager transactionManager,
				@Nullable TransactionAttribute transactionAttribute, String joinpointIdentification) {

			this.transactionManager = transactionManager;
			this.transactionAttribute = transactionAttribute;
			this.joinpointIdentification = joinpointIdentification;
		}

		public ReactiveTransactionManager getTransactionManager() {
			Assert.state(this.transactionManager != null, "No ReactiveTransactionManager set");
			return this.transactionManager;
		}

		@Nullable
		public TransactionAttribute getTransactionAttribute() {
			return this.transactionAttribute;
		}

		/**
		 * Return a String representation of this joinpoint (usually a Method call)
		 * for use in logging.
		 */
		public String getJoinpointIdentification() {
			return this.joinpointIdentification;
		}

		public void newReactiveTransaction(@Nullable ReactiveTransaction transaction) {
			this.reactiveTransaction = transaction;
		}

		@Nullable
		public ReactiveTransaction getReactiveTransaction() {
			return this.reactiveTransaction;
		}

		@Override
		public String toString() {
			return (this.transactionAttribute != null ? this.transactionAttribute.toString() : "No transaction");
		}
	}

}
