package com.springstudy.msb.s_07.selfEditor2;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 不使用xml的方式，自定义属性性编辑器
 * <p>
 * ⚠️注意：这个是在31课01:03:48讲解的
 * @date 2022/4/15 11:33 下午
 */
public class Main {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext classPathXmlApplicationContext = new AnnotationConfigApplicationContext(SelfEditor2Configuration.class);
		Customer customer = (Customer) classPathXmlApplicationContext.getBean("customer");
		System.out.println(customer);

		/**
		 * ⚠️扩展：如果是下面这种写法，那么就要显示调用refresh()，因为register()只是完成一个注册功能，并没有后续的解析工作，refresh()才是真正的解析工作！
		 */
		//classPathXmlApplicationContext.register(SelfEditor2Configuration.class);
		//classPathXmlApplicationContext.refresh();
	}

}