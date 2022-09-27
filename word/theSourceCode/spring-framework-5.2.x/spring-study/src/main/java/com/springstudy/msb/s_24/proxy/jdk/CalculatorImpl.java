package com.springstudy.msb.s_24.proxy.jdk;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/6/6 4:33 下午
 */
public class CalculatorImpl implements Calculator {

	@Override
	public int add(int i, int j) {
		int result = i + j;
		return result;
	}

	@Override
	public int sub(int i, int j) {
		int result = i - j;
		return result;
	}

	@Override
	public int mult(int i, int j) {
		int result = i * j;
		return result;
	}

	@Override
	public int div(int i, int j) {
		int result = i / j;
		return result;
	}
}