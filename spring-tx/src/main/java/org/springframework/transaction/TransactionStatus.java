/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.transaction;

import java.io.Flushable;

/**
 * 事务具体的运行状态接口，描述了某个时间点上事务对象的状态信息。包含的方法操作：刷新事务、获取是否存在存储点、获取事务是否完成、获取事务是否为新的事务、获取事务是否回滚、设置事务回滚
 *
 * 作用：保存事务运行时所需要的状态信息
 *
 * Representation of the status of a transaction.
 *
 * <p>Transactional code can use this to retrieve status information,
 * and to programmatically request a rollback (instead of throwing
 * an exception that causes an implicit rollback).
 *
 * <p>Includes the {@link SavepointManager} interface to provide access
 * to savepoint management facilities. Note that savepoint management
 * is only available if supported by the underlying transaction manager.
 *
 * @author Juergen Hoeller
 * @since 27.03.2003
 * @see #setRollbackOnly()
 * @see PlatformTransactionManager#getTransaction
 * @see org.springframework.transaction.support.TransactionCallback#doInTransaction
 * @see org.springframework.transaction.interceptor.TransactionInterceptor#currentTransactionStatus()
 */
public interface TransactionStatus extends TransactionExecution, SavepointManager, Flushable {

	/**
	 * 获取当前事务，是否存在"存储点"
	 *
	 * 题外："存储点"存在的意义：回滚到指定的位置！
	 *
	 * 题外：按步提交，将一个事务分为多个存储点，每个存储点都是一步，如果全部完成，则代表事务完成，如果失败，就会回滚到当前这一步
	 *
	 * Return whether this transaction internally carries a savepoint,
	 * that is, has been created as nested transaction based on a savepoint.
	 * <p>This method is mainly here for diagnostic purposes, alongside
	 * {@link #isNewTransaction()}. For programmatic handling of custom
	 * savepoints, use the operations provided by {@link SavepointManager}.
	 * @see #isNewTransaction()
	 * @see #createSavepoint()
	 * @see #rollbackToSavepoint(Object)
	 * @see #releaseSavepoint(Object)
	 */
	boolean hasSavepoint();

	/**
	 * 刷新事务
	 *
	 * Flush the underlying session to the datastore, if applicable:
	 * for example, all affected Hibernate/JPA sessions.
	 * <p>This is effectively just a hint and may be a no-op if the underlying
	 * transaction manager does not have a flush concept. A flush signal may
	 * get applied to the primary resource or to transaction synchronizations,
	 * depending on the underlying resource.
	 */
	@Override
	void flush();

}