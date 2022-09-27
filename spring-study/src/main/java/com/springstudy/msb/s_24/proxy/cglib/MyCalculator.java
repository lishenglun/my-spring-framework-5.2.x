package com.springstudy.msb.s_24.proxy.cglib;


/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/6/6 4:33 下午
 */
public class MyCalculator {

	public int add(int i, int j) {
		int result = i + j;
		return result;
	}

	public int sub(int i, int j) {
		int result = i - j;
		return result;
	}

	public int mult(int i, int j) {
		int result = i * j;
		return result;
	}

	public int div(int i, int j) {
		int result = i / j;
		return result;
	}

}