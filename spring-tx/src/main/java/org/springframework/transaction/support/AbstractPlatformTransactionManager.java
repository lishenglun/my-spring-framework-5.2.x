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

package org.springframework.transaction.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Constants;
import org.springframework.lang.Nullable;
import org.springframework.transaction.*;
import org.springframework.transaction.interceptor.DelegatingTransactionAttribute;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.List;

/**
 * Abstract base class that implements Spring's standard transaction workflow,
 * serving as basis for concrete platform transaction managers like
 * {@link org.springframework.transaction.jta.JtaTransactionManager}.
 *
 * <p>This base class provides the following workflow handling:
 * <ul>
 * <li>determines if there is an existing transaction;
 * <li>applies the appropriate propagation behavior;
 * <li>suspends and resumes transactions if necessary;
 * <li>checks the rollback-only flag on commit;
 * <li>applies the appropriate modification on rollback
 * (actual rollback or setting rollback-only);
 * <li>triggers registered synchronization callbacks
 * (if transaction synchronization is active).
 * </ul>
 *
 * <p>Subclasses have to implement specific template methods for specific
 * states of a transaction, e.g.: begin, suspend, resume, commit, rollback.
 * The most important of them are abstract and must be provided by a concrete
 * implementation; for the rest, defaults are provided, so overriding is optional.
 *
 * <p>Transaction synchronization is a generic mechanism for registering callbacks
 * that get invoked at transaction completion time. This is mainly used internally
 * by the data access support classes for JDBC, Hibernate, JPA, etc when running
 * within a JTA transaction: They register resources that are opened within the
 * transaction for closing at transaction completion time, allowing e.g. for reuse
 * of the same Hibernate Session within the transaction. The same mechanism can
 * also be leveraged for custom synchronization needs in an application.
 *
 * <p>The state of this class is serializable, to allow for serializing the
 * transaction strategy along with proxies that carry a transaction interceptor.
 * It is up to subclasses if they wish to make their state to be serializable too.
 * They should implement the {@code java.io.Serializable} marker interface in
 * that case, and potentially a private {@code readObject()} method (according
 * to Java serialization rules) if they need to restore any transient state.
 *
 * @author Juergen Hoeller
 * @since 28.03.2003
 * @see #setTransactionSynchronization
 * @see TransactionSynchronizationManager
 * @see org.springframework.transaction.jta.JtaTransactionManager
 */
@SuppressWarnings("serial")
public abstract class AbstractPlatformTransactionManager implements PlatformTransactionManager, Serializable {

	/**
	 * 始终激活事务同步属性，哪怕你是一个空事务（就算你是一个空事务，我也要进行激活）
	 *
	 * Always activate transaction synchronization, even for "empty" transactions
	 * that result from PROPAGATION_SUPPORTS with no existing backend transaction.
	 *
	 * 始终激活事务同步，即使对于由 PROPAGATION_SUPPORTS 产生的没有现有后端事务的“空”事务也是如此。
	 *
	 * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_SUPPORTS
	 * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_NOT_SUPPORTED
	 * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_NEVER
	 */
	public static final int SYNCHRONIZATION_ALWAYS/* 始终同步 */ = 0;

	/**
	 * Activate transaction synchronization only for actual transactions,
	 * that is, not for empty ones that result from PROPAGATION_SUPPORTS with
	 * no existing backend transaction.
	 * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRED
	 * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_MANDATORY
	 * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRES_NEW
	 */
	public static final int SYNCHRONIZATION_ON_ACTUAL_TRANSACTION = 1;

	/**
	 * Never active transaction synchronization, not even for actual transactions.
	 */
	public static final int SYNCHRONIZATION_NEVER = 2;


	/** Constants instance for AbstractPlatformTransactionManager. */
	private static final Constants constants = new Constants(AbstractPlatformTransactionManager.class);


	protected transient Log logger = LogFactory.getLog(getClass());

	private int transactionSynchronization = SYNCHRONIZATION_ALWAYS;

	private int defaultTimeout = TransactionDefinition.TIMEOUT_DEFAULT;

	private boolean nestedTransactionAllowed = false;

	private boolean validateExistingTransaction = false;

	private boolean globalRollbackOnParticipationFailure = true;

	private boolean failEarlyOnGlobalRollbackOnly = false;

	private boolean rollbackOnCommitFailure = false;


	/**
	 * Set the transaction synchronization by the name of the corresponding constant
	 * in this class, e.g. "SYNCHRONIZATION_ALWAYS".
	 * @param constantName name of the constant
	 * @see #SYNCHRONIZATION_ALWAYS
	 */
	public final void setTransactionSynchronizationName(String constantName) {
		setTransactionSynchronization(constants.asNumber(constantName).intValue());
	}

	/**
	 * Set when this transaction manager should activate the thread-bound
	 * transaction synchronization support. Default is "always".
	 * <p>Note that transaction synchronization isn't supported for
	 * multiple concurrent transactions by different transaction managers.
	 * Only one transaction manager is allowed to activate it at any time.
	 * @see #SYNCHRONIZATION_ALWAYS
	 * @see #SYNCHRONIZATION_ON_ACTUAL_TRANSACTION
	 * @see #SYNCHRONIZATION_NEVER
	 * @see TransactionSynchronizationManager
	 * @see TransactionSynchronization
	 */
	public final void setTransactionSynchronization(int transactionSynchronization) {
		this.transactionSynchronization = transactionSynchronization;
	}

	/**
	 * Return if this transaction manager should activate the thread-bound
	 * transaction synchronization support.
	 */
	public final int getTransactionSynchronization() {
		return this.transactionSynchronization;
	}

	/**
	 * Specify the default timeout that this transaction manager should apply
	 * if there is no timeout specified at the transaction level, in seconds.
	 * <p>Default is the underlying transaction infrastructure's default timeout,
	 * e.g. typically 30 seconds in case of a JTA provider, indicated by the
	 * {@code TransactionDefinition.TIMEOUT_DEFAULT} value.
	 * @see org.springframework.transaction.TransactionDefinition#TIMEOUT_DEFAULT
	 */
	public final void setDefaultTimeout(int defaultTimeout) {
		if (defaultTimeout < TransactionDefinition.TIMEOUT_DEFAULT) {
			throw new InvalidTimeoutException("Invalid default timeout", defaultTimeout);
		}
		this.defaultTimeout = defaultTimeout;
	}

	/**
	 * Return the default timeout that this transaction manager should apply
	 * if there is no timeout specified at the transaction level, in seconds.
	 * <p>Returns {@code TransactionDefinition.TIMEOUT_DEFAULT} to indicate
	 * the underlying transaction infrastructure's default timeout.
	 */
	public final int getDefaultTimeout() {
		return this.defaultTimeout;
	}

	/**
	 * Set whether nested transactions are allowed. Default is "false".
	 * <p>Typically initialized with an appropriate default by the
	 * concrete transaction manager subclass.
	 */
	public final void setNestedTransactionAllowed(boolean nestedTransactionAllowed) {
		this.nestedTransactionAllowed = nestedTransactionAllowed;
	}

	/**
	 * Return whether nested transactions are allowed.
	 */
	public final boolean isNestedTransactionAllowed() {
		return this.nestedTransactionAllowed;
	}

	/**
	 * Set whether existing transactions should be validated before participating
	 * in them.
	 * <p>When participating in an existing transaction (e.g. with
	 * PROPAGATION_REQUIRED or PROPAGATION_SUPPORTS encountering an existing
	 * transaction), this outer transaction's characteristics will apply even
	 * to the inner transaction scope. Validation will detect incompatible
	 * isolation level and read-only settings on the inner transaction definition
	 * and reject participation accordingly through throwing a corresponding exception.
	 * <p>Default is "false", leniently ignoring inner transaction settings,
	 * simply overriding them with the outer transaction's characteristics.
	 * Switch this flag to "true" in order to enforce strict validation.
	 * @since 2.5.1
	 */
	public final void setValidateExistingTransaction(boolean validateExistingTransaction) {
		this.validateExistingTransaction = validateExistingTransaction;
	}

	/**
	 * Return whether existing transactions should be validated before participating
	 * in them.
	 *
	 * 返回是否应在参与现有交易之前对其进行验证。
	 *
	 * @since 2.5.1
	 */
	public final boolean isValidateExistingTransaction() {
		return this.validateExistingTransaction;
	}

