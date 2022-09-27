package com.springstudy.msb.s_27.tx_xml;

import com.springstudy.msb.s_27.tx_xml.service.AccountService;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 事务
 * @date 2022/6/14 5:30 下午
 */
public class Main {

	/**
	 * bug1：
	 * Exception in thread "main" org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'dataSource' defined in class path resource [spring-27-tx.xml]: Error setting property values; nested exception is org.springframework.beans.PropertyBatchUpdateException; nested PropertyAccessExceptions (1) are:
	 * PropertyAccessException 1: org.springframework.beans.MethodInvocationException: Property 'driverClassName' threw exception; nested exception is java.lang.IllegalStateException: Could not load JDBC driver class [com.mysql.jdbc.Driver]
	 * <p>
	 * 问题与解决：缺少mysql-connector-java的jar包，引入即可
	 */
	/**
	 * 1、{@link PlatformTransactionManager}：spring的事务管理器接口，里面提供了我们常用的操作事务的方法，例如：获取事务状态，提交事务、回滚事务
	 *
	 * 开发中使用的是它的实现类，也就是，真正管理事务的对象：
	 * （1）org.springframework.jdbc.datasource.DataSourceTransactionManager —— 使用SpringJDBC或iBatis进行持久化数据时使用
	 * （2）org.springframework.orm.hibernate5.HibernateTransactionManager —— 使用Hibernate版本进行持久化数据时使用
	 *
	 * 题外：在TransactionInterceptor中获取的事务管理器，必须要是当前PlatformTransactionManager的实例，否则报错！
	 *
	 * 2、{@link TransactionDefinition}：事务的定义信息接口，用于获取用户定义的事务信息。包含的方法操作：获取事务对象名称、获取事务隔离级别、获取事务传播行为、获取事务超时时间、获取事务是否只读
	 *
	 * ⚠️TransactionAttribute extends TransactionDefinition
	 *
	 * （1）事务的隔离级别
	 * 事务隔离级反映事务提交并发访问时的处理态度
	 * ISOLATION_DEFAULT：默认级别，归属下列某一种
	 * ISOLATION_READ_UNCOMMITTED：可以读取未提交数据
	 * ISOLATION_READ_COMMITTED：只能读取已提交数据，解决脏读问题(Oracle默认级别）
	 * ISOLATION_REPEATABLE_READ：是否读取其他事务提交修改后的数据，解决不可重复读问题(MySQL默认级别）
	 * ISOLATION_SERIALIZABLE：是否读取其他事务提交添加后的数据，解决幻影读问题
	 *
	 * （2）事务的传播行为
	 * REQUIRED：如果当前没有事务，就新建一个事务，如果已经存在一个事务中，加入到这个事务中。一般的选择（默认值）
	 * SUPPORTS：支持当前事务，如果当前没有事务，就以非事务方式执行（没有事务）
	 * MANDATORY：使用当前的事务，如果当前没有事务，就抛出异常
	 * REQUERS_NEW：新建事务，如果当前在事务中，把当前事务挂起。
	 * NOT_SUPPORTED：以非事务方式执行操作，如果当前存在事务，就把当前事务挂起
	 * NEVER：以非事务方式运行，如果当前存在事务，抛出异常
	 * NESTED：如果当前存在事务，则在嵌套事务内执行。如果当前没有事务，则执行 REQUIRED 类似的操作
	 *
	 * （3）超时时间
	 * 默认值是-1，代币哦没有超时限制。如果有，以秒为单位进行设置
	 *
	 * （4）是否是只读事务
	 * 建议查询时设置为只读
	 *
	 * 3、{@link TransactionStatus}：事务具体的运行状态接口。包含的方法操作：刷新事务、获取是否存在存储点、获取事务是否完成、获取事务是否为新的事务、获取事务是否回滚、设置事务回滚
	 * 作用：获取事务的状态，决定了方法事务的运行方式，例如是以事务方式运行，还是非事务方式运行、是以什么隔离级别运行事务
	 *
	 * 4、注意默认情况下Spring中的事务处理只对RuntimeException异常进行回滚，所以，如果将RuntimeException替换成普通的Exception不会产生回滚效果。
	 */
	public static void main(String[] args) {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("msb/spring-27-tx.xml");
		//BookService bookService = ac.getBean(BookService.class);
		//bookService.updateBalanceInService("eee", 1);

		// 编程式事务
		AccountService accountService = ac.getBean(AccountService.class);
		accountService.updateAccountById();

		BoBo bean = (BoBo) ac.getBean("boBo");
		System.out.println(bean);
	}

}