package com.springstudy.msb.s_21;

import org.springframework.aop.config.AopNamespaceHandler;
import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description AOP
 * @date 2022/5/31 3:35 下午
 */
public class Main {

	/**
	 * 1、不使用AOP的弊端：例如对一些方法加入日志来跟踪调试，如果直接修改源码并不符合面向对象的设计方法，而且随意改动原有代码也会造成一定的风险
	 *
	 * 2、AOP作用：使"辅助功能"可以独立于"核心业务"之外，方便与程序的"扩展"和"解耦"
	 *
	 * 3、AOP的实现方式：动态代理
	 *
	 * 4、AOP的NamespaceHandler：AopNamespaceHandler
	 *
	 * 5、查看AOP相关标签的解析器：{@link AopNamespaceHandler#init()}
	 *
	 * 6、jdk和cglib
	 *
	 * （1）jdk
	 *
	 * jdk创建器：JdkDynamicAopProxy
	 *
	 * InvocationHandler：JdkDynamicAopProxy
	 *
	 * （2）cglib
	 *
	 * cglib创建器：ObjenesisCglibAopProxy（题外：ObjenesisCglibAopProxy extends CglibAopProxy）
	 *
	 * 在cglib中，不是InvocationHandler，是Callback，且有一堆，最核心的Callback：DynamicAdvisedInterceptor
	 */
	public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException {
		saveGeneratedCGlibProxyFiles(System.getProperty("user.dir") + "/proxy");
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("msb/spring-21-aop.xml");
		MyCalculator bean = ac.getBean(MyCalculator.class);
		/**
		 * jdk走的拦截器是{@link JdkDynamicAopProxy#invoke(Object, Method, Object[])} }
		 *
		 * cglib走的拦截器都是{@link org.springframework.aop.framework.CglibAopProxy.DynamicAdvisedInterceptor#intercept(Object, Method, Object[], MethodProxy)}
		 */
		bean.add(1, 1);
	}

	public static void saveGeneratedCGlibProxyFiles(String dir) throws NoSuchFieldException, IllegalAccessException {
		Field field = System.class.getDeclaredField("props");
		field.setAccessible(true);
		Properties props = (Properties) field.get(null);
		System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, dir); // dir为保存文件路径
		props.put("net.sf.cglib.core.DebuggingClassWriter.traceEnabled", "true");
	}

}