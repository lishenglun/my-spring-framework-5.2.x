package com.springstudy.msb.s_27.tx_xml.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/20 21:13
 */
public class AccountServiceImpl implements AccountService {

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Override
	public void updateAccountById() {
		//开启事务保存数据
		boolean result = transactionTemplate.execute(new TransactionCallback<Boolean>() {
			@Override
			public Boolean doInTransaction(TransactionStatus status) {
				try {
					// TODO something
				} catch (Exception e) {
					//TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); //手动开启事务回滚
					status.setRollbackOnly();
					return false;
				}
				return true;
			}
		});
	}

	@Override
	public void updateAccountByName() {
		/**
		 * 定义事务
		 */
		DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		def.setReadOnly(false);
		//隔离级别,-1表示使用数据库默认级别
		def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
		def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
		// 获取事务
		TransactionStatus status = transactionManager.getTransaction(def);
		try {
			//TODO something
			transactionManager.commit(status);
		} catch (Exception e) {
			transactionManager.rollback(status);

			throw new RuntimeException("异常失败");
		}
	}




}