package com.springstudy.msb.s_14.replace;

import org.springframework.beans.factory.support.MethodReplacer;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/4 12:09 下午
 */
public class ReplaceDog implements MethodReplacer {

	/**
	 * Reimplement the given method.
	 *
	 * @param obj    the instance we're reimplementing the method for
	 * @param method the method to reimplement
	 * @param args   arguments to the method
	 * @return return value for the method
	 */
	@Override
	public Object reimplement(Object obj, Method method, Object[] args) throws Throwable {
		System.out.println("Hello,I am  white dog...");
		Arrays.stream(args).forEach(str -> System.out.println("参数：" + str));
		return obj;
	}

}