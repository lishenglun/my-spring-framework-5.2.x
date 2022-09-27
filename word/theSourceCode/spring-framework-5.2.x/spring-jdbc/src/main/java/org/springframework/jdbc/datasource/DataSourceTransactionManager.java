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

package org.springframework.jdbc.datasource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.*;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * {@link org.springframework.transaction.PlatformTransactionManager}
 * implementation for a single JDBC {@link javax.sql.DataSource}. This class is
 * capable of working in any environment with any JDBC driver, as long as the setup
 * uses a {@code javax.sql.DataSource} as its {@code Connection} factory mechanism.
 * Binds a JDBC Connection from the specified DataSource to the current thread,
 * potentially allowing for one thread-bound Connection per DataSource.
 *
 * <p><b>Note: The DataSource that this transaction manager operates on needs
 * to return independent Connections.</b> The Connections may come from a pool
 * (the typical case), but the DataSource must not return thread-scoped /
 * request-scoped Connections or the like. This transaction manager will
 * associate Connections with thread-bound transactions itself, according
 * to the specified propagation behavior. It assumes that a separate,
 * independent Connection can be obtained even during an ongoing transaction.
 *
 * <p>Application code is required to retrieve the JDBC Connection via
 * {@link DataSourceUtils#getConnection(DataSource)} instead of a standard
 * Java EE-style {@link DataSource#getConnection()} call. Spring classes such as
 * {@link org.springframework.jdbc.core.JdbcTemplate} use this strategy implicitly.
 * If not used in combination with this transaction manager, the
 * {@link DataSourceUtils} lookup strategy behaves exactly like the native
 * DataSource lookup; it can thus be used in a portable fashion.
 *
 * <p>Alternatively, you can allow application code to work with the standard
 * Java EE-style lookup pattern {@link DataSource#getConnection()}, for example for
 * legacy code that is not aware of Spring at all. In that case, define a
 * {@link TransactionAwareDataSourceProxy} for your target DataSource, and pass
 * that proxy DataSource to your DAOs, which will automatically participate in
 * Spring-managed transactions when accessing it.
 *
 * <p>Supports custom isolation levels, and timeouts which get applied as
 * appropriate JDBC statement timeouts. To support the latter, application code
 * must either use {@link org.springframework.jdbc.core.JdbcTemplate}, call
 * {@link DataSourceUtils#applyTransactionTimeout} for each created JDBC Statement,
 * or go through a {@link TransactionAwareDataSourceProxy} which will create
 * timeout-aware JDBC Connections and Statements automatically.
 *
 * <p>Consider defining a {@link LazyConnectionDataSourceProxy} for your target
 * DataSource, pointing both this transaction manager and your DAOs to it.
 * This will lead to optimized handling of "empty" transactions, i.e. of transactions
 * without any JDBC statements executed. A LazyConnectionDataSourceProxy will not fetch
 * an actual JDBC Connection from the target DataSource until a Statement gets executed,
 * lazily applying the specified transaction settings to the target Connection.
 *
 * <p>This transaction manager supports nested transactions via the JDBC 3.0
 * {@link java.sql.Savepoint} mechanism. The
 * {@link #setNestedTransactionAllowed "nestedTransactionAllowed"} flag defaults
 * to "true", since nested transactions will work without restrictions on JDBC
 * drivers that support savepoints (such as the Oracle JDBC driver).
 *
 * <p>This transaction manager can be used as a replacement for the
 * {@link org.springframework.transaction.jta.JtaTransactionManager} in the single
 * resource case, as it does not require a container that supports JTA, typically
 * in combination with a locally defined JDBC DataSource (e.g. an Apache Commons
 * DBCP connection pool). Switching between this local strategy and a JTA
 * environment is just a matter of configuration!
 *
 * <p>As of 4.3.4, this transaction manager triggers flush callbacks on registered
 * transaction synchronizations (if synchronization is generally active), assuming
 * resources operating on the underlying JDBC {@code Connection}. This allows for
 * setup analogous to {@code JtaTransactionManager}, in particular with respect to
 * lazily registered ORM resources (e.g. a Hibernate {@code Session}).
 *
 * @author Juergen Hoeller
 * @since 02.05.2003
 * @see #setNestedTransactionAllowed
 * @see java.sql.Savepoint
 * @see DataSourceUtils#getConnection(javax.sql.DataSource)
 * @see DataSourceUtils#applyTransactionTimeout
 * @see DataSourceUtils#releaseConnection
 * @see TransactionAwareDataSourceProxy
 * @see LazyConnectionDataSourceProxy
 * @see org.springframework.jdbc.core.JdbcTemplate
 */
@SuppressWarnings("serial")
public class DataSourceTransactionManager extends AbstractPlatformTransactionManager
		implements ResourceTransactionManager, InitializingBean {

	@Nullable
	private DataSource dataSource;

	private boolean enforceReadOnly = false;


	/**
	 * Create a new DataSourceTransactionManager instance.
	 * A DataSource has to be set to be able to use it.
	 * @see #setDataSource
	 */
	public DataSourceTransactionManager() {
		setNestedTransactionAllowed(true);
	}

	/**
	 * Create a new DataSourceTransactionManager instance.
	 * @param dataSource the JDBC DataSource to manage transactions for
	 */
	public DataSourceTransactionManager(DataSource dataSource) {
		this();
		setDataSource(dataSource);
		afterPropertiesSet();
	}


	/**
	 * Set the JDBC DataSource that this instance should manage transactions for.
	 * <p>This will typically be a locally defined DataSource, for example an
	 * Apache Commons DBCP connection pool. Alternatively, you can also drive
	 * transactions for a non-XA J2EE DataSource fetched from JNDI. For an XA
	 * DataSource, use JtaTransactionManager.
	 * <p>The DataSource specified here should be the target DataSource to manage
	 * transactions for, not a TransactionAwareDataSourceProxy. Only data access
	 * code may work with TransactionAwareDataSourceProxy, while the transaction
	 * manager needs to work on the underlying target DataSource. If there's
	 * nevertheless a TransactionAwareDataSourceProxy passed in, it will be
	 * unwrapped to extract its target DataSource.
	 * <p><b>The DataSource passed in here needs to return independent Connections.</b>
	 * The Connections may come from a pool (the typical case), but the DataSource
	 * must not return thread-scoped / request-scoped Connections or the like.
	 * @see TransactionAwareDataSourceProxy
	 * @see org.springframework.transaction.jta.JtaTransactionManager
	 */
	public void setDataSource(@Nullable DataSource dataSource) {
		if (dataSource instanceof TransactionAwareDataSourceProxy) {
			// If we got a TransactionAwareDataSourceProxy, we need to perform transactions
			// for its underlying target DataSource, else data access code won't see
			// properly exposed transactions (i.e. transactions for the target DataSource).
			this.dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
		}
		else {
			this.dataSource = dataSource;
		}
	}

	/**
	 * Return the JDBC DataSource that this instance manages transactions for.
	 */
	@Nullable
	public DataSource getDataSource() {
		return this.dataSource;
	}

	/**
	 * Obtain the DataSource for actual use.
	 * @return the DataSource (never {@code null})
	 * @throws IllegalStateException in case of no DataSource set
	 * @since 5.0
	 */
	protected DataSource obtainDataSource() {
		DataSource dataSource = getDataSource();
		Assert.state(dataSource != null, "No DataSource set");
		return dataSource;
	}

	/**
	 * Specify whether to enforce the read-only nature of a transaction
	 * (as indicated by {@link TransactionDefinition#isReadOnly()}
	 * through an explicit statement on the transactional connection:
	 * "SET TRANSACTION READ ONLY" as understood by Oracle, MySQL and Postgres.
	 * <p>The exact treatment, including any SQL statement executed on the connection,
	 * can be customized through {@link #prepareTransactionalConnection}.
	 * <p>This mode of read-only handling goes beyond the {@link Connection#setReadOnly}
	 * hint that Spring applies by default. In contrast to that standard JDBC hint,
	 * "SET TRANSACTION READ ONLY" enforces an isolation-level-like connection mode
	 * where data manipulation statements are strictly disallowed. Also, on Oracle,
	 * this read-only mode provides read consistency for the entire transaction.
	 * <p>Note that older Oracle JDBC drivers (9i, 10g) used to enforce this read-only
	 * mode even for {@code Connection.setReadOnly(true}. However, with recent drivers,
	 * this strong enforcement needs to be applied explicitly, e.g. through this flag.
	 * @since 4.3.7
	 * @see #prepareTransactionalConnection
	 */
	public void setEnforceReadOnly(boolean enforceReadOnly) {
		this.enforceReadOnly = enforceReadOnly;
	}

	/**
	 * Return whether to enforce the read-only nature of a transaction
	 * through an explicit statement on the transactional connection.
	 * @since 4.3.7
	 * @see #setEnforceReadOnly
	 */
	public boolean isEnforceReadOnly() {
		return this.enforceReadOnly;
	}

	@Override
	public void afterPropertiesSet() {
		if (getDataSource() == null) {
			throw new IllegalArgumentException("Property 'dataSource' is required");
		}
	}


	@Override
	public Object getResourceFactory() {
		return obtainDataSource();
	}

	/**
	 * 获取数据源事务对象（DataSourceTransactionObject），然后往里面设置：是否允许当前事务添加"保存点"的标识、连接持有器
	 *
	 * 题外："保存点"只跟nested传播特性(嵌套事务)有关，其它事务传播特性用不到"保存点"，只有当开启了嵌套事务，才允许添加保存点。
	 * 题外：是从resources ThreadLoad中，获取数据源对应的连接持有器(ConnectionHolder)，设置到事务实例中。第一次连接持有器为null。后续创建完成之后会进行设置工作，方便下一次获取。
	 */
	@Override
	protected Object doGetTransaction() {
		/* 1、创建事务对象 */
		DataSourceTransactionObject txObject = new DataSourceTransactionObject();

		/* 2、设置属性 */

		/*

		(1)设置【是否允许当前事务添加"保存点"的标识】
		保存点只跟嵌套事务有关，只有当开启了嵌套事务，才允许添加保存点

		*/
		// 题外："保存点"只跟nested传播特性(嵌套事务)有关，其它事务传播特性用不到"保存点"，只有当开启了嵌套事务，才允许添加保存点。
		txObject.setSavepointAllowed/* 设置允许保存点 */(isNestedTransactionAllowed/* 是否允许嵌套事务 */());
		/**
		 * TransactionSynchronizationManager事务同步管理器对象(该类中都是局部线程变量)：
		 * 用来保存当前事务的信息，我们第一次从这里去线程变最中获取"事务连接持有器对象"，通过数据源为key去获取
		 * 由于第一次进来开始事务，我们的事务同步管理器中没有被存放，所以此时获取出来的conHolder为null
		 */
		/*

		(2)从本地线程变量中获取，连接持有器(ConnectionHolder)，设置到事务对象中。

		题外：刚调用事务方法，第一次连接持有器为null，后续会创建连接持有器，然后放入本地线程变量中，方便后面嵌套的事物方法获取

		*/
		/**
		 * 连接持有器的作用：如果当前线程已经存在数据库连接，则使用原有连接
		 */
		// 获取连接持有器。
		// 题外：在第一次调用的时候，连接持有器一定为null，后续创建完连接持有器之后，会放入本地线程变量，方便后面嵌套的事物方法获取
		ConnectionHolder/* 连接持有器 */ conHolder =
				// ⚠️从resources ThreadLoad中获取数据源对应的连接持有器
				(ConnectionHolder) TransactionSynchronizationManager.getResource(obtainDataSource()/* 获取数据源 */);
		// 设置连接持有器
		txObject.setConnectionHolder(conHolder, false/* false：非新创建的连接包装器 */);

		// 返回事务对象
		return txObject;
	}

	/**
	 * 判断当前线程是否存在事务：如果当前线程有连接持有器，并且事务是激活的，就返回true，代表存在事务
	 *
	 * 题外：当前第一次是没有连接持有器的，所以当前事务不存在！
	 * 题外：刚刚获取的事务，是新创建的事务对象，这里是要看一下，是否之前就存在了事务对象
	 *
	 * @param transaction the transaction object returned by doGetTransaction
	 * @return
	 */
	@Override
	protected boolean isExistingTransaction(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		// 判断有没有连接持有器(本质是看是否存在连接)
		return (txObject.hasConnectionHolder()/* 是否存在连接持有器() */
				// 判断事务是不是激活的
					&& txObject.getConnectionHolder().isTransactionActive()/* 事务是否是激活的 */);
	}

	/**
	 * 开启事务
	 * （1）如果事务对象中不存在连接持有器，那么就获取新的连接，和构建新的连接持有器对象包装连接，然后把连接持有器设置到事务对象中
	 * （2）将TransactionDefinition中的事务属性设置到事务对象中：是否只读、隔离级别、关闭自动提交、标记事务激活、设置事务超时时间
	 * （3）很重要的一点，如果创建了一个新的连接持有器，则绑定当前线程新获取的连接持有器到当前线程变量中，方便后续嵌套的事务方法复用相同的连接，达到融入外层事务的作用
	 *
	 * 构造transaction,包括设置ConnectionHolder、隔离级别、timeout、如果是新连接，绑定到当前线程
	 *
	 * @param transaction the transaction object returned by {@code doGetTransaction}
	 *                    	事务对象
	 * @param definition a TransactionDefinition instance, describing propagation
	 *                   	事务定义
	 * behavior, isolation level, read-only flag, timeout, and transaction name
	 */
	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) {
		// 事务对象
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		Connection con = null;

		try {
			/* 1、如果事务对象中不存在连接持有器，那么就获取新的连接，和构建新的连接持有器对象包装连接，然后把连接持有器设置到事务对象中 */
			// 不存在连接持有器 || SynchronizedWithTransaction=true
			if (!txObject.hasConnectionHolder() ||
					txObject.getConnectionHolder().isSynchronizedWithTransaction()) {

				/* 获取连接 */
				// 通过数据源获取一个数据库连接
				Connection newCon = obtainDataSource().getConnection();

				if (logger.isDebugEnabled()) {
					logger.debug("Acquired Connection [" + newCon + "] for JDBC transaction");
				}

				/* 创建一个ConnectionHolder，包装连接，然后再把ConnectionHolder设置到事务对象中 */
				txObject.setConnectionHolder(new ConnectionHolder(newCon), true/* 表示这是一个新的连接持有器 */);
			}

			/* 2、设置事务的属性：是否只读、隔离级别、关闭自动提交、标记事务激活、设置事务超时时间 */

			// 标记当前的连接是一个同步事务
			// 跟我们当前的事务来进行相关的绑定操作（绑定，也就是，跟事务进行一个同步）
			// 设置一下，是否跟我们当前的事务保持同步值
			txObject.getConnectionHolder().setSynchronizedWithTransaction/* 设置与事务同步 */(true);
			con = txObject.getConnectionHolder().getConnection();

			/* 是否只读、隔离级别 */
			// 设置数据库的只读标识、隔离级别
			Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
			// 设置先前隔离级别
			txObject.setPreviousIsolationLevel(previousIsolationLevel);
			// 设置是否只读
			txObject.setReadOnly(definition.isReadOnly());

			/* 关闭自动提交 */
			// Switch to manual commit if necessary. This is very expensive in some JDBC drivers,
			// so we don't want to do it unnecessarily (for example if we've explicitly
			// configured the connection pool to set it already).
			// 上面翻译：如有必要，切换到手动提交。这在一些JDBC驱动程序中非常昂贵，所以我们不想不必要地这样做（例如，如果我们已经显式配置连接池以设置它）。

			// 如果是自动提交，则关闭自动提交
			// 题外：如果想自己控制事务的提交/回滚，必须把自动提交给关闭掉！如果不关掉的话，那么每次执行完sql语句，它就自动提交，就没办法自己控制事务提交/回滚了。
			if (con.getAutoCommit()) {
				// 设置"是否需要恢复自动提交的标识"为true，表达的意思是：日后是否需要恢复为自动提交。
				// 这里因为我们设置关闭了自动提交，所以日后需要恢复为自动提交，让连接恢复它原来的状态
				txObject.setMustRestoreAutoCommit(true);
				if (logger.isDebugEnabled()) {
					logger.debug("Switching JDBC Connection [" + con + "] to manual commit");
				}
				// ⚠️关闭自动提交，这样后续的提交和回滚就由spring进行控制了
				con.setAutoCommit(false);
			}

			// 判断事务是否需要设置为只读事务
			prepareTransactionalConnection/* 准备当前事务的连接 */(con, definition);

			/* 标记事务激活 */
			// 标记事务激活，也就是：⚠️设置判断当前线程是否存在事务的依据
			txObject.getConnectionHolder().setTransactionActive(true);

			/* 设置事务超时时间 */
			// 设置事务超时时间
			int timeout = determineTimeout(definition);
			// 如果超时时间不是默认值的话，就进行设置
			if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
				txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
			}

			/*

			3、如果是一个新的连接持有器，则绑定当前线程新获取的连接持有器到当前线程变量中，方便后续嵌套的事务方法复用相同的连接，达到融入外层事务的作用

			注意：⚠️绑定当前线程新获取的连接持有器到当前线程变量中，这一点很重要。
			如果后续嵌套的事务方法传播特性是融入外层事务，就可以通过本地线程变量中的连接持有器，获取到外层事务的连接，使用和外层事务相同的连接，从而达到融入外层事务的作用

			*/
			// Bind the connection holder to the thread. —— 将连接持有器绑定到当前线程
			if (txObject.isNewConnectionHolder()/* 判断是不是一个新的连接持有器 */) {
				/**
				 * 在绝大多数的情况下，我们的事务，我们的连接，都是共用的；除非指定了独特的传播特性的时候，它才有可能不共用，而去创建一个新的事务，新连接。
				 * 既然大部分情况是同一个，我们就要保证复用性，所以放入一个本地线程变量中，作为缓存；
				 * 而我们在进行sql语句提交的时候，存在并发的情况，所以把map结构放入ThreadLocal里面，跟当前的线程进行绑定，避免多线程的并发影响
				 * 数据源为key，连接持有器为value，构成一个map，存入当前线程变量ThreadLocal，名称为resources！
				 */
				// 将当前线程新获取的连接持有器，绑定到当前线程变量中
				TransactionSynchronizationManager.bindResource(obtainDataSource(), txObject.getConnectionHolder());
			}
		}
		catch (Throwable ex) {
			if (txObject.isNewConnectionHolder()) {
				// 释放数据库连接
				DataSourceUtils.releaseConnection(con, obtainDataSource());
				txObject.setConnectionHolder(null, false);
			}
			throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", ex);
		}
	}

	@Override
	protected Object doSuspend(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		// 设置连接持有器为null
		txObject.setConnectionHolder(null);
		return TransactionSynchronizationManager.unbindResource/* 解绑资源 */(obtainDataSource());
	}

	@Override
	protected void doResume(@Nullable Object transaction, Object suspendedResources) {
		TransactionSynchronizationManager.bindResource(obtainDataSource(), suspendedResources);
	}

	@Override
	protected void doCommit(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		// 获取连接
		Connection con = txObject.getConnectionHolder().getConnection();
		if (status.isDebug()) {
			logger.debug("Committing JDBC transaction on Connection [" + con + "]");
		}
		try {
			// ⚠️JDBC连接提交
			con.commit();
		}
		catch (SQLException ex) {
			throw new TransactionSystemException("Could not commit JDBC transaction", ex);
		}
	}

	/**
	 * 真正的回滚处理方法，获取jdbc连接，然后回滚
	 * @param status the status representation of the transaction
	 */
	@Override
	protected void doRollback(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		Connection con = txObject.getConnectionHolder().getConnection();
		if (status.isDebug()) {
			logger.debug("Rolling back JDBC transaction on Connection [" + con + "]");
		}
		try {
			// jdbc的回滚
			con.rollback();
		}
		catch (SQLException ex) {
			throw new TransactionSystemException("Could not roll back JDBC transaction", ex);
		}
	}

	/**
	 * 设置回滚标记，如果既没有保存点，又不是新事务，如果可以设置全局的回滚标记的话，就会设置。
	 *
	 * @param status the status representation of the transaction
	 */
	@Override
	protected void doSetRollbackOnly(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		if (status.isDebug()) {
			logger.debug("Setting JDBC transaction [" + txObject.getConnectionHolder().getConnection() +
					"] rollback-only");
		}
		// 设置当前事务是需要回滚的！
		txObject.setRollbackOnly();
	}

	/**
	 * 此方法做清除连接相关操作，比如重置自动提交啊，只读属性啊，解绑数据源啊，释放连接啊，清除连接持有器属性
	 *
	 * @param transaction the transaction object returned by {@code doGetTransaction}
	 */
	@Override
	protected void doCleanupAfterCompletion(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;

		// Remove the connection holder from the thread, if exposed.
		if (txObject.isNewConnectionHolder()) {
			// 将数据库连接从当前线程中解除绑定
			TransactionSynchronizationManager.unbindResource(obtainDataSource());
		}

		// Reset connection.
		// 释放链接
		Connection con = txObject.getConnectionHolder().getConnection();
		try {
			if (txObject.isMustRestoreAutoCommit()) {
				// 恢复数据库连接的自动提交属性
				con.setAutoCommit(true);
			}
			// 重置数据库连接
			DataSourceUtils.resetConnectionAfterTransaction(
					con, txObject.getPreviousIsolationLevel(), txObject.isReadOnly());
		}
		catch (Throwable ex) {
			logger.debug("Could not reset JDBC Connection after transaction", ex);
		}

		if (txObject.isNewConnectionHolder()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Releasing JDBC Connection [" + con + "] after transaction");
			}
			// ⚠️如果当前事务是独立的新创建的事务，则在事务完成时释放数据库连接
			DataSourceUtils.releaseConnection(con, this.dataSource);
		}

		txObject.getConnectionHolder().clear();
	}


	/**
	 * Prepare the transactional {@code Connection} right after transaction begin.
	 * <p>The default implementation executes a "SET TRANSACTION READ ONLY" statement
	 * if the {@link #setEnforceReadOnly "enforceReadOnly"} flag is set to {@code true}
	 * and the transaction definition indicates a read-only transaction.
	 * <p>The "SET TRANSACTION READ ONLY" is understood by Oracle, MySQL and Postgres
	 * and may work with other databases as well. If you'd like to adapt this treatment,
	 * override this method accordingly.
	 * @param con the transactional JDBC Connection
	 * @param definition the current transaction definition
	 * @throws SQLException if thrown by JDBC API
	 * @since 4.3.7
	 * @see #setEnforceReadOnly
	 */
	protected void prepareTransactionalConnection(Connection con, TransactionDefinition definition)
			throws SQLException {

		if (isEnforceReadOnly/* 是强制只读 */() && definition.isReadOnly()) {
			try (Statement stmt = con.createStatement()) {
				// 说当前的事务是一个只读事务
				// 只读事务：在将事务设置成只读后，当前只读事务就不能进行写的操作，否则报错。
				stmt.executeUpdate("SET TRANSACTION READ ONLY"/* 将事务设置为只读 */);
			}
		}
	}


	/**
	 * 数据源事务对象。代表一个连接持有器，用作事务管理器的事务对象。
	 *
	 * 数据源事务对象：做了一个连接相关的属性设置。
	 *
	 * DataSource transaction object, representing a ConnectionHolder.
	 * Used as transaction object by DataSourceTransactionManager.
	 */
	private static class DataSourceTransactionObject extends JdbcTransactionObjectSupport {

		// 是否是一个新的连接持有器（true：新的；false：不是新的）
		private boolean newConnectionHolder;

		// 是否需要恢复自动提交的标识（true：需要，false：不需要），表达的意思是：日后是否需要恢复为自动提交。
		// 一般来说，当我们设置关闭了自动提交，则日后需要恢复为自动提交，让连接恢复它原来的状态
		private boolean mustRestoreAutoCommit;

		public void setConnectionHolder(@Nullable ConnectionHolder connectionHolder, boolean newConnectionHolder) {
			super.setConnectionHolder(connectionHolder);
			this.newConnectionHolder = newConnectionHolder;
		}

		public boolean isNewConnectionHolder() {
			return this.newConnectionHolder;
		}

		public void setMustRestoreAutoCommit(boolean mustRestoreAutoCommit) {
			this.mustRestoreAutoCommit = mustRestoreAutoCommit;
		}

		public boolean isMustRestoreAutoCommit() {
			return this.mustRestoreAutoCommit;
		}

		/**
		 * 设置回滚标记，就是设置连接持有器的回滚标记
		 */
		public void setRollbackOnly() {
			// 将rollbackOnly设置改为true，标记当前事务是需要回滚的状态！
			getConnectionHolder().setRollbackOnly();
		}

		@Override
		public boolean isRollbackOnly() {
			return getConnectionHolder().isRollbackOnly();
		}

		@Override
		public void flush() {
			if (TransactionSynchronizationManager.isSynchronizationActive()) {
				TransactionSynchronizationUtils.triggerFlush();
			}
		}
	}

}
