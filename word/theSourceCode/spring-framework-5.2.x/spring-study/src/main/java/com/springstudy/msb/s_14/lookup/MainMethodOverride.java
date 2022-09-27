package com.springstudy.msb.s_14.lookup;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description <bean lookup-method="">标签
 *
 * spring中默认对象都是单例的，spring会在一级缓存中持有该对象，方便下次直接获取，
 * 那么如果是原型作用域（scope=prototype，代表多例）的话，不会存入spring的一级缓存，每次都会创建一个新的对象
 *
 * 如果想在一个单例模式的bean下引用一个原型模式的bean，怎么办？
 * 默认情况下，单例模式下引入多例bean，多例bean也只会变成单例bean，因为单例bean是固定的，存入单例bean中的多例bean，也会被赋予固定的bean对象
 *
 * 【<lookup-method>标签】或者【@Lookup注解】可以解决此问题，原理是：对单例bean进行动态代理（cglib的方式），然后每次通过方法获取对象的时候，会走动态代理对应的拦截器，
 * 在拦截器的内部每次都会执行getBean()方法获取对象，因为多例bean不会在一级缓存中，所以每次都是重新创建新对象，然后返回；
 * 如果单例bean当中是引入单例bean的话，由于单例bean是会存在于一级缓存中的，所以每次getBean()获取到的是同一个单例对象
 * —— ⚠️原理总结：通过cglib创建代理bean对象，然后通过拦截器的方式，每次获取引入的bean对象都走getBean()方法，如果引入的是单例，由于单例会缓存，所以每次都可以从缓存中获取到都是同一个对象，即单例；如果引入的是多例(原型)，多例不会缓存，所以每次都是创建新的对象，所以得到的是多例
 *
 * 源码：AbstractBeanFactory#getBean() ——> AbstractBeanFactory#doGetBean() ——> DefaultSingletonBeanRegistry#getSingleton
 * ——> singletonFactory.getObject() ——> AbstractAutowireCapableBeanFactory#createBean() ——> AbstractAutowireCapableBeanFactory#doCreateBean()
 * ——> AbstractAutowireCapableBeanFactory#createBeanInstance() ——> AbstractAutowireCapableBeanFactory#instantiateBean()
 * ——> beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, this);
 *
 * 拦截器：LookupOverrideMethodInterceptor#intercept()
 *
 * @date 2022/5/4 10:51 上午
 */
public class MainMethodOverride {

	/**
	 * 题外：当前这个例子举得并不恰当。FruitPlate可以不需要是抽象的！
	 * @param args
	 */
	public static void main(String[] args) {

		// ⚠️<lookup-method>解决的问题：保证单例可以引用原型（保证单例中获取的原型对象每次都是最新的对象）

		ApplicationContext ac = new ClassPathXmlApplicationContext("msb/spring-14-methodOverride-config.xml");
		FruitPlate fruitPlate1 = (FruitPlate) ac.getBean("fruitPlate1");
		// 单例引入单例
		System.out.println(fruitPlate1.getFruit());
		System.out.println(fruitPlate1.getFruit());
		System.out.println("===========");
		// 单例引入原型
		FruitPlate fruitPlate2 = (FruitPlate) ac.getBean("fruitPlate2");
		System.out.println(fruitPlate2.getFruit());
		System.out.println(fruitPlate2.getFruit());
	}

}