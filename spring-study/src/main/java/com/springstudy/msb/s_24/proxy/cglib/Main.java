package com.springstudy.msb.s_24.proxy.cglib;

import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.cglib.proxy.Enhancer;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/6/6 10:03 下午
 */
public class Main {

	/**
	 * 回调方法存在的意义：
	 * （1）扩展；
	 * （2）不知道什么时候触发，例如：
	 * 我在执行实际逻辑的时候，我只需要提前把逻辑准备好，当我执行到某个点的时候，就去调用具体逻辑，而不是像原来写代码一样，全部都是以线性执行。
	 * 假设我写了一个业务逻辑处理类，一定是从第一行挨个往下执行，假如在中间某个环节，我需要异步执行另外一个逻辑，这个时候怎么办？是不是可以提前把逻辑设置进来，当我需要执行的时候，进行回调就可以了
	 *
	 * 题外：在进行实际的调用触发的时候，很可能包含其它的一些触发方式
	 */

	/**
	 * 1、在整个创建动态代理的过程中，会用到N多个缓存
	 * 2、jdk的流程和cglib的流程是一样的，只不过cglib做了更加复杂的处理，而且里面用了另外一个方式：fastClass
	 * 3、FastClass是一个抽象类，CGLib在运行时通过FastClass内的Generator这个内部类将其子类动态生成出来，然后再利用ClassLoader将生成的子类加载进JVM里面去.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		// 动态代理创建的class文件存储到本地
		System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "/Users/lishenglun/word/theSourceCode/spring-framework-5.2.x/spring-study/src/main/java/com/springstudy/mashibing/s_24/proxy/cglib");
		/**
		 * ⚠️⚠️面试可说的点：
		 * 在创建Enhancer的时候，就会准备一个缓存，里面包含一个类加载器和一个ClassLoadData，
		 * ClassLoadData包含了两个Function的处理逻辑，一个是为了获取生成器的key值，一个是为了获取字节码文件
		 */
		// 通过cglib动态代理获取代理对象的过程，创建调用的对象
		// 在后续的创建过程中需要EnhancerKey对象，所以在进行enhancer对象创建的时候需要把EnhancerKey(newInstance())对象准备好，
		// 恰好这个对象也需要动态代理来生成
		Enhancer enhancer = new Enhancer();
		// 设置enhancer对象的父类 —— 设置代理对象的父类 —— 设置需要代理的类
		enhancer.setSuperclass(MyCalculator.class);
		// 设置enhancer的回调对象
		enhancer.setCallback(new MyCglibMethodInvocation());
		// 创建代理对象
		MyCalculator myCalculator = (MyCalculator) enhancer.create();
		// 通过代理对象调用目标方法
		myCalculator.add(1, 1);
		System.out.println(myCalculator.getClass());
	}

}