package com.springstudy.book2.chapter7_aop.instrument;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

/**
 * agent类（Java代理）
 *
 * 1、题外：java.lang.instrument包：JDK5新增的。它类似一种更低级、更松耦合的AOP，可以在类加载时对字节码进行转换，来改变一个类的行为，从而实现AOP功能。相当于在JVM层面做了AOP支持。
 * >>> 通过java.lang.instrument包实现agent，使得"监控代码"和"应用代码"完全隔离了。
 *
 * 题外：ClassFileTransformer和Instrumentation都属于java.lang.instrument包下的
 */
public class PerfMonAgent {

	static private Instrumentation inst = null;

	/**
	 * 在main方法执行前调用
	 */
	public static void premain(String agentArgs, Instrumentation inst) {
		System.out.println("PerfMonAgent.premain() was called.");

		// Initialize the static variables we use to track information .
		// 初始化我们用来跟踪信息的静态变量。
		PerfMonAgent.inst = inst;

		/*

		实例化一个定制的ClassFileTransformer

		JVM启动时，在应用加载前会调用PerfMonAgent.premain()；
		然后PerfMonAgent.premain()中实例化了一个定制的ClassFileTransformer = PerfMonXformer；
		并通过inst.addTransformer(trans)把PerfMonXformer的实例加入Instrumentation实例(由JVM传入)；
		这就使得应用中的类加载时，PerfMonXformer.transform()都会被调用，你在此方法中可以改变加载的类

		*/
		// Set up the class file transformer
		// 设置类文件转换器
		ClassFileTransformer transformer = new PerfMonXformer();
		System.out.println("Adding a PerfMonXformer instance to the JVM.");
		inst.addTransformer(transformer);
	}

}