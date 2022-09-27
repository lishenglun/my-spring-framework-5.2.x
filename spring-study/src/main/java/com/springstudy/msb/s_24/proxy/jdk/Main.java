package com.springstudy.msb.s_24.proxy.jdk;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/6/6 5:33 下午
 */
public class Main {

	/**
	 * 1、不管是jdk、cglib，都是用asm实现的
	 * 2、asm：专门的字节码框架
	 * @param args
	 */
	public static void main(String[] args) {
		// 准备好方法，准备好属性，然后直接进行写就完事了
		// 知道怎么生成，怎么往里面写即可，把你需要的东西写下来即可
		System.getProperties().put("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");
		Calculator proxy = CalculatorProxy.getProxy(new CalculatorImpl());
		proxy.add(1, 1);
		System.out.println(proxy.getClass());
	}

}