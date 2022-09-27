package com.springstudy.Initializethebean.lookup;

import com.springstudy.config.LookupConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 测试@Lockup
 * https://www.cnblogs.com/wl20200316/p/12850300.html
 */
public class UseLookupTest {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext(LookupConfig.class);

		context.getBean("singletonBean", SingletonBean.class).print();
		context.getBean("singletonBean", SingletonBean.class).print();
		context.getBean("singletonBean", SingletonBean.class).print();
		context.getBean("singletonBean", SingletonBean.class).print();
		context.getBean("singletonBean", SingletonBean.class).print();


	}

}