	/**
	 * Set whether to globally mark an existing transaction as rollback-only
	 * after a participating transaction failed.
	 * <p>Default is "true": If a participating transaction (e.g. with
	 * PROPAGATION_REQUIRED or PROPAGATION_SUPPORTS encountering an existing
	 * transaction) fails, the transaction will be globally marked as rollback-only.
	 * The only possible outcome of such a transaction is a rollback: The
	 * transaction originator <i>cannot</i> make the transaction commit anymore.
	 * <p>Switch this to "false" to let the transaction originator make the rollback
	 * decision. If a participating transaction fails with an exception, the caller
	 * can still decide to continue with a different path within the transaction.
	 * However, note that this will only work as long as all participating resources
	 * are capable of continuing towards a transaction commit even after a data access
	 * failure: This is generally not the case for a Hibernate Session, for example;
	 * neither is it for a sequence of JDBC insert/update/delete operations.
	 * <p><b>Note:</b>This flag only applies to an explicit rollback attempt for a
	 * subtransaction, typically caused by an exception thrown by a data access operation
	 * (where TransactionInterceptor will trigger a {@code PlatformTransactionManager.rollback()}
	 * call according to a rollback rule). If the flag is off, the caller can handle the exception
	 * and decide on a rollback, independent of the rollback rules of the subtransaction.
	 * This flag does, however, <i>not</i> apply to explicit {@code setRollbackOnly}
	 * calls on a {@code TransactionStatus}, which will always cause an eventual
	 * global rollback (as it might not throw an exception after the rollback-only call).
	 * <p>The recommended solution for handling failure of a subtransaction
	 * is a "nested transaction", where the global transaction can be rolled
	 * back to a savepoint taken at the beginning of the subtransaction.
	 * PROPAGATION_NESTED provides exactly those semantics; however, it will
	 * only work when nested transaction support is available. This is the case
	 * with DataSourceTransactionManager, but not with JtaTransactionManager.
	 * @see #setNestedTransactionAllowed
	 * @see org.springframework.transaction.jta.JtaTransactionManager
	 */
	public final void setGlobalRollbackOnParticipationFailure(boolean globalRollbackOnParticipationFailure) {
		this.globalRollbackOnParticipationFailure = globalRollbackOnParticipationFailure;
	}

	/**
	 * Return whether to globally mark an existing transaction as rollback-only
	 * after a participating transaction failed.
	 */
	public final boolean isGlobalRollbackOnParticipationFailure() {
		return this.globalRollbackOnParticipationFailure;
	}

	/**
	 * Set whether to fail early in case of the transaction being globally marked
	 * as rollback-only.
	 * <p>Default is "false", only causing an UnexpectedRollbackException at the
	 * outermost transaction boundary. Switch this flag on to cause an
	 * UnexpectedRollbackException as early as the global rollback-only marker
	 * has been first detected, even from within an inner transaction boundary.
	 * <p>Note that, as of Spring 2.0, the fail-early behavior for global
	 * rollback-only markers has been unified: All transaction managers will by
	 * default only cause UnexpectedRollbackException at the outermost transaction
	 * boundary. This allows, for example, to continue unit tests even after an
	 * operation failed and the transaction will never be completed. All transaction
	 * managers will only fail earlier if this flag has explicitly been set to "true".
	 * @since 2.0
	 * @see org.springframework.transaction.UnexpectedRollbackException
	 */
	public final void setFailEarlyOnGlobalRollbackOnly(boolean failEarlyOnGlobalRollbackOnly) {
		this.failEarlyOnGlobalRollbackOnly = failEarlyOnGlobalRollbackOnly;
	}

	/**
	 * Return whether to fail early in case of the transaction being globally marked
	 * as rollback-only.
	 * @since 2.0
	 */
	public final boolean isFailEarlyOnGlobalRollbackOnly() {
		return this.failEarlyOnGlobalRollbackOnly;
	}

	/**
	 * Set whether {@code doRollback} should be performed on failure of the
	 * {@code doCommit} call. Typically not necessary and thus to be avoided,
	 * as it can potentially override the commit exception with a subsequent
	 * rollback exception.
	 * <p>Default is "false".
	 * @see #doCommit
	 * @see #doRollback
	 */
	public final void setRollbackOnCommitFailure(boolean rollbackOnCommitFailure) {
		this.rollbackOnCommitFailure = rollbackOnCommitFailure;
	}

	/**
	 * Return whether {@code doRollback} should be performed on failure of the
	 * {@code doCommit} call.
	 */
	public final boolean isRollbackOnCommitFailure() {
		return this.rollbackOnCommitFailure;
	}


	//---------------------------------------------------------------------
	// Implementation of PlatformTransactionManager
	//---------------------------------------------------------------------

	/**
	 * 获取事务，得到的是一个TransactionStatus对象，里面包含了：TransactionDefinition、事务对象
	 *
	 * This implementation handles propagation behavior. Delegates to
	 * {@code doGetTransaction}, {@code isExistingTransaction}
	 * and {@code doBegin}.
	 *
	 * 此实现处理传播行为。委托给 {@code doGetTransaction}，{@code isExistingTransaction}和{@code doBegin}
	 *
	 * @see #doGetTransaction
	 * @see #isExistingTransaction
	 * @see #doBegin
	 *
	 * @param definition 			{@link DelegatingTransactionAttribute}，里面包含了RuleBasedTransactionAttribute
	 */
	@Override
	public final TransactionStatus getTransaction(@Nullable TransactionDefinition definition/* 由TransactionAttribute转换而成 */)
			throws TransactionException {

		/* 1、获取TransactionDefinition(事务定义信息) */
		/**
		 * 题外：TransactionDefinition由TransactionAttribute转换而成(TransactionAttribute extends TransactionDefinition)
		 * TransactionDefinition用于获取用户定义的事务信息，因为TransactionDefinition接口中定义了很多可以获取用户定义的事务信息的方法
		 */
		// Use defaults if no transaction definition given. —— 如果没有给出事务定义，则使用默认值
		// 获取当前方法的TransactionDefinition(事务定义)
		// (1)️TransactionAttribute extends TransactionDefinition，所以其实TransactionDefinition也就是TransactionAttribute(事务属性)；
		// (2)如果没有TransactionDefinition，则使用默认的TransactionDefinition
		// 题外：⚠️一般definition不为null，所以def=definition
		// 题外：⚠️TransactionAttribute extends TransactionDefinition，所以获取的TransactionDefinition，是由TransactionAttribute类型转换而成的
		TransactionDefinition def =
				// 看下传入进来的definition是不是为空，不为空，就获取传入进来的definition；为空，就获取默认值
				(definition != null ? definition : TransactionDefinition.withDefaults()/* 默认值 */);

		/* 2、创建事务对象，例如：DataSourceTransactionObject */
		/**
		 * 1、DataSourceTransactionManager#doGetTransaction()
		 *
		 * 创建的事务对象:{@link DataSourceTransactionObject}，里面会去获取本地线程中的连接持有器，设置到DataSourceTransactionObject中
		 *
		 * 2、JpaTransactionManager#doGetTransaction()
		 */
		// 创建事务对象
		Object transaction = doGetTransaction();

		boolean debugEnabled = logger.isDebugEnabled();

		/* 3、判断当前线程中是否已经存在事务 */

		/* 3.1、当前线程中存在事务 */
		// DataSourceTransactionManager判断依据：如果当前线程有连接持有器，并且事务是激活的，就代表当前线程存在事务
		if (isExistingTransaction/* 是现有的事务 */(transaction)) {

			// Existing transaction found -> check propagation behavior to find out how to behave.
			// 上面的翻译：找到现有事务 -> 检查传播行为以了解行为方式。
			// 当前线程已经存在事务，所以直接返回！
			return handleExistingTransaction/* 处理已经存在的事务 */(def, transaction, debugEnabled);
		}

		/* 3.2、当前线程中不存在事务 */

		/* (1)验证事务的超时属性，是否小于-1。事务的超时时间，不能设置为小于-1，如果小于-1，则报错 */
		// Check definition settings for new transaction.
		// 事务超时时间的验证，我们所设置的事务的超时时间不能小于-1，如果小于，就报错
		if (def.getTimeout() < TransactionDefinition.TIMEOUT_DEFAULT/* -1 */) {
			throw new InvalidTimeoutException("Invalid transaction timeout"/* 无效的事务超时时间 */, def.getTimeout());
		}

		// No existing transaction found -> check propagation behavior to find out how to proceed.
		// 上面的翻译：未找到现有事务 -> 检查传播行为以了解如何继续。

		/* (2)判断当前方法的事务传播行为，是不是mandatory，是的话就抛出异常 */
		/**
		 * mandatory：使用当前事务（外层事务），没有的话就抛出异常
		 */
		if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_MANDATORY/* 强制传播 *//* 2 */) {
			// 如果当前线程不存在事务，且传播特性是mandatory，就抛出异常，告诉你必须要有一个事务
			throw new IllegalTransactionStateException(
					"No existing transaction found for transaction marked with propagation 'mandatory'");
		}
		/**
		 * required：当前有事务，就用；没有，就新建
		 * requires_new：无论当前有没有当前事务，我都新建一个新的事务；有当前事务的话，我就将当前事务挂起来
		 * nested：如果有事务，就在这个事务的嵌套事务内运行；如果没有事务，就创建一个新的事物
		 *
		 * 上面三个的共同点就是：如果没有当前事务，就新建一个事务！
		 */
		/* (3)判断当前方法事务的传播行为，是不是required、requires_new、nested其中的一个，是的话就创建新事务 */
		else if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED ||
				def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW ||
				def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {

			/* 挂起空事务（因为这里是不存在事务的处理逻辑，所以事务一定为null，所以挂起空事务） */
			// 没有当前事务的话，REQUIRED、REQUIRES_NEW、NESTED挂起的是空事务，也就是说这个挂起什么事情都没干，所以传入的挂起事务对象是null；然后创建一个新事务
			// 题外：当要挂起一个事务的时候，都需要把那个事务的状态信息给保存起来，方便后续恢复
			SuspendedResourcesHolder suspendedResources = suspend/* 暂停、挂起：不让它继续运行 */(null);
			if (debugEnabled) {
				logger.debug("Creating new transaction with name [" + def.getName() + "]: " + def);
			}
			try {
				/* 开启(创建)一个新的事务 */
				/**
				 * 构造transaction，包括设置ConnectionHolder、隔离级别、timout，如果是新连接，绑定到当前线程
				 */
				// ⚠️开启事务：创建一个新的事务
				return startTransaction(def, transaction, debugEnabled, suspendedResources);
			}
			catch (RuntimeException | Error ex) {
				// 恢复挂起的事务
				resume(null, suspendedResources);
				throw ex;
			}
		}
		/* (4)都不是以上传播特性，则以非事务状态运行 */
		/**
		 * 当前方法事务的传播行为以上都不是，则可能是：supports、not_supports、never，这些传播特性都不需要事务
		 *
		 * supports：有外层事务就用，没有就不用事务
		 * not_supports：不需要事务，如果有外层事务，就将外层事务挂起（当前逻辑线是没有事务的逻辑线，所以不需要挂起任何东西）
		 * never：不需要事务，如果有外层事务，就报错
		 */
		else {
			// Create "empty" transaction: no actual transaction, but potentially synchronization. —— 创建“空”事务：没有实际事务，但可能同步
			if (def.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT && logger.isWarnEnabled()) {
				logger.warn("Custom isolation level specified but no actual transaction initiated; " +
						"isolation level will effectively be ignored: " + def);
			}
			boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);

