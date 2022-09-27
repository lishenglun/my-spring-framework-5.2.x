package com.springstudy.msb.s_21;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;

import java.util.Arrays;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/30 10:44 下午
 */
//@Aspect
//@Component
//@Order(200)
public class LogUtil {

	//@Pointcut("execution(public Integer com.springstudy.mashibing.s_21.MyCalculator.*(Integer,Integer))")
	public void myPointCut() {
	}

	//@Pointcut("execution(* *(..))")
	public void myPointCut1() {
	}

	//@Before(value = "myPointCut()")
	public int start(JoinPoint joinPoint) {
		// 获取方法签名
		Signature signature = joinPoint.getSignature();
		Object[] args = joinPoint.getArgs();
		System.out.println("log---" + signature.getName() + "方法开始执行，参数是" + Arrays.asList(args) + "====before");
		return 100;
	}

	//@AfterReturning(value = "myPointCut()", returning = "result")
	public static void stop(JoinPoint joinPoint, Object result) {
		Signature signature = joinPoint.getSignature();
		System.out.println("log---" + signature.getName() + "方法执行结束，结果是:" + result + "====AfterReturning====后置通知");
	}

	//@AfterThrowing(value = "myPointCut()", throwing = "e")
	public static void logException(JoinPoint joinPoint, Exception e) {
		Signature signature = joinPoint.getSignature();
		System.out.println("log---" + signature.getName() + "方法抛出异常:" + e.getMessage() + "====AfterThrowing");
	}

	//@After("myPointCut()")
	public static void logFinally(JoinPoint joinPoint) {
		Signature signature = joinPoint.getSignature();
		System.out.println("log----" + signature.getName() + "方法执行结束。。。。over" + "====After=======最终通知");
	}

	//@After("myPointCut()")
	public Object around(ProceedingJoinPoint pjp) throws Throwable {
		Signature signature = pjp.getSignature();
		Object[] args = pjp.getArgs();
		Object result;
		try {
			System.out.println("log----" + signature.getName() + "方法开始执行，参数为" + args + "====around start...");
			// 执行方法
			// 通过反射的方式调用目标的方法，相当于执行method.invoke()，可以自己修改结果值
			result = pjp.proceed(args);
			// ⚠️在around里面修改结果，会影响最后的返回结果。但是刚刚在before里面修改是不会有任何影响的，因为根本就不会去获取它的返回值
			//result = 100
			System.out.println("log----" + signature.getName() + "方法执行结束" + "====around stop...");
		} catch (Throwable throwable) {
			System.out.println("log----" + signature.getName() + "出现异常" + "====around catch...");
			throw throwable;
		} finally {
			System.out.println("log----" + signature.getName() + "执行最终逻辑" + "====around finally...");
		}

		return result;
	}

}

