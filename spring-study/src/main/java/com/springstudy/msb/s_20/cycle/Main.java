package com.springstudy.msb.s_20.cycle;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 循坏依赖
 * @date 2022/5/25 4:13 下午
 */
public class Main {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("msb/spring-20-cycle.xml");
		System.out.println("==============================");
		A a = ac.getBean(A.class);
		// a.testPoint();
		B b = ac.getBean(B.class);
		System.out.println(b);
		System.out.println("==============================");
	}

}

// ⚠️可忽略
//class T2{
//	public static void main(String[] args) {
//		// runtimeOnly group: 'org.aspectj', name: 'aspectjweaver', version: '1.9.5'
//		Constructor<?>[] declaredConstructors = AspectJMethodBeforeAdvice.class.getDeclaredConstructors();
//		System.out.println(declaredConstructors);
//	}
//}