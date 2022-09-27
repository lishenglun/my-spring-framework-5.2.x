package com.springstudy.book2.chapter10_tx;

import org.springframework.aop.framework.autoproxy.InfrastructureAdvisorAutoProxyCreator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.config.TxNamespaceHandler;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/19 10:00
 */
//@EnableTransactionManagement
public class SpringTxMain {

	/**
	 * 1、<tx:annotation-driven transaction-manager="transactionManager"/>的命名空间：{@link TxNamespaceHandler}
	 *
	 * 2、从TxNamespaceHandler得知，<tx:annotation-driven>标签的解析器：
	 * {@link org.springframework.transaction.config.AnnotationDrivenBeanDefinitionParser}
	 *
	 * 3、AnnotationDrivenBeanDefinitionParser里面注册了：
	 * (1)注册"自动代理创建器bd" = {@link InfrastructureAdvisorAutoProxyCreator}
	 * key = org.springframework.aop.config.internalAutoProxyCreator
	 * (2)注册TransactionAttributeSource bd = {@link AnnotationTransactionAttributeSource}
	 * (3)注册{@link TransactionInterceptor} bd
	 * (4)注册{@link BeanFactoryTransactionAttributeSourceAdvisor} bd
	 * 里面adviceBeanName属性指向TransactionInterceptor beanName，transactionAttributeSource属性指向AnnotationTransactionAttributeSource beanName，
	 * 同时在BeanFactoryTransactionAttributeSourceAdvisor实例化的时候，内部会有一个{@link org.springframework.transaction.interceptor.TransactionAttributeSourcePointcut}，作为变量进行实例化
	 */
	public static void main(String[] args) {
		ApplicationContext ac = new ClassPathXmlApplicationContext("book2/chapter10_tx/spring-tx-config.xml");

		UserService userService = (UserService) ac.getBean("userService");
		User user = new User();
		user.setName("zhangsan");
		user.setAge(20);
		user.setSex("男");
		userService.save(user);
	}

}