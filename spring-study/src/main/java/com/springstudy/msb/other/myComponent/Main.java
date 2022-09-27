package com.springstudy.msb.other.myComponent;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/7/29 10:59 下午
 */
public class Main {

	/**
	 * 测试：我自定义的一个注解@MyComponent，上面加上了@Component修饰。
	 * 然后用我自定义的@MyComponent去修饰一个类，看是否能够注册这个类的对象到容器中！
	 *
	 * 答案：能！
	 */
	public static void main(String[] args) {
		ApplicationContext ac = new AnnotationConfigApplicationContext(ScanConfig.class);
		System.out.println(ac.getBean(User.class));
	}

}