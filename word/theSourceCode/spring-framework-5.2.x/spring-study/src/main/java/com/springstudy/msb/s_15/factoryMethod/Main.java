package com.springstudy.msb.s_15.factoryMethod;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 实例化Bean的三种方式
 * <p>
 * 第一种方式：使用默认无参构造函数
 * <!--在默认情况下：它会根据默认无参构造函数来创建类对象。如果bean中没有默认无参构造函数，将会创建失败。-->
 * <bean id="accountService" class="com.itheima.service.impl.AccountServiceImpl"/>
 * <p>
 * 第二种方式：spring管理静态工厂-使用静态工厂的方法创建对象
 * <!--此种方式是:使用StaticFactory类中的静态方法createAccountService创建对象，并存入spring容器
 * id属性：指定bean的id，用于从容器中获取
 * class属性：指定静态工厂的全限定类名
 * factory-method属性：指定生产对象的静态方法
 * -->
 * <bean id="accountService" class="com.itheima.factory.StaticFactory" factory-method="createAccountService"></bean>
 * <p>
 * 第三种方式：spring管理实例工厂-使用实例工厂的方法创建对象
 * <!--此种方式是：先把工厂的创建交给spring来管理。然后在使用工厂的bean来调用里面的方法
 * factory-bean属性：用于指定实例工厂bean的id。
 * factory-method属性：用于指定实例工厂中创建对象的方法。
 * -->
 * <bean id="instancFactory" class="com.itheima.factory.InstanceFactory"></bean>
 * <bean id="accountService" factory-bean="instancFactory" factory-method="createAccountService"></bean>
 * <p>
 * <p>
 * 题外：Bean：在计算机英语中，有可重用组件的含义
 * @date 2022/5/6 2:39 下午
 */
public class Main {

	public static void main(String[] args) {
		ApplicationContext ac = new ClassPathXmlApplicationContext("msb/spring-15-factoryMethod-config.xml");
	}

}