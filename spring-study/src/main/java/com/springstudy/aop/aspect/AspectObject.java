//package com.springstudy.aop.aspect;
//
//import org.aspectj.lang.JoinPoint;
//import org.aspectj.lang.annotation.Aspect;
//import org.aspectj.lang.annotation.Before;
//import org.aspectj.lang.annotation.Pointcut;
//import org.springframework.context.annotation.EnableAspectJAutoProxy;
//import org.springframework.stereotype.Component;
//
///**
// * TODO
// *
// * @author lishenglun
// * @version v1.1
// * @since 2020/8/30 7:49 下午
// */
//@Aspect
//@Component
//public class AspectObject {
//
//	// 定义切点（切入位置）
//	@Pointcut("execution(* com.springstudy.aop.aopObject.*.*(..))")
//	private void pointcut() {
//	}
//
//	@Before("pointcut()")
//	public void before(JoinPoint joinPoint) {
//		System.out.println("我是前置通知");
//	}
//
//}