			// 创建一个事务状态对象(DefaultTransactionStatus)，内部事务对象为空，表示以非事务状态运行
			return prepareTransactionStatus(def, null, true, newSynchronization, debugEnabled, null);
		}
	}

	/**
	 * 开启新的事务
	 *
	 * Start a new transaction.
	 *
	 * @param definition						TransactionDefinition事务定义
	 * @param transaction						新创建的事务对象
	 * @param debugEnabled						debug
	 * @param suspendedResources				挂起的事务资源
	 */
	private TransactionStatus startTransaction(TransactionDefinition definition, Object transaction,
			boolean debugEnabled, @Nullable SuspendedResourcesHolder suspendedResources) {

		/* 1、创建当前新事务的状态对象(DefaultTransactionStatus) */
		// 是否需要同步当前事务状态到本地线程变量的标识
		boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);
		// 创建事务状态对象
		DefaultTransactionStatus status = newTransactionStatus(
				definition, transaction, true/* 是一个新事务 */,
				newSynchronization, debugEnabled,
				suspendedResources);
		/*

		2、之前只是创建事务对象，但是并没有开启事务，这里是正式开启事务：获取新连接，赋予事务对象相关的属性，例如：是否只读、隔离级别、关闭自动提交、标记事务激活、设置事务超时时间

		题外：什么叫做没有开启事务对象？也就是：没有获取新的连接，没有赋予事务对象相关的属性

		*/
		// 根据给定的事务定义开始一个具有语义的新事务。如有必要会获取连接，也就是正宗的开启新事务。
		//（1）如果事务对象中不存在连接持有器，那么就获取新的连接，和构建新的连接持有器对象包装连接，然后把连接持有器设置到事务对象中
		//（2）将TransactionDefinition中的事务属性设置到事务对象中
		//（3）⚠️很重要的一点，如果创建了一个新的连接持有器，则绑定当前线程新获取的连接持有器到当前线程变量中，方便后续嵌套的事务方法复用相同的连接，达到融入外层事务的作用
		doBegin(transaction, definition);

		/*

		3、如果需要，同步当前事务的一些状态到本地线程变量，牵扯的状态有：
		当前事务"是否是实际活跃的事务的标识"、当前事务的"隔离级别"、当前事务的"是否只读标识"、当前事务的"名称"、初始化本地线程变量中的TransactionSynchronization集合

		*/
		prepareSynchronization(status, definition);

		return status;
	}

	/**
	 * 处理已经存在一个事务的情况
	 *
	 * Create a TransactionStatus for an existing transaction. —— 为现有事务创建TransactionStatus
	 */
	private TransactionStatus handleExistingTransaction/* 处理一个已经存在的事务 */(
			TransactionDefinition definition, Object transaction, boolean debugEnabled)
			throws TransactionException {
		/*

		1、判断当前方法的事务传播行为，是不是never。是的话，则抛出异常
		never：不支持事务，如果存在一个事务，就报错。(题外：可以进入到当前方法，就代表存在一个事务了，所以抛出异常！)

		*/
		if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NEVER/* 5=never */) {
			// 抛出异常
			throw new IllegalTransactionStateException(
					"Existing transaction found for transaction marked with propagation 'never'");
		}

		/*

		2、 判断当前方法的事务传播行为，是不是not_supported。是的话，就挂起当前事务，以非事务状态开始运行
		 not_supported：不需要事务，如果存在，就将外层事务挂起

		*/
		if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NOT_SUPPORTED/* 4=not_supported */) {
			if (debugEnabled) {
				logger.debug("Suspending current transaction");
			}

			// 挂起当前事务，返回挂起的事务资源
			Object suspendedResources = suspend(transaction);
			// 是否需要同步当前事务的状态到本地线程变量的标识
			boolean newSynchronization = (getTransactionSynchronization()/* 获取事务同步 */ == SYNCHRONIZATION_ALWAYS);

			// 创建一个事务状态对象(DefaultTransactionStatus)，内部事务对象为空，表示以非事务状态运行
			// (创建一个非事务的状态对象(DefaultTransactionStatus)，表示以非事务状态开始运行)
			// 题外：由于挂起了当前事务，所以DefaultTransactionStatus里面保存了挂起的事务资源（事务的状态属性）
			return prepareTransactionStatus(
					definition, null/* ⚠️表示没有事务 */, false/* ⚠️表示不需要事务 */, newSynchronization, debugEnabled, suspendedResources);
		}

		/*

		3、判断当前方法的事务传播行为，是不是requires_new。是的话，就挂起当前事务，开启一个新的事务执行
		requires_new：不需要外层事务，如果存在，就将外层事务挂起；自己新建一个事务

		*/
		if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW/* 3=requires_new */) {
			if (debugEnabled) {
				logger.debug("Suspending current transaction, creating new transaction with name [" +
						definition.getName() + "]");
			}
			// 挂起当前事务，返回挂起的事务资源持有器，用于获取"挂起的事务"
			SuspendedResourcesHolder suspendedResources = suspend(transaction);
			try {
				// ⚠️开启一个新事务：
				// 创建一个事务状态对象(DefaultTransactionStatus)，内部事务对象为新创建的事务对象，并正式获取连接，表示开启一个新的事务执行
				return startTransaction(definition, transaction, debugEnabled, suspendedResources);
			}
			catch (RuntimeException | Error beginEx) {
				resumeAfterBeginException(transaction, suspendedResources, beginEx);
				throw beginEx;
			}
		}

		/* 4、判断当前方法的事务传播行为，是不是nested(嵌套事务)。是的话，就设置保存点，然后还是以当前事务状态继续执行 */
		if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED/* 6=nested */) {
			/* 嵌套事务的处理 */

			// 不允许就报异常
			// 题外：在DataSourceTransactionManager#doGetTransaction()中也有调用isNestedTransactionAllowed()，用于设置是否运行当前事务增加保存点
			if (!isNestedTransactionAllowed()) {
				throw new NestedTransactionNotSupportedException(
						"Transaction manager does not allow nested transactions by default - " +
						"specify 'nestedTransactionAllowed' property with value 'true'");
			}

			if (debugEnabled) {
				logger.debug("Creating nested transaction with name [" + definition.getName() + "]");
			}

			/* 如果没有可以使用保存点的方式控制事务回滚，那么在嵌入式事务的建立初始，建立保存点 */
			if (useSavepointForNestedTransaction()/* 使用保存点，对于Nested传播行为的事务 */) {
				// Create savepoint within existing Spring-managed transaction,
				// through the SavepointManager API implemented by TransactionStatus.
				// Usually uses JDBC 3.0 savepoints. Never activates Spring synchronization.
				// 上面的翻译：通过TransactionStatus实现的SavepointManager API，在现有Spring管理的事务中创建保存点。通常使用JDBC 3.0保存点。从不激活Spring同步。

				// 创建一个事务状态对象(DefaultTransactionStatus)，内部事务对象为新创建的事务对象，不过被标识为非新事务，然后为当前事务创建一个保存点，并且设置这个保存点到事务状态对象中
				DefaultTransactionStatus status =
						prepareTransactionStatus(definition, transaction, false, false, debugEnabled, null);
				// 为事务设置一个回退点
				// ⚠️创建了一个保存点，保存一下现在存在的状态，然后以当前事务状态继续运行
				status.createAndHoldSavepoint/* 创建并保存保存点 */();

				return status;
			}
			/* 有些情况是不能使用保存点操作，比如 JTA，那么建立新事务 */
			else {
				// Nested transaction through nested begin and commit/rollback calls.
				// Usually only for JTA: Spring synchronization might get activated here
				// in case of a pre-existing JTA transaction.
				// 上面翻译：通过嵌套的begin和commitrollback调用嵌套事务。通常仅适用于JTA：如果存在预先存在的JTA事务，则可能会在此处激活Spring同步。

				return startTransaction(definition, transaction, debugEnabled, null);
			}
		}

		// Assumably PROPAGATION_SUPPORTS or PROPAGATION_REQUIRED.
		if (debugEnabled) {
			logger.debug("Participating in existing transaction");
		}

		/* 5、验证当前存在的事务。没问题的话，还是以当前的事务状态继续执行下去 */

		// 判断是否验证当前事务
		if (isValidateExistingTransaction()/* 是否验证当前事务 */) {
			if (definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT/* 默认隔离级别 */) {
				Integer currentIsolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
				if (currentIsolationLevel == null || currentIsolationLevel != definition.getIsolationLevel()) {
					Constants isoConstants = DefaultTransactionDefinition.constants;
					throw new IllegalTransactionStateException("Participating transaction with definition [" +
							definition + "] specifies isolation level which is incompatible with existing transaction: " +
							(currentIsolationLevel != null ?
									isoConstants.toCode(currentIsolationLevel, DefaultTransactionDefinition.PREFIX_ISOLATION) :
									"(unknown)"));
				}
			}
			if (!definition.isReadOnly()) {
				if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
					throw new IllegalTransactionStateException("Participating transaction with definition [" +
							definition + "] is not marked as read-only but existing transaction is");
				}
			}
		}
		boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);

		/* 上面验证通过，以当前的事务状态继续执行下去 */

		// 创建一个事务状态对象(DefaultTransactionStatus)，内部事务对象为新创建的事务对象，不过被标识为非新事务
		// 当前这行代码只改变了一个状态：newTransaction=false，不是一个新事务，所以设置为false！
		// 跟外层的TransactionStatus一样，用的就是之前的，只不过把当前这个事务在进行运行的时候，我把你对应的一些状态信息进行了调整，我改了状态信息了
		return prepareTransactionStatus(definition, transaction, false/* 标记当前使用的事务不是一个新的事务 */,
				newSynchronization, debugEnabled, null/* 因为不需要挂起，所以是null */);
	}

	/**
	 * 创建事务状态对象，以及根据需要，同步当前事务的一些状态到本地线程变量，牵扯的状态有：
	 * 当前事务"是否是实际活跃的事务的标识"、当前事务的"隔离级别"、当前事务的"是否只读标识"、当前事务的"名称"、初始化本地线程变量中的TransactionSynchronization集合
	 *
	 * Create a new TransactionStatus for the given arguments,
	 * also initializing transaction synchronization as appropriate.
	 * @see #newTransactionStatus
	 * @see #prepareTransactionStatus
	 *
	 * @param definition				TransactionDefinition事务定义
	 * @param transaction				事务对象(有可能为null，代表没有事务)
	 * @param newTransaction			传入进来的事务，是否是一个新事务？（true：是，false：️表示不需要事务）
	 * @param newSynchronization		是否需要同步当前事务状态到本地线程变量
	 * @param debug						debug
	 * @param suspendedResources		被挂起的事务
	 */
	protected final DefaultTransactionStatus prepareTransactionStatus(
			TransactionDefinition definition, @Nullable Object transaction, boolean newTransaction,
			boolean newSynchronization, boolean debug, @Nullable Object suspendedResources) {

		/* ⚠️1、创建事务状态对象 */
		/**
		 * 1、TransactionStatus(事务状态对象)作用：获取事务的状态，决定了方法事务的运行方式，例如是以事务方式运行，还是非事务方式运行、是以什么隔离级别运行事务
		 */
		DefaultTransactionStatus status = newTransactionStatus(
				definition, transaction, newTransaction, newSynchronization, debug, suspendedResources);

		/*

		⚠️2、如果需要，同步当前事务的一些状态到本地线程变量，牵扯的状态有：
		当前事务"是否是实际活跃的事务的标识"、当前事务的"隔离级别"、当前事务的"是否只读标识"、当前事务的"名称"、初始化本地线程变量中的TransactionSynchronization集合

		*/
		prepareSynchronization(status, definition);

		return status;
	}

	/**
	 * 通过给定的参数创建事务状态对象（DefaultTransactionStatus）
	 *
	 * 有一个非常关键的参数是newTransaction，用于判断是否是新事务新连接。如果事务不存在，那么肯定是true；如果存在，就需要根据传播特性来决定是否是false了
	 *
	 * Create a TransactionStatus instance for the given arguments.
	 *
	 * @param definition				TransactionDefinition事务定义
	 * @param transaction				事务对象(有可能为null，代表没有事务)
	 * @param newTransaction			传入进来的事务，是不是一个新事务？（true：是，false：不是）
	 * @param newSynchronization		是否需要同步当前事务状态到本地线程变量
	 * @param debug						debug
	 * @param suspendedResources		被挂起的事务
	 */
	protected DefaultTransactionStatus newTransactionStatus(
			TransactionDefinition definition, @Nullable Object transaction, boolean newTransaction,
			boolean newSynchronization, boolean debug, @Nullable Object suspendedResources) {

		// 是否需要新同步，只要有新同步且当前无同步激活事务
		boolean actualNewSynchronization/* 实际的新同步 */ = newSynchronization &&
				// 判断当前线程的"事务同步"是不是处于活动状态
				/**
				 * 当前线程变量synchronizations ThreadLocal里面的事务同步信息不为nul，返回true，代表当前线程的"事务同步"处于活动状态，也就是说，当前线程在进行事务同步！
				 * true取反为false，也就是说，既然当前线程在进行事务同步，那么这个事务就不进行事务同步了！
				 *
				 * 题外：如果外层事务在进行事务同步，那么当前线程就是在进行事务同步，如果内层方法用的还是外层事务，由于外层事务已经在同步了，同步过了，
				 * 所以actualNewSynchronization改为false，表示内层方法(当前方法)的事务就不需要进行同步了！
				 */
				!TransactionSynchronizationManager.isSynchronizationActive();

		// DefaultTransactionStatus：保存事务所需要的状态信息
		return new DefaultTransactionStatus(
				transaction, newTransaction, actualNewSynchronization,
				definition.isReadOnly(), debug, suspendedResources);
	}

	/**
	 * 如果需要，同步(保存)当前事务的一些状态到本地线程变量，牵扯的状态有：当前事务"是否是实际活跃的事务的标识"、当前事务的"隔离级别"、当前事务的"是否只读标识"、当前事务的"名称"、初始化本地线程变量中的TransactionSynchronization集合
	 *
	 * Initialize transaction synchronization as appropriate. —— 根据需要初始化事务同步
	 */
	protected void prepareSynchronization/* 准备同步 */(DefaultTransactionStatus status, TransactionDefinition definition) {
		// 判断是否需要同步当前事务状态到本地线程变量
		if (status.isNewSynchronization()) {
			/* 只有一个新事务的时候，且需要进行同步操作的时候，才会绑定对应ThreadLocal的属性！ */
			/* 将当前事务的一些相关信息绑定到ThreadLocal里面去 */

			// 设置当前事务"是否是实际活跃的事务的标识"到本地线程变量(actualTransactionActive ThreadLocal)，可用于判断当前事务是否已经激活
			TransactionSynchronizationManager.setActualTransactionActive(status.hasTransaction());
			// 设置当前事务的"隔离级别"到本地线程变量(currentTransactionIsolationLevel ThreadLocal)
			TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(
					definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT ?
							definition.getIsolationLevel() : null);
			// 设置当前事务的"是否只读标识"到本地线程变量(currentTransactionReadOnly ThreadLocal)
			TransactionSynchronizationManager.setCurrentTransactionReadOnly(definition.isReadOnly());
			// 设置当前事务的"名称"到本地线程变量(currentTransactionName ThreadLocal)
			TransactionSynchronizationManager.setCurrentTransactionName(definition.getName());
			// 初始化本地线程变量(synchronizations ThreadLocal)中的TransactionSynchronization集合
			TransactionSynchronizationManager.initSynchronization();
		}
	}

	/**
	 * Determine the actual timeout to use for the given definition.
	 * Will fall back to this manager's default timeout if the
	 * transaction definition doesn't specify a non-default value.
	 * @param definition the transaction definition
	 * @return the actual timeout to use
	 * @see org.springframework.transaction.TransactionDefinition#getTimeout()
	 * @see #setDefaultTimeout
	 */
	protected int determineTimeout(TransactionDefinition definition) {
		if (definition.getTimeout() != TransactionDefinition.TIMEOUT_DEFAULT) {
			return definition.getTimeout();
		}
		return getDefaultTimeout();
	}


	/**
	 * 挂起事务（⚠️挂起事务的目的是为了，记录原有事务的状态；在当前事务执行完毕后，再将原事务还原）
	 *
	 * Suspend the given transaction. Suspends transaction synchronization first,
	 * then delegates to the {@code doSuspend} template method.
	 * @param transaction the current transaction object
	 * (or {@code null} to just suspend active synchronizations, if any)
	 * @return an object that holds suspended resources
	 * (or {@code null} if neither transaction nor synchronization active)
	 * @see #doSuspend
	 * @see #resume
	 */
	@Nullable
	protected final SuspendedResourcesHolder suspend(@Nullable Object transaction) throws TransactionException {
		// 判断当前线程中，是否存在激活了同步操作的事务。其实也就是判断有没有激活的事务
		// 有激活的事务，就需要清空线程变量
		if (TransactionSynchronizationManager.isSynchronizationActive()/* 是否同步活跃 */) {
			// ⚠️
			List<TransactionSynchronization> suspendedSynchronizations = doSuspendSynchronization();
			try {
				// 挂起的资源。其实也就是一个连接持有器！
				Object suspendedResources = null;
				if (transaction != null) {
					// 从resources ThreadLocal里面移除数据源对应的连接持有器，并返回这个移除的连接持有器
					// 解除当前线程里面数据源和连接持有器的对应关系，并且把它从resources ThreadLocal变量里面移除掉！禁止我下次拿过来直接复用，下次再有事务进来的时候，就获取不到了
					suspendedResources = doSuspend(transaction);
				}

				// 获取当前事务的名称
				String name = TransactionSynchronizationManager.getCurrentTransactionName();
				// 清空线程变量中"当前事务的名称"
				TransactionSynchronizationManager.setCurrentTransactionName(null);

				// 获取出是否只读事务的标识
				boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
				// 清空线程变量中"是否只读事务的标识"
				TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

				// 获取事务的隔离级别
				Integer isolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
				// 清空线程变量中"事务的隔离级别"
				TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);

				// 获取"是否是实际活跃的事务的标识（事务是否是激活状态的标识）"
				boolean wasActive = TransactionSynchronizationManager.isActualTransactionActive/* 是实际事务活跃 */();
				// 清空线程变量中"是否是实际活跃的事务的标识"
				TransactionSynchronizationManager.setActualTransactionActive(false);


				// 把上述从线程变量中获取出来的，存在事务属性，封装为挂起的事务属性并返回出去
				// 方便我后续创建一个新连接

				// 上面是清空，这里是保存上一次清空的值，用于后续的恢复！
				// ⚠️保存挂起的事务状态值，只要把这几个状态值给恢复，我就能够恢复当前挂起的事务的运行状态
				return new SuspendedResourcesHolder/* 包装挂起的资源，也就是保存挂起的资源！ */(
						suspendedResources, suspendedSynchronizations, name, readOnly, isolationLevel, wasActive);
			}
			catch (RuntimeException | Error ex) {
				// doSuspend failed - original transaction is still active...
				doResumeSynchronization(suspendedSynchronizations);
				throw ex;
			}
		}
		else if (transaction != null) {
			// Transaction active but no synchronization active. —— 事务活动但没有同步活动。
			Object suspendedResources = doSuspend(transaction);
			return new SuspendedResourcesHolder(suspendedResources);
		}
		else {
			// Neither transaction nor synchronization active.
			return null;
		}
	}

	/**
	 * Resume the given transaction. Delegates to the {@code doResume}
	 * template method first, then resuming transaction synchronization.
	 * @param transaction the current transaction object
	 * @param resourcesHolder the object that holds suspended resources,
	 * as returned by {@code suspend} (or {@code null} to just
	 * resume synchronizations, if any)
	 * @see #doResume
	 * @see #suspend
	 */
	protected final void resume(@Nullable Object transaction, @Nullable SuspendedResourcesHolder resourcesHolder)
			throws TransactionException {

		if (resourcesHolder != null) {
			// 挂起的事务
			Object suspendedResources = resourcesHolder.suspendedResources;
			if (suspendedResources != null) {
				// 恢复数据源和连接持有器的绑定关系到resources ThreadLocal里面去
				doResume(transaction, suspendedResources);
			}
			// 事务同步信息
			List<TransactionSynchronization> suspendedSynchronizations = resourcesHolder.suspendedSynchronizations;
			// 如果事务同步信息不为null
			if (suspendedSynchronizations != null) {
				// 恢复挂起的事务信息到ThreadLocal里面去！

				TransactionSynchronizationManager.setActualTransactionActive(resourcesHolder.wasActive);
				TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(resourcesHolder.isolationLevel);
				TransactionSynchronizationManager.setCurrentTransactionReadOnly(resourcesHolder.readOnly);
				TransactionSynchronizationManager.setCurrentTransactionName(resourcesHolder.name);
				doResumeSynchronization(suspendedSynchronizations);
			}
		}
	}

	/**
	 * Resume outer transaction after inner transaction begin failed.
	 */
	private void resumeAfterBeginException(
			Object transaction, @Nullable SuspendedResourcesHolder suspendedResources, Throwable beginEx) {

		try {
			resume(transaction, suspendedResources);
		}
		catch (RuntimeException | Error resumeEx) {
			String exMessage = "Inner transaction begin exception overridden by outer transaction resume exception";
			logger.error(exMessage, beginEx);
			throw resumeEx;
		}
	}

	/**
	 * Suspend all current synchronizations and deactivate transaction
	 * synchronization for the current thread.
	 * @return the List of suspended TransactionSynchronization objects
	 */
	private List<TransactionSynchronization> doSuspendSynchronization() {
		List<TransactionSynchronization> suspendedSynchronizations =
				TransactionSynchronizationManager.getSynchronizations();
		for (TransactionSynchronization synchronization : suspendedSynchronizations) {
			synchronization.suspend();
		}
		// 清空当前线程里面所有的事务同步信息！
		TransactionSynchronizationManager.clearSynchronization();
		return suspendedSynchronizations;
	}

	/**
	 * Reactivate transaction synchronization for the current thread
	 * and resume all given synchronizations.
	 * @param suspendedSynchronizations a List of TransactionSynchronization objects
	 */
	private void doResumeSynchronization(List<TransactionSynchronization> suspendedSynchronizations) {
		TransactionSynchronizationManager.initSynchronization();
		for (TransactionSynchronization synchronization : suspendedSynchronizations) {
			synchronization.resume();
			TransactionSynchronizationManager.registerSynchronization(synchronization);
		}
	}


	/**
	 * This implementation of commit handles participating in existing
	 * transactions and programmatic rollback requests.
	 * Delegates to {@code isRollbackOnly}, {@code doCommit}
	 * and {@code rollback}.
	 * @see org.springframework.transaction.TransactionStatus#isRollbackOnly()
	 * @see #doCommit
	 * @see #rollback
	 */
	@Override
	public final void commit(TransactionStatus status) throws TransactionException {
		// 判断当前事务是否已经完成，防止多次提交！
		if (status.isCompleted()) {
			throw new IllegalTransactionStateException(
					// 事务已完成 - 每个事务不要多次调用提交或回滚
					"Transaction is already completed - do not call commit or rollback more than once per transaction");
		}

		/* 如果回滚标识为true，则直接回滚 */
		/**
		 * 事务异常回滚逻辑中，当某个事务既没有保存点又不是新事务，Spring对它的处理方式只是设置一个回滚标识，
		 * 这个回滚标识在这里就会派上用场了，主要的应用场景如下：
		 * 某个事务是另一个事务的嵌人事务，但是，这些事务又不在Spring的管理范围内，或者无法设置保存点，那么Spring会通过设置回滚标识的方式来禁止提交。
		 * 首先当某个嵌人事务发生异常回滚的时候会设置回滚标识，而等到外部事务提交时，一旦判断出当前事务流中被设置了回滚标识，则由外部事务来统一进行整体事务的回滚。
		 * 所以，当事务没有被异常捕获的时候，并不意味着一定会真正提交
		 */
		// 如果存在事务链中，已经被标记回滚，那么不会尝试提交事务，直接回滚
		DefaultTransactionStatus defStatus = (DefaultTransactionStatus) status;
		if (defStatus.isLocalRollbackOnly()) {
			if (defStatus.isDebug()) {
				logger.debug("Transactional code has requested rollback");
			}
			// 不可预期的回滚
			processRollback(defStatus, false);
			return;
		}

		// 设置了全局回滚
		if (!shouldCommitOnGlobalRollbackOnly() && defStatus.isGlobalRollbackOnly()/* ⚠️通过连接持有器判断连接是不是需要回滚的！ */) {
			if (defStatus.isDebug()) {
				// 全局事务被标记为仅回滚但事务代码请求提交
				logger.debug("Global transaction is marked as rollback-only but transactional code requested commit");
			}
			// ⚠️回滚
			processRollback(defStatus, true);
			return;
		}

		// 处理事务提交
		processCommit(defStatus);
	}

	/**
	 * 处理提交，先处理保存点，然后处理新事务，如果不是新事务不会真正提交，要等外层是新事务的才会提交；
	 * 最后根据条件执行数据清除，线程的私有资源解绑，重置连接自动提交，隔离级别，是否只读，释放连接，恢复挂起事务等
	 *
	 * Process an actual commit.
	 * Rollback-only flags have already been checked and applied.
	 * @param status object representing the transaction
	 * @throws TransactionException in case of commit failure
	 */
	private void processCommit(DefaultTransactionStatus status) throws TransactionException {
		try {
			boolean beforeCompletionInvoked = false;

			try {
				boolean unexpectedRollback = false;
				// 钩子方法
				prepareForCommit(status);

				/* TransactionSynchronization#beforeCommit */
				triggerBeforeCommit/* commit前触发的操作 */(status);

				/* TransactionSynchronization#beforeCompletion */
				// 提交完成前回调
				triggerBeforeCompletion/* commit完成前触发的操作 */(status);


				beforeCompletionInvoked = true;

				/**
				 * 在提交过程中也并不是直接提交的，而是考虑了诸多的方面，符合提交的条件如下：
				 *
				 * （1）当事务状态中有保存点信息的话便不会去提交事务。
				 * （2）当事务非新事务的时候也不会去执行提交事务操作。
				 *
				 * 此条件主要考虑内嵌事务的情况，对于内嵌事务，在 Spring 中正常的处理方式是将内嵌事务开始之前设置保存点，
				 * 一旦内嵌事务出现异常便根据保存点信息进行回滚，但是如果没有出现异常，内嵌事务并不会单独提交，
				 * 而是根据事务流由最外层事务负责提交，所以如果当前存在保存点信息便不是最外层事务，不做保存操作，对于是否是新事务的判断也是基于此考虑。
				 */

				// 是否有保存点
				if (status.hasSavepoint()) {
					if (status.isDebug()) {
						logger.debug("Releasing transaction savepoint");
					}
					// 是否有全局回滚标记
					unexpectedRollback = status.isGlobalRollbackOnly();
					// 如果存在保存点，则清除保存点信息
					status.releaseHeldSavepoint/* 释放保持的保存点 */();
				}
				// 当前事务是一个新事务
				else if (status.isNewTransaction()) {
					if (status.isDebug()) {
						logger.debug("Initiating transaction commit");
					}
					unexpectedRollback = status.isGlobalRollbackOnly();
					// ⚠️如果是独立的事务则直接提交
					doCommit(status);
				}
				else if (isFailEarlyOnGlobalRollbackOnly()) {
					unexpectedRollback = status.isGlobalRollbackOnly();
				}

				// Throw UnexpectedRollbackException if we have a global rollback-only
				// marker but still didn't get a corresponding exception from commit.

				// 有全局回滚标记就报异常
				if (unexpectedRollback) {
					throw new UnexpectedRollbackException(
							"Transaction silently rolled back because it has been marked as rollback-only");
				}
			}
			catch (UnexpectedRollbackException ex) {
				// can only be caused by doCommit
				triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);
				throw ex;
			}
			catch (TransactionException ex) {
				// can only be caused by doCommit
				if (isRollbackOnCommitFailure()) {
					doRollbackOnCommitException(status, ex);
				}
				else {
					triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
				}
				throw ex;
			}
			catch (RuntimeException | Error ex) {

				/* TransactionSynchronization#beforeCompletion */
				if (!beforeCompletionInvoked) {
					triggerBeforeCompletion(status);
				}

				// 提交过程中出现异常则回滚
				doRollbackOnCommitException(status, ex);
				throw ex;
			}

			// Trigger afterCommit callbacks, with an exception thrown there
			// propagated to callers but the transaction still considered as committed.
			try {
				/* TransactionSynchronization#afterCommit */
				// 提交后触发的操作
				triggerAfterCommit(status);
			}
			finally {
				/* TransactionSynchronization#afterCompletion */
				// 提交后清除线程私有同步状态
				triggerAfterCompletion(status, TransactionSynchronization.STATUS_COMMITTED);
			}

		}
		finally {
			// commit完成后触发的操作
			// 根据条件，完成后数据清除，和线程的私有资源解绑，重置连接自动提交，隔离级别，是否只读，释放连接，恢复挂起事务等
			cleanupAfterCompletion(status);
		}
	}

	/**
	 * 事务管理器根据事务状态来处理回滚
	 *
	 * This implementation of rollback handles participating in existing
	 * transactions. Delegates to {@code doRollback} and
	 * {@code doSetRollbackOnly}.
	 * @see #doRollback
	 * @see #doSetRollbackOnly
	 */
	@Override
	public final void rollback(TransactionStatus status) throws TransactionException {
		// 判断事务是不是完成了，防止多次提交/回滚
		// 如果事务已经完成，那么再次回滚会抛出异常
		if (status.isCompleted()) {
			throw new IllegalTransactionStateException(
					"Transaction is already completed - do not call commit or rollback more than once per transaction");
		}

		DefaultTransactionStatus defStatus = (DefaultTransactionStatus) status;

		// ⚠️回滚
		processRollback(defStatus, false);
	}

	/**
	 * unexpected这个一般是false. 除非是设置rollback-only=true.才是true, 表示是全局的回滚标记。首先会进行回滚前回调，
	 * 然后判断是否设置了保存点，比如NESTED会设置， 要先回滚到保存点。
	 * 如果状态是新的事务， 那就进行回浪，如果不是新的，就设置一个回滚标记。
	 * 内部是设置连接持有器回滚标记。然后执行回滚完成回调，根据事务状态信息，完成后数据清除，和线程的私有资源解练，重置连接自动提交，隔离级别，是否只读，释放连接，恢复挂起事务等
	 *
	 *
	 * Process an actual rollback.
	 * The completed flag has already been checked.
	 * @param status object representing the transaction
	 * @throws TransactionException in case of rollback failure
	 */
	private void processRollback(DefaultTransactionStatus status, boolean unexpected) {
		try {
			// 意外的回滚
			boolean unexpectedRollback = unexpected;

			try {
				/* 1、TransactionSynchronization#beforeCompletion() */
				// 回滚完成前触发
				// 激活所有 TransactionSynchronization 中对应的方法
				triggerBeforeCompletion(status);

				/* 2、回滚 */

				/* (1)有保存点，则回滚到保存点 */
				// 当之前已经保存的事务信息中有保存点信息的时候，使用保存点信息进行回滚。常用于嵌入式事务，对于嵌入式的事务的处理，内嵌的事务异常并不会引起外部事务的回滚。
				if (status.hasSavepoint()) {
					if (status.isDebug()) {
						logger.debug("Rolling back transaction to savepoint");
					}
					// 回滚到保存点！
					status.rollbackToHeldSavepoint();
				}
				/* (2)没有保存点，是一个新事务，则直接回滚 */
				// 当之前已经保存的事务信息中的事务为新事务，那么直接回滚。常用于单独事务的处理。
				else if (status.isNewTransaction()) {
					if (status.isDebug()) {
						logger.debug("Initiating transaction rollback");
					}
					// ⚠️如果当前事务为独立的新事务，则直接回退
					doRollback(status);
				}
				/* (3)既没有保存点，也不是新事务，则设置一个回滚标识，等到外层事务，提交的时候，统一回滚 */
				else {
					/**
					 * 当前事务信息中表明是存在事务的，又不属于以上两种情况，多数用于JTA，只做回滚标识，等到提交的时候统一不提交。
					 *
					 * 当某个事务既没有保存点又不是新事务，Spring 对它的处理方式只是设置一个回滚标识
					 *
					 * 某个事务是另一个事务的嵌人事务，但是，这些事务又不在 Spring 的管理范围内，或者无法设置保存点，那么 Spring 会通过设置回滚标识的方式来禁止提交。
					 * 首先当某个嵌人事务发生回滚的时候会设置回滚标识，而等到外部事务提交时，一旦判断出当前事务流被设置了回滚标识，则由外部事务来统一进行整体事务的回滚。
					 */
					// Participating in larger transaction —— 参与更大的事务
					// 判断有没有事务
					if (status.hasTransaction()) {
						if (status.isLocalRollbackOnly() || isGlobalRollbackOnParticipationFailure()) {
							if (status.isDebug()) {
								logger.debug("Participating transaction failed - marking existing transaction as rollback-only");
							}
							// 标记当前事务连接需要回滚，也就是全局回滚
							// 将rollbackOnly改为true
							// 如果当前事务不是独立的事务，那么只能标记状态，等到事务链执行完华后统一回滚
							doSetRollbackOnly(status);
						}
						else {
							if (status.isDebug()) {
								logger.debug("Participating transaction failed - letting transaction originator decide on rollback");
							}
						}
					}
					else {
						logger.debug("Should roll back transaction but cannot - no transaction available");
					}

					// Unexpected rollback only matters here if we're asked to fail early —— 只有当我们被要求提前失败时，意外的回滚才有意义
					if (!isFailEarlyOnGlobalRollbackOnly()) {
						// 是否发现了直接不可预期的回滚状态
						unexpectedRollback = false;
					}

				}
			}
			catch (RuntimeException | Error ex) {
				triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
				throw ex;
			}

			// 回滚完成后触发
			triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);

			// Raise UnexpectedRollbackException if we had a global rollback-only marker
			if (unexpectedRollback/* 意外回滚：不可预期的异常操作 */) {
				// ⚠️
				throw new UnexpectedRollbackException(
						"Transaction rolled back because it has been marked as rollback-only"/* 事务回滚，因为它已被标记为仅回滚 */);
			}
		}
		/* 回滚后的信息清除 */
		finally {
			// ⚠️清空记录的资源，并将挂起的资源恢复
			// 根据事务状态信息，完成数据清除，和线程的私有资源解绑，重置连接自动提交，隔离级别，是否只读，释放连接，恢复挂起事务等
			cleanupAfterCompletion(status);
		}
	}

	/**
	 * Invoke {@code doRollback}, handling rollback exceptions properly.
	 * @param status object representing the transaction
	 * @param ex the thrown application exception or error
	 * @throws TransactionException in case of rollback failure
	 * @see #doRollback
	 */
	private void doRollbackOnCommitException(DefaultTransactionStatus status, Throwable ex) throws TransactionException {
		try {
			if (status.isNewTransaction()) {
				if (status.isDebug()) {
					logger.debug("Initiating transaction rollback after commit exception", ex);
				}
				doRollback(status);
			}
			else if (status.hasTransaction() && isGlobalRollbackOnParticipationFailure()) {
				if (status.isDebug()) {
					logger.debug("Marking existing transaction as rollback-only after commit exception", ex);
				}
				doSetRollbackOnly(status);
			}
		}
		catch (RuntimeException | Error rbex) {
			logger.error("Commit exception overridden by rollback exception", ex);
			triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
			throw rbex;
		}
		triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);
	}


	/**
	 * Trigger {@code beforeCommit} callbacks.
	 * @param status object representing the transaction
	 */
	protected final void triggerBeforeCommit(DefaultTransactionStatus status) {
		// 判断是不是一个新的同步状态
		if (status.isNewSynchronization()) {
			if (status.isDebug()) {
				logger.trace("Triggering beforeCommit synchronization");
			}
			TransactionSynchronizationUtils.triggerBeforeCommit(status.isReadOnly());
		}
	}

	/**
	 * Trigger {@code beforeCompletion} callbacks.
	 * @param status object representing the transaction
	 */
	protected final void triggerBeforeCompletion(DefaultTransactionStatus status) {
		if (status.isNewSynchronization()) {
			if (status.isDebug()) {
				logger.trace("Triggering beforeCompletion synchronization");
			}
			TransactionSynchronizationUtils.triggerBeforeCompletion();
		}
	}

	/**
	 * Trigger {@code afterCommit} callbacks.
	 * @param status object representing the transaction
	 */
	private void triggerAfterCommit(DefaultTransactionStatus status) {
		if (status.isNewSynchronization()) {
			if (status.isDebug()) {
				logger.trace("Triggering afterCommit synchronization");
			}
			TransactionSynchronizationUtils.triggerAfterCommit();
		}
	}

	/**
	 * Trigger {@code afterCompletion} callbacks.
	 * @param status object representing the transaction
	 * @param completionStatus completion status according to TransactionSynchronization constants
	 */
	private void triggerAfterCompletion(DefaultTransactionStatus status, int completionStatus) {
		if (status.isNewSynchronization()) {
			List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
			TransactionSynchronizationManager.clearSynchronization();
			if (!status.hasTransaction() || status.isNewTransaction()) {
				if (status.isDebug()) {
					logger.trace("Triggering afterCompletion synchronization");
				}
				// No transaction or new transaction for the current scope ->
				// invoke the afterCompletion callbacks immediately
				invokeAfterCompletion(synchronizations, completionStatus);
			}
			else if (!synchronizations.isEmpty()) {
				// Existing transaction that we participate in, controlled outside
				// of the scope of this Spring transaction manager -> try to register
				// an afterCompletion callback with the existing (JTA) transaction.
				registerAfterCompletionWithExistingTransaction(status.getTransaction(), synchronizations);
			}
		}
	}

	/**
	 * Actually invoke the {@code afterCompletion} methods of the
	 * given Spring TransactionSynchronization objects.
	 * <p>To be called by this abstract manager itself, or by special implementations
	 * of the {@code registerAfterCompletionWithExistingTransaction} callback.
	 * @param synchronizations a List of TransactionSynchronization objects
	 * @param completionStatus the completion status according to the
	 * constants in the TransactionSynchronization interface
	 * @see #registerAfterCompletionWithExistingTransaction(Object, java.util.List)
	 * @see TransactionSynchronization#STATUS_COMMITTED
	 * @see TransactionSynchronization#STATUS_ROLLED_BACK
	 * @see TransactionSynchronization#STATUS_UNKNOWN
	 */
	protected final void invokeAfterCompletion(List<TransactionSynchronization> synchronizations, int completionStatus) {
		TransactionSynchronizationUtils.invokeAfterCompletion(synchronizations, completionStatus);
	}

	/**
	 * 完成后清理
	 *
	 * Clean up after completion, clearing synchronization if necessary,
	 * and invoking doCleanupAfterCompletion.
	 * @param status object representing the transaction
	 * @see #doCleanupAfterCompletion
	 */
	private void cleanupAfterCompletion(DefaultTransactionStatus status) {
		/* 设置当前事务为完成状态，以避免重复调用 */
		status.setCompleted();

		/* 如果当前事务是新的同步状态，需要将绑定到当前线程的事务信息清除 */
		if (status.isNewSynchronization()) {
			// 线程同步状态清除
			TransactionSynchronizationManager.clear();
		}

		/* 如果是新事务需要做些清除资源的工作 */
		// 如果是新事务的话，进行数据清除，线程的私有资源解绑，重置连接自动提交，隔离级别，是否只读，释放连接等
		if (status.isNewTransaction()) {
			doCleanupAfterCompletion(status.getTransaction());
		}

		/* 有挂起的事务，则恢复 */
		// 判断是不是有挂起的事务，有挂起的事务要恢复
		if (status.getSuspendedResources() != null) {
			if (status.isDebug()) {
				logger.debug("Resuming suspended transaction after completion of inner transaction");
			}
			Object transaction = (status.hasTransaction() ? status.getTransaction() : null);
			// 结束之前事务的挂起状态：恢复之前挂起的事务
			// 如果在事务执行前有事务挂起，那么当前事务执行结束后，需要将挂起事务恢复
			resume(transaction, (SuspendedResourcesHolder) status.getSuspendedResources());
		}
	}


	//---------------------------------------------------------------------
	// Template methods to be implemented in subclasses
	//---------------------------------------------------------------------

	/**
	 * Return a transaction object for the current transaction state.
	 * <p>The returned object will usually be specific to the concrete transaction
	 * manager implementation, carrying corresponding transaction state in a
	 * modifiable fashion. This object will be passed into the other template
	 * methods (e.g. doBegin and doCommit), either directly or as part of a
	 * DefaultTransactionStatus instance.
	 * <p>The returned object should contain information about any existing
	 * transaction, that is, a transaction that has already started before the
	 * current {@code getTransaction} call on the transaction manager.
	 * Consequently, a {@code doGetTransaction} implementation will usually
	 * look for an existing transaction and store corresponding state in the
	 * returned transaction object.
	 * @return the current transaction object
	 * @throws org.springframework.transaction.CannotCreateTransactionException
	 * if transaction support is not available
	 * @throws TransactionException in case of lookup or system errors
	 * @see #doBegin
	 * @see #doCommit
	 * @see #doRollback
	 * @see DefaultTransactionStatus#getTransaction
	 */
	protected abstract Object doGetTransaction() throws TransactionException;

	/**
	 * Check if the given transaction object indicates an existing transaction
	 * (that is, a transaction which has already started).
	 * <p>The result will be evaluated according to the specified propagation
	 * behavior for the new transaction. An existing transaction might get
	 * suspended (in case of PROPAGATION_REQUIRES_NEW), or the new transaction
	 * might participate in the existing one (in case of PROPAGATION_REQUIRED).
	 * <p>The default implementation returns {@code false}, assuming that
	 * participating in existing transactions is generally not supported.
	 * Subclasses are of course encouraged to provide such support.
	 * @param transaction the transaction object returned by doGetTransaction
	 * @return if there is an existing transaction
	 * @throws TransactionException in case of system errors
	 * @see #doGetTransaction
	 */
	protected boolean isExistingTransaction(Object transaction) throws TransactionException {
		return false;
	}

	/**
	 * Return whether to use a savepoint for a nested transaction.
	 * <p>Default is {@code true}, which causes delegation to DefaultTransactionStatus
	 * for creating and holding a savepoint. If the transaction object does not implement
	 * the SavepointManager interface, a NestedTransactionNotSupportedException will be
	 * thrown. Else, the SavepointManager will be asked to create a new savepoint to
	 * demarcate the start of the nested transaction.
	 * <p>Subclasses can override this to return {@code false}, causing a further
	 * call to {@code doBegin} - within the context of an already existing transaction.
	 * The {@code doBegin} implementation needs to handle this accordingly in such
	 * a scenario. This is appropriate for JTA, for example.
	 * @see DefaultTransactionStatus#createAndHoldSavepoint
	 * @see DefaultTransactionStatus#rollbackToHeldSavepoint
	 * @see DefaultTransactionStatus#releaseHeldSavepoint
	 * @see #doBegin
	 */
	protected boolean useSavepointForNestedTransaction() {
		return true;
	}

	/**
	 * Begin a new transaction with semantics according to the given transaction
	 * definition. Does not have to care about applying the propagation behavior,
	 * as this has already been handled by this abstract manager.
	 *
	 * 根据给定的事务定义开始一个具有语义的新事务。不必关心应用传播行为，因为这已经由这个抽象管理器处理了。
	 *
	 * <p>This method gets called when the transaction manager has decided to actually
	 * start a new transaction. Either there wasn't any transaction before, or the
	 * previous transaction has been suspended.
	 * <p>A special scenario is a nested transaction without savepoint: If
	 * {@code useSavepointForNestedTransaction()} returns "false", this method
	 * will be called to start a nested transaction when necessary. In such a context,
	 * there will be an active transaction: The implementation of this method has
	 * to detect this and start an appropriate nested transaction.
	 * @param transaction the transaction object returned by {@code doGetTransaction}
	 * @param definition a TransactionDefinition instance, describing propagation
	 * behavior, isolation level, read-only flag, timeout, and transaction name
	 * @throws TransactionException in case of creation or system errors
	 * @throws org.springframework.transaction.NestedTransactionNotSupportedException
	 * if the underlying transaction does not support nesting
	 */
	protected abstract void doBegin(Object transaction, TransactionDefinition definition)
			throws TransactionException;

	/**
	 * Suspend the resources of the current transaction.
	 * Transaction synchronization will already have been suspended.
	 * <p>The default implementation throws a TransactionSuspensionNotSupportedException,
	 * assuming that transaction suspension is generally not supported.
	 * @param transaction the transaction object returned by {@code doGetTransaction}
	 * @return an object that holds suspended resources
	 * (will be kept unexamined for passing it into doResume)
	 * @throws org.springframework.transaction.TransactionSuspensionNotSupportedException
	 * if suspending is not supported by the transaction manager implementation
	 * @throws TransactionException in case of system errors
	 * @see #doResume
	 */
	protected Object doSuspend(Object transaction) throws TransactionException {
		throw new TransactionSuspensionNotSupportedException(
				"Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
	}

	/**
	 * Resume the resources of the current transaction.
	 * Transaction synchronization will be resumed afterwards.
	 * <p>The default implementation throws a TransactionSuspensionNotSupportedException,
	 * assuming that transaction suspension is generally not supported.
	 * @param transaction the transaction object returned by {@code doGetTransaction}
	 * @param suspendedResources the object that holds suspended resources,
	 * as returned by doSuspend
	 * @throws org.springframework.transaction.TransactionSuspensionNotSupportedException
	 * if resuming is not supported by the transaction manager implementation
	 * @throws TransactionException in case of system errors
	 * @see #doSuspend
	 */
	protected void doResume(@Nullable Object transaction, Object suspendedResources) throws TransactionException {
		throw new TransactionSuspensionNotSupportedException(
				"Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
	}

	/**
	 * Return whether to call {@code doCommit} on a transaction that has been
	 * marked as rollback-only in a global fashion.
	 * <p>Does not apply if an application locally sets the transaction to rollback-only
	 * via the TransactionStatus, but only to the transaction itself being marked as
	 * rollback-only by the transaction coordinator.
	 * <p>Default is "false": Local transaction strategies usually don't hold the rollback-only
	 * marker in the transaction itself, therefore they can't handle rollback-only transactions
	 * as part of transaction commit. Hence, AbstractPlatformTransactionManager will trigger
	 * a rollback in that case, throwing an UnexpectedRollbackException afterwards.
	 * <p>Override this to return "true" if the concrete transaction manager expects a
	 * {@code doCommit} call even for a rollback-only transaction, allowing for
	 * special handling there. This will, for example, be the case for JTA, where
	 * {@code UserTransaction.commit} will check the read-only flag itself and
	 * throw a corresponding RollbackException, which might include the specific reason
	 * (such as a transaction timeout).
	 * <p>If this method returns "true" but the {@code doCommit} implementation does not
	 * throw an exception, this transaction manager will throw an UnexpectedRollbackException
	 * itself. This should not be the typical case; it is mainly checked to cover misbehaving
	 * JTA providers that silently roll back even when the rollback has not been requested
	 * by the calling code.
	 * @see #doCommit
	 * @see DefaultTransactionStatus#isGlobalRollbackOnly()
	 * @see DefaultTransactionStatus#isLocalRollbackOnly()
	 * @see org.springframework.transaction.TransactionStatus#setRollbackOnly()
	 * @see org.springframework.transaction.UnexpectedRollbackException
	 * @see javax.transaction.UserTransaction#commit()
	 * @see javax.transaction.RollbackException
	 */
	protected boolean shouldCommitOnGlobalRollbackOnly() {
		return false;
	}

	/**
	 * Make preparations for commit, to be performed before the
	 * {@code beforeCommit} synchronization callbacks occur.
	 * <p>Note that exceptions will get propagated to the commit caller
	 * and cause a rollback of the transaction.
	 * @param status the status representation of the transaction
	 * @throws RuntimeException in case of errors; will be <b>propagated to the caller</b>
	 * (note: do not throw TransactionException subclasses here!)
	 */
	protected void prepareForCommit(DefaultTransactionStatus status) {
	}

	/**
	 * Perform an actual commit of the given transaction.
	 * <p>An implementation does not need to check the "new transaction" flag
	 * or the rollback-only flag; this will already have been handled before.
	 * Usually, a straight commit will be performed on the transaction object
	 * contained in the passed-in status.
	 * @param status the status representation of the transaction
	 * @throws TransactionException in case of commit or system errors
	 * @see DefaultTransactionStatus#getTransaction
	 */
	protected abstract void doCommit(DefaultTransactionStatus status) throws TransactionException;

	/**
	 * Perform an actual rollback of the given transaction.
	 * <p>An implementation does not need to check the "new transaction" flag;
	 * this will already have been handled before. Usually, a straight rollback
	 * will be performed on the transaction object contained in the passed-in status.
	 * @param status the status representation of the transaction
	 * @throws TransactionException in case of system errors
	 * @see DefaultTransactionStatus#getTransaction
	 */
	protected abstract void doRollback(DefaultTransactionStatus status) throws TransactionException;

	/**
	 * Set the given transaction rollback-only. Only called on rollback
	 * if the current transaction participates in an existing one.
	 * <p>The default implementation throws an IllegalTransactionStateException,
	 * assuming that participating in existing transactions is generally not
	 * supported. Subclasses are of course encouraged to provide such support.
	 * @param status the status representation of the transaction
	 * @throws TransactionException in case of system errors
	 */
	protected void doSetRollbackOnly(DefaultTransactionStatus status) throws TransactionException {
		throw new IllegalTransactionStateException(
				"Participating in existing transactions is not supported - when 'isExistingTransaction' " +
				"returns true, appropriate 'doSetRollbackOnly' behavior must be provided");
	}

	/**
	 * Register the given list of transaction synchronizations with the existing transaction.
	 * <p>Invoked when the control of the Spring transaction manager and thus all Spring
	 * transaction synchronizations end, without the transaction being completed yet. This
	 * is for example the case when participating in an existing JTA or EJB CMT transaction.
	 * <p>The default implementation simply invokes the {@code afterCompletion} methods
	 * immediately, passing in "STATUS_UNKNOWN". This is the best we can do if there's no
	 * chance to determine the actual outcome of the outer transaction.
	 * @param transaction the transaction object returned by {@code doGetTransaction}
	 * @param synchronizations a List of TransactionSynchronization objects
	 * @throws TransactionException in case of system errors
	 * @see #invokeAfterCompletion(java.util.List, int)
	 * @see TransactionSynchronization#afterCompletion(int)
	 * @see TransactionSynchronization#STATUS_UNKNOWN
	 */
	protected void registerAfterCompletionWithExistingTransaction(
			Object transaction, List<TransactionSynchronization> synchronizations) throws TransactionException {

		logger.debug("Cannot register Spring after-completion synchronization with existing transaction - " +
				"processing Spring after-completion callbacks immediately, with outcome status 'unknown'");
		invokeAfterCompletion(synchronizations, TransactionSynchronization.STATUS_UNKNOWN);
	}

	/**
	 * Cleanup resources after transaction completion.
	 * <p>Called after {@code doCommit} and {@code doRollback} execution,
	 * on any outcome. The default implementation does nothing.
	 * <p>Should not throw any exceptions but just issue warnings on errors.
	 * @param transaction the transaction object returned by {@code doGetTransaction}
	 */
	protected void doCleanupAfterCompletion(Object transaction) {
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();

		// Initialize transient fields.
		this.logger = LogFactory.getLog(getClass());
	}


	/**
	 * Holder for suspended resources.
	 * Used internally by {@code suspend} and {@code resume}.
	 */
	protected static final class SuspendedResourcesHolder {

		@Nullable
		private final Object suspendedResources;

		@Nullable
		private List<TransactionSynchronization> suspendedSynchronizations;

		@Nullable
		private String name;

		private boolean readOnly;

		@Nullable
		private Integer isolationLevel;

		private boolean wasActive;

		private SuspendedResourcesHolder(Object suspendedResources) {
			this.suspendedResources = suspendedResources;
		}

		private SuspendedResourcesHolder(
				@Nullable Object suspendedResources, List<TransactionSynchronization> suspendedSynchronizations,
				@Nullable String name, boolean readOnly, @Nullable Integer isolationLevel, boolean wasActive) {

			this.suspendedResources = suspendedResources;
			this.suspendedSynchronizations = suspendedSynchronizations;
			this.name = name;
			this.readOnly = readOnly;
			this.isolationLevel = isolationLevel;
			this.wasActive = wasActive;
		}
	}

}
