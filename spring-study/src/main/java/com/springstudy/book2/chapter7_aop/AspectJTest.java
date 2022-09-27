package com.springstudy.book2.chapter7_aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description	Advisor
 * @date 2022/9/13 4:33 下午
 */
@Aspect
public class AspectJTest {

	@Pointcut("execution(* com.springstudy.book2.chapter7_aop.TestBean.*(..))")
	public void test() {

	}

	@Before("test()")
	public void beforeTest() {
		System.out.println("beforeTest");
	}

	@After("test()")
	public void afterTest() {
		System.out.println("afterTest");
	}

	@Around("test()")
	public Object aroundTest(ProceedingJoinPoint p) {
		System.out.println("before_around");

		Object o = null;
		try {
			o = p.proceed();
		} catch (Throwable throwable) {
			throwable.printStackTrace();
		}

		System.out.println("after_around");

		return o;
	}

	public void a(){

	}